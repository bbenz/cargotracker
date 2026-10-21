<#
.SYNOPSIS
Deploys Cargo Tracker with the shipment dashboard to Azure Kubernetes Service (AKS).

.DESCRIPTION
Creates a resource group, a container registry, and an AKS cluster if they do not exist.
Builds the image with Docker, pushes it to the registry, and applies the manifests in k8s/.
Model settings come from .env in the repository root. The API key goes into a Kubernetes
Secret. It is never part of the image or of a command line.

These resources cost money while they run. Remove them with:
  az group delete --name cargo-tracker-ai

.EXAMPLE
./deploy/aks/deploy.ps1
./deploy/aks/deploy.ps1 -Location westus3 -Tag 2
#>
param(
  [string]$ResourceGroup = "cargo-tracker-ai",
  [string]$Location = "eastus2",
  [string]$Cluster = "cargo-tracker-aks",
  [string]$Registry = "",
  [string]$Tag = (Get-Date -Format "yyyyMMdd-HHmm"),
  [string]$NodeSize = "Standard_D4s_v5"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$namespace = "cargo-tracker"

function Read-DotEnv([string]$path) {
  $values = @{}
  if (Test-Path $path) {
    foreach ($line in Get-Content $path) {
      if ($line -match '^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$') {
        $values[$Matches[1]] = $Matches[2].Trim()
      }
    }
  }
  return $values
}

function Get-Setting([hashtable]$values, [string]$name, [string]$default) {
  if ($values.ContainsKey($name) -and $values[$name] -ne "") { return $values[$name] }
  return $default
}

function Invoke-Checked([scriptblock]$command) {
  & $command
  if ($LASTEXITCODE -ne 0) { throw "Command failed: $command" }
}

$settings = Read-DotEnv (Join-Path $root ".env")
$apiKey = Get-Setting $settings "AI_API_KEY" ""
if ($apiKey -eq "") {
  Write-Warning "No AI_API_KEY in .env. The app runs, and the assistant stays off."
}

if ($Registry -eq "") {
  $subscription = az account show --query id -o tsv
  if ($LASTEXITCODE -ne 0) { throw "Sign in first with: az login" }
  $Registry = "cargotracker" + $subscription.Replace("-", "").Substring(0, 8)
}
$image = "$Registry.azurecr.io/cargo-tracker-ai:$Tag"

Write-Host "Resource group $ResourceGroup in $Location"
Invoke-Checked { az group create --name $ResourceGroup --location $Location --output none }

$existingRegistry = az acr list --resource-group $ResourceGroup --query "[?name=='$Registry'].name" -o tsv
if (-not $existingRegistry) {
  Write-Host "Creating container registry $Registry"
  Invoke-Checked { az acr create --name $Registry --resource-group $ResourceGroup --sku Basic --output none }
}

$existingCluster = az aks list --resource-group $ResourceGroup --query "[?name=='$Cluster'].name" -o tsv
if (-not $existingCluster) {
  Write-Host "Creating AKS cluster $Cluster. This takes several minutes."
  Invoke-Checked {
    az aks create --name $Cluster --resource-group $ResourceGroup --node-count 1 `
      --node-vm-size $NodeSize --attach-acr $Registry --generate-ssh-keys --output none
  }
}

Write-Host "Building and pushing $image"
Invoke-Checked { docker build --platform linux/amd64 -t $image $root }
Invoke-Checked { az acr login --name $Registry }
Invoke-Checked { docker push $image }

Invoke-Checked { az aks get-credentials --name $Cluster --resource-group $ResourceGroup --overwrite-existing }

Invoke-Checked { kubectl apply -f (Join-Path $root "k8s/namespace.yaml") }

$encodedKey = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($apiKey))
@"
apiVersion: v1
kind: Secret
metadata:
  name: ai-endpoint
  namespace: $namespace
type: Opaque
data:
  api-key: "$encodedKey"
"@ | kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw "Could not create the ai-endpoint Secret" }

$enabled = if ($apiKey -eq "") { "false" } else { Get-Setting $settings "AI_ENABLED" "true" }
@"
apiVersion: v1
kind: ConfigMap
metadata:
  name: ai-config
  namespace: $namespace
data:
  enabled: "$enabled"
  base-url: "$(Get-Setting $settings 'AI_BASE_URL' 'https://api.openai.com/v1')"
  api: "$(Get-Setting $settings 'AI_API' 'responses')"
  chat-model: "$(Get-Setting $settings 'AI_CHAT_MODEL' 'gpt-6.1-sol')"
  reasoning-effort: "$(Get-Setting $settings 'AI_REASONING_EFFORT' '')"
  timeout-seconds: "$(Get-Setting $settings 'AI_TIMEOUT_SECONDS' '20')"
  docs-dir: /config/ai-docs
"@ | kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw "Could not create the ai-config ConfigMap" }

Invoke-Checked { kubectl apply -f (Join-Path $root "k8s/otel-config.yaml") -f (Join-Path $root "k8s/jaeger.yaml") }
(Get-Content (Join-Path $root "k8s/cargo-tracker.yaml") -Raw).Replace("cargo-tracker-ai:local", $image) | kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw "Could not apply the cargo-tracker deployment" }

Invoke-Checked { kubectl -n $namespace rollout status deployment/cargo-tracker --timeout=10m }

Write-Host ""
Write-Host "Deployed $image"
Write-Host "Open the app and the traces from your machine:"
Write-Host "  kubectl -n $namespace port-forward svc/cargo-tracker 8080:80"
Write-Host "  kubectl -n $namespace port-forward svc/jaeger 16686:16686"
Write-Host "Then browse to http://localhost:8080/cargo-tracker/dashboard/"
