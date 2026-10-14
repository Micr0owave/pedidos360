# Evaluacion Parcial 3 - RabbitMQ en Pedidos360

## Que se hizo

| Servicio | Cambio |
|---|---|
| `ms-pedidos360-orders` (productor) | Nombres de exchanges, colas y routing keys movidos a `application.yml` (`p360.messaging`). `RabbitConfig` y `CommandPublisher` los leen de `MessagingProperties`. `OrderService` ya no tiene routing keys escritas: usa `CommandType`. |
| `ms-pedidos360-notify` (consumidor) | El archivo unico se separo en `config/`, `consumer/`, `service/`, `messaging/` y `exception/`. Un consumidor por dominio (`EmailConsumer`, `KitchenConsumer`, `InvoiceConsumer`), ACK manual, reintentos y DLQ. |
| `ms-pedidos360-mqadmin` (**nuevo**, puerto 8086) | API REST para crear/eliminar colas, exchanges y bindings, con servicio dedicado, validacion y JWT (rol ADMIN). |

La logica de negocio (pedidos, Kafka, auditoria, reportes) no se toco.

## Donde esta cada indicador de la pauta

| Indicador | Donde |
|---|---|
| Nombres centralizados (12%) | `p360.messaging` en `application.yml` de orders y notify; `MessagingProperties` |
| Beans Queue/Exchange/Binding por caso de uso (13%) | `RabbitConfig` (orders y notify): email, kitchen e invoice, cada uno con cola, DLQ y bindings directo/topic/DLX |
| Negocio separado de la configuracion (10%) | `config/` solo configura; `service/` solo negocio; consumidores solo delegan |
| `@RabbitListener` por dominio (15%) | `notify/consumer/` |
| ACK y manejo de errores (20%) | `notify/messaging/MessageAcknowledger` (+ `IdempotencyGuard`) |
| Endpoints REST del administrador (13%) | `mqadmin/controller/` |
| Logica en servicio dedicado (10%) | `mqadmin/service/RabbitAdminService` |
| Validacion de entrada (7%) | DTOs con `@NotBlank/@Pattern/@Size` + `GlobalExceptionHandler` + prefijo `amq.` bloqueado |

## Como se comporta el consumidor

| Situacion | Resultado |
|---|---|
| Mensaje procesado bien | `basicAck` |
| Mismo `messageId` ya procesado | `basicAck` sin repetir el trabajo (log `[IDEMPOTENCIA]`) |
| Error no recuperable (`payload.forceError=true`) | `basicNack` sin requeue -> DLQ + log `[DLQ]` con el motivo |
| Error recuperable (`payload.forceTransient=true`) | se republica con `x-retry-count` (hasta `max-retries`=3); luego DLQ |

## Evidencia rapida (perfil local)

```bash
bash scripts/levantar-local.sh                 # en Windows: .\scripts\levantar-local.ps1
bash scripts/prueba_flujo.sh                   # flujo normal: se ven [EMAIL], [COCINA], [BOLETA] en notify
```
Para ver la DLQ, publica a mano desde la consola de RabbitMQ (http://localhost:15672, p360/p360pass), exchange `cmd.direct`, routing key `email.send`, con las propiedades `message_id` (cualquier texto) y `content_type` = `application/json`, y este cuerpo:
`{"type":"Prueba","eventId":"x","timestamp":"2025-01-01T00:00:00Z","traceId":"t","correlationId":"c","actor":"yo","payload":{"forceError":true}}`
En el log de notify aparece `[DLQ]` y el mensaje queda registrado en `q.cmd.email.dlq`. Con `"forceTransient":true` se ven 3 lineas de reintento y luego la DLQ.

El administrador se prueba con `scripts/pruebas.http` (casos 15 al 22) o en `http://localhost:8086/swagger-ui.html`.

## Notas
- Los reintentos son inmediatos (sin espera); en produccion se usaria una cola de retardo con TTL.
- Si una cola ya existe con otros argumentos, RabbitMQ rechaza redeclararla (PRECONDITION_FAILED); el administrador lo responde como 409.
- Borrar un exchange inexistente responde 204: RabbitMQ lo trata como exito.
- `mqadmin` no esta detras del BFF: se accede directo por el puerto 8086 con un token de rol ADMIN.
