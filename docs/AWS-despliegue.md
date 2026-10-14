# Despliegue en AWS – Pedidos360

Arquitectura (la del README):

```
Frontend Angular ──HTTPS──► API Gateway (HTTP API) ──► EC2 apps :8080 (BFF)
                                                          │  red privada (misma VPC)
                       ┌──────────────┬───────────────────┼────────────────┐
                       ▼              ▼                   ▼                ▼
                EC2 mq :5672     EC2 kafka :9092     RDS Oracle :1521   (orders, catalog,
                (RabbitMQ)       (Kafka+Zookeeper)   (base de datos)     notify, mqadmin, audit, report)
```

> Este documento no pudo probarse contra tu cuenta de AWS: son los pasos y valores que el repo espera. Cada paso tiene su comprobación.

## 0. Antes de empezar
- Región única para todo (ej. `us-east-1`) y **una sola VPC**.
- Todos los servicios se hablan por **IP privada**. Solo se expone al exterior: API Gateway → BFF (8080).

## 1. Security Groups (el 80 % de los "no conecta" está aquí)

| SG | Entrada permitida |
|---|---|
| `sg-apps` (EC2 apps) | 22 desde tu IP · 8080 desde `0.0.0.0/0` (lo usa API Gateway) · 8086 desde tu IP (mqadmin) |
| `sg-mq` (EC2 mq) | 22 y 15672 desde tu IP · **5672 desde `sg-apps`** |
| `sg-kafka` (EC2 kafka) | 22 desde tu IP · **9092 desde `sg-apps`** |
| `sg-rds` (RDS) | **1521 desde `sg-apps`** (no abrir al mundo; RDS "Public access: No") |

## 2. Base de datos (RDS Oracle)
1. RDS → Create database → **Oracle** (SE2, *License included*), plantilla Dev/Test, `db.t3.medium` o similar.
2. *Initial database name*: `ORCL`. Usuario maestro y contraseña: anótalos.
3. Misma VPC, `sg-rds`, Public access **No**. Tarda 10–20 minutos.
4. Copia el **Endpoint** (`xxxx.xxxx.us-east-1.rds.amazonaws.com`).
5. En `infra/apps/.env` de la EC2 apps:
   ```
   SPRING_PROFILES_ACTIVE=docker,oracle
   ORACLE_URL=jdbc:oracle:thin:@//<ENDPOINT>:1521/ORCL
   ORACLE_USER=<usuario maestro>
   ORACLE_PASSWORD=<contraseña>
   ```
   Con el perfil `oracle`, orders, catalog, audit y report crean sus tablas `P360_*` solas (`ddl-auto: update`). Sin ese perfil usan H2 en memoria.
> RDS Oracle no entra en la capa gratuita. Si tu curso usa Oracle Autonomous con *wallet*, la URL cambia: avísame y la adapto.

## 3. EC2 de RabbitMQ y de Kafka
En cada una (Amazon Linux 2023, `t3.small`): `sudo dnf install -y docker git && sudo systemctl enable --now docker`, instala el plugin compose y clona el repo.
```bash
# EC2 mq
docker compose -f infra/mq/compose.yml up -d            # consola: http://<IP-mq>:15672  (p360 / p360pass)
# EC2 kafka  (KAFKA_HOST = IP PRIVADA de esta misma VM)
KAFKA_HOST=<IP-privada-kafka> docker compose -f infra/kafka/compose.yml up -d
```
Comprobación: en la consola de RabbitMQ aparecen `cmd.direct`, `cmd.topic`, `cmd.dead.dlx` y las 6 colas; en Kafka-UI los 4 tópicos.

## 4. EC2 de las aplicaciones
Usa `t3.medium` (4 GB): son 6 Spring Boot en una máquina; con 2 GB se queda sin memoria.
```bash
cd infra/apps && cp .env.example .env && nano .env      # rellena todas las líneas
export COMPOSE_PARALLEL_LIMIT=1                          # construye de a un servicio (menos memoria)
docker compose up -d --build
```
Valores clave de `.env`: `MQ_HOST` e `KAFKA_HOST` = **IP privada** de cada VM, `AZURE_TENANT_ID`, `API_CLIENT_ID`, `ORACLE_*` y `CORS_ORIGINS` (ver paso 6).

## 5. API Gateway
1. API Gateway → **HTTP API** → Build.
2. Integración: *HTTP* → `http://<IP-publica-EC2-apps>:8080/{proxy}` (usa una Elastic IP para que no cambie al reiniciar).
3. Ruta: `ANY /{proxy+}`. Stage `$default` con auto-deploy.
4. **No actives CORS en el gateway**: ya lo maneja el BFF (`CORS_ORIGINS`); activar ambos duplica las cabeceras y el navegador falla.
5. Copia el *Invoke URL* (`https://abc123.execute-api.us-east-1.amazonaws.com`).

## 6. Frontend y Azure AD
- Azure AD **solo acepta redirect URIs `https` o `http://localhost`**. No sirve `http://<IP-EC2>`.
- Opción simple para la presentación: correr el frontend en tu PC contra AWS. En `frontend-pedidos360/src/environments/environment.ts` pon `apiBaseUrl` = *Invoke URL*, y `CORS_ORIGINS` debe incluir `http://localhost:4200`. Luego `npm ci && npx ng serve`.
- Opción desplegada: S3 + CloudFront (HTTPS) con `ng build --configuration production`. Edita antes `environment.prod.ts` (`redirectUri`, `postLogoutRedirectUri`, `apiBaseUrl`), agrega esa URL en Azure AD → App registration del frontend → Authentication → *Single-page application*, y ponla en `CORS_ORIGINS`.

## 7. Comprobación de punta a punta
```bash
bash scripts/verificar-aws.sh <IP-privada-mq> <IP-privada-kafka> <endpoint-rds> <invoke-url>
```
Luego, en el frontend: inicia sesión → pantalla `/diagnostico` (token + seis servicios en verde) → crea un pedido y llévalo a ENTREGADO → en `notify` aparecen `[EMAIL]`, `[COCINA]`, `[BOLETA]` (`docker logs p360-notify`).

## Problemas típicos
| Síntoma | Causa probable |
|---|---|
| `Connection refused` a RabbitMQ/Kafka | Falta la regla de `sg-mq`/`sg-kafka` desde `sg-apps`, o `MQ_HOST`/`KAFKA_HOST` con IP pública |
| Kafka conecta pero falla al producir | `KAFKA_HOST` de la VM de Kafka no es su IP privada (advertised listener) |
| `ORA-12541` / timeout al arrancar | `sg-rds` no permite 1521 desde `sg-apps`, o endpoint mal copiado |
| `ORA-01017` | usuario/clave incorrectos; el servicio (`ORCL`) debe coincidir con el *Initial database name* |
| 401 desde el BFF | token con otra audiencia/versión: revisa `/api/me` y `AZURE_TENANT_ID`/`API_CLIENT_ID` |
| 403 en `/api/report` o mqadmin | el usuario no tiene el App Role `ADMIN` en Azure AD |
| Error CORS en el navegador | la URL del frontend no está en `CORS_ORIGINS`, o hay CORS activo también en API Gateway |
| Un contenedor se reinicia solo | poca memoria: usa `t3.medium` y `COMPOSE_PARALLEL_LIMIT=1` |
