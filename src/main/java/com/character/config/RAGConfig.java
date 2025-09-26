package com.character.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RAGConfig {

    @Value("${langchain4j.open-ai.text-chat-model.api-key}")
    private String openAiApiKey;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)               // 从配置文件读取
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1") // 可选
                .modelName("text-embedding-v4") // 你的模型名
                .build();
    }


    /**
     * 加载本地文档（递归子目录）并返回 List<Document>
     */
    @Bean
    public List<Document> documents() {
        // 启动时加载文档
        return FileSystemDocumentLoader.loadDocumentsRecursively("E:\\code\\idea\\AiChat\\src\\main\\resources\\ragDocuments");
    }

    /**
     * 内存向量存储
     */
    @Bean
    public InMemoryEmbeddingStore<TextSegment> embeddingStore(List<Document> documents) {
        // 创建内存向量库
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        // 把文档切分并生成 embedding 存入内存
        EmbeddingStoreIngestor.ingest(documents, store);
        return store;
    }


}
