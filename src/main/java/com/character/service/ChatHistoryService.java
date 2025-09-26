package com.character.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.character.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.character.model.entity.ChatHistory;
import com.baomidou.mybatisplus.extension.service.IService;
import com.character.model.entity.User;
import com.character.model.vo.ChatHistoryResponse;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
* @author lixuewu
* @description 针对表【chat_history(对话历史)】的数据库操作Service
* @createDate 2025-09-23 17:22:13
*/
public interface ChatHistoryService extends IService<ChatHistory> {
    /**
     *加载历史对话到内存
     * @param appId
     * @param userId
     * @param chatMemory
     * @param maxCount 最多加载多少条
     * @return 加载成功的条数
     */
    int loadChatHistoryToMemory(long appId, Long userId, MessageWindowChatMemory chatMemory, int maxCount);

    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 分页查询某app的对话记录
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    ChatHistoryResponse listAppChatHistoryByPage(Long appId, int pageSize, LocalDateTime lastCreateTime, String messageType, User loginUser);

    /**
     * 构造查询条件
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
