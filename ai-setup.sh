#!/bin/bash
# Setup script for AI features on OpenLiberty

echo "🤖 Setting up AI features for Cargo Tracker..."

# Check prerequisites
echo "Checking prerequisites..."
command -v docker >/dev/null 2>&1 || { echo "❌ Docker required for Qdrant"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "❌ Java 21 required"; exit 1; }

# Verify Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Java 21 or higher required. Current: $JAVA_VERSION"
    exit 1
fi

# Create ai-data directory for Qdrant persistence
mkdir -p ai-data/qdrant

# Stop and remove existing Qdrant container if it exists
if [ "$(docker ps -aq -f name=cargotracker-qdrant)" ]; then
    echo "Stopping existing Qdrant container..."
    docker stop cargotracker-qdrant >/dev/null 2>&1
    docker rm cargotracker-qdrant >/dev/null 2>&1
fi

# Start Qdrant vector database
echo "🚀 Starting Qdrant vector database..."
docker run -d --name cargotracker-qdrant \
    -p 6333:6333 \
    -v $(pwd)/ai-data/qdrant:/qdrant/storage \
    qdrant/qdrant

# Wait for Qdrant to be ready
echo "⏳ Waiting for Qdrant to start..."
sleep 5

# Verify Qdrant is running
if curl -s http://localhost:6333/collections > /dev/null; then
    echo "✅ Qdrant is running"
else
    echo "❌ Qdrant failed to start"
    echo "Check Docker logs with: docker logs cargotracker-qdrant"
    exit 1
fi

# Check Azure OpenAI credentials
if [ -z "$AZURE_OPENAI_KEY" ]; then
    echo ""
    echo "⚠️  Azure OpenAI credentials not set"
    echo "   Please set environment variables:"
    echo ""
    echo "   export AZURE_OPENAI_ENDPOINT='https://your-resource.openai.azure.com/'"
    echo "   export AZURE_OPENAI_KEY='your-key'"
    echo "   export AZURE_OPENAI_DEPLOYMENT_NAME='gpt-4'"
    echo "   export AZURE_OPENAI_EMBEDDING_DEPLOYMENT='text-embedding-ada-002'"
    echo ""
    echo "   See .env.example for all required variables"
    echo ""
    exit 1
fi

echo ""
echo "✅ AI features setup complete!"
echo ""
echo "To run the application:"
echo "  ./mvnw liberty:dev -Popenliberty"
echo ""
echo "Application will be available at:"
echo "  http://localhost:8080/cargo-tracker"
echo ""
echo "To stop Qdrant:"
echo "  docker stop cargotracker-qdrant"
echo ""
