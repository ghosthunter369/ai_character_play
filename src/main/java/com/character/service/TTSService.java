package com.character.service;

/**
 * 语音合成服务接口
 */
public interface TTSService {

    /**
     * 同步文本转语音
     * @param text 文本内容
     * @return 音频数据字节数组
     * @throws Exception 转换失败时抛出异常
     */
    byte[] textToSpeech(String text) throws Exception;
}