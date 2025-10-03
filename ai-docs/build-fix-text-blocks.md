# Build Fix: Java Text Blocks

## Issue
Maven compilation failed with:
```
[ERROR] text blocks are not supported in -source 11
  (use -source 15 or higher to enable text blocks)
```

## Root Cause
The `AiRAGQueryService.java` file used Java 15+ text blocks (triple quotes `"""`) in the `buildAugmentedPrompt()` method, but the project is configured to compile with Java 11 source compatibility.

## Solution
Replaced the text block with traditional string concatenation using `+` operator:

### Before (Java 15+ text block):
```java
private String buildAugmentedPrompt(String query, String context) {
    return String.format(
        """
        Context from historical cargo data:
        %s
        
        User Question: %s
        
        Please answer the question based on the context provided above.
        ...
        """,
        context,
        query
    );
}
```

### After (Java 11 compatible):
```java
private String buildAugmentedPrompt(String query, String context) {
    return String.format(
        "Context from historical cargo data:\n%s\n\n" +
        "User Question: %s\n\n" +
        "Please answer the question based on the context provided above. " +
        ...
        context,
        query
    );
}
```

## Files Modified
- `src/main/java/org/eclipse/cargotracker/ai/aiservices/airag/AiRAGQueryService.java`

## Why This Matters
The project uses Java 21 runtime but compiles with `-source 11` for broader compatibility. Text blocks are a Java 15+ feature and must be avoided or the Maven compiler settings must be updated.

## Alternative Solution (Not Implemented)
Could update `pom.xml` to use Java 15+ source level:
```xml
<maven.compiler.source>15</maven.compiler.source>
<maven.compiler.target>15</maven.compiler.target>
```

However, the current approach (string concatenation) maintains compatibility with the existing build configuration.

## Status
✅ Fixed - Build should now compile successfully with Java 11 source compatibility.
