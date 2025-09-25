package com.character.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 讯飞TTS WebSocket连接池
 */
@Service
public class XunfeiTTSConnectionPool {

    private static final Logger logger = LoggerFactory.getLogger(XunfeiTTSConnectionPool.class);
    private static final int MAX_POOL_SIZE = 10;
    private static final String HOST_URL = "https://cbm01.cn-huabei-1.xf-yun.com/v1/private/mcd9m97e6";
    private static final String VCN = "x5_lingfeiyi_flow"; // 发音人参数
    private static final Gson gson = new Gson();

    @Value("${xunfei.app-id}")
    private String appId;

    @Value("${xunfei.access-key-id}")
    private String apiKey;

    @Value("${xunfei.access-key-secret}")
    private String apiSecret;

    private final BlockingQueue<XunfeiTTSConnection> availableConnections = new LinkedBlockingQueue<>();
    private final Set<XunfeiTTSConnection> allConnections = Collections.synchronizedSet(new HashSet<>());
    private final OkHttpClient client;

    public XunfeiTTSConnectionPool() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        logger.info("讯飞TTS连接池初始化完成，最大连接数: {}", MAX_POOL_SIZE);
    }

    /**
     * 获取TTS连接
     */
    public XunfeiTTSConnection getConnection() throws Exception {
        XunfeiTTSConnection connection = availableConnections.poll();
        if (connection == null || !connection.isConnected()) {
            if (allConnections.size() < MAX_POOL_SIZE) {
                connection = createNewConnection();
                allConnections.add(connection);
                logger.debug("创建新的TTS连接，当前连接数: {}", allConnections.size());
            } else {
                connection = availableConnections.take();
                logger.debug("复用现有TTS连接");
            }
        }
        return connection;
    }

    /**
     * 归还连接到池中
     */
    public void returnConnection(XunfeiTTSConnection connection) {
        if (connection != null && connection.isConnected()) {
            connection.reset();
            availableConnections.offer(connection);
            logger.debug("TTS连接已归还到连接池");
        }
    }

    /**
     * 创建新的TTS连接
     */
    private XunfeiTTSConnection createNewConnection() throws Exception {
        String authUrl = getAuthUrl(HOST_URL, apiKey, apiSecret);
        String wsUrl = authUrl.replace("http://", "ws://").replace("https://", "wss://");

        XunfeiTTSConnection connection = new XunfeiTTSConnection(wsUrl, client);
        if (connection.connect()) {
            logger.info("成功创建新的讯飞TTS WebSocket连接");
            return connection;
        } else {
            throw new Exception("连接讯飞TTS服务失败");
        }
    }

    /**
     * 签名鉴权加密
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

    /**
     * TTS连接封装类
     */
    public class XunfeiTTSConnection {
        private final String wsUrl;
        private final OkHttpClient client;
        private WebSocket webSocket;
        private Consumer<byte[]> audioDataHandler;
        private Consumer<String> errorHandler;
        private Runnable completionHandler;
        private int sequenceNumber = 0;
        private volatile boolean connected = false;
        private final CountDownLatch connectionLatch = new CountDownLatch(1);
        private static final long CONNECTION_TIMEOUT_MS = 15000; // 15秒连接超时

        public XunfeiTTSConnection(String wsUrl, OkHttpClient client) {
            this.wsUrl = wsUrl;
            this.client = client;
        }

        public boolean connect() throws Exception {
            WebSocketListener listener = new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    connected = true;
                    connectionLatch.countDown();
                    logger.debug("TTS WebSocket连接已建立");
                    XunfeiTTSConnection.this.sendInitMessage();
                }

                    @Override
                    public void onMessage(WebSocket webSocket, String responseText) {
                        super.onMessage(webSocket, responseText);
                        //处理返回数据
                        logger.info("receive=>" + responseText);
                        try {
                            JsonParse jsonParse = gson.fromJson(responseText, JsonParse.class);
                            if (jsonParse.header.code != 0) {
                                logger.info("发生错误，错误码为：" + jsonParse.header.code);
                                logger.info("本次请求的sid为：" + jsonParse.header.sid);
                            }

                            if (jsonParse.payload != null && jsonParse.payload.audio != null &&
                                    jsonParse.payload.audio.audio != null) {
                                try {
                                    byte[] audioData = Base64.getDecoder().decode(jsonParse.payload.audio.audio);
                                    //发送二进制音频数据到前端
                                    if (audioDataHandler != null) {
                                        audioDataHandler.accept(audioData);
                                    }
                                    logger.debug(" 接收到TTS音频数据，长度: {} 字节", audioData.length);
                                } catch (Exception e) {
                                    logger.error("解码TTS音频数据失败", e);
                                }
                            }

                            if (jsonParse.header.status == 2) {
                                logger.debug("TTS合成完成");
                                if (completionHandler != null) {
                                    completionHandler.run();
                                }
                            }
                        } catch (Exception e) {
                            logger.error("处理TTS响应数据失败", e);
                            if (errorHandler != null) {
                                errorHandler.accept("处理响应失败: " + e.getMessage());
                            }
                        }
                    }
                    @Override
                    public void onMessage(WebSocket webSocket, ByteString bytes) {
                        super.onMessage(webSocket, bytes);
                    }
                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    connected = false;
                    connectionLatch.countDown();
                    logger.error("TTS WebSocket连接失败", t);
                    if (errorHandler != null) {
                        errorHandler.accept("WebSocket连接失败: " + t.getMessage());
                    }
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    connected = false;
                    logger.debug("TTS WebSocket连接已关闭: {}", reason);
                    if (completionHandler != null) {
                        completionHandler.run();
                    }
                }
            };

            Request request = new Request.Builder().url(wsUrl).build();
            this.webSocket = client.newWebSocket(request, listener);
            
            // 等待连接建立完成
            boolean connectionSuccess = connectionLatch.await(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS) && connected;
            if (!connectionSuccess) {
                logger.error("TTS WebSocket连接超时或失败");
                if (webSocket != null) {
                    webSocket.close(1000, "连接超时");
                }
                throw new Exception("TTS WebSocket连接建立失败");
            }
            
            logger.info("TTS WebSocket连接建立成功");
            return true;
        }

        public void setHandlers(Consumer<byte[]> audioDataHandler, Consumer<String> errorHandler, Runnable completionHandler) {
            this.audioDataHandler = audioDataHandler;
            this.errorHandler = errorHandler;
            this.completionHandler = completionHandler;
        }

        public void sendText(String text, boolean isLast) {
            if (!connected || webSocket == null) {
                logger.warn("TTS连接未建立，无法发送文本");
                return;
            }

            // 确保不发送空文本（除非是结束标记）
            if (text.trim().isEmpty() && !isLast) {
                logger.warn("跳过空文本发送");
                return;
            }

            JsonObject requestData = buildTTSRequest(text, isLast);
            String requestJson = requestData.toString();
            webSocket.send(requestJson);
            logger.debug("发送TTS文本: {}", text.isEmpty() ? "[结束标记]" : text);
            logger.debug("TTS请求JSON: {}", requestJson);
        }

        public void sendInitMessage() {
            if (!connected || webSocket == null) {
                logger.warn("TTS连接未建立，无法发送初始化请求");
                return;
            }

            // 发送初始化请求，包含一个空的开始标记
            JsonObject requestData = buildInitRequest();
            String requestJson = requestData.toString();
            webSocket.send(requestJson);
            logger.debug("发送TTS初始化请求");
            logger.debug("TTS初始化请求JSON: {}", requestJson);
        }

        public void sendEndMessage() {
            if (!connected || webSocket == null) {
                logger.warn("TTS连接未建立，无法发送结束标记");
                return;
            }

            // 发送结束标记，使用特殊的结束请求格式
            JsonObject requestData = buildEndRequest();
            String requestJson = requestData.toString();
            webSocket.send(requestJson);
            logger.debug("发送TTS结束标记");
            logger.debug("TTS结束请求JSON: {}", requestJson);
        }

        private JsonObject buildTTSRequest(String text, boolean isLast) {
            JsonObject requestData = new JsonObject();
            JsonObject header = new JsonObject();
            JsonObject parameter = new JsonObject();
            JsonObject payload = new JsonObject();

            // 填充header
            header.addProperty("app_id", appId);
            header.addProperty("status", isLast ? 2 : 1);

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
            JsonObject textObj = new JsonObject();
            textObj.addProperty("encoding", "utf8");
            textObj.addProperty("compress", "raw");
            textObj.addProperty("format", "json");
            textObj.addProperty("status", isLast ? 2 : 1);
            textObj.addProperty("seq", sequenceNumber++);

            if (!text.isEmpty()) {
                try {
                    textObj.addProperty("text", Base64.getEncoder().encodeToString(text.getBytes("utf8")));
                } catch (Exception e) {
                    logger.error("文本编码失败", e);
                }
            }

            payload.add("text", textObj);
            requestData.add("header", header);
            requestData.add("parameter", parameter);
            requestData.add("payload", payload);

            return requestData;
        }

        private JsonObject buildInitRequest() {
            JsonObject requestData = new JsonObject();
            JsonObject header = new JsonObject();
            JsonObject parameter = new JsonObject();
            JsonObject payload = new JsonObject();

            // 填充header - 初始化请求
            header.addProperty("app_id", appId);
            header.addProperty("status", 0); // 0表示开始

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

            // 填充payload - 初始化请求，发送一个空的开始标记
            JsonObject textObj = new JsonObject();
            textObj.addProperty("encoding", "utf8");
            textObj.addProperty("compress", "raw");
            textObj.addProperty("format", "json");
            textObj.addProperty("status", 0); // 0表示开始
            textObj.addProperty("seq", sequenceNumber++);
            // 发送一个占位符文本作为初始化，避免空文本错误
            try {
                textObj.addProperty("text", Base64.getEncoder().encodeToString("\\n".getBytes("utf8")));
            } catch (Exception e) {
                logger.error("初始化文本编码失败", e);
            }

            payload.add("text", textObj);
            requestData.add("header", header);
            requestData.add("parameter", parameter);
            requestData.add("payload", payload);

            return requestData;
        }

        private JsonObject buildEndRequest() {
            JsonObject requestData = new JsonObject();
            JsonObject header = new JsonObject();
            JsonObject parameter = new JsonObject();
            JsonObject payload = new JsonObject();

            // 填充header - 结束标记
            header.addProperty("app_id", appId);
            header.addProperty("status", 2); // 2表示结束

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

            // 填充payload - 结束标记不需要文本内容
            JsonObject textObj = new JsonObject();
            textObj.addProperty("encoding", "utf8");
            textObj.addProperty("compress", "raw");
            textObj.addProperty("format", "json");
            textObj.addProperty("status", 2); // 2表示结束
            textObj.addProperty("seq", sequenceNumber++);
            // 不添加text字段

            payload.add("text", textObj);
            requestData.add("header", header);
            requestData.add("parameter", parameter);
            requestData.add("payload", payload);

            return requestData;
        }



        public boolean isConnected() {
            return connected && webSocket != null;
        }

        public void reset() {
            this.sequenceNumber = 0;
            this.audioDataHandler = null;
            this.errorHandler = null;
            this.completionHandler = null;
        }

        public void close() {
            if (webSocket != null) {
                webSocket.close(1000, "正常关闭");
            }
            connected = false;
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