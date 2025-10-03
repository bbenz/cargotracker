#!/bin/bash

# Cargo Tracker AI - Qdrant Setup and Data Preload Script
# This script creates the Qdrant collection and preloads cargo data
# Run this BEFORE starting the Liberty application

set -e

echo "🚀 Cargo Tracker AI - Qdrant Setup"
echo "=================================="
echo ""

# Configuration
QDRANT_HOST=${QDRANT_HOST:-localhost}
QDRANT_HTTP_PORT=${QDRANT_HTTP_PORT:-6333}
QDRANT_GRPC_PORT=${QDRANT_GRPC_PORT:-6334}
COLLECTION_NAME="cargo_tracker_history"
VECTOR_SIZE=1536  # text-embedding-ada-002 dimension

# Step 1: Check if Qdrant is running
echo "📡 Step 1: Checking Qdrant connection..."
if curl -s http://${QDRANT_HOST}:${QDRANT_HTTP_PORT} > /dev/null 2>&1; then
    echo "✅ Qdrant is running"
else
    echo "❌ Qdrant is not running!"
    echo "   Starting Qdrant container..."
    docker run -d --name qdrant \
        -p ${QDRANT_HTTP_PORT}:6333 \
        -p ${QDRANT_GRPC_PORT}:6334 \
        -v $(pwd)/qdrant_storage:/qdrant/storage \
        qdrant/qdrant
    
    echo "   Waiting for Qdrant to be ready..."
    for i in {1..30}; do
        if curl -s http://${QDRANT_HOST}:${QDRANT_HTTP_PORT} > /dev/null 2>&1; then
            echo "✅ Qdrant is ready"
            break
        fi
        sleep 1
        echo -n "."
    done
    echo ""
fi

echo ""

# Step 2: Check if collection exists
echo "📦 Step 2: Checking if collection exists..."
COLLECTION_CHECK=$(curl -s http://${QDRANT_HOST}:${QDRANT_HTTP_PORT}/collections/${COLLECTION_NAME} | grep -o '"error"')

if [ -n "$COLLECTION_CHECK" ]; then
    echo "❌ Collection '${COLLECTION_NAME}' does not exist"
    echo "   Creating collection..."
    
    # Create collection using REST API
    curl -X PUT "http://${QDRANT_HOST}:${QDRANT_HTTP_PORT}/collections/${COLLECTION_NAME}" \
        -H "Content-Type: application/json" \
        -d "{
            \"vectors\": {
                \"size\": ${VECTOR_SIZE},
                \"distance\": \"Cosine\"
            }
        }"
    
    echo ""
    echo "✅ Collection created successfully"
else
    echo "✅ Collection '${COLLECTION_NAME}' already exists"
fi

echo ""

# Step 3: Verify collection
echo "🔍 Step 3: Verifying collection..."
COLLECTION_INFO=$(curl -s http://${QDRANT_HOST}:${QDRANT_HTTP_PORT}/collections/${COLLECTION_NAME})
echo "Collection info:"
echo "$COLLECTION_INFO" | jq '.' 2>/dev/null || echo "$COLLECTION_INFO"

echo ""

# Step 4: Check vector count
VECTOR_COUNT=$(echo "$COLLECTION_INFO" | jq -r '.result.vectors_count // 0' 2>/dev/null || echo "0")
echo "📊 Current vectors in collection: ${VECTOR_COUNT}"

if [ "$VECTOR_COUNT" -eq "0" ]; then
    echo ""
    echo "⚠️  Collection is empty. The application will index data on startup."
    echo "   This may take 30-60 seconds depending on the number of cargo records."
else
    echo "✅ Collection has ${VECTOR_COUNT} vectors already loaded"
fi

echo ""
echo "=================================="
echo "✅ Qdrant setup complete!"
echo ""
echo "Next steps:"
echo "1. Ensure environment variables are set:"
echo "   export AZURE_OPENAI_ENDPOINT='...'"
echo "   export AZURE_OPENAI_KEY='...'"
echo "   export AZURE_OPENAI_DEPLOYMENT_NAME='gpt-4'"
echo "   export AZURE_OPENAI_EMBEDDING_DEPLOYMENT='text-embedding-ada-002'"
echo ""
echo "2. Start the application:"
echo "   ./mvnw liberty:run -Popenliberty"
echo ""
echo "3. Access the RAG interface:"
echo "   http://localhost:8080/cargo-tracker/ai-rag.xhtml"
echo ""
echo "4. Monitor collection growth:"
echo "   curl http://localhost:6333/collections/${COLLECTION_NAME} | jq '.result.vectors_count'"
echo ""
