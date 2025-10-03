# Phase 4: Smart Auto-Fill Implementation Guide

**Target Audience**: JCON 2025 Conference - "From CRUD to Cognitive"  
**Estimated Implementation Time**: 3-4 hours  
**Complexity**: Medium

---

## 🎯 Business Value

**Problem**: Booking agents spend significant time manually entering cargo details from shipping documents (PDFs, emails, forms).

**Solution**: AI extracts data from documents and pre-fills booking forms, reducing data entry time by 70-80%.

**ROI**: 
- ⏱️ **Time Savings**: 5-10 minutes per booking → 30 seconds
- 📉 **Error Reduction**: 90% fewer data entry errors
- 💰 **Cost Savings**: $50K-100K annually for mid-size operations

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    User Interface Layer                      │
├─────────────────────────────────────────────────────────────┤
│  Booking Form (booking.xhtml)                               │
│  ┌──────────────┐  ┌─────────────────────────────────────┐ │
│  │ Upload Button│  │  Auto-Fill Suggestions Panel        │ │
│  │ (PDF/Image)  │  │  - Origin: Hong Kong (95% conf)     │ │
│  │              │  │  - Destination: Stockholm (92%)     │ │
│  └──────────────┘  │  - Deadline: 2025-11-15 (88%)       │ │
│                    │  [✓ Apply All] [Edit] [Reject]      │ │
│                    └─────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    REST API Layer                            │
├─────────────────────────────────────────────────────────────┤
│  AiAutoFillResource.java                                    │
│  - POST /api/ai/autofill/extract     (upload document)     │
│  - GET  /api/ai/autofill/suggest     (get suggestions)     │
│  - POST /api/ai/autofill/validate    (verify extracted)    │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Service Layer                             │
├─────────────────────────────────────────────────────────────┤
│  AiAutoFillService.java                                     │
│  ┌──────────────────┐  ┌───────────────────────────────┐  │
│  │ Document Extract │  │ Historical Pattern Matching   │  │
│  │ - PDF Parser     │  │ - Similar shipments (RAG)     │  │
│  │ - OCR (images)   │  │ - Common routes               │  │
│  │ - GPT-4 Vision   │  │ - Frequent customers          │  │
│  └──────────────────┘  └───────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    AI Components                             │
├─────────────────────────────────────────────────────────────┤
│  LangChain4j                  Azure OpenAI                  │
│  - Document Loaders           - GPT-4 (text extraction)     │
│  - PDF/Image Parsers          - GPT-4 Vision (scanned docs) │
│  - Text Splitters             - Embeddings (similarity)     │
└─────────────────────────────────────────────────────────────┘
```

---

## 📋 Implementation Steps

### Step 1: Add Document Processing Dependencies

**File**: `pom.xml`

```xml
<!-- Phase 4: Document Processing -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
    <version>0.35.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-poi</artifactId>
    <version>0.35.0</version>
</dependency>
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
```

### Step 2: Create Auto-Fill Service

**File**: `src/main/java/org/eclipse/cargotracker/ai/aiservices/aiautofill/AiAutoFillService.java`

```java
@ApplicationScoped
public class AiAutoFillService {
    
    @Inject
    private ChatLanguageModel chatModel;
    
    @Inject
    private EmbeddingStore<TextSegment> embeddingStore;
    
    @Inject
    private EmbeddingModel embeddingModel;
    
    /**
     * Extract cargo details from uploaded document
     */
    public AutoFillSuggestion extractFromDocument(InputStream documentStream, 
                                                   String fileName) {
        // 1. Parse document (PDF/image/text)
        Document document = loadDocument(documentStream, fileName);
        
        // 2. Extract structured data using GPT-4
        String prompt = buildExtractionPrompt(document.text());
        String response = chatModel.generate(prompt);
        
        // 3. Parse AI response into structured data
        return parseAutoFillSuggestion(response);
    }
    
    /**
     * Get suggestions based on historical patterns
     */
    public List<AutoFillSuggestion> getSuggestionsFromHistory(
            String partialOrigin, String partialDestination) {
        
        // Build query from partial inputs
        String query = "Shipments from " + partialOrigin + 
                      " to " + partialDestination;
        
        // Search similar historical shipments
        List<EmbeddingMatch<TextSegment>> matches = 
            embeddingStore.findRelevant(
                embeddingModel.embed(query).content(),
                3  // Top 3 matches
            );
        
        // Extract patterns and return suggestions
        return buildSuggestionsFromMatches(matches);
    }
    
