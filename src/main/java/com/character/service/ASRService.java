package com.character.service;

import com.character.model.entity.User;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;

/**
 * 实时语音识别服务接口（service 目录只放接口）
 */
public interface ASRService {

    /**
     * 启动实时语音识别流
     * @param sessionId WebSocket 会话ID
     * @param appId 应用ID
     * @param loginUser 登录用户信息
     * @return 文本识别结果的 Flux 流
     */
    Flux<String> startASR(String sessionId, Long appId, User loginUser);

    /**
     * 发送音频帧数据
     * @param sessionId 会话ID
     * @param audioData PCM 二进制帧
     * @param appId 应用ID
     * @param loginUser 登录用户信息
     */
    void sendAudioData(String sessionId, ByteBuffer audioData, Long appId, User loginUser);

    /**
     * 发送段落结束信号（智能断句）
     * @param sessionId 会话ID
     * @param appId 应用ID
     * @param loginUser 登录用户信息
     */
    void sendSegmentEnd(String sessionId, Long appId, User loginUser);

    /**
     * 结束语音识别（整段结束）
     * @param sessionId 会话ID
     * @param appId 应用ID
     * @param loginUser 登录用户信息
     */
    void endASR(String sessionId, Long appId, User loginUser);
}