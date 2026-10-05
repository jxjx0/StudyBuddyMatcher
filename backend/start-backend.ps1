# Starts the backend services, each in its own PowerShell window.
# Usage (from the backend folder):  .\start-backend.ps1
# Pass -All to also start connection-service and group-service.
param([switch]$All)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$services = @("user-service", "api-gateway", "matching-service")
if ($All) { $services += @("connection-service", "group-service") }

# Build once first so the parallel runs don't all download dependencies at the same time.
& .\mvnw.cmd -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed."; exit $LASTEXITCODE }

$useWindowsTerminal = [bool](Get-Command wt -ErrorAction SilentlyContinue)

foreach ($service in $services) {
    if ($useWindowsTerminal) {
        # One Windows Terminal window named "backend", with a tab per service.
        # A reused Terminal window can hand tabs a stale PATH, so launch powershell by full path
        # (wt error 0x80070002) and put it back on PATH for mvnw.cmd, which calls "powershell" itself.
        # Encoded so wt doesn't treat the ";" in the command as its own separator.
        $command = "`$env:Path = '$PSHOME;' + `$env:Path; .\mvnw.cmd -pl $service spring-boot:run"
        $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
        Start-Process wt -ArgumentList "-w backend new-tab --title $service -d `"$PSScriptRoot`" `"$PSHOME\powershell.exe`" -NoExit -EncodedCommand $encoded"
        Start-Sleep -Milliseconds 800  # let the window exist before adding the next tab
    } else {
        Start-Process powershell -WorkingDirectory $PSScriptRoot -ArgumentList @(
            "-NoExit",
            "-Command",
            "`$Host.UI.RawUI.WindowTitle = '$service'; .\mvnw.cmd -pl $service spring-boot:run"
        )
    }
}

Write-Host "Started: $($services -join ', '). Close a tab/window to stop that service."
