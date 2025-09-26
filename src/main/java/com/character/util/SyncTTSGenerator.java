package com.character.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 同步TTS音频生成器
 * 基于讯飞超拟人合成API
 */
public class SyncTTSGenerator extends WebSocketListener {
    
    private static final String HOST_URL = "https://cbm01.cn-huabei-1.xf-yun.com/v1/private/mcd9m97e6";
    private static final String VCN = "x5_lingfeiyi_flow"; // 发音人参数
    private static final Gson gson = new Gson();
    
    private String appId;
    private String accessKeyId;
    private String accessKeySecret;
    private CountDownLatch latch;
    private Exception error;
    private String textToSynthesize;
    private java.io.ByteArrayOutputStream audioBuffer;
    
    /**
     * 构造函数，传入认证信息
     */
    public SyncTTSGenerator(String appId, String accessKeyId, String accessKeySecret) {
        this.appId = appId;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
    }
    
    /**
     * 生成TTS音频数据（返回字节数组）
     *
     * @param text 要合成的文本
     * @return 音频字节数据
     * @throws Exception 生成失败时抛出异常
     */
    public byte[] generateAudioData(String text) throws Exception {
        this.textToSynthesize = text;
        this.audioBuffer = new java.io.ByteArrayOutputStream();
        this.latch = new CountDownLatch(1);
        this.error = null;
        
        try {
            // 获取认证URL
            String authUrl = getAuthUrl(HOST_URL,accessKeyId, accessKeySecret);
            
            // 创建WebSocket连接
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
            
            String wsUrl = authUrl.replace("http://", "ws://").replace("https://", "wss://");
            Request request = new Request.Builder().url(wsUrl).build();
            WebSocket webSocket = client.newWebSocket(request, this);
            
            // 等待完成，最多等待30秒
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            
            if (!completed) {
                throw new Exception("TTS音频生成超时");
            }
            
            if (error != null) {
                throw error;
            }
            
            byte[] audioData = audioBuffer.toByteArray();
            System.out.println("TTS音频生成完成，数据大小: " + audioData.length + " 字节");
            return audioData;
            
        } finally {
            if (audioBuffer != null) {
                try {
                    audioBuffer.close();
                } catch (IOException e) {
                    // 忽略关闭异常
                }
            }
        }
    }
    
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        super.onOpen(webSocket, response);
        
        try {
            // 构建TTS请求数据
            JsonObject sendData = new JsonObject();
            JsonObject header = new JsonObject();
            JsonObject parameter = new JsonObject();
            JsonObject payload = new JsonObject();
            
            // 填充header
            header.addProperty("app_id", appId);
            header.addProperty("status", 2); // 2表示最后一帧，一次性发送完整文本
            
            // 填充parameter
            JsonObject tts = new JsonObject();
            tts.addProperty("vcn", VCN); // 发音人
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
            
            // 组装完整请求
            sendData.add("header", header);
            sendData.add("parameter", parameter);
            sendData.add("payload", payload);
            
            // 发送请求
            webSocket.send(sendData.toString());
            System.out.println("TTS请求已发送: " + textToSynthesize);
            
        } catch (Exception e) {
            this.error = e;
            latch.countDown();
        }
    }
    
    @Override
    public void onMessage(WebSocket webSocket, String text) {
        super.onMessage(webSocket, text);
        
        try {
            JsonParse response = gson.fromJson(text, JsonParse.class);
            
            if (response.header.code != 0) {
                this.error = new Exception("TTS API错误，错误码: " + response.header.code + ", SID: " + response.header.sid);
                latch.countDown();
                return;
            }
            
            // 处理音频数据
            if (response.payload != null && response.payload.audio != null && response.payload.audio.audio != null) {
                byte[] audioData = Base64.getDecoder().decode(response.payload.audio.audio);
                audioBuffer.write(audioData);
                audioBuffer.flush();
            }
            
            // 检查是否完成
            if (response.header.status == 2) {
                System.out.println("TTS合成完成，状态: " + response.header.status);
                webSocket.close(1000, "正常完成");
                latch.countDown();
            }
            
        } catch (Exception e) {
            this.error = e;
            latch.countDown();
        }
    }
    
    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        super.onMessage(webSocket, bytes);
    }
    
    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        super.onClosing(webSocket, code, reason);
        System.out.println("TTS WebSocket正在关闭: " + reason);
    }
    
    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        super.onClosed(webSocket, code, reason);
        System.out.println("TTS WebSocket已关闭: " + reason);
        latch.countDown();
    }
    
    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        super.onFailure(webSocket, t, response);
        System.out.println("TTS WebSocket连接失败: " + t.getMessage());
        this.error = new Exception("WebSocket连接失败", t);
        latch.countDown();
    }
    
    /**
     * 生成认证URL
     */
    private static String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(hostUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        
        StringBuilder builder = new StringBuilder("host: ").append(url.getHost()).append("\n")
                .append("date: ").append(date).append("\n")
                .append("GET ").append(url.getPath()).append(" HTTP/1.1");
        
        Charset charset = Charset.forName("UTF-8");
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(charset), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = Base64.getEncoder().encodeToString(hexDigits);
        
        String authorization = String.format("hmac username=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", 
                apiKey, "hmac-sha256", "host date request-line", sha);
        
        HttpUrl httpUrl = HttpUrl.parse("https://" + url.getHost() + url.getPath()).newBuilder()
                .addQueryParameter("authorization", Base64.getEncoder().encodeToString(authorization.getBytes(charset)))
                .addQueryParameter("date", date)
                .addQueryParameter("host", url.getHost())
                .build();
        
        return httpUrl.toString();
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