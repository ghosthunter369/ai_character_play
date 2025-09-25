package com.character.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.character.ai.AiChatService;
import com.character.ai.AiChatServiceFactory;
import com.character.exception.ErrorCode;
import com.character.exception.ThrowUtils;
import com.character.model.entity.App;
import com.character.model.entity.User;
import com.character.model.enums.ChatHistoryMessageTypeEnum;
import com.character.service.AppService;
import com.character.mapper.AppMapper;
import com.character.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
* @author lixuewu
* @description 针对表【app(应用表)】的数据库操作Service实现
* @createDate 2025-09-23 17:22:13
*/
@Service
@RequiredArgsConstructor
public class AppServiceImpl extends ServiceImpl<AppMapper, App>
    implements AppService{


    @Lazy
    @Resource
    private  ChatHistoryService chatHistoryService;
    @Lazy
    @Resource
    private  AiChatServiceFactory aiChatServiceFactory;
    @Override
    public Flux<String> chat(Long appId, String message, User loginUser) {
        // 1. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 2. 添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 3. 根据 appId 和 userId获取对应的 AI 服务实例
        AiChatService aiService = aiChatServiceFactory.getAiCodeGeneratorService(appId, loginUser.getId());
        // 4. 调用 AI 生成消息（流式）
        Flux<String> messageStream = aiService.generateChatMessageStream(app.getInitPrompt(),message , appId + "_" + loginUser.getId());
        // 5. 收集AI响应内容并在完成后记录到对话历史
        StringBuilder aiResponseBuilder = new StringBuilder();
        return messageStream
                .map(chunk -> {
                    // 收集AI响应内容
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史
                    String aiResponse = aiResponseBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }



}




