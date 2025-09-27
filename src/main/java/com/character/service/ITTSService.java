package com.character.service;

import reactor.core.publisher.Flux;

/**
 * TTS 语音合成服务接口
 */
public interface ITTSService {

    /**
     * 流式文本转语音（已废弃，保留兼容性）
     * @param sessionId 会话ID
     * @param textFlux 文本流
     * @param appId 应用ID
     * @return 音频数据流（Base64编码的音频帧）
     * @deprecated 使用 {@link #syncTextToSpeech(String)} 替代
     */
    @Deprecated
    Flux<byte[]> streamTextToSpeech(String sessionId, Flux<String> textFlux, Long appId);

    /**
     * 同步文本转语音
     * @param text 完整的文本内容
     * @return 完整的音频数据（字节数组）
     * @throws Exception 转换失败时抛出异常
     */
    byte[] syncTextToSpeech(String text) throws Exception;

    /**
     * 关闭TTS会话
     * @param sessionId 会话ID
     */
    void closeTTSSession(String sessionId);
}