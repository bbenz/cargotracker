# Fix: NoClassDefFoundError for Guava

## Problem
```
[ERROR] java.lang.NoClassDefFoundError: com/google/common/util/concurrent/internal/InternalFutureFailureAccess
```

**Root Cause**: Guava (Google's common utilities) is a transitive dependency of LangChain4j that wasn't being copied to Liberty's shared library.

## Solution Applied

### 1. Added Guava to OpenLiberty Profile Dependencies
**File**: `pom.xml` (lines ~472-477)
```xml
<!-- Guava - explicitly declared for Liberty shared library -->
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>33.0.0-jre</version>
</dependency>
```

### 2. Added Guava to Liberty's copyDependencies
**File**: `pom.xml` (lines ~510-514)
```xml
<!-- Guava - required by LangChain4j -->
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
</dependency>
```

### 3. Updated Liberty aiLib to Include Guava
**File**: `src/main/liberty/config/server.xml` (line ~116)
```xml
<library id="aiLib">
  <fileset dir="${shared.resource.dir}" includes="langchain4j*.jar,guava*.jar,failureaccess*.jar"/>
</library>
```

## How to Apply

```bash
# Stop Liberty (Ctrl+C)

# Export environment variables
export AZURE_OPENAI_ENDPOINT="https://bbenz-crudtocognitive.openai.azure.com/"
export AZURE_OPENAI_KEY="your-key"
export AZURE_OPENAI_DEPLOYMENT_NAME="gpt-4"
export AZURE_OPENAI_EMBEDDING_DEPLOYMENT="text-embedding-ada-002"
export QDRANT_HOST="localhost"
export QDRANT_PORT="6333"

# Rebuild
./mvnw clean package -Popenliberty -DskipTests

# Run
./mvnw liberty:run -Popenliberty
```

## Verification

After Liberty starts, check the logs for:
```
[INFO] copyDependencies copied file guava-33.0.0-jre.jar
[INFO] copyDependencies copied file failureaccess-1.0.2.jar
```

Then test the RAG UI at http://localhost:8080/cargo-tracker/ai-rag.xhtml

## What to Expect

1. ✅ Page loads successfully
2. ✅ Form is visible with query text area
3. ✅ "Ask AI" button is enabled
4. ✅ Clicking "Ask AI" submits the form without errors
5. ✅ AI processes the query and returns an answer

## Test Query

Try: "What cargo went from Hong Kong to Stockholm?"

Expected behavior:
- Loading indicator appears
- After 2-5 seconds, answer appears with source citations
- No NoClassDefFoundError in console

---

**Date**: October 2, 2025  
**Issue**: NoClassDefFoundError for Guava  
**Root Cause**: Transitive dependency not copied to Liberty  
**Fix**: Explicitly declare Guava, add to copyDependencies, update server.xml library pattern
