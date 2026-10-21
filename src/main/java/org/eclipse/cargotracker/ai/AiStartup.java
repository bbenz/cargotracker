package org.eclipse.cargotracker.ai;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Builds the document index when the application starts, not on the first question. */
@ApplicationScoped
public class AiStartup {

  @Inject private ContentRetriever documents;
  @Inject private Logger logger;

  void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
    try {
      documents.retrieve(Query.from("cargo"));
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "Could not build the document index at startup", e);
    }
  }
}
