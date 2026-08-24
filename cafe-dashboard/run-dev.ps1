# Loads Database/.env into environment variables, then runs the app.
$envFile = Join-Path $PSScriptRoot "..\Database\.env"

if (-not (Test-Path $envFile)) {
    Write-Error "$envFile 를 찾을 수 없습니다."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $parts = $line -split "=", 2
    if ($parts.Length -eq 2) {
        [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim())
    }
}

& "$PSScriptRoot\mvnw.cmd" spring-boot:run
