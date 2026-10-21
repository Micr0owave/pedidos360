# Despliegue en AWS Academy – Pedidos360 (paso a paso)

**Plan simple (una sola VM)**: RabbitMQ, Kafka y las 7 apps corren con Docker en **una** EC2. API Gateway le da una URL https. Sin Oracle: se usa H2 en memoria (perfil `docker`), así no dependes de RDS.

```
Frontend (tu PC, localhost:4200) ─► API Gateway (HTTP API, https) ─► EC2 :8080 (BFF) ─► orders/catalog/notify/mqadmin/... + RabbitMQ + Kafka
```

## Limitaciones de AWS Academy que importan
- Tienes ~US$100 de crédito. Si se acaba se pierde el laboratorio. **No uses NAT Gateway ni RDS** (siguen cobrando aunque cierres la sesión).
- Al terminar la sesión (4 h por defecto, botón *Start Lab* la reinicia) las EC2 se **detienen** y su IP pública cambia. Los recursos siguen existiendo; *Reset* los borra todos.
- No puedes crear usuarios/roles IAM: no hace falta, las EC2 no los necesitan.
- Trabaja siempre en **us-east-1** (la región que abre el laboratorio).
- Fuente de límites: [guía Learner Lab](https://josejuansanchez.org/iaw/taller-aws/resources/guia_aws_academy_learner_lab.pdf). Los tamaños de instancia permitidos pueden variar por curso: si `t3.large` no aparece, usa `t3.medium` y quita Kafka (ver Problemas).

## Paso 1 – Entrar al laboratorio
1. Canvas/AWS Academy → tu curso → **Módulos → Learner Lab** → **Start Lab**. Espera el círculo verde.
2. Pulsa **AWS** (abre la consola). Arriba a la derecha confirma región **N. Virginia (us-east-1)**.

## Paso 2 – Security Group
EC2 → *Security Groups* → **Create**: nombre `sg-p360`, VPC por defecto. Reglas de entrada:

| Puerto | Origen | Para qué |
|---|---|---|
| 22 | My IP | SSH |
| 8080 | 0.0.0.0/0 | BFF (lo usa API Gateway) |
| 15672 | My IP | consola RabbitMQ (la presentación) |
| 8086 | My IP | Swagger de mqadmin (opcional) |

Los puertos internos (5672, 9092, 8081…) funcionan entre contenedores y no se abren.

## Paso 3 – Lanzar la EC2
EC2 → **Launch instance**:
- Nombre `p360`, AMI **Amazon Linux 2023**, tipo **t3.large** (8 GB: son 7 Java + RabbitMQ + Kafka).
- Key pair: *Create new* → `vockey` ya existe en Academy; úsala y descarga el `.pem` desde *AWS Details* del laboratorio si no lo tienes.
- Security group: `sg-p360`. Disco 30 GiB. **Launch**.
- EC2 → *Elastic IPs* → **Allocate** → **Associate** a `p360` (la IP pública ya no cambia al reiniciar el laboratorio).

## Paso 4 – Instalar Docker y levantar todo
Conéctate (EC2 → instancia → **Connect → EC2 Instance Connect**, no necesitas el .pem):
```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
sudo usermod -aG docker ec2-user && newgrp docker

git clone -b ep3-rabbitmq-aws https://github.com/Micr0owave/pedidos360.git && cd pedidos360
PRIV=$(hostname -I | awk '{print $1}'); echo $PRIV          # IP privada

docker compose -f infra/mq/compose.yml up -d
KAFKA_HOST=$PRIV docker compose -f infra/kafka/compose.yml up -d

cd infra/apps && cp .env.example .env
sed -i "s/^MQ_HOST=.*/MQ_HOST=$PRIV/; s/^KAFKA_HOST=.*/KAFKA_HOST=$PRIV/; s/^SPRING_PROFILES_ACTIVE=.*/SPRING_PROFILES_ACTIVE=docker/" .env
nano .env          # revisa AZURE_TENANT_ID, API_CLIENT_ID y CORS_ORIGINS=http://localhost:4200
export COMPOSE_PARALLEL_LIMIT=1
docker compose up -d --build                              # tarda 10-15 min
```
**Comprobación**: `http://<ElasticIP>:15672` (p360 / p360pass) muestra colas y exchanges; `curl http://localhost:8080/actuator/health` responde UP.

## Paso 5 – API Gateway (el "gateway")
1. Consola → **API Gateway** → **Create API** → **HTTP API → Build**.
2. *Integrations → Add integration*: **HTTP**, método `ANY`, URL `http://<ElasticIP>:8080/{proxy}`.
3. *Configure routes*: método `ANY`, recurso `/{proxy+}`, apuntando a esa integración.
4. Stage `$default`, *auto-deploy* activado. **Create**.
5. Copia el **Invoke URL** (`https://abc123.execute-api.us-east-1.amazonaws.com`).
6. **No actives CORS en API Gateway**: ya lo hace el BFF.
7. Prueba: `curl <Invoke URL>/actuator/health` debe dar `UP`.

## Paso 6 – Frontend contra AWS
Azure AD solo acepta redirect `https` o `http://localhost`, así que para la presentación corre el frontend en tu PC:
1. En `frontend-pedidos360/src/environments/environment.ts` pon `apiBaseUrl` = Invoke URL.
2. `npm ci && npx ng serve` → `http://localhost:4200`.
3. `CORS_ORIGINS` en la EC2 ya incluye `http://localhost:4200`.

## Paso 7 – Rol CLIENTE (usuario sin admin) en Azure
Portal Azure → **Microsoft Entra ID → App registrations → la app de la API** (`API_CLIENT_ID`):
1. **App roles → Create app role**: Display name `Cliente`, Allowed member types **Users/Groups**, Value `Cliente`, activar → Apply. (Si ya existe `Admin`, queda junto a él.)
2. **Entra ID → Enterprise applications →** la misma app → **Users and groups → Add user/group** → elige un usuario de prueba → rol **Cliente** → Assign. (Crea el usuario antes en *Users → New user*.)
3. Tu usuario actual debe seguir con el rol `Admin`.
4. Cierra sesión en el frontend e ingresa con el usuario cliente: el menú muestra solo lo permitido (sin Mensajería ni Reportes/Auditoría) y las rutas de admin devuelven 403.
5. Ingresa de nuevo como admin: la barra lateral muestra `Admin` y aparece **Mensajería**.

## Paso 8 – Comprobación de punta a punta
```bash
bash scripts/verificar-aws.sh <IP-privada> <IP-privada> - <invoke-url>
```
(si el script pide endpoint RDS, pásale un guion; la comprobación de BD se omite.) Luego en el frontend: `/diagnostico` en verde → crear pedido hasta ENTREGADO → `docker logs p360-notify` muestra `[EMAIL]`, `[COCINA]`, `[BOLETA]`.

## Cada vez que reinicias el laboratorio
Start Lab → las EC2 arrancan solas con la misma Elastic IP. Entra por SSH y: `cd pedidos360/infra/mq && docker compose up -d`, igual en `infra/kafka` (con `KAFKA_HOST`) y `infra/apps`. (Los contenedores tienen `restart` solo si lo definiste; revisa con `docker ps`.) H2 es en memoria: los datos se pierden al reiniciar apps.

## Problemas típicos
| Síntoma | Causa probable |
|---|---|
| No abre `:15672` o `:8080` | regla de `sg-p360` falta, o tu IP cambió (actualiza *My IP*) |
| Contenedor se reinicia solo | poca memoria: `t3.large`, `COMPOSE_PARALLEL_LIMIT=1`; con `t3.medium` apaga Kafka y los servicios audit/report |
| Kafka conecta pero falla al producir | `KAFKA_HOST` no es la IP privada de la VM |
| 401 en el BFF | revisa `AZURE_TENANT_ID` / `API_CLIENT_ID` y `/api/me` |
| 403 en Mensajería/Reportes | el usuario no tiene el rol `Admin` asignado en *Enterprise applications* |
| Error CORS | URL del frontend no está en `CORS_ORIGINS`, o hay CORS activo también en API Gateway |
| Quieres Oracle | RDS Oracle puede estar restringido en Academy y cobra aunque cierres sesión; usa `SPRING_PROFILES_ACTIVE=docker,oracle` y `ORACLE_*` solo si tu profesor lo exige |
