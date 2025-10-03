# SOLUTION: Liberty Dev Mode vs Run Mode

## 🔍 Problem Identified

When you run `./mvnw liberty:dev -Popenliberty`, Liberty deploys a **test WAR** (`cargo-tracker-test.war`) that only contains test-specific code, not your full application including the new AI features.

This is why:
- ✅ Classes compiled successfully (`target/classes` has all AI classes)
- ✅ `hasResponse()` method exists in compiled `AiRAGBean.class`
- ❌ But the deployed WAR doesn't contain `AiRAGBean` or any AI code
- ❌ Result: `PropertyNotFoundException` because the bean isn't deployed

## ✅ Solution: Use Run Mode Instead of Dev Mode

### Option 1: Deploy and Run (Recommended for Testing)

```bash
# Stop Liberty if running (press 'q' or Ctrl+C)

# Package the application
./mvnw clean package -Popenliberty

# Deploy and run
./mvnw liberty:run -Popenliberty
```

**What this does**:
- Creates the proper `cargo-tracker.war` with ALL your code
- Deploys it to Liberty
- Starts the server
- Runs in foreground (use Ctrl+C to stop)

### Option 2: Install and Start (For Background Running)

```bash
# Package and install
./mvnw clean install -Popenliberty

# Start server in background
./mvnw liberty:start -Popenliberty

# Watch logs
tail -f target/liberty/wlp/usr/servers/defaultServer/logs/console.log

# When done, stop server
./mvnw liberty:stop -Popenliberty
```

## 🤔 Why Dev Mode Doesn't Work

`liberty:dev` is designed for **test-driven development** with Arquillian:
- Creates minimal test WARs for integration tests
- Hot-reloads test code
- Doesn't deploy the full application WAR
- The test WAR only includes domain/application/infrastructure packages (not interfaces or AI packages)

## 📋 New Testing Steps

Update your `demosteps.md` with these corrected commands:

```bash
# Build and run
./mvnw clean package -Popenliberty && ./mvnw liberty:run -Popenliberty

# Or combine in one command
./mvnw clean install -Popenliberty liberty:run
```

Wait 5-10 minutes, then access:
- Main app: http://localhost:8080/cargo-tracker
- AI RAG: http://localhost:8080/cargo-tracker/ai-rag.xhtml

REST API test:
```bash
curl -X POST http://localhost:8080/cargo-tracker/api/ai/rag/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What cargo went from Hong Kong to Stockholm?"}'
```

## ✅ Expected Results

After using `liberty:run`, you should see:

### In Logs:
```
[INFO] Building war: .../target/cargo-tracker-3.1-SNAPSHOT.war
[INFO] [AUDIT] CWWKT0016I: Web application available (default_host): http://...8080/cargo-tracker/
[INFO] [AUDIT] CWWKZ0001I: Application cargo-tracker started
```

### Deployed Structure:
```bash
$ find target/liberty/.../cargo-tracker.war/WEB-INF/classes -name "AiRAGBean.class"
.../cargo-tracker.war/WEB-INF/classes/org/eclipse/cargotracker/interfaces/backing/AiRAGBean.class
```

### At http://localhost:8080/cargo-tracker/ai-rag.xhtml:
- ✅ Page loads successfully
- ✅ "AI-Powered Cargo Query" header visible
- ✅ Text area for questions
- ✅ "Ask AI" and "Clear" buttons
- ✅ No `PropertyNotFoundException` errors

## 🐛 Verification Command

After Liberty starts with `liberty:run`, verify the bean is deployed:

```bash
# Check that AiRAGBean exists in deployed WAR
find target/liberty -name "AiRAGBean.class" -exec ls -la {} \;

# Should show something like:
# .../cargo-tracker.war/WEB-INF/classes/org/eclipse/cargotracker/interfaces/backing/AiRAGBean.class
```

## 📝 Why This Wasn't Obvious

1. `liberty:dev` is commonly used for development
2. It works fine for the original cargo-tracker code (which was test-focused)
3. The new AI features are in packages (`interfaces.backing`, `ai.aiservices`) that aren't included in test WARs
4. Error message said "property not found" (runtime) not "class not found" (deployment)

## 🎯 Summary

| Command | Purpose | Deploys Full App? | Use For |
|---------|---------|-------------------|---------|
| `liberty:dev` | Test-driven development | ❌ No (test WAR only) | Running integration tests |
| `liberty:run` | Run full application | ✅ Yes | Development & testing |
| `liberty:start` | Start in background | ✅ Yes | Production-like testing |

**For testing your AI features**: Use `liberty:run` or `liberty:start`, NOT `liberty:dev`.

---

**Date**: October 1, 2025  
**Issue**: PropertyNotFoundException for hasResponse  
**Root Cause**: liberty:dev deploys test WAR without AI code  
**Solution**: Use liberty:run to deploy full application WAR
