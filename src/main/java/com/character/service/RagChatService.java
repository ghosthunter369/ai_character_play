package com.character.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Deprecated
public class RagChatService {

    @Resource
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;
    @Resource
    private  EmbeddingModel embeddingModel;
    public RagChatService(InMemoryEmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    public List<TextSegment> search(String query) {
        // 1. 生成 embedding 对象
        Embedding embeddingObj = embeddingModel.embed(query).content();

        System.out.println("向量长度：" + embeddingObj.vector().length);
// 2. 构造搜索请求
        EmbeddingSearchRequest request = EmbeddingSearchRequest.<TextSegment>builder()
                .queryEmbedding(embeddingObj)
                .maxResults(3)
                .build();

// 3. 搜索
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        List<TextSegment> segments = result.matches().stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());
        return  segments;
    }
}
