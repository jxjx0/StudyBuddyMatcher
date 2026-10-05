# Stops the backend services by ending whatever is listening on their ports.
# Usage (from the backend folder):  .\stop-backend.ps1

$ports = @{
    8080 = "api-gateway"
    8081 = "user-service"
    8083 = "matching-service"
    8084 = "connection-service"
    8085 = "group-service"
}

$listeners = Get-NetTCPConnection -State Listen -LocalPort $ports.Keys -ErrorAction SilentlyContinue

if (-not $listeners) {
    Write-Host "No backend services are running."
    exit 0
}

foreach ($listener in $listeners | Sort-Object LocalPort -Unique) {
    $name = $ports[[int]$listener.LocalPort]
    try {
        Stop-Process -Id $listener.OwningProcess -Force -ErrorAction Stop
        Write-Host "Stopped $name (port $($listener.LocalPort), PID $($listener.OwningProcess))"
    } catch {
        Write-Warning "Could not stop $name (port $($listener.LocalPort)): $($_.Exception.Message)"
    }
}
