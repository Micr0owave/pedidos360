$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Start-Service([string] $name) {
    $directory = Join-Path $root "ms-pedidos360-$name"
    $stdout = Join-Path $directory 'run.log'
    $stderr = Join-Path $directory 'run-error.log'
    Start-Process -FilePath 'mvn.cmd' `
        -WorkingDirectory $directory `
        -ArgumentList '-q', 'spring-boot:run', '-Dspring-boot.run.profiles=local' `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr | Out-Null
    Write-Host "Iniciando ms-pedidos360-$name"
}

Push-Location $root
try {
    Write-Host '== Levantando RabbitMQ =='
    docker compose -f infra/mq/compose.yml up -d

    Write-Host '== Levantando Kafka + Zookeeper =='
    docker compose -f infra/kafka/compose.yml up -d

    Write-Host 'Esperando a que la infraestructura quede lista (40s)...'
    Start-Sleep -Seconds 40

    foreach ($service in @('orders', 'catalog', 'notify', 'audit', 'report', 'bff')) {
        Start-Service $service
    }

    Write-Host ''
    Write-Host 'Servicios iniciando. Revisa los logs en ms-pedidos360-*/run.log'
    Write-Host 'Swagger orders : http://localhost:8081/swagger-ui.html'
    Write-Host 'RabbitMQ UI    : http://localhost:15672  (p360 / p360pass)'
    Write-Host 'Kafka UI       : http://localhost:8090'
}
finally {
    Pop-Location
}
