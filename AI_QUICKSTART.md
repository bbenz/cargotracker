# Cargo Tracker AI - Quick Start Guide

## Problem Solved

The Qdrant collection was not being created because CDI beans were lazy-loaded and never instantiated at startup. 

## Solution

Added `AiStartupInitializer` - a `@Startup @Singleton` EJB bean that forces initialization of AI components at deployment time.

## How It Works

1. **AiStartupInitializer** (@Startup @Singleton)
   - Instantiated immediately when application deploys
   - Injects `EmbeddingStore<TextSegment>` - triggers AiQdrantConfig.createEmbeddingStore()
   - Injects `AiHistoricalIndexer` - ensures data indexing happens

2. **AiQdrantConfig** (@ApplicationScoped)
   - Producer method creates QdrantClient and EmbeddingStore
   - Calls `createCollectionIfNotExists()` automatically
   - Creates "cargo_tracker_history" collection with vector size 1536

3. **AiHistoricalIndexer** (@ApplicationScoped)
   - @PostConstruct method runs after injection
   - Loads all Cargo records from database
   - Generates embeddings and stores in Qdrant

## Quick Start

### Option A: Automated Setup (Recommended)

```bash
# 1. Run the setup script to ensure Qdrant is ready
./qdrant-setup.sh

# 2. Set environment variables (if not already set)
export AZURE_OPENAI_ENDPOINT='https://your-instance.openai.azure.com/'
export AZURE_OPENAI_KEY='your-key-here'
export AZURE_OPENAI_DEPLOYMENT_NAME='gpt-4'
export AZURE_OPENAI_EMBEDDING_DEPLOYMENT='text-embedding-ada-002'
export QDRANT_HOST='localhost'
export QDRANT_GRPC_PORT='6334'

# 3. Build and run
./mvnw clean package -Popenliberty -DskipTests
./mvnw liberty:run -Popenliberty

# 4. Monitor startup in another terminal
tail -f target/liberty/wlp/usr/servers/cargo-tracker/logs/messages.log | grep -E "🚀|✅|❌|AiStartup|Qdrant|indexing"
```

### Option B: Manual Verification

```bash
# Check if Qdrant is running
curl http://localhost:6333

# Check collections before startup
curl http://localhost:6333/collections

# Start application
./mvnw liberty:run -Popenliberty

# Check collections after startup
curl http://localhost:6333/collections/cargo_tracker_history

# Check vector count
curl http://localhost:6333/collections/cargo_tracker_history | jq '.result.vectors_count'
```

## Expected Log Output

When working correctly, you'll see:

```
[INFO] 🚀 AI Startup Initializer - Beginning initialization...
[INFO] ✅ Successfully created collection 'cargo_tracker_history'
[INFO] ✅ EmbeddingStore initialized successfully
[INFO] 🚀 Starting historical cargo data indexing for RAG...
[INFO] 📦 Found X cargo records to index
[INFO] ✅ Successfully indexed X cargo records for RAG queries
[INFO] ✅ Historical indexer initialized successfully
[INFO] ✅ AI Startup Initializer - Initialization complete!
```

## Testing the RAG Feature

1. **Navigate to RAG Interface**
   ```
   http://localhost:8080/cargo-tracker/ai-rag.xhtml
   ```

2. **Test Queries**
   - "What cargo went from Hong Kong to Stockholm?"
   - "Show me all shipments to Tokyo"
   - "Which cargo is being tracked with ID XYZ123?"
   - "What are the most common destination cities?"

3. **Expected Response**
   - Conversational answer from GPT-4
   - Based on actual cargo data from database
   - Sources section showing matched cargo records

## Troubleshooting

### Collection Not Created

```bash
# Check Qdrant is running
docker ps | grep qdrant

# Check logs for errors
grep -i "qdrant\|collection" target/liberty/wlp/usr/servers/cargo-tracker/logs/messages.log

# Manually create collection
curl -X PUT "http://localhost:6333/collections/cargo_tracker_history" \
  -H "Content-Type: application/json" \
  -d '{"vectors": {"size": 1536, "distance": "Cosine"}}'
```

### No AI Logs Appearing

This was the original problem - beans weren't instantiating. `AiStartupInitializer` fixes this.

If you still don't see logs:
1. Check `src/main/webapp/WEB-INF/beans.xml` exists
2. Verify `AiStartupInitializer.class` is in WAR: `jar -tf target/cargo-tracker.war | grep AiStartupInitializer`
3. Check server.xml has AI libraries: `<library id="aiLib">`

