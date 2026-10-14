#!/usr/bin/env bash
# Comprueba la conectividad de cada pieza del despliegue en AWS. Ejecutar DESDE la EC2 de apps.
# Uso: bash scripts/verificar-aws.sh <ip-privada-mq> <ip-privada-kafka> <endpoint-rds> [invoke-url-api-gateway]
MQ="$1"; KAFKA="$2"; RDS="$3"; GW="$4"
if [ -z "$MQ" ] || [ -z "$KAFKA" ] || [ -z "$RDS" ]; then
  echo "Uso: $0 <ip-mq> <ip-kafka> <endpoint-rds> [invoke-url]"; exit 2
fi
fail=0
tcp() { # nombre host puerto
  if timeout 5 bash -c "exec 3<>/dev/tcp/$2/$3" 2>/dev/null; then echo "OK   $1 ($2:$3)"
  else echo "FALLA $1 ($2:$3)  -> revisa Security Group e IP privada"; fail=1; fi
}
http() { # nombre url
  code=$(curl -s -o /dev/null -m 8 -w '%{http_code}' "$2")
  if [ "$code" = "200" ]; then echo "OK   $1 ($2)"; else echo "FALLA $1 ($2) -> HTTP $code"; fail=1; fi
}
echo "== Conectividad de red =="
tcp "RabbitMQ" "$MQ" 5672
tcp "Kafka" "$KAFKA" 9092
tcp "RDS Oracle" "$RDS" 1521
echo "== Servicios (health) =="
for p in 8080 8081 8082 8084 8085 8086; do http "servicio :$p" "http://localhost:$p/actuator/health"; done
if [ -n "$GW" ]; then
  echo "== API Gateway =="
  http "gateway -> BFF health" "${GW%/}/actuator/health"
fi
echo
[ $fail -eq 0 ] && echo "Todo bien." || echo "Hay fallas: ver mensajes arriba."
exit $fail
