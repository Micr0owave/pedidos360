$ErrorActionPreference = 'Stop'

$orders = if ($env:ORDERS) { $env:ORDERS } else { 'http://localhost:8081' }
$catalog = if ($env:CATALOG) { $env:CATALOG } else { 'http://localhost:8082' }
$report = if ($env:REPORT) { $env:REPORT } else { 'http://localhost:8085' }
$audit = if ($env:AUDIT) { $env:AUDIT } else { 'http://localhost:8084' }

function Say([string] $message) {
    Write-Host "`n=== $message ==="
}

function Invoke-Json([string] $uri, [string] $method = 'GET', [object] $body) {
    $params = @{ Uri = $uri; Method = $method; ContentType = 'application/json' }
    if ($null -ne $body) {
        $params.Body = ($body | ConvertTo-Json -Depth 10 -Compress)
    }
    Invoke-RestMethod @params
}

Say '1. Catalogo inicial'
Invoke-Json "$catalog/api/catalog/products" | ConvertTo-Json -Depth 10

Say '2. Creando pedido'
$newOrder = Invoke-Json "$orders/api/orders" 'POST' @{
    customerEmail = 'cliente@duocuc.cl'
    items = @(@{ productId = 'P-001'; quantity = 2; unitPrice = 8990 })
}
$id = $newOrder.id
Write-Host "orderId = $id"

Say '3. Intentando DESPACHAR sin ACEPTAR (debe dar 409 TRANSICION_INVALIDA)'
try {
    Invoke-Json "$orders/api/orders/$id/status" 'PATCH' @{ target = 'DESPACHADO' } | ConvertTo-Json -Depth 10
} catch {
    Write-Host "HTTP $($_.Exception.Response.StatusCode.value__)"
    Write-Host $_.ErrorDetails.Message
}

Say '4. Recorriendo el flujo valido'
foreach ($status in @('ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO')) {
    Write-Host "-> $status"
    Invoke-Json "$orders/api/orders/$id/status" 'PATCH' @{ target = $status } | Out-Null
    Start-Sleep -Seconds 1
}

Say '5. Stock despues de aceptar'
Invoke-Json "$catalog/api/catalog/products/P-001" | ConvertTo-Json -Depth 10

Say '6. KPIs (report, alimentado por Kafka)'
Invoke-Json "$report/api/report/kpi/active-states" | ConvertTo-Json -Depth 10
Invoke-Json "$report/api/report/kpi/lead-time" | ConvertTo-Json -Depth 10

Say '7. Timeline de auditoria'
Invoke-Json "$audit/api/audit/timeline/$id" | ConvertTo-Json -Depth 10

Write-Host "`nListo. Toma capturas de esta salida, del Management UI de RabbitMQ y de Kafka UI."