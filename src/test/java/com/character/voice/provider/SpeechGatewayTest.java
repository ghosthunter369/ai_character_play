package com.character.voice.provider;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SpeechGatewayTest {
    HttpServer server;
    ExecutorService workers;
    MockEnvironment env;
    AtomicReference<String> request = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newCachedThreadPool(); server.setExecutor(workers); server.start();
        env = new MockEnvironment();
        for (String provider : new String[]{"local", "dashscope", "siliconflow", "azure"}) {
            env.setProperty("voice." + provider + ".base-url", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        }
    }
    @AfterEach void stop() { server.stop(0); workers.shutdownNow(); }
    void respond(String path, String type, byte[] bytes) {
        server.createContext(path, exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
    }
    @Test void uploadsLocalWavAsMultipartWithoutCredentials() throws Exception {
        respond("/v1/audio/transcriptions", "application/json", "{\"text\":\"你好\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("你好", new SpeechGateway(env).transcribe(new byte[]{82,73,70,70}, "local", new CallScope()));
        assertTrue(request.get().contains("name=\"file\"")); assertTrue(request.get().contains("RIFF")); assertNull(authorization.get());
    }
    @Test void localSynthesisReturnsAudioBytes() throws Exception {
        respond("/v1/audio/speech", "audio/wav", new byte[]{1,2,3});
        AudioResult result = new SpeechGateway(env).synthesize("你好", "local", "test-voice", new CallScope());
        assertArrayEquals(new byte[]{1,2,3}, result.data()); assertEquals("audio/wav", result.mimeType());
        assertTrue(request.get().contains("test-voice"));
    }
    @Test void dashscopeUsesBase64ChatInput() throws Exception {
        env.setProperty("voice.dashscope.api-key", "test-key");
        respond("/v1/chat/completions", "application/json", "{\"choices\":[{\"message\":{\"content\":\"识别结果\"}}]}".getBytes(StandardCharsets.UTF_8));
        assertEquals("识别结果", new SpeechGateway(env).transcribe(new byte[]{1,2}, "dashscope", new CallScope()));
        assertEquals("data:audio/wav;base64,AQI=", new org.json.JSONObject(request.get()).getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0).getJSONObject("input_audio").getString("data")); assertEquals("Bearer test-key", authorization.get());
    }
    @Test void cloudWithoutKeyFailsBeforeNetwork() {
        Exception error = assertThrows(Exception.class, () -> new SpeechGateway(env).transcribe(new byte[]{1}, "siliconflow", new CallScope()));
        assertTrue(error.getMessage().contains("key")); assertNull(request.get());
    }
    @Test void rejectsUnknownBrowserAndOversizedInput() {
        SpeechGateway gateway = new SpeechGateway(env);
        assertThrows(IllegalArgumentException.class, () -> gateway.transcribe(new byte[]{1}, "unknown", new CallScope()));
        assertThrows(IllegalArgumentException.class, () -> gateway.synthesize("hello", "browser", "", new CallScope()));
        assertThrows(IllegalArgumentException.class, () -> gateway.transcribe(new byte[5 * 1024 * 1024 + 1], "local", new CallScope()));
        assertThrows(IllegalArgumentException.class, () -> gateway.synthesize("字".repeat(601), "local", "", new CallScope()));
    }
    @Test void rejectsDashscopeUnsafeDownloadUrl() {
        env.setProperty("voice.dashscope.api-key", "test-key");
        respond("/v1/services/aigc/multimodal-generation/generation", "application/json", "{\"output\":{\"audio\":{\"url\":\"http://127.0.0.1/private\"}}}".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> new SpeechGateway(env).synthesize("hello", "dashscope", "Cherry", new CallScope()));
    }
    @Test void boundsHttpResponseSize() {
        respond("/v1/audio/speech", "audio/wav", new byte[8 * 1024 * 1024 + 1]);
        assertThrows(Exception.class, () -> new SpeechGateway(env).synthesize("hello", "local", "", new CallScope()));
    }
    @Test void callTimeoutIsEnforced() {
        env.setProperty("voice.http.timeout-ms", "150");
        server.createContext("/v1/audio/transcriptions", exchange -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        long before = System.nanoTime();
        assertThrows(Exception.class, () -> new SpeechGateway(env).transcribe(new byte[]{1}, "local", new CallScope()));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 1200);
    }
    @Test void cancellationAbortsActiveAndLateRegisteredCalls() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        server.createContext("/v1/audio/transcriptions", exchange -> { entered.countDown(); try { Thread.sleep(3000); } catch (InterruptedException ignored) {} exchange.close(); });
        CallScope scope = new CallScope();
        Future<?> call = workers.submit(() -> assertThrows(Exception.class, () -> new SpeechGateway(env).transcribe(new byte[]{1}, "local", scope)));
        assertTrue(entered.await(2, TimeUnit.SECONDS)); scope.cancel(); call.get(2, TimeUnit.SECONDS);
        assertTrue(scope.isCancelled());
        okhttp3.Call late = new okhttp3.OkHttpClient().newCall(new okhttp3.Request.Builder().url("http://127.0.0.1/").build());
        scope.register(late); assertTrue(late.isCanceled()); scope.close();
    }
    @Test void capabilitiesExposeConfigurationWithoutSecrets() {
        var caps = new SpeechGateway(env).capabilities();
        assertTrue(caps.get("tts").stream().anyMatch(p -> "browser".equals(p.get("id")) && Boolean.TRUE.equals(p.get("configured"))));
        assertFalse(caps.toString().contains("test-key"));
    }
    @Test void siliconflowUploadsFileWithBearer() throws Exception {
        env.setProperty("voice.siliconflow.api-key", "silicon-key");
        respond("/v1/audio/transcriptions", "application/json", "{\"text\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("ok", new SpeechGateway(env).transcribe(new byte[]{1,2}, "siliconflow", new CallScope()));
        assertEquals("Bearer silicon-key", authorization.get());
        assertTrue(request.get().contains("FunAudioLLM/SenseVoiceSmall"));
    }
    @Test void dashscopeAcceptsInlineAudioWithoutDownload() throws Exception {
        env.setProperty("voice.dashscope.api-key", "test-key");
        respond("/v1/services/aigc/multimodal-generation/generation", "application/json", "{\"output\":{\"audio\":{\"data\":\"AQID\"}}}".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(new byte[]{1,2,3}, new SpeechGateway(env).synthesize("hello", "dashscope", "Cherry", new CallScope()).data());
        assertTrue(request.get().contains("qwen3-tts-flash"));
    }
    @Test void azureUploadsWavAndUsesSubscriptionHeader() throws Exception {
        env.setProperty("voice.azure.api-key", "azure-key");
        AtomicReference<String> azureKey = new AtomicReference<>();
        server.createContext("/v1/speech/recognition/conversation/cognitiveservices/v1", exchange -> {
            azureKey.set(exchange.getRequestHeaders().getFirst("Ocp-Apim-Subscription-Key"));
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"RecognitionStatus\":\"Success\",\"DisplayText\":\"azure result\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        assertEquals("azure result", new SpeechGateway(env).transcribe(new byte[]{82,73,70,70}, "azure", new CallScope()));
        assertEquals("azure-key", azureKey.get()); assertEquals("RIFF", request.get());
    }
    @Test void azureSynthesisEscapesXmlTextAndReturnsWav() throws Exception {
        env.setProperty("voice.azure.api-key", "azure-key");
        respond("/v1/cognitiveservices/v1", "audio/wav", new byte[]{1,2});
        assertArrayEquals(new byte[]{1,2}, new SpeechGateway(env).synthesize("a < b & c", "azure", "zh-CN-XiaoxiaoNeural", new CallScope()).data());
        assertTrue(request.get().contains("a &lt; b &amp; c"));
    }    @Test void refusesSuccessfulJsonMasqueradingAsAudio() {
        respond("/v1/audio/speech", "application/json", "{\"error\":\"bad\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> new SpeechGateway(env).synthesize("hello", "local", "", new CallScope()));
    }
    @Test void refusesAsrResponseWithoutText() {
        respond("/v1/audio/transcriptions", "application/json", "{\"error\":\"bad\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> new SpeechGateway(env).transcribe(new byte[]{1}, "local", new CallScope()));
    }
    @Test void closedScopeDoesNotSendNetworkRequest() {
        respond("/v1/audio/transcriptions", "application/json", "{\"text\":\"late\"}".getBytes(StandardCharsets.UTF_8));
        CallScope scope = new CallScope(); scope.close();
        assertThrows(Exception.class, () -> new SpeechGateway(env).transcribe(new byte[]{1}, "local", scope));
        assertNull(request.get());
    }}
