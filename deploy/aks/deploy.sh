#!/usr/bin/env bash
# Deploys Cargo Tracker with the shipment dashboard to Azure Kubernetes Service (AKS).
#
# Creates a resource group, a container registry, and an AKS cluster if they do not exist.
# Builds the image with Docker, pushes it, and applies the manifests in k8s/. Model settings
# come from .env in the repository root. The API key goes into a Kubernetes Secret through
# standard input. It is never part of the image or of a command line.
#
# These resources cost money while they run. Remove them with:
#   az group delete --name cargo-tracker-ai
#
# Usage: deploy/aks/deploy.sh [resource-group] [location] [tag]
set -euo pipefail

RESOURCE_GROUP="${1:-cargo-tracker-ai}"
LOCATION="${2:-eastus2}"
TAG="${3:-$(date +%Y%m%d-%H%M)}"
CLUSTER="${CLUSTER:-cargo-tracker-aks}"
NODE_SIZE="${NODE_SIZE:-Standard_D4s_v5}"
NAMESPACE="cargo-tracker"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

setting() {
  local name="$1" default="$2" value=""
  if [ -f "$ROOT/.env" ]; then
    value="$(grep -E "^${name}=" "$ROOT/.env" | tail -n 1 | cut -d= -f2- | tr -d '\r' || true)"
  fi
  echo "${value:-$default}"
}

API_KEY="$(setting AI_API_KEY "")"
if [ -z "$API_KEY" ]; then
  echo "Warning: no AI_API_KEY in .env. The app runs, and the assistant stays off." >&2
fi

if [ -z "${REGISTRY:-}" ]; then
  SUBSCRIPTION="$(az account show --query id -o tsv)"
  REGISTRY="cargotracker$(echo "$SUBSCRIPTION" | tr -d '-' | cut -c1-8)"
fi
IMAGE="$REGISTRY.azurecr.io/cargo-tracker-ai:$TAG"

echo "Resource group $RESOURCE_GROUP in $LOCATION"
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" --output none

if [ -z "$(az acr list --resource-group "$RESOURCE_GROUP" --query "[?name=='$REGISTRY'].name" -o tsv)" ]; then
  echo "Creating container registry $REGISTRY"
  az acr create --name "$REGISTRY" --resource-group "$RESOURCE_GROUP" --sku Basic --output none
fi

if [ -z "$(az aks list --resource-group "$RESOURCE_GROUP" --query "[?name=='$CLUSTER'].name" -o tsv)" ]; then
  echo "Creating AKS cluster $CLUSTER. This takes several minutes."
  az aks create --name "$CLUSTER" --resource-group "$RESOURCE_GROUP" --node-count 1 \
    --node-vm-size "$NODE_SIZE" --attach-acr "$REGISTRY" --generate-ssh-keys --output none
fi

echo "Building and pushing $IMAGE"
docker build --platform linux/amd64 -t "$IMAGE" "$ROOT"
az acr login --name "$REGISTRY"
docker push "$IMAGE"

az aks get-credentials --name "$CLUSTER" --resource-group "$RESOURCE_GROUP" --overwrite-existing
kubectl apply -f "$ROOT/k8s/namespace.yaml"

ENCODED_KEY="$(printf '%s' "$API_KEY" | base64 | tr -d '\n')"
kubectl apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ai-endpoint
  namespace: $NAMESPACE
type: Opaque
data:
  api-key: "$ENCODED_KEY"
EOF

ENABLED="$(setting AI_ENABLED true)"
if [ -z "$API_KEY" ]; then ENABLED="false"; fi
kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: ai-config
  namespace: $NAMESPACE
data:
  enabled: "$ENABLED"
  base-url: "$(setting AI_BASE_URL https://api.openai.com/v1)"
  api: "$(setting AI_API responses)"
  chat-model: "$(setting AI_CHAT_MODEL gpt-6.1-sol)"
  reasoning-effort: "$(setting AI_REASONING_EFFORT "")"
  timeout-seconds: "$(setting AI_TIMEOUT_SECONDS 20)"
  docs-dir: /config/ai-docs
EOF

kubectl apply -f "$ROOT/k8s/otel-config.yaml" -f "$ROOT/k8s/jaeger.yaml"
sed "s|cargo-tracker-ai:local|$IMAGE|" "$ROOT/k8s/cargo-tracker.yaml" | kubectl apply -f -
kubectl -n "$NAMESPACE" rollout status deployment/cargo-tracker --timeout=10m

echo
echo "Deployed $IMAGE"
echo "Open the app and the traces from your machine:"
echo "  kubectl -n $NAMESPACE port-forward svc/cargo-tracker 8080:80"
echo "  kubectl -n $NAMESPACE port-forward svc/jaeger 16686:16686"
echo "Then browse to http://localhost:8080/cargo-tracker/dashboard/"
