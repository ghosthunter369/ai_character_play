package com.character.ai;


import com.character.model.entity.App;
import com.character.service.AppService;
import com.character.service.ChatHistoryService;
import com.character.util.SpringContextUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;
import java.util.List;

@Configuration
@Slf4j
public class AiChatServiceFactory {

    @Resource
    private ChatModel chatModel;



    @Resource
    @Lazy
    private ChatHistoryService chatHistoryService;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    @Lazy
    private AppService appService;
    @Resource
    private EmbeddingModel embeddingModel;
//    @Resource
//    private RagChatService ragChatService;
@Resource
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;
    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiChatService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId和userId获取服务（带缓存）
     */
    public AiChatService getAiCodeGeneratorService(long appId, Long userId) {
        String cacheKey = buildCacheKey(appId, userId);
        return serviceCache.get(cacheKey, key -> createAiChatService(appId, userId));
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, Long userId) {
        return appId + "_" + userId;
    }


    /**
     * 创建新的 AI 服务实例
     */
    private AiChatService createAiChatService(long appId, Long userId) {
        // 根据 appId 和 userId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId + "_" + userId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(25)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, userId,chatMemory, 25);
        // 使用多例模式的 StreamingChatModel 解决并发问题
        App app = appService.getById(appId);
//        //使用RAG进行检索
//        List<TextSegment> segments = ragChatService.search(app.getDescription());
//        // 拼接成一个字符串
//        StringBuilder contextBuilder = new StringBuilder();
//        for (TextSegment segment : segments) {
//            contextBuilder.append(segment.text()).append("\n"); // 每段换行
//        }
//        String contextText = contextBuilder.toString();
//        String initPrompt = app.getInitPrompt();
//        initPrompt = initPrompt + "下面是参考资料：\n" + contextText + "\n根据以上资料回答用户问题：";
        StreamingChatModel streamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
        //String finalInitPrompt = initPrompt;
        //使用appName进行过滤
        Filter appNameFilter = MetadataFilterBuilder.metadataKey("appName").isEqualTo(app.getAppName());
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .filter(appNameFilter)
                .build();
        return AiServices.builder(AiChatService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> chatMemory)
                .systemMessageProvider(chatMemoryId -> app.getInitPrompt())
                .contentRetriever(contentRetriever)
                .build();
    }
}
