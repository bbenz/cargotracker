# Setup script for AI features on OpenLiberty (Windows)

Write-Host "🤖 Setting up AI features for Cargo Tracker..." -ForegroundColor Cyan
Write-Host ""

# Check Docker
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Docker is required for Qdrant" -ForegroundColor Red
    Write-Host "   Please install Docker Desktop: https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    exit 1
}

# Check if Docker is running
try {
    docker ps | Out-Null
} catch {
    Write-Host "❌ Docker is not running" -ForegroundColor Red
    Write-Host "   Please start Docker Desktop" -ForegroundColor Yellow
    exit 1
}

# Create ai-data directory for Qdrant persistence
if (-not (Test-Path "ai-data\qdrant")) {
    Write-Host "Creating ai-data directory..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Path "ai-data\qdrant" -Force | Out-Null
}

# Stop and remove existing Qdrant container if it exists
$existingContainer = docker ps -aq -f name=cargotracker-qdrant
if ($existingContainer) {
    Write-Host "Stopping existing Qdrant container..." -ForegroundColor Cyan
    docker stop cargotracker-qdrant | Out-Null
    docker rm cargotracker-qdrant | Out-Null
}

# Start Qdrant
Write-Host "🚀 Starting Qdrant vector database..." -ForegroundColor Cyan
docker run -d --name cargotracker-qdrant `
    -p 6333:6333 `
    -v ${PWD}/ai-data/qdrant:/qdrant/storage `
    qdrant/qdrant | Out-Null

Write-Host "⏳ Waiting for Qdrant to start..." -ForegroundColor Cyan
Start-Sleep -Seconds 5

# Verify Qdrant
try {
    $response = Invoke-WebRequest -Uri "http://localhost:6333/collections" -UseBasicParsing -TimeoutSec 5
    Write-Host "✅ Qdrant is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Qdrant failed to start" -ForegroundColor Red
    Write-Host "   Check Docker logs with: docker logs cargotracker-qdrant" -ForegroundColor Yellow
    exit 1
}

# Check environment variables
Write-Host ""
if (-not $env:AZURE_OPENAI_KEY) {
    Write-Host "⚠️  Azure OpenAI credentials not set" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "   Set these environment variables:" -ForegroundColor Yellow
    Write-Host '   $env:AZURE_OPENAI_ENDPOINT = "https://your-resource.openai.azure.com/"' -ForegroundColor White
    Write-Host '   $env:AZURE_OPENAI_KEY = "your-key"' -ForegroundColor White
    Write-Host '   $env:AZURE_OPENAI_DEPLOYMENT_NAME = "gpt-4"' -ForegroundColor White
    Write-Host '   $env:AZURE_OPENAI_EMBEDDING_DEPLOYMENT = "text-embedding-ada-002"' -ForegroundColor White
    Write-Host ""
    Write-Host "   See .env.example for all required variables" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

Write-Host ""
Write-Host "✅ AI features setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "To run the application:" -ForegroundColor Cyan
Write-Host "  .\mvnw.cmd liberty:dev -Popenliberty" -ForegroundColor White
Write-Host ""
Write-Host "Application will be available at:" -ForegroundColor Cyan
Write-Host "  http://localhost:8080/cargo-tracker" -ForegroundColor White
Write-Host ""
Write-Host "To stop Qdrant:" -ForegroundColor Cyan
Write-Host "  docker stop cargotracker-qdrant" -ForegroundColor White
Write-Host ""
