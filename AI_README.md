# From CRUD to Cognitive - AI Enhancement Project

This branch contains AI-powered enhancements to the Eclipse Cargo Tracker application, demonstrating how to modernize legacy Jakarta EE applications with GenAI capabilities.

## Quick Start

### Prerequisites
- Java 21+
- Docker (for Qdrant vector database)
- Azure OpenAI subscription with API key
- Maven (via wrapper: `./mvnw`)

### Setup

1. **Set environment variables**:
   ```bash
   export AZURE_OPENAI_ENDPOINT="https://your-resource.openai.azure.com/"
   export AZURE_OPENAI_KEY="your-api-key"
   export AZURE_OPENAI_DEPLOYMENT_NAME="gpt-4"
   export AZURE_OPENAI_EMBEDDING_DEPLOYMENT="text-embedding-ada-002"
   ```

2. **Run setup script** to start Qdrant:
   ```bash
   ./ai-setup.sh    # Linux/Mac
   .\ai-setup.ps1   # Windows PowerShell
   ```

3. **Start the application**:
   ```bash
   ./mvnw liberty:dev -Popenliberty
   ```

4. **Access at**: http://localhost:8080/cargo-tracker

## AI Features

### 1. RAG - Historical Shipment Intelligence
Semantic search and Q&A over historical cargo data using Qdrant vector database.

### 2. AI Chatbot
Natural language cargo tracking queries via floating chat widget.

### 3. Smart Auto-Fill
AI-powered form suggestions based on historical patterns and document extraction.

### 4. Workflow Optimization
Route suggestions and cargo consolidation recommendations.

## Project Structure

```
src/main/java/org/eclipse/cargotracker/ai/
├── aiconfig/        # Azure OpenAI & Qdrant configuration
├── aiservices/      # AI business logic
│   ├── airag/
│   ├── aichatbot/
│   ├── aiautofill/
│   └── aiworkflow/
├── airest/          # REST API endpoints
├── aiui/            # JSF backing beans
└── aiutils/         # Utilities

src/main/webapp/
├── ai-components/   # AI UI components
└── resources/
    ├── css/ai-styles.css
    └── js/
        ├── ai-chatbot.js
        └── ai-autofill.js

ai-docs/            # Documentation
├── ai-architecture.md
├── ai-developer-guide.md
├── ai-user-guide.md
├── ai-api-reference.md
├── ai-demo-script.md
└── ai-troubleshooting.md
```

## Technology Stack

- **AI Framework**: LangChain4j 0.35.0
- **LLM**: Azure OpenAI Service (gpt-4, text-embedding-ada-002)
- **Vector DB**: Qdrant (Docker)
- **Server**: OpenLiberty (Java 21 compatible)
- **Database**: H2 (in-memory)

## Development

### Build
```bash
./mvnw clean install -Popenliberty
```

### Run Tests
```bash
./mvnw test -Popenliberty
```

### Dev Mode (Hot Reload)
```bash
./mvnw liberty:dev -Popenliberty
```

## Documentation

See `/ai-docs` folder for comprehensive documentation:
- [Developer Guide](ai-docs/ai-developer-guide.md) - Setup and development
- [Architecture](ai-docs/ai-architecture.md) - Technical architecture
- [API Reference](ai-docs/ai-api-reference.md) - REST API docs (TBD)
- [Demo Script](ai-docs/ai-demo-script.md) - JCON presentation guide (TBD)

## Demo Target

**Event**: JCON One USA 2025  
**Date**: June 9, 2025  
**Session**: "From CRUD to Cognitive: Modernizing Legacy Java Apps with Embedded AI"  
**Speakers**: Brian Benz & Ed Burns

## Phase 1 Completion ✅

- [x] Created CRUDToCognitive branch
- [x] Added LangChain4j dependencies to OpenLiberty profile
- [x] Created AI configuration beans (Azure OpenAI, Qdrant)
- [x] Created environment configuration files
- [x] Created setup scripts (Linux/Windows)
- [x] Started Qdrant Docker container
- [x] Created initial documentation structure
- [x] Verified basic connectivity test structure

## Next Steps

### Phase 2: RAG Implementation
- Implement historical data indexer
- Create RAG query service
- Build REST endpoint for RAG queries
- Add UI panel for testing

### Phase 3: Chatbot Widget
- Implement chatbot service with conversation memory
- Create floating chat widget UI
- Add intent parsing and cargo queries
- Integration testing

### Phase 4-6: Auto-Fill & Workflow Optimization
(Details in implementation prompt)

## Contributing

This is a demo project for JCON 2025. All AI code uses `ai-` prefix for easy identification.

## License

See main project LICENSE.

---

**Version**: 3.1-SNAPSHOT  
**Branch**: CRUDToCognitive  
**Updated**: October 2025
