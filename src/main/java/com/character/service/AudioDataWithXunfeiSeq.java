package com.character.service;

/**
 * 包含讯飞TTS序号的音频数据类
 * 用于在TTS服务内部传递带有讯飞原始序号的音频数据
 */
public class AudioDataWithXunfeiSeq {
    private final byte[] audioData;
    private final int xunfeiSeq;    // 讯飞TTS返回的原始序号
    private final int status;       // 讯飞TTS返回的状态

    public AudioDataWithXunfeiSeq(byte[] audioData, int xunfeiSeq, int status) {
        this.audioData = audioData;
        this.xunfeiSeq = xunfeiSeq;
        this.status = status;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    public int getXunfeiSeq() {
        return xunfeiSeq;
    }

    public int getStatus() {
        return status;
    }

    /**
     * 判断是否为结束片段
     */
    public boolean isEnd() {
        return status == 2;
    }

    @Override
    public String toString() {
        return String.format("AudioDataWithXunfeiSeq{xunfeiSeq=%d, status=%d, dataLength=%d}", 
                           xunfeiSeq, status, audioData != null ? audioData.length : 0);
    }
}