package com.character.service;

import com.character.model.entity.User;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;

/**
 * 语音识别服务接口
 */
public interface ASRService {

    /**
     * 启动实时语音识别流
     * @param sessionId WebSocket会话ID
     * @param appId 应用ID
     * @param loginUser 登录用户信息
     * @return 识别结果流
     */
    Flux<String> startASR(String sessionId, Long appId, User loginUser);

    /**
     * 发送音频数据
     * @param sessionId 会话ID
     * @param audioData 音频数据
     * @param appId 应用ID
     * @param loginUser 登录用户信息
     */
    void sendAudioData(String sessionId, ByteBuffer audioData, Long appId, User loginUser);

    /**
     * 发送段落结束信号
     * @param sessionId 会话ID
     * @param appId 应用ID
     * @param loginUser 登录用户信息
     */
    void sendSegmentEnd(String sessionId, Long appId, User loginUser);

    /**
     * 结束语音识别
     * @param sessionId 会话ID
     * @param appId 应用ID
     * @param loginUser 登录用户信息
     */
    void endASR(String sessionId, Long appId, User loginUser);

    /**
     * 清理会话资源
     * @param sessionId 会话ID
     */
    void cleanupSession(String sessionId);
}