### Data Not Indexing

```bash
# Check if indexer bean is loaded
grep "AiHistoricalIndexer" target/liberty/wlp/usr/servers/cargo-tracker/logs/messages.log

# Check database has cargo records
# (Access H2 console or check via application)

# Manually trigger reindex by restarting application
./mvnw liberty:stop -Popenliberty
./mvnw liberty:run -Popenliberty
```

### Azure OpenAI Errors

```bash
# Verify environment variables
env | grep AZURE_OPENAI

# Test endpoint manually
curl -H "api-key: $AZURE_OPENAI_KEY" \
     "$AZURE_OPENAI_ENDPOINT/openai/deployments?api-version=2023-05-15"

# Check deployment names match
curl -H "api-key: $AZURE_OPENAI_KEY" \
     "$AZURE_OPENAI_ENDPOINT/openai/deployments/$AZURE_OPENAI_DEPLOYMENT_NAME?api-version=2023-05-15"
```

## Architecture Notes

### Why @Startup @Singleton Instead of @ApplicationScoped?

- `@ApplicationScoped` CDI beans are lazy-loaded (created on first injection)
- `@Startup @Singleton` EJB beans are eager-loaded (created at deployment)
- No other component was injecting AI beans, so they never initialized
- AiStartupInitializer forces instantiation by being @Startup

### Classpath Configuration

All LangChain4j JARs are in `target/liberty/wlp/usr/servers/cargo-tracker/shared/resources/`:
- langchain4j-*.jar (core)
- langchain4j-azure-open-ai-*.jar (Azure integration)
- langchain4j-qdrant-*.jar (Qdrant integration)
- langchain4j-embeddings-*.jar (embedding utilities)
- guava-*.jar, failureaccess-*.jar (dependencies)

Liberty server.xml references them:
```xml
<library id="aiLib">
    <fileset dir="${shared.resource.dir}" includes="*.jar" excludes="hsqldb*.jar"/>
</library>
<webApplication ... >
    <classloader commonLibraryRef="dbLib,aiLib"/>
</webApplication>
```

### Collection Configuration

- **Collection Name**: cargo_tracker_history
- **Vector Dimension**: 1536 (Azure text-embedding-ada-002)
- **Distance Metric**: Cosine similarity
- **Storage**: Persistent (./qdrant_storage volume)
- **Ports**: 6333 (HTTP API), 6334 (gRPC)

## Demo Preparation

For JCON 2025 presentation:

1. **Before Demo**
   ```bash
   # Start with clean Qdrant
   docker stop qdrant && docker rm qdrant
   ./qdrant-setup.sh
   
   # Verify environment
   env | grep AZURE_OPENAI | sort
   
   # Start application
   ./mvnw liberty:run -Popenliberty
   
   # Wait for indexing to complete (check logs)
   tail -f target/liberty/wlp/usr/servers/cargo-tracker/logs/messages.log
   ```

2. **During Demo**
   - Show Qdrant dashboard: http://localhost:6333/dashboard
   - Show collection: cargo_tracker_history with N vectors
   - Open RAG interface: http://localhost:8080/cargo-tracker/ai-rag.xhtml
   - Run test query: "What cargo went from Hong Kong to Stockholm?"
   - Show conversational response + source metadata

3. **Slide-Based Phases 4-6**
   - Refer to `PHASE_4_SMART_AUTOFILL.md`
   - Refer to `PHASE_5_WORKFLOW_OPTIMIZATION.md`
   - Refer to `PHASE_6_DEMO_PREPARATION.md`

## Success Criteria

✅ Qdrant container running
✅ Collection "cargo_tracker_history" exists
✅ Vector count > 0 (matches number of cargo records)
✅ Application starts without errors
✅ Logs show: "AI Startup Initializer - Initialization complete!"
✅ RAG interface loads
✅ Test query returns intelligent response
✅ Response includes source cargo data

## Files Modified

1. **src/main/java/org/eclipse/cargotracker/interfaces/ai/AiStartupInitializer.java** (NEW)
   - @Startup @Singleton bean to force initialization

2. **AiQdrantConfig.java** (PREVIOUSLY MODIFIED)
   - Collection creation logic with error handling

3. **AiHistoricalIndexer.java** (PREVIOUSLY MODIFIED)
   - @PostConstruct indexing with null checks

4. **qdrant-setup.sh** (NEW)
   - Automated Qdrant setup and verification script

5. **AI_QUICKSTART.md** (THIS FILE)
   - Comprehensive setup and troubleshooting guide
