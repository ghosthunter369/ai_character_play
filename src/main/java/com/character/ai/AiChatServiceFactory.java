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
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;

import static com.character.constant.AppConstant.LIMIT_PROMPT;

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
    private ObjectProvider<EmbeddingModel> embeddingModel;
//    @Resource
//    private RagChatService ragChatService;
@Resource
    private ObjectProvider<InMemoryEmbeddingStore<TextSegment>> embeddingStore;
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
        return getAiChatService(appId, userId, "legacy");
    }

    public AiChatService getAiChatService(long appId, Long userId, String conversationId) {
        String cacheKey = buildCacheKey(appId, userId) + "_" + conversationId;
        return serviceCache.get(cacheKey, key -> createAiChatService(appId, userId, conversationId));
    }

    public void invalidate(long appId, Long userId, String conversationId) {
        serviceCache.invalidate(buildCacheKey(appId, userId) + "_" + conversationId);
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
    private AiChatService createAiChatService(long appId, Long userId, String conversationId) {
        // 根据 appId 和 userId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId + "_" + userId + "_" + conversationId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(25)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, userId, conversationId, chatMemory, 25);
        // 使用多例模式的 StreamingChatModel 解决并发问题
        App app = appService.getById(appId);
        StreamingChatModel streamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
        //String finalInitPrompt = initPrompt;
        //使用appName进行过滤
        Filter appNameFilter = MetadataFilterBuilder.metadataKey("appName").isEqualTo(app.getAppName());
        AiServices<AiChatService> builder = AiServices.builder(AiChatService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> chatMemory)
                .systemMessageProvider(chatMemoryId -> "你正在进行语音角色扮演，请用简短自然的口语回答，通常一到三句话，保持角色设定。\n" + app.getInitPrompt());
        if (embeddingStore.getIfAvailable() != null && embeddingModel.getIfAvailable() != null) {
            builder.contentRetriever(EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore.getObject()).embeddingModel(embeddingModel.getObject())
                    .filter(appNameFilter).build());
        }
        return builder.build();
    }
}
