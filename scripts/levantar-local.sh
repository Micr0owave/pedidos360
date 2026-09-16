#!/usr/bin/env bash
# Levanta infra + los 6 servicios en tu maquina, en perfil local (sin Azure AD).
set -e
cd "$(dirname "$0")/.."

echo "== Levantando RabbitMQ =="
docker compose -f infra/mq/compose.yml up -d

echo "== Levantando Kafka + Zookeeper =="
docker compose -f infra/kafka/compose.yml up -d

echo "Esperando a que la infraestructura quede lista (40s)..."
sleep 40

for SVC in orders catalog notify audit report bff; do
  echo "== Iniciando ms-pedidos360-$SVC =="
  (cd ms-pedidos360-$SVC && mvn -q spring-boot:run -Dspring-boot.run.profiles=local > /tmp/$SVC.log 2>&1 &)
  sleep 5
done

echo
echo "Servicios iniciando. Revisa los logs en /tmp/<servicio>.log"
echo "  Swagger orders : http://localhost:8081/swagger-ui.html"
echo "  RabbitMQ UI    : http://localhost:15672  (p360 / p360pass)"
echo "  Kafka UI       : http://localhost:8090"
