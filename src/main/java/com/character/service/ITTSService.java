package com.character.service;

import reactor.core.publisher.Flux;

/**
 * TTS 语音合成服务接口
 */
public interface ITTSService {

    /**
     * 流式文本转语音
     * @param sessionId 会话ID
     * @param textFlux 文本流
     * @param appId 应用ID
     * @return 音频数据流（Base64编码的音频帧）
     */
    Flux<byte[]> streamTextToSpeech(String sessionId, Flux<String> textFlux, Long appId);

    /**
     * 关闭TTS会话
     * @param sessionId 会话ID
     */
    void closeTTSSession(String sessionId);
}