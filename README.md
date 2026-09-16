# Pedidos360

Sistema cloud-native de gestión de pedidos: Angular 18 con MSAL, microservicios Spring Boot tras un BFF, RabbitMQ para comandos, Kafka para eventos, y despliegue con Docker Compose sobre EC2 detrás de AWS API Gateway.

## Por dónde empezar

Si vuelves al proyecto después de un rato, lee **`docs/00-EMPIEZA-AQUI.md`**. Dice qué está hecho, qué falta y en qué orden avanzar.

| Documento | Para qué sirve |
|---|---|
| `docs/00-EMPIEZA-AQUI.md` | Estado real del proyecto y plan de cierre |
| `docs/CONFIG.md` | Todos los IDs, URLs y credenciales en un solo lugar |
| `docs/MENSAJERIA.md` | Por qué está cada pieza: RabbitMQ, Kafka, BFF, Gateway |
| `docs/ARQUITECTURA.md` | Diseño técnico y topologías |
| `docs/AZURE-AD.md` | Configuración de Entra ID paso a paso |
| `docs/GITHUB.md` | Publicar el repositorio |
| `docs/EC2-DESPLIEGUE.md` | Las tres instancias y el frontend con Nginx |
| `docs/AWS-API-GATEWAY.md` | HTTP API con JWT Authorizer |
| `docs/EVIDENCIAS.md` | Checklist de capturas |
| `docs/PRESENTACION.md` | Guion de la demostración y preguntas frecuentes |

## Qué hay aquí

| Carpeta | Qué es | Puerto |
|---|---|---|
| `frontend-pedidos360/` | Angular 18 con MSAL y guards por rol | 4200 |
| `ms-pedidos360-bff/` | Punto único de entrada, valida JWT y enruta | 8080 |
| `ms-pedidos360-orders/` | Pedidos, máquina de estados, produce a RabbitMQ y Kafka | 8081 |
| `ms-pedidos360-catalog/` | Productos y stock | 8082 |
| `ms-pedidos360-notify/` | Consume comandos RabbitMQ (email, cocina, boleta) | 8083 |
| `ms-pedidos360-audit/` | Consume `audit.timeline` y persiste el historial | 8084 |
| `ms-pedidos360-report/` | Consume `orders.events` y expone KPIs | 8085 |
| `infra/mq/` | RabbitMQ con las 6 colas, 3 exchanges y bindings ya cargados | 5672 / 15672 |
| `infra/kafka/` | Zookeeper + Kafka + creación automática de tópicos | 9092 / 8090 |
| `infra/apps/` | Compose de las aplicaciones para la EC2 | — |
| `scripts/` | Pruebas sin Postman y arranque local | — |

El frontend incluye una pantalla en `/diagnostico` que muestra el access token con botón de copiar, lo decodifica en una tabla y chequea en vivo que los seis servicios respondan. Sirve para probar la API sin pelear con la pestaña Network y como evidencia durante la presentación.

## Arrancar en 5 minutos

```bash
docker compose -f infra/mq/compose.yml up -d
docker compose -f infra/kafka/compose.yml up -d
# espera ~40 segundos

cd ms-pedidos360-catalog && mvn spring-boot:run -Dspring-boot.run.profiles=local
# en otra terminal, lo mismo para orders, notify, audit, report, bff
```

En Windows puedes levantar la infraestructura y los seis servicios con:

```powershell
.\scripts\levantar-local.ps1
```

El perfil `local` desactiva la validación de JWT, así puedes probar todo el flujo antes de tener Azure AD configurado. Para la entrega desplegada se usa el perfil por defecto, que sí valida el token.

Después:

```bash
bash scripts/prueba_flujo.sh
```

En Windows también puedes ejecutarlo directamente desde PowerShell, sin WSL:

```powershell
.\scripts\prueba_flujo.ps1
```

Ese script crea un pedido, intenta despacharlo sin aceptarlo (debe fallar con 409), recorre el flujo válido completo y consulta los KPIs. Es la evidencia más rápida de que todo funciona.

## Cómo probar los endpoints sin Postman

Tres opciones, todas sirven para capturas:

- **Swagger UI**: `http://localhost:8081/swagger-ui.html`. Botón "Try it out" en cada endpoint. Si activaste seguridad, pega el token en "Authorize".
- **REST Client de VS Code**: abre `scripts/pruebas.http` y haz clic en "Send Request". El archivo queda versionado en el repo como evidencia extra.
- **curl**: ver `scripts/prueba-flujo.sh`.

## Decisiones tomadas y por qué

**H2 en modo Oracle como base por defecto.** El caso pide Oracle. El perfil `oracle` ya está escrito en cada `application.yml` con el driver `ojdbc11` incluido: solo cambias `SPRING_PROFILES_ACTIVE=oracle` y las variables de conexión. Mientras tanto H2 corre con `MODE=Oracle`, así el SQL generado es equivalente y no pierdes tiempo levantando la base. Documenta esto en tu informe como decisión consciente, no como carencia.

**La topología de RabbitMQ está declarada dos veces**: en `infra/mq/definitions.json` (se carga al arrancar el contenedor) y en `RabbitConfig.java` (se declara al arrancar el servicio). Es redundante a propósito: funciona con un Rabbit limpio y también con uno preconfigurado.

**El stock decrece al ACEPTAR, no al crear.** Es la regla explícita del caso y está implementada en `OrderService.dispatchCommands()`.

**`orders` publica a `orders.events` y a `audit.timeline`.** Report consume el primero, audit el segundo. Ninguno de los dos consulta la base de datos del core, que es lo que pide el enunciado.

## Cómo demostrar la DLQ

Los consumidores de `notify` fallan a propósito si el payload trae `forceError: true`. Publica un mensaje así desde el Management UI de RabbitMQ (Exchanges → `cmd.direct` → Publish message, routing key `email.send`) y verás el mensaje aparecer en `q.cmd.email.dlq` después de los reintentos. Captura las dos colas.
