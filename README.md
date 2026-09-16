# Pedidos360

Sistema cloud-native de gestión de pedidos: Angular 18 con MSAL, microservicios Spring Boot tras un BFF, RabbitMQ para comandos, Kafka para eventos, y despliegue con Docker Compose sobre EC2 detrás de AWS API Gateway.

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
