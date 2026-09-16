#!/usr/bin/env bash
# Prueba el flujo completo sin Postman. Requiere los servicios corriendo con perfil local.
# Uso:  bash scripts/prueba-flujo.sh
set -e
ORDERS=${ORDERS:-http://localhost:8081}
CATALOG=${CATALOG:-http://localhost:8082}
REPORT=${REPORT:-http://localhost:8085}
AUDIT=${AUDIT:-http://localhost:8084}

say() { echo; echo "=== $1 ==="; }

say "1. Catalogo inicial"
curl -s $CATALOG/api/catalog/products

say "2. Creando pedido"
ID=$(curl -s -X POST $ORDERS/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerEmail":"cliente@duocuc.cl","items":[{"productId":"P-001","quantity":2,"unitPrice":8990}]}' \
  | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "orderId = $ID"

say "3. Intentando DESPACHAR sin ACEPTAR (debe dar 409 TRANSICION_INVALIDA)"
curl -s -o /dev/stderr -w "HTTP %{http_code}\n" -X PATCH $ORDERS/api/orders/$ID/status \
  -H "Content-Type: application/json" -d '{"target":"DESPACHADO"}'

say "4. Recorriendo el flujo valido"
for S in ACEPTADO EN_PREPARACION DESPACHADO ENTREGADO; do
  echo "-> $S"
  curl -s -X PATCH $ORDERS/api/orders/$ID/status \
    -H "Content-Type: application/json" -d "{\"target\":\"$S\"}" > /dev/null
  sleep 1
done

say "5. Stock despues de aceptar"
curl -s $CATALOG/api/catalog/products/P-001

say "6. KPIs (report, alimentado por Kafka)"
curl -s $REPORT/api/report/kpi/active-states; echo
curl -s $REPORT/api/report/kpi/lead-time

say "7. Timeline de auditoria"
curl -s $AUDIT/api/audit/timeline/$ID

echo; echo "Listo. Toma capturas de esta salida, del Management UI de RabbitMQ y de Kafka UI."
