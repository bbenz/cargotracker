<#
.SYNOPSIS
Replaces the model API key in the ai-endpoint Secret and restarts Cargo Tracker.

.DESCRIPTION
The image stays the same. The key goes to Kubernetes through standard input, so it never
appears on a command line. By default the script asks for the key. Use -FromEnvFile to read
AI_API_KEY from .env in the repository root. Restarting resets the sample data, because the
sample uses an in-memory database.

.EXAMPLE
./deploy/aks/rotate-key.ps1
./deploy/aks/rotate-key.ps1 -FromEnvFile
#>
param(
  [switch]$FromEnvFile
)

$ErrorActionPreference = "Stop"
$namespace = "cargo-tracker"

if ($FromEnvFile) {
  $envFile = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path ".env"
  $line = Get-Content $envFile | Where-Object { $_ -match '^\s*AI_API_KEY\s*=' } | Select-Object -Last 1
  if (-not $line) { throw "No AI_API_KEY in $envFile" }
  $key = ($line -split '=', 2)[1].Trim()
} else {
  $secure = Read-Host "New API key" -AsSecureString
  $key = [System.Net.NetworkCredential]::new("", $secure).Password
}
if ($key -eq "") { throw "The key is empty" }

$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($key))
@"
apiVersion: v1
kind: Secret
metadata:
  name: ai-endpoint
  namespace: $namespace
type: Opaque
data:
  api-key: "$encoded"
"@ | kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw "Could not update the ai-endpoint Secret" }

kubectl -n $namespace rollout restart deployment/cargo-tracker
kubectl -n $namespace rollout status deployment/cargo-tracker --timeout=5m
Write-Host "The new key is in use. The image did not change."
