package org.eclipse.cargotracker.ai.aiservices.airag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Historical Data Indexer for RAG.
 * 
 * Extracts cargo and handling event data from the H2 database,
 * generates embeddings using Azure OpenAI, and stores them in Qdrant
 * for semantic search.
 * 
 * @author Brian Benz & Ed Burns
 * @since 3.1
 */
@ApplicationScoped
public class AiHistoricalIndexer {
    
    private static final Logger logger = Logger.getLogger(AiHistoricalIndexer.class.getName());
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Inject
    private EmbeddingModel embeddingModel;
    
    @Inject
    private EmbeddingStore<TextSegment> embeddingStore;
    
    private boolean indexed = false;
    
    /**
     * Index historical cargo data.
     * Called automatically on first query or manually via REST API.
     */
    public synchronized void indexHistoricalData() {
        if (embeddingModel == null || embeddingStore == null) {
            logger.warning("Embedding model or store not available. Skipping historical data indexing.");
            logger.info("To enable RAG features, configure Azure OpenAI and start Qdrant.");
            return;
        }
        
        if (indexed) {
            logger.info("Historical data already indexed. Skipping.");
            return;
        }
        
        try {
            logger.info("🚀 Starting historical cargo data indexing for RAG...");
            
            // Get all cargo from database
            List<Cargo> allCargo = entityManager.createNamedQuery("Cargo.findAll", Cargo.class)
                    .getResultList();
            
            logger.info("Found " + allCargo.size() + " cargo records to index.");
            
            if (allCargo.isEmpty()) {
                logger.info("No cargo data to index yet. Will index when data is available.");
                indexed = true;
                return;
            }
            
            List<TextSegment> segments = new ArrayList<>();
            
            for (Cargo cargo : allCargo) {
                try {
                    // Create text representation of cargo for embedding
                    String cargoText = buildCargoDocument(cargo);
                    
                    // Create metadata for source tracking
                    Metadata metadata = new Metadata();
                    metadata.put("trackingId", cargo.getTrackingId().getIdString());
                    metadata.put("origin", cargo.getOrigin().getName());
                    metadata.put("destination", cargo.getRouteSpecification().getDestination().getName());
                    metadata.put("type", "cargo");
                    
                    TextSegment segment = TextSegment.from(cargoText, metadata);
                    segments.add(segment);
                    
                } catch (Exception e) {
                    logger.warning("Failed to process cargo " + cargo.getTrackingId() + ": " + e.getMessage());
                }
            }
            
            if (!segments.isEmpty()) {
                // Generate embeddings and store in Qdrant
                logger.info("Generating embeddings for " + segments.size() + " documents...");
                embeddingStore.addAll(segments.stream()
                        .map(segment -> embeddingModel.embed(segment).content())
                        .collect(Collectors.toList()), 
                        segments);
                
                logger.info("✅ Successfully indexed " + segments.size() + " cargo records to Qdrant.");
            }
            
            indexed = true;
            
        } catch (Exception e) {
            logger.severe("Failed to index historical data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Build a text document from cargo data for embedding.
     * 
     * @param cargo The cargo to document
     * @return Text representation
     */
    private String buildCargoDocument(Cargo cargo) {
        StringBuilder doc = new StringBuilder();
        
        doc.append("Cargo Tracking ID: ").append(cargo.getTrackingId().getIdString()).append("\n");
        doc.append("Origin: ").append(cargo.getOrigin().getName()).append("\n");
        doc.append("Destination: ").append(cargo.getRouteSpecification().getDestination().getName()).append("\n");
        doc.append("Arrival Deadline: ").append(cargo.getRouteSpecification().getArrivalDeadline()).append("\n");
        
        if (cargo.getItinerary() != null && !cargo.getItinerary().getLegs().isEmpty()) {
            doc.append("Route: ");
            cargo.getItinerary().getLegs().forEach(leg -> {
                doc.append(leg.getLoadLocation().getName())
                   .append(" → ")
                   .append(leg.getUnloadLocation().getName())
                   .append(" via ")
                   .append(leg.getVoyage().getVoyageNumber().getIdString())
                   .append("; ");
            });
            doc.append("\n");
        }
        
        // Add delivery status
        if (cargo.getDelivery() != null) {
            doc.append("Transport Status: ").append(cargo.getDelivery().getTransportStatus()).append("\n");
            doc.append("Routing Status: ").append(cargo.getDelivery().getRoutingStatus()).append("\n");
            
            if (cargo.getDelivery().getCurrentVoyage() != null) {
                doc.append("Current Voyage: ").append(cargo.getDelivery().getCurrentVoyage().getVoyageNumber().getIdString()).append("\n");
            }
            
            if (cargo.getDelivery().getLastKnownLocation() != null) {
                doc.append("Last Known Location: ").append(cargo.getDelivery().getLastKnownLocation().getName()).append("\n");
            }
        }
        
        return doc.toString();
    }
    
    /**
     * Re-index all historical data.
     * Can be called manually to refresh the index.
     */
    public void reindex() {
        indexed = false;
        indexHistoricalData();
    }
    
    /**
     * Check if historical data has been indexed.
     * 
     * @return true if indexed
     */
    public boolean isIndexed() {
        return indexed;
    }
}
