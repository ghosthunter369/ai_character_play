package com.character.constant;

public class ARSConstant {
    // 配置参数
    public static final String AUDIO_ENCODE = "pcm_s16le";
    public static final String LANG = "autodialect";
    public static final String SAMPLERATE = "16000";
    public static final int AUDIO_FRAME_SIZE = 1280;  // 每帧字节数
    public static final int FRAME_INTERVAL_MS = 40;   // 帧间隔(毫秒)

    public static final String baseWsUrl = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1";
}