<#
.SYNOPSIS
Changes one model setting in the ai-config ConfigMap and restarts Cargo Tracker.

.DESCRIPTION
Use it for the "Break it" part of the demo. The same image runs with new settings.
Restarting resets the sample data, because the sample uses an in-memory database.

.EXAMPLE
./deploy/aks/set-ai.ps1 timeout-seconds 1        # make the model time out
./deploy/aks/set-ai.ps1 chat-model gpt-6.1-sol-x # a model name the endpoint does not know
./deploy/aks/set-ai.ps1 enabled false            # turn the assistant off
#>
param(
  [Parameter(Mandatory = $true)]
  [ValidateSet("enabled", "base-url", "api", "chat-model", "reasoning-effort", "timeout-seconds")]
  [string]$Name,
  [Parameter(Mandatory = $true)]
  [AllowEmptyString()]
  [string]$Value
)

$ErrorActionPreference = "Stop"
$namespace = "cargo-tracker"

$configMap = (kubectl -n $namespace get configmap ai-config -o json | Out-String) | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw "Could not read the ai-config ConfigMap" }
$configMap.data.$Name = $Value
$configMap | ConvertTo-Json -Depth 10 | kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw "Could not update the ai-config ConfigMap" }

kubectl -n $namespace rollout restart deployment/cargo-tracker
kubectl -n $namespace rollout status deployment/cargo-tracker --timeout=5m
Write-Host "ai-config: $Name is now '$Value'"
