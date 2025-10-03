# Verification: Check if Dependencies Were Copied

After running `./mvnw clean package -Popenliberty -DskipTests`, run these commands to verify:

## 1. Check if Guava was copied
```bash
ls -la target/liberty/wlp/usr/shared/resources/guava*.jar
```

**Expected**: Should show `guava-33.0.0-jre.jar`

## 2. Check if failureaccess was copied
```bash
ls -la target/liberty/wlp/usr/shared/resources/failureaccess*.jar
```

**Expected**: Should show `failureaccess-1.0.2.jar`

## 3. Count all JARs copied
```bash
ls -1 target/liberty/wlp/usr/shared/resources/*.jar | wc -l
```

**Expected**: Should show 100+ JARs

## 4. List all AI-related JARs
```bash
ls -1 target/liberty/wlp/usr/shared/resources/*.jar | grep -E "(langchain|guava|azure|qdrant|netty|grpc|reactor)"
```

**Expected**: Should show dozens of JARs including:
- langchain4j-*.jar
- guava-*.jar
- failureaccess-*.jar
- azure-*.jar
- reactor-*.jar
- netty-*.jar
- grpc-*.jar

## If JARs are NOT there:

The maven-dependency-plugin isn't running. Try this alternative:

```bash
# Build first
./mvnw clean package -Popenliberty -DskipTests

# Manually copy dependencies
cd target/liberty/wlp/usr/shared/resources
cp ../../../../classes/../../dependency/*.jar . 2>/dev/null || true

# Or use this command to copy from Maven local repo
./mvnw dependency:copy-dependencies -DoutputDirectory=target/liberty/wlp/usr/shared/resources -Popenliberty
```

## After verifying JARs exist, start Liberty:

```bash
./mvnw liberty:run -Popenliberty
```

---

**Key Change Made**: 
- Changed phase from `package` to `prepare-package` (runs earlier)
- Changed scope from `runtime` to `compile` (includes LangChain4j dependencies)
- Added `excludeScope=provided` (excludes HSQLDB which is handled separately)