    private String buildExtractionPrompt(String documentText) {
        return """
            Extract cargo shipment details from the following document.
            
            Document:
            %s
            
            Extract and return in JSON format:
            {
              "origin": "location code or name",
              "destination": "location code or name",
              "arrivalDeadline": "YYYY-MM-DD",
              "cargoDescription": "description",
              "confidence": 0.95
            }
            
            If any field cannot be determined, set it to null.
            Provide a confidence score (0.0-1.0) for the extraction.
            """.formatted(documentText);
    }
}
```

### Step 3: Create REST Endpoint

**File**: `src/main/java/org/eclipse/cargotracker/ai/airest/AiAutoFillResource.java`

```java
@Path("/api/ai/autofill")
@ApplicationScoped
public class AiAutoFillResource {
    
    @Inject
    private AiAutoFillService autoFillService;
    
    /**
     * Upload document and extract cargo details
     * POST /api/ai/autofill/extract
     */
    @POST
    @Path("/extract")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response extractFromDocument(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileMetaData) {
        
        try {
            AutoFillSuggestion suggestion = autoFillService.extractFromDocument(
                fileInputStream, 
                fileMetaData.getFileName()
            );
            
            return Response.ok(suggestion).build();
        } catch (Exception e) {
            return Response.status(500)
                .entity(new ErrorResponse("Extraction failed: " + e.getMessage()))
                .build();
        }
    }
    
    /**
     * Get suggestions based on partial input
     * GET /api/ai/autofill/suggest?origin=Hong&destination=Stock
     */
    @GET
    @Path("/suggest")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSuggestions(
            @QueryParam("origin") String origin,
            @QueryParam("destination") String destination) {
        
        List<AutoFillSuggestion> suggestions = 
            autoFillService.getSuggestionsFromHistory(origin, destination);
        
        return Response.ok(suggestions).build();
    }
}
```

### Step 4: Update Booking UI

**File**: `src/main/webapp/booking/booking.xhtml`

**Add Upload Section**:

```xml
<!-- Smart Auto-Fill Section -->
<div class="ai-autofill-section">
    <h:panelGroup>
        <p:outputLabel value="🤖 Smart Auto-Fill: Upload shipping document" 
                       styleClass="ai-label"/>
        
        <p:fileUpload mode="simple" 
                      value="#{booking.uploadedFile}"
                      skinSimple="true"
                      accept=".pdf,.png,.jpg,.jpeg"
                      auto="false"/>
        
        <p:commandButton value="Extract Details" 
                        action="#{booking.extractFromDocument}"
                        update="extractionPanel"
                        styleClass="ui-button-success"
                        icon="pi pi-upload"/>
    </h:panelGroup>
    
    <!-- Extraction Results Panel -->
    <p:panel id="extractionPanel" 
             header="AI-Extracted Details" 
             rendered="#{booking.hasExtraction}"
             styleClass="ai-suggestion-panel">
        
        <p:panelGrid columns="3">
            <p:outputLabel value="Origin:"/>
            <p:outputLabel value="#{booking.extraction.origin}" 
                          styleClass="ai-extracted-value"/>
            <p:outputLabel value="#{booking.extraction.originConfidence}% confident" 
                          styleClass="confidence-badge"/>
            
            <p:outputLabel value="Destination:"/>
            <p:outputLabel value="#{booking.extraction.destination}"
                          styleClass="ai-extracted-value"/>
            <p:outputLabel value="#{booking.extraction.destinationConfidence}% confident"
                          styleClass="confidence-badge"/>
            
            <p:outputLabel value="Deadline:"/>
            <p:outputLabel value="#{booking.extraction.deadline}"
                          styleClass="ai-extracted-value"/>
            <p:outputLabel value="#{booking.extraction.deadlineConfidence}% confident"
                          styleClass="confidence-badge"/>
        </p:panelGrid>
        
        <p:commandButton value="✓ Apply to Form" 
                        action="#{booking.applyExtraction}"
                        update="@form"
                        styleClass="ui-button-success"/>
        
        <p:commandButton value="✗ Reject" 
                        action="#{booking.clearExtraction}"
                        update="extractionPanel"
                        styleClass="ui-button-secondary"/>
    </p:panel>
</div>

