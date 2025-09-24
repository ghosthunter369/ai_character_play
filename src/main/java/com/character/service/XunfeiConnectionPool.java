package com.character.service;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class XunfeiConnectionPool {
    
    private static final Logger logger = LoggerFactory.getLogger(XunfeiConnectionPool.class);
    
    // 配置参数
    private static final String AUDIO_ENCODE = "pcm_s16le";
    private static final String LANG = "autodialect";
    private static final String SAMPLERATE = "16000";
    private static final String BASE_WS_URL = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1";
    
    // 连接池配置
    private static final int MAX_POOL_SIZE = 10;
    private static final long CONNECTION_TIMEOUT_MS = 15000;
    
    @Value("${xunfei.app-id:0cf43f30}")
    private String appId;
    
    @Value("${xunfei.access-key-id:f22002ae080385f704032f9d53fb9d45}")
    private String accessKeyId;
    
    @Value("${xunfei.access-key-secret: M2NkMGM2NjIzNmIwMGZmNWE5ZDM2YzVh}")
    private String accessKeySecret;
    
    private final BlockingQueue<XunfeiConnection> availableConnections = new LinkedBlockingQueue<>();
    private final Set<XunfeiConnection> allConnections = ConcurrentHashMap.newKeySet();
    
    /**
     * 获取可用连接
     */
    public XunfeiConnection getConnection() throws InterruptedException {
        XunfeiConnection connection = availableConnections.poll();
        if (connection == null || !connection.isConnected()) {
            connection = createNewConnection();
        }
        return connection;
    }
    
    /**
     * 归还连接到池中
     */
    public void returnConnection(XunfeiConnection connection) {
        if (connection != null && connection.isConnected()) {
            availableConnections.offer(connection);
        }
    }
    
    /**
     * 创建新的WebSocket连接
     */
    private XunfeiConnection createNewConnection() {
        if (allConnections.size() >= MAX_POOL_SIZE) {
            throw new RuntimeException("连接池已满，无法创建新连接");
        }
        
        try {
            Map<String, String> authParams = generateAuthParams();
            String paramsStr = buildParamsString(authParams);
            String fullWsUrl = BASE_WS_URL + "?" + paramsStr;
            
            XunfeiConnection connection = new XunfeiConnection(fullWsUrl);
            allConnections.add(connection);
            
            if (connection.connect()) {
                logger.info("成功创建新的讯飞WebSocket连接");
                return connection;
            } else {
                allConnections.remove(connection);
                throw new RuntimeException("连接讯飞服务失败");
            }
        } catch (Exception e) {
            logger.error("创建讯飞连接失败", e);
            throw new RuntimeException("创建讯飞连接失败", e);
        }
    }
    
    /**
     * 讯飞WebSocket连接封装类
     */
    public static class XunfeiConnection {
        private final String wsUrl;
        private WebSocketClient webSocketClient;
        private final AtomicBoolean connected = new AtomicBoolean(false);
        private String sessionId;
        private Consumer<String> messageHandler;
        private final CountDownLatch connectionLatch = new CountDownLatch(1);
        
        public XunfeiConnection(String wsUrl) {
            this.wsUrl = wsUrl;
        }
        
        public boolean connect() {
            try {
                webSocketClient = new WebSocketClient(new URI(wsUrl)) {
                    @Override
                    public void onOpen(ServerHandshake handshake) {
                        connected.set(true);
                        connectionLatch.countDown();
                        logger.info("讯飞WebSocket连接已建立");
                        
                        // 等待服务端初始化
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    
                    @Override
                    public void onMessage(String message) {
                        try {
                            JSONObject json = new JSONObject(message);
                            logger.debug("收到讯飞消息: {}", json.toString());
                            
                            // 处理会话ID
                            if ("action".equals(json.optString("msg_type"))) {
                                JSONObject data = json.optJSONObject("data");
                                if (data != null && data.has("sessionId")) {
                                    sessionId = data.getString("sessionId");
                                    logger.info("获取到sessionId: {}", sessionId);
                                }
                            }
                            
                            // 调用消息处理器
                            if (messageHandler != null) {
                                messageHandler.accept(message);
                            }
                        } catch (Exception e) {
                            logger.warn("处理讯飞消息异常: {}", e.getMessage());
                        }
                    }
                    
                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        connected.set(false);
                        logger.info("讯飞WebSocket连接关闭: code={}, reason={}", code, reason);
                    }
                    
                    @Override
                    public void onError(Exception ex) {
                        connected.set(false);
                        logger.error("讯飞WebSocket连接错误", ex);
                    }
                };
                
                webSocketClient.connectBlocking(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                return connectionLatch.await(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS) && connected.get();
            } catch (Exception e) {
                logger.error("连接讯飞WebSocket失败", e);
                return false;
            }
        }
        
        public boolean isConnected() {
            return connected.get() && webSocketClient != null && webSocketClient.isOpen();
        }
        
        public void sendAudioFrame(byte[] audioData) {
            if (isConnected()) {
                webSocketClient.send(audioData);
            }
        }
        
        public void sendEndMessage() {
            if (isConnected()) {
                JSONObject endMsg = new JSONObject();
                endMsg.put("end", true);
                if (sessionId != null && !sessionId.isEmpty()) {
                    endMsg.put("sessionId", sessionId);
                }
                webSocketClient.send(endMsg.toString());
                logger.info("发送结束标记: {}", endMsg.toString());
            }
        }
        
        public void setMessageHandler(Consumer<String> handler) {
            this.messageHandler = handler;
        }
        
        public void close() {
            if (webSocketClient != null) {
                connected.set(false);
                webSocketClient.close();
            }
        }
        
        public String getSessionId() {
            return sessionId;
        }
    }
    
    /**
     * 生成鉴权参数
     */
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
    
    /**
     * 生成UTC时间字符串
     */
    private String getUtcTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(new Date());
    }
    
    /**
     * 计算HMAC-SHA1签名
     */
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
    
    /**
     * 构建参数字符串
     */
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
    
    /**
     * 关闭连接池
     */
    public void shutdown() {
        for (XunfeiConnection connection : allConnections) {
            connection.close();
        }
        allConnections.clear();
        availableConnections.clear();
    }
}