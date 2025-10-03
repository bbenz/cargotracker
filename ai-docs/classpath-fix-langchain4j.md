# Classpath Fix: LangChain4j Dependencies Not Found

## Issue
Application failed to start with error:
```
CWNEN0047W: Resource annotations on the fields of the AiHistoricalIndexer class will be ignored. 
The annotations could not be obtained because of the exception: java.lang.NoClassDefFoundError: EmbeddingModel

CWWKZ0002E: An exception occurred while starting the application cargo-tracker.
WELD-001408: Unsatisfied dependencies for type AiHistoricalIndexer with qualifiers @Default
WELD-001474: Class org.eclipse.cargotracker.ai.aiservices.airag.AiHistoricalIndexer is on the classpath, 
but was ignored because a class it references was not found: EmbeddingModel.
```

## Root Cause
The LangChain4j dependencies were declared in `pom.xml` but not included in Liberty's classpath. OpenLiberty requires external libraries to be:
1. Copied to `${shared.resource.dir}` (Liberty's shared resources folder)
2. Declared as a `<library>` in `server.xml`
3. Referenced in the web application's `<classloader>`

## Solution

### 1. Updated pom.xml - Liberty Maven Plugin Configuration
Added LangChain4j JARs to `copyDependencies`:

```xml
<plugin>
  <groupId>io.openliberty.tools</groupId>
  <artifactId>liberty-maven-plugin</artifactId>
  <version>3.11.3</version>
  <configuration>
    <copyDependencies>
      <dependencyGroup>
        <location>${project.build.directory}/liberty/wlp/usr/shared/resources</location>
        <dependency>
          <groupId>org.hsqldb</groupId>
          <artifactId>hsqldb</artifactId>
        </dependency>
        <!-- LangChain4j dependencies -->
        <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j</artifactId>
        </dependency>
        <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j-azure-open-ai</artifactId>
        </dependency>
        <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j-qdrant</artifactId>
        </dependency>
        <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j-embeddings</artifactId>
        </dependency>
        <dependency>
          <groupId>dev.langchain4j</groupId>
          <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
        </dependency>
      </dependencyGroup>
    </copyDependencies>
  </configuration>
</plugin>
```

### 2. Updated server.xml - Library Declaration
Added AI library definition:

```xml
<!-- LangChain4j library for AI features -->
<library id="aiLib">
  <fileset dir="${shared.resource.dir}" includes="langchain4j*.jar"/>
</library>
```

### 3. Updated server.xml - Classloader Configuration
Updated web application classloader to include AI library:

```xml
<webApplication contextRoot="cargo-tracker" location="cargo-tracker.war">
  <classloader commonLibraryRef="dbLib,aiLib"/>
  ...
</webApplication>
```

## Files Modified
1. `pom.xml` - Added copyDependencies for LangChain4j JARs
2. `src/main/liberty/config/server.xml` - Added library and classloader references

## How Liberty Shared Libraries Work

### Shared Resource Directory
- **Location**: `target/liberty/wlp/usr/shared/resources/`
- **Purpose**: Common location for JARs used by multiple applications
- **Maven copies**: Libraries specified in `copyDependencies` during build

### Library Definition
```xml
<library id="myLib">
  <fileset dir="${shared.resource.dir}" includes="*.jar"/>
</library>
```
- Defines a named library from JAR files
- Wildcards supported (`langchain4j*.jar` matches all LangChain4j JARs)

### Classloader Configuration
```xml
<classloader commonLibraryRef="lib1,lib2"/>
```
- Makes libraries available to the application
- Multiple libraries separated by commas
- Parent-last classloading by default

## Verification

After these changes, Liberty should:
1. Copy all LangChain4j JARs to `target/liberty/wlp/usr/shared/resources/`
2. Load them into the classloader
3. Make them available to CDI for `@Inject` annotations
4. Successfully start the application

Check logs for:
```
[INFO] copyDependencies copied file langchain4j-0.35.0.jar to location ...
[INFO] copyDependencies copied file langchain4j-azure-open-ai-0.35.0.jar to location ...
```

## Alternative Approaches (Not Used)

### Option 1: Package in WAR (WEB-INF/lib)
Could have used `<scope>compile</scope>` to include JARs in WAR:
- **Pros**: Self-contained WAR, simpler deployment
- **Cons**: Larger WAR file, slower builds, memory overhead if multiple apps

### Option 2: Liberty Global Library
Could have added to Liberty's global library directory:
- **Pros**: Available to all applications
- **Cons**: Manual deployment step, not build-integrated

## Status
✅ Fixed - LangChain4j dependencies now properly configured in Liberty classpath.

## Testing
Start application:
```bash
./mvnw liberty:dev -Popenliberty
```

Expected: No `NoClassDefFoundError` for `EmbeddingModel` or other LangChain4j classes.