<!-- Enhanced Origin Field with Auto-Complete -->
<p:autoComplete id="origin" 
                value="#{booking.originUnlocode}"
                completeMethod="#{booking.suggestOrigin}"
                var="suggestion"
                itemLabel="#{suggestion.location}"
                itemValue="#{suggestion.code}">
    <p:column>
        <h:outputText value="#{suggestion.location}"/>
        <h:outputText value=" (#{suggestion.frequency} shipments)"
                     styleClass="suggestion-meta"/>
    </p:column>
</p:autoComplete>
```

### Step 5: Add JavaScript Enhancement

**File**: `src/main/webapp/resources/js/ai-autofill.js`

```javascript
/**
 * AI Auto-Fill Enhancement
 * Provides real-time suggestions as user types
 */
class AiAutoFill {
    constructor() {
        this.debounceTimer = null;
        this.suggestionCache = new Map();
    }
    
    /**
     * Initialize auto-fill on booking form
     */
    init() {
        const originField = document.getElementById('origin');
        const destField = document.getElementById('destination');
        
        if (originField && destField) {
            originField.addEventListener('input', 
                (e) => this.handleInput('origin', e.target.value));
            destField.addEventListener('input', 
                (e) => this.handleInput('destination', e.target.value));
        }
    }
    
    /**
     * Debounced input handler
     */
    handleInput(field, value) {
        clearTimeout(this.debounceTimer);
        
        if (value.length < 3) return;
        
        this.debounceTimer = setTimeout(() => {
            this.fetchSuggestions(field, value);
        }, 500);
    }
    
    /**
     * Fetch AI suggestions
     */
    async fetchSuggestions(field, value) {
        const cacheKey = `${field}:${value}`;
        
        if (this.suggestionCache.has(cacheKey)) {
            this.displaySuggestions(this.suggestionCache.get(cacheKey));
            return;
        }
        
        try {
            const response = await fetch(
                `/cargo-tracker/api/ai/autofill/suggest?${field}=${value}`
            );
            
            const suggestions = await response.json();
            this.suggestionCache.set(cacheKey, suggestions);
            this.displaySuggestions(suggestions);
            
        } catch (error) {
            console.error('Failed to fetch suggestions:', error);
        }
    }
    
    /**
     * Display suggestions in dropdown
     */
    displaySuggestions(suggestions) {
        // Show PrimeFaces auto-complete overlay
        // with AI-enhanced suggestions
        PF('autoCompleteWidget').show();
    }
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    const autoFill = new AiAutoFill();
    autoFill.init();
});
```

---

## 🎨 UI/UX Mockup

```
┌────────────────────────────────────────────────────────────┐
│  📦 Book New Cargo                                         │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  🤖 Smart Auto-Fill                                        │
│  ┌────────────────────────────────────────────────────┐   │
│  │ [📁 Choose File: shipping_order.pdf          ]     │   │
│  │                                                     │   │
│  │ [🚀 Extract Details]                               │   │
│  └────────────────────────────────────────────────────┘   │
│                                                            │
│  AI-Extracted Details (95% confidence)                    │
│  ┌────────────────────────────────────────────────────┐   │
│  │ ✓ Origin: Hong Kong (CNHKG)         [96% ✓]       │   │
│  │ ✓ Destination: Stockholm (SESTO)    [94% ✓]       │   │
│  │ ✓ Deadline: 2025-11-15              [92% ✓]       │   │
│  │                                                     │   │
│  │ [✓ Apply to Form]  [✗ Reject]                     │   │
│  └────────────────────────────────────────────────────┘   │
│                                                            │
│  1. Origin                                                 │
│  ┌────────────────────────────────────────────────────┐   │
│  │ Hong Kong ▼                                        │   │
│  ├────────────────────────────────────────────────────┤   │
│  │ 💡 Hong Kong, China (CNHKG)     [152 shipments]   │   │
│  │    Most common: → Stockholm, Rotterdam            │   │
│  └────────────────────────────────────────────────────┘   │
│                                                            │
│  2. Destination                                            │
│  ┌────────────────────────────────────────────────────┐   │
│  │ Stockholm ▼                                        │   │
│  ├────────────────────────────────────────────────────┤   │
│  │ 💡 Stockholm, Sweden (SESTO)    [89 shipments]    │   │
│  │    Avg transit: 28 days                           │   │
│  │    Suggested deadline: 2025-11-20                 │   │
│  └────────────────────────────────────────────────────┘   │
│                                                            │
│  3. Arrival Deadline                                       │
│  ┌────────────────────────────────────────────────────┐   │
│  │ 2025-11-15  📅                                     │   │
│  │ ⚠️  Tight deadline! Avg transit: 28 days          │   │
│  └────────────────────────────────────────────────────┘   │
│                                                            │
│  [📝 Book Cargo]                                          │
└────────────────────────────────────────────────────────────┘
```

---

## 🧪 Testing Scenarios

### Test Case 1: PDF Extraction
```
Given: User uploads "shipping_order_HKG_SESTO.pdf"
When: System extracts document content
Then: 
  - Origin = "CNHKG" (Hong Kong) with 96% confidence
  - Destination = "SESTO" (Stockholm) with 94% confidence
  - Deadline = "2025-11-15" with 92% confidence
  - User can apply or reject extraction
