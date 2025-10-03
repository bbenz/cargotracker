# Troubleshooting Guide - RAG Implementation

## Current Error Analysis

### Error: `Property [hasResponse] not found on type [org.eclipse.cargotracker.interfaces.backing.AiRAGBean]`

**Status**: ✅ The `hasResponse()` method EXISTS in the code (line 108 of AiRAGBean.java)

**Root Cause**: Likely a stale class issue. The application was deployed before all changes were compiled.

---

## 🔧 Fix: Clean Rebuild and Restart

Run these commands **in order**:

```bash
# Stop Liberty (press 'q' in the terminal where it's running, or Ctrl+C)

# Clean everything
./mvnw clean -Popenliberty

# Full rebuild
./mvnw package -Popenliberty

# Start Liberty
./mvnw liberty:dev -Popenliberty
```

**Wait 5-10 minutes** for Liberty to fully start, then access:
```
http://localhost:8080/cargo-tracker/ai-rag.xhtml
```

---

## ✅ What Should Happen

### Expected Log Messages:
```
[INFO] copyDependencies copied file langchain4j-azure-open-ai-0.35.0.jar
[INFO] copyDependencies copied file langchain4j-qdrant-0.35.0.jar
[INFO] [AUDIT] CWWKZ0001I: Application cargo-tracker started in XXX seconds
```

### Expected Warnings (OK to ignore):
```
[WARNING] CNTR4016W: The message endpoint for the CargoHandledConsumer...
```
These JMS warnings are normal - the application doesn't have JMS queues configured.

```
[WARNING] CWWJP9991W: [eclipselink.metadata] isNullAllowed is reset to false...
```
These JPA warnings are normal - EclipseLink metadata configuration.

```
[WARNING] Apache MyFaces Core is running in DEVELOPMENT mode
```
This is expected for development builds.

### Expected Error (if Azure OpenAI/Qdrant not configured):
```
[WARNING] Embedding model or store not available. Skipping historical data indexing.
```

---

## 🧪 Verification Steps

### 1. Check the Bean Class is Correct

Run this to verify the `hasResponse()` method exists:

```bash
grep -A 3 "public boolean hasResponse" src/main/java/org/eclipse/cargotracker/interfaces/backing/AiRAGBean.java
```

**Expected output**:
```java
public boolean hasResponse() {
    return response != null;
}
```

### 2. Verify Compiled Classes

After `./mvnw package`, check that the class file exists:

```bash
ls -la target/classes/org/eclipse/cargotracker/interfaces/backing/AiRAGBean.class
```

### 3. Check Liberty Deployed the Latest Code

After Liberty starts, check the deployed application:

```bash
ls -la target/liberty/wlp/usr/servers/defaultServer/apps/cargo-tracker.war/WEB-INF/classes/org/eclipse/cargotracker/interfaces/backing/AiRAGBean.class
```

The timestamp should be recent (today's date).

---

## 🐛 If Error Persists

### Option 1: Nuclear Clean
```bash
# Stop Liberty
./mvnw liberty:stop -Popenliberty

# Delete all build artifacts
rm -rf target/
rm -rf src/main/liberty/wlp/

# Full rebuild
./mvnw clean package -Popenliberty

# Start fresh
./mvnw liberty:dev -Popenliberty
```

### Option 2: Check Bean Visibility

Add this to verify the bean is being created:

```bash
# Check Liberty logs for CDI bean discovery
grep "AiRAGBean" target/liberty/wlp/usr/servers/defaultServer/logs/messages.log
```

### Option 3: Verify faces-config.xml

The bean should be auto-discovered, but check if there's any explicit configuration:

```bash
cat src/main/webapp/WEB-INF/faces-config.xml
```

---

## 📊 Common Issues and Solutions

### Issue: "Response already committed" warnings
**Cause**: JSF tried to render error page after response started  
**Impact**: Cosmetic only, doesn't affect functionality  
**Fix**: Will be resolved after clean rebuild

### Issue: JMS warnings
**Cause**: Application has JMS consumers but no JMS provider configured  
**Impact**: None - these features aren't used for RAG  
**Fix**: Can be ignored, or configure embedded messaging in server.xml

### Issue: MyFaces DEVELOPMENT mode
**Cause**: JSF configured for development  
**Impact**: More verbose error messages, slower performance  
**Fix**: Normal for development - will be changed for production

### Issue: PropertyNotFoundException for hasResponse
**Cause**: Stale compiled classes  
**Impact**: RAG UI doesn't work  
**Fix**: ✅ **Clean rebuild (see above)**

---

## 🎯 Success Criteria

After clean rebuild, you should see:

1. ✅ No `PropertyNotFoundException` errors
2. ✅ AI RAG page loads at http://localhost:8080/cargo-tracker/ai-rag.xhtml
3. ✅ Query text area is visible
4. ✅ "Ask AI" button is clickable (may be disabled if Azure OpenAI not configured)
5. ✅ No CDI deployment errors in logs

---

## 🔍 Debug: Check What Got Compiled

Run this to see all the AI-related classes that were compiled:

```bash
find target/classes/org/eclipse/cargotracker/ai -name "*.class" -type f
```

**Expected output** (should include):
```
target/classes/org/eclipse/cargotracker/ai/aiservices/airag/AiRAGResponse.class
target/classes/org/eclipse/cargotracker/ai/aiservices/airag/AiRAGQueryService.class
target/classes/org/eclipse/cargotracker/ai/aiservices/airag/AiHistoricalIndexer.class
target/classes/org/eclipse/cargotracker/ai/aiservices/airag/AiRAGResource.class
```

And for the backing bean:
```bash
ls -la target/classes/org/eclipse/cargotracker/interfaces/backing/AiRAGBean.class
```

---

## 📝 Environment Check

Before testing RAG functionality, make sure these are set:

```bash
# Check environment variables
echo $AZURE_OPENAI_ENDPOINT
echo $AZURE_OPENAI_DEPLOYMENT_NAME
echo $AZURE_OPENAI_EMBEDDING_DEPLOYMENT
echo $QDRANT_HOST
echo $QDRANT_PORT

# Check Qdrant is running
docker ps | grep qdrant
```

If Qdrant isn't running:
```bash
docker run -d -p 6333:6333 qdrant/qdrant
```

---

## 🚀 Next Steps After Fix

Once the clean rebuild resolves the error:

1. **Test Basic Load**: Verify http://localhost:8080/cargo-tracker/ai-rag.xhtml loads
2. **Test Service Status**: Check if "AI service is currently unavailable" message appears
3. **Configure Environment**: Set Azure OpenAI and Qdrant environment variables
4. **Test Indexing**: Restart and check logs for indexing messages
5. **Test Query**: Try "What cargo went from Hong Kong to Stockholm?"

---

## 📞 Still Having Issues?

If the clean rebuild doesn't fix it, gather this information:

```bash
# Java version
java -version

# Maven version
./mvnw -version

# Check the actual compiled method
javap -p target/classes/org/eclipse/cargotracker/interfaces/backing/AiRAGBean.class | grep -A 2 hasResponse

# Check Liberty version
cat target/liberty/wlp/lib/versions/openliberty.properties | grep com.ibm.websphere.productVersion

# Full error from Liberty logs
tail -n 100 target/liberty/wlp/usr/servers/defaultServer/logs/messages.log
```

---

**Last Updated**: October 1, 2025  
**Status**: Clean rebuild should resolve PropertyNotFoundException  
**Branch**: CRUDtoCognitive
