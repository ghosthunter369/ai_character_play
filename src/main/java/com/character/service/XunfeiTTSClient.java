package com.character.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 讯飞超拟人合成 TTS 客户端：将文本 Flux 转为音频二进制 Flux。
 * 依赖 OkHttp WebSocket
 */
@Service
public class XunfeiTTSClient {

    private static final Logger logger = LoggerFactory.getLogger(XunfeiTTSClient.class);
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();
    private static final Gson gson = new Gson();

    // 参考用户示例 hostUrl（http 不支持 ws/wss 解析，鉴权后替换为 wss）
    @Value("${xunfei.tts.hostUrl:https://cbm01.cn-huabei-1.xf-yun.com/v1/private/mcd9m97e6}")
    private String hostUrl;

    @Value("${xunfei.app-id:YOUR_APP_ID}")
    private String appId;

    @Value("${xunfei.api-key:f22002ae080385f704032f9d53fb9d45}")
    private String apiKey;

    @Value("${xunfei.api-secret:M2NkMGM2NjIzNmIwMGZmNWE5ZDM2YzVh}")
    private String apiSecret;

    // 发音人等参数，可外部化
    @Value("${xunfei.tts.vcn:x5_lingfeiyi_flow}")
    private String vcn;

    @Value("${xunfei.tts.audio.encoding:lame}")
    private String audioEncoding;

    @Value("${xunfei.tts.audio.sampleRate:24000}")
    private int audioSampleRate;

    /**
     * 将文本流合成为音频流。对每条文本发送一次合成请求，返回的音频片段通过 Flux<byte[]> 输出。
     */
    public Flux<byte[]> ttsStream(Flux<String> textFlux) {
        Sinks.Many<byte[]> sink = Sinks.many().unicast().onBackpressureBuffer();

        AtomicBoolean closed = new AtomicBoolean(false);
        WebSocket[] socketRef = new WebSocket[1];

        try {
            String authUrl = getAuthUrl(hostUrl, apiKey, apiSecret);
            String wsUrl = authUrl.replace("http://", "ws://").replace("https://", "wss://");
            Request request = new Request.Builder().url(wsUrl).build();

            WebSocketListener listener = new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    logger.info("TTS WebSocket 已连接");
                    socketRef[0] = webSocket;

                    // 订阅文本流并逐条发送
                    textFlux.subscribe(
                        txt -> {
                            try {
                                JsonObject sendData = buildTtsRequestJson(txt, /*final frame*/ true, /*seq*/ 0);
                                webSocket.send(sendData.toString());
                            } catch (Exception e) {
                                logger.error("发送 TTS 文本失败", e);
                                sink.tryEmitError(e);
                            }
                        },
                        err -> {
                            logger.error("文本流错误", err);
                            tryClose(webSocket);
                            sink.tryEmitError(err);
                        },
                        () -> {
                            logger.info("文本流完成，等待 TTS 结束");
                            // 不强制立即关闭，让服务端返回 status=2 后关闭
                        }
                    );
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    try {
                        JsonParse parsed = gson.fromJson(text, JsonParse.class);
                        if (parsed == null || parsed.header == null) {
                            return;
                        }
                        if (parsed.header.code != 0) {
                            logger.warn("TTS 返回错误 code={}, sid={}", parsed.header.code, parsed.header.sid);
                        }
                        if (parsed.payload != null && parsed.payload.audio != null && parsed.payload.audio.audio != null) {
                            byte[] audioBytes = Base64.getDecoder().decode(parsed.payload.audio.audio);
                            sink.tryEmitNext(audioBytes);
                        }
                        // status==2 表示本次合成结束
                        if (parsed.header.status == 2) {
                            tryClose(webSocket);
                            sink.tryEmitComplete();
                        }
                    } catch (Exception e) {
                        logger.error("解析 TTS 消息失败", e);
                        sink.tryEmitError(e);
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    // 若服务端返回二进制帧，可直接透传
                    sink.tryEmitNext(bytes.toByteArray());
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    logger.info("TTS WebSocket 正在关闭: {} {}", code, reason);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    logger.info("TTS WebSocket 已关闭: {} {}", code, reason);
                    if (!closed.get()) {
                        sink.tryEmitComplete();
                        closed.set(true);
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    logger.error("TTS WebSocket 连接失败", t);
                    sink.tryEmitError(t);
                    tryClose(webSocket);
                }
            };

            httpClient.newWebSocket(request, listener);
        } catch (Exception e) {
            logger.error("建立 TTS 连接失败", e);
            sink.tryEmitError(e);
        }

        return sink.asFlux();
    }

    private void tryClose(WebSocket ws) {
        try {
            if (ws != null) {
                ws.close(1000, "normal close");
            }
        } catch (Exception ignore) {}
    }

    private JsonObject buildTtsRequestJson(String textUtf8, boolean finalFrame, int seq) throws Exception {
        JsonObject sendData = new JsonObject();
        JsonObject header = new JsonObject();
        JsonObject parameter = new JsonObject();
        JsonObject payload = new JsonObject();

        // header
        header.addProperty("app_id", appId);
        header.addProperty("status", finalFrame ? 2 : 1);

        // parameter.tts
        JsonObject tts = new JsonObject();
        tts.addProperty("vcn", vcn);
        tts.addProperty("speed", 50);
        tts.addProperty("volume", 50);
        tts.addProperty("pitch", 50);
        tts.addProperty("bgs", 0);
        tts.addProperty("reg", 0);
        tts.addProperty("rdn", 0);

        JsonObject audio = new JsonObject();
        audio.addProperty("encoding", audioEncoding);
        audio.addProperty("sample_rate", audioSampleRate);
        audio.addProperty("channels", 1);
        audio.addProperty("bit_depth", 16);
        audio.addProperty("frame_size", 0);
        tts.add("audio", audio);

        parameter.add("tts", tts);

        // payload.text
        JsonObject text = new JsonObject();
        text.addProperty("encoding", "utf8");
        text.addProperty("compress", "raw");
        text.addProperty("format", "json");
        text.addProperty("status", finalFrame ? 2 : 1);
        text.addProperty("seq", seq);
        text.addProperty("text", Base64.getEncoder().encodeToString(textUtf8.getBytes("utf8")));

        payload.add("text", text);

        sendData.add("header", header);
        sendData.add("parameter", parameter);
        sendData.add("payload", payload);

        return sendData;
    }

    /**
     * 签名鉴权 URL（参考示例）
     */
    private String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
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

    // 对应返回 JSON 的简化结构
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
        String audio; // base64
        int seq;
    }
}