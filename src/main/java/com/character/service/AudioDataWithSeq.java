package com.character.service;

/**
 * 带序号和状态的音频数据包装类
 * 根据讯飞TTS文档设计，用于确保音频片段的正确顺序和完整性
 */
public class AudioDataWithSeq {
    private final byte[] audioData;
    private final int seq;        // 数据序号，范围：0-9999999
    private final int status;     // 数据状态：0=开始, 1=中间, 2=结束

    public AudioDataWithSeq(byte[] audioData, int seq, int status) {
        this.audioData = audioData;
        this.seq = seq;
        this.status = status;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    public int getSeq() {
        return seq;
    }

    public int getStatus() {
        return status;
    }

    /**
     * 判断是否为开始片段
     */
    public boolean isStart() {
        return status == 0;
    }

    /**
     * 判断是否为中间片段
     */
    public boolean isMiddle() {
        return status == 1;
    }

    /**
     * 判断是否为结束片段
     */
    public boolean isEnd() {
        return status == 2;
    }

    @Override
    public String toString() {
        return String.format("AudioDataWithSeq{seq=%d, status=%d(%s), dataLength=%d}", 
                           seq, status, getStatusName(), audioData != null ? audioData.length : 0);
    }

    private String getStatusName() {
        switch (status) {
            case 0: return "开始";
            case 1: return "中间";
            case 2: return "结束";
            default: return "未知";
        }
    }
}