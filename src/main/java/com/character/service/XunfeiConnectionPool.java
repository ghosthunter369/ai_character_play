package com.character.service;

import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 讯飞ASR连接池
 */
@Service
public class XunfeiConnectionPool {

    private static final Logger logger = LoggerFactory.getLogger(XunfeiConnectionPool.class);
    private static final int MAX_POOL_SIZE = 10;
    private static final String BASE_WS_URL = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1";
    private static final String AUDIO_ENCODE = "pcm_s16le";
    private static final String LANG = "autodialect";
    private static final String SAMPLERATE = "16000";

    @Value("${xunfei.app-id}")
    private String appId;

    @Value("${xunfei.access-key-id}")
    private String accessKeyId;

    @Value("${xunfei.access-key-secret}")
    private String accessKeySecret;

    private final BlockingQueue<XunfeiConnection> availableConnections = new LinkedBlockingQueue<>();
    private final Set<XunfeiConnection> allConnections = Collections.synchronizedSet(new HashSet<>());
    private final OkHttpClient client;

    public XunfeiConnectionPool() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        logger.info("讯飞ASR连接池初始化完成，最大连接数: {}", MAX_POOL_SIZE);
    }

    public XunfeiConnection getConnection() throws InterruptedException {
        XunfeiConnection connection = availableConnections.poll();
        if (connection == null || !connection.isConnected()) {
            if (allConnections.size() < MAX_POOL_SIZE) {
                connection = createNewConnection();
                allConnections.add(connection);
                logger.debug("创建新的ASR连接，当前连接数: {}", allConnections.size());
            } else {
                connection = availableConnections.take();
                logger.debug("复用现有ASR连接");
            }
        }
        return connection;
    }

    public void returnConnection(XunfeiConnection connection) {
        if (connection != null && connection.isConnected()) {
            availableConnections.offer(connection);
            logger.debug("ASR连接已归还到连接池");
        }
    }

    private XunfeiConnection createNewConnection() {
        try {
            String wsUrl = getWsUrl();
            XunfeiConnection connection = new XunfeiConnection(wsUrl);
            if (connection.connect()) {
                logger.info("成功创建新的讯飞ASR WebSocket连接");
                return connection;
            } else {
                throw new RuntimeException("连接讯飞ASR服务失败");
            }
        } catch (Exception e) {
            logger.error("创建ASR连接失败", e);
            throw new RuntimeException("创建ASR连接失败", e);
        }
    }

    private String getWsUrl() {
        Map<String, String> params = generateAuthParams();
        String paramsStr = buildParamsString(params);
        return BASE_WS_URL + "?" + paramsStr;
    }

    private Map<String, String> generateAuthParams() {
        Map<String, String> params = new TreeMap<>();

        params.put("audio_encode", AUDIO_ENCODE);
        params.put("lang", LANG);
        params.put("samplerate", SAMPLERATE);
        params.put("accessKeyId", accessKeyId);
        params.put("appId", appId);
        params.put("uuid", UUID.randomUUID().toString().replaceAll("-", ""));
        params.put("utc", getUtcTime());

        String signature = calculateSignature(params);
        params.put("signature", signature);
        return params;
    }

    private String getUtcTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(new Date());
    }

    private String calculateSignature(Map<String, String> params) {
        try {
            StringBuilder baseStr = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if ("signature".equals(key)) continue;
                if (value == null || value.trim().isEmpty()) continue;

                if (!first) {
                    baseStr.append("&");
                }
                baseStr.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name()))
                       .append("=")
                       .append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
                first = false;
            }

            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] signBytes = mac.doFinal(baseStr.toString().getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(signBytes);
        } catch (Exception e) {
            throw new RuntimeException("计算签名失败", e);
        }
    }

    private String buildParamsString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            try {
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()))
                  .append("=")
                  .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
            } catch (Exception e) {
                logger.error("URL编码失败", e);
            }
            first = false;
        }
        return sb.toString();
    }

    public class XunfeiConnection {
        private final String wsUrl;
        private WebSocket webSocket;
        private volatile boolean connected = false;
        private Consumer<String> messageHandler;
        private String sessionId;

        public XunfeiConnection(String wsUrl) {
            this.wsUrl = wsUrl;
            this.sessionId = UUID.randomUUID().toString();
        }

        public boolean connect() {
            CountDownLatch connectionLatch = new CountDownLatch(1);
            final boolean[] connectionSuccess = {false};

            Request request = new Request.Builder().url(wsUrl).build();
            
            webSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    connected = true;
                    connectionSuccess[0] = true;
                    connectionLatch.countDown();
                    logger.debug("ASR WebSocket连接已建立");
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    if (messageHandler != null) {
                        messageHandler.accept(text);
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    // ASR不处理二进制消息
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    connected = false;
                    logger.debug("ASR WebSocket正在关闭: {}", reason);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    connected = false;
                    logger.debug("ASR WebSocket已关闭: {}", reason);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    connected = false;
                    connectionLatch.countDown();
                    logger.error("ASR WebSocket连接失败", t);
                }
            });

            try {
                boolean success = connectionLatch.await(10, TimeUnit.SECONDS);
                if (!success || !connectionSuccess[0]) {
                    logger.error("ASR WebSocket连接超时或失败");
                    if (webSocket != null) {
                        webSocket.close(1000, "连接超时");
                    }
                    return false;
                }
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        public boolean isConnected() {
            return connected && webSocket != null;
        }

        public void sendAudioFrame(byte[] audioData) {
            if (connected && webSocket != null) {
                webSocket.send(ByteString.of(audioData));
            }
        }

        public void sendStartMessage() {
            if (connected && webSocket != null) {
                webSocket.send("{\"msg_type\":\"start\"}");
                logger.debug("发送ASR开始消息");
            }
        }

        public void sendEndMessage() {
            if (connected && webSocket != null) {
                webSocket.send("{\"msg_type\":\"end\"}");
                logger.debug("发送ASR结束消息");
            }
        }

        public void setMessageHandler(Consumer<String> handler) {
            this.messageHandler = handler;
        }

        public void close() {
            connected = false;
            if (webSocket != null) {
                webSocket.close(1000, "正常关闭");
                webSocket = null;
            }
        }

        public String getSessionId() {
            return sessionId;
        }
    }
}