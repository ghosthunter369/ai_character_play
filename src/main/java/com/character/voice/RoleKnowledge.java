package com.character.voice;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

/** Optional role-filtered RAG; no embedding calls when disabled. */
@Component
public class RoleKnowledge {
    private final ObjectProvider<EmbeddingModel> models;
    private final ObjectProvider<InMemoryEmbeddingStore<TextSegment>> stores;
    public RoleKnowledge(ObjectProvider<EmbeddingModel> models,ObjectProvider<InMemoryEmbeddingStore<TextSegment>> stores) {
        this.models=models; this.stores=stores;
    }
    public String context(String role,String message) {
        if(stores.getIfAvailable()==null || models.getIfAvailable()==null) return "";
        return EmbeddingStoreContentRetriever.builder().embeddingModel(models.getObject()).embeddingStore(stores.getObject())
                .filter(MetadataFilterBuilder.metadataKey("appName").isEqualTo(role)).maxResults(3).build()
                .retrieve(Query.from(message)).stream().map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n"));
    }
}