```

### Test Case 2: Partial Input Suggestion
```
Given: User types "Hong" in origin field
When: System fetches historical data
Then: 
  - Shows "Hong Kong, China (CNHKG)" with 152 past shipments
  - Shows most common destinations from Hong Kong
  - Suggests optimal transit times
```

### Test Case 3: Low Confidence Handling
```
Given: Document is low quality or ambiguous
When: AI extracts with <70% confidence
Then:
  - Shows warning "⚠️ Low confidence extraction"
  - Highlights uncertain fields in yellow
  - Recommends manual verification
```

---

## 📊 Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Extraction Accuracy | >90% | Compare extracted vs manual entry |
| Time Savings | 5-10 min → 30 sec | Avg booking completion time |
| User Adoption | >60% | % of bookings using auto-fill |
| Error Rate | <5% | Incorrect extractions requiring fix |
| Confidence Score | >85% avg | AI confidence in extractions |

---

## 🚀 Demo Script (JCON Presentation)

**Slide 1: The Problem**
> "Booking agents manually enter data from PDFs, emails, and forms. This takes 5-10 minutes per shipment and causes frequent errors."

**Slide 2: The Solution** (Show Architecture Diagram)
> "We use LangChain4j to parse documents, GPT-4 to extract structured data, and RAG to suggest common patterns."

**Slide 3: Live Demo** (Show UI Mockup)
> "Watch as I upload a shipping order PDF. The AI extracts origin, destination, and deadline in seconds with 95% confidence."

**Slide 4: The Results**
> "70-80% faster data entry, 90% fewer errors, $50K-100K annual savings for mid-size operations."

**Slide 5: Implementation** (Show Code Snippet)
> "Here's the core service - just 50 lines of Java using LangChain4j. Notice how we leverage the existing RAG infrastructure."

---

## 🔧 Technical Considerations

### Performance
- **Document parsing**: 2-5 seconds for PDFs (cached)
- **AI extraction**: 3-7 seconds (Azure OpenAI latency)
- **Suggestion lookup**: <500ms (from existing RAG index)

### Security
- **File validation**: Whitelist .pdf, .png, .jpg only
- **Size limits**: Max 10MB per upload
- **Virus scanning**: Integrate ClamAV for production
- **Data privacy**: Documents not stored, only extracted data

### Scalability
- **Async processing**: Queue large documents
- **Caching**: Cache suggestions for 24 hours
- **Rate limiting**: 10 extractions per user per hour

---

## 💡 Future Enhancements

1. **Multi-language Support**: Extract from Chinese, German, Spanish documents
2. **Image Recognition**: GPT-4 Vision for scanned/photographed documents
3. **Email Integration**: Parse booking requests from emails
4. **Confidence Tuning**: Machine learning to improve confidence scores
5. **Batch Processing**: Upload multiple documents at once

---

## 📚 Resources

- **LangChain4j Docs**: https://docs.langchain4j.dev/
- **Azure OpenAI**: https://learn.microsoft.com/azure/ai-services/openai/
- **Apache PDFBox**: https://pdfbox.apache.org/
- **PrimeFaces FileUpload**: https://www.primefaces.org/showcase/ui/file/upload/basic.xhtml

---

**Next Phase**: [Phase 5 - Workflow Optimization →](phase5-workflow-optimization.md)

**Questions for Audience**:
1. How much time does your team spend on data entry?
2. What percentage of bookings have errors?
3. What document formats do you receive?

