package com.character.service.impl;

import com.character.service.TTSService;
import com.character.util.TTSUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 语音合成服务实现
 */
@Service
public class TTSServiceImpl implements TTSService {

    private static final Logger logger = LoggerFactory.getLogger(TTSServiceImpl.class);
    private static final String HOST_URL = "https://cbm01.cn-huabei-1.xf-yun.com/v1/private/mcd9m97e6";
    private static final String VCN = "x5_lingfeiyi_flow";
    private static final Gson gson = new Gson();

    @Value("${xunfei.app-id}")
    private String appId;

    @Value("${xunfei.access-key-id}")
    private String apiKey;

    @Value("${xunfei.access-key-secret}")
    private String apiSecret;

    private final OkHttpClient client;

    public TTSServiceImpl() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public byte[] textToSpeech(String text) throws Exception {
        logger.info("开始TTS转换，文本长度: {}", text.length());
        
        TTSGenerator generator = new TTSGenerator(text);
        byte[] audioData = generator.generate();
        
        logger.info("TTS转换完成，音频数据大小: {} 字节", audioData.length);
        return audioData;
    }

    private class TTSGenerator extends WebSocketListener {
        private final String textToSynthesize;
        private final ByteArrayOutputStream audioBuffer;
        private final CountDownLatch latch;
        private Exception error;

        public TTSGenerator(String text) {
            this.textToSynthesize = text;
            this.audioBuffer = new ByteArrayOutputStream();
            this.latch = new CountDownLatch(1);
        }

        public byte[] generate() throws Exception {
            try {
                String authUrl = TTSUtil.getAuthUrl(HOST_URL, apiKey, apiSecret);
                String wsUrl = authUrl.replace("http://", "ws://").replace("https://", "wss://");
                
                Request request = new Request.Builder().url(wsUrl).build();
                WebSocket webSocket = client.newWebSocket(request, this);
                
                boolean completed = latch.await(30, TimeUnit.SECONDS);
                
                if (!completed) {
                    throw new Exception("TTS音频生成超时");
                }
                
                if (error != null) {
                    throw error;
                }
                
                return audioBuffer.toByteArray();
                
            } finally {
                try {
                    audioBuffer.close();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
            }
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            try {
                JsonObject sendData = buildTTSRequest();
                webSocket.send(sendData.toString());
                logger.debug("TTS请求已发送");
            } catch (Exception e) {
                this.error = e;
                latch.countDown();
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JsonParse response = gson.fromJson(text, JsonParse.class);
                
                if (response.header.code != 0) {
                    this.error = new Exception("TTS API错误，错误码: " + response.header.code);
                    latch.countDown();
                    return;
                }
                
                if (response.payload != null && response.payload.audio != null && response.payload.audio.audio != null) {
                    byte[] audioData = Base64.getDecoder().decode(response.payload.audio.audio);
                    audioBuffer.write(audioData);
                    audioBuffer.flush();
                }
                
                if (response.header.status == 2) {
                    logger.debug("TTS合成完成");
                    webSocket.close(1000, "正常完成");
                    latch.countDown();
                }
                
            } catch (Exception e) {
                this.error = e;
                latch.countDown();
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            this.error = new Exception("WebSocket连接失败", t);
            latch.countDown();
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            latch.countDown();
        }

        private JsonObject buildTTSRequest() throws Exception {
            JsonObject sendData = new JsonObject();
            JsonObject header = new JsonObject();
            JsonObject parameter = new JsonObject();
            JsonObject payload = new JsonObject();
            
            // 填充header
            header.addProperty("app_id", appId);
            header.addProperty("status", 2);
            
            // 填充parameter
            JsonObject tts = new JsonObject();
            tts.addProperty("vcn", VCN);
            tts.addProperty("speed", 50);
            tts.addProperty("volume", 50);
            tts.addProperty("pitch", 50);
            tts.addProperty("bgs", 0);
            tts.addProperty("reg", 0);
            tts.addProperty("rdn", 0);
            tts.addProperty("rhy", 0);
            
            JsonObject audio = new JsonObject();
            audio.addProperty("encoding", "lame");
            audio.addProperty("sample_rate", 24000);
            audio.addProperty("channels", 1);
            audio.addProperty("bit_depth", 16);
            audio.addProperty("frame_size", 0);
            
            tts.add("audio", audio);
            parameter.add("tts", tts);
            
            // 填充payload
            JsonObject text = new JsonObject();
            text.addProperty("encoding", "utf8");
            text.addProperty("compress", "raw");
            text.addProperty("format", "json");
            text.addProperty("status", 2);
            text.addProperty("seq", 0);
            text.addProperty("text", Base64.getEncoder().encodeToString(textToSynthesize.getBytes("utf8")));
            
            payload.add("text", text);
            
            sendData.add("header", header);
            sendData.add("parameter", parameter);
            sendData.add("payload", payload);
            
            return sendData;
        }
    }

    // JSON解析类
    static class JsonParse {
        Header header;
        Payload payload;
    }
    
    static class Header {
        int code;
        String sid;
        int status;
    }
    
    static class Payload {
        Audio audio;
    }
    
    static class Audio {
        String audio;
        int seq;
    }
}