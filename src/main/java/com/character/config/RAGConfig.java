package com.character.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RAGConfig {

    @Value("${EMBEDDING_API_KEY:${DASHSCOPE_API_KEY:}}")
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
    public List<Document> documents() throws java.io.IOException {
        var resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath*:ragDocuments/**/*.md");
        java.util.ArrayList<Document> documents = new java.util.ArrayList<>();
        for (var resource : resources) {
            String location = resource.getURL().toString();
            String decoded = java.net.URLDecoder.decode(location, java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = decoded.split("/");
            try (var input = resource.getInputStream()) {
                Document document = Document.from(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                document.metadata().put("appName", parts[parts.length-2]);
                documents.add(document);
            }
        }
        return documents;
    }



    /**
     * 内存向量存储
     */
    @Bean
    public InMemoryEmbeddingStore<TextSegment> embeddingStore(List<Document> documents, EmbeddingModel embeddingModel) {
        documents.forEach(doc -> {
                    String filePath = doc.metadata().getString("file_path");
                    if (filePath != null) {
                        Path path = Paths.get(filePath);
                        String appName = path.getParent().getFileName().toString(); // 这里就是中文
                        doc.metadata().put("appName", appName);
                    }
                });
        // 创建内存向量库
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        // 把文档切分并生成 embedding 存入内存
        EmbeddingStoreIngestor.builder().embeddingModel(embeddingModel).embeddingStore(store).build().ingest(documents);
        return store;
    }


}
