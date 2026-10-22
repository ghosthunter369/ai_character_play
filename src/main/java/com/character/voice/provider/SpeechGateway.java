package com.character.voice.provider;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicitly selected speech provider. Never retries against another provider. */
@Component
public class SpeechGateway {
    private static final int MAX_AUDIO = 5 * 1024 * 1024;
    private static final int MAX_RESPONSE = 8 * 1024 * 1024;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Set<String> PROVIDERS = Set.of("dashscope", "siliconflow", "azure", "local");
    private static final Set<String> RESULT_HOSTS = Set.of(
            "dashscope-result-bj.oss-cn-beijing.aliyuncs.com",
            "dashscope-result-sg.oss-ap-southeast-1.aliyuncs.com",
            "dashscope-result-us.oss-us-east-1.aliyuncs.com");
    private final Environment env;
    private final OkHttpClient client;

    public SpeechGateway(Environment env) {
        this.env = env;
        long timeout = Long.parseLong(setting("voice.http.timeout-ms", "SPEECH_HTTP_TIMEOUT_MS", "20000"));
        if (timeout < 1 || timeout > 120000) throw new IllegalArgumentException("Speech timeout must be 1..120000ms");
        client = new OkHttpClient.Builder().connectTimeout(Math.min(timeout, 5000), TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .callTimeout(timeout, TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build();
    }

    public String transcribe(byte[] wav, String provider, CallScope scope) throws Exception {
        String id = provider(provider);
        if (wav == null || wav.length == 0 || wav.length > MAX_AUDIO) throw new IllegalArgumentException("Audio must be 1..5MiB");
        requireActive(scope);
        String key = key(id);
        Request.Builder request;
        if (id.equals("dashscope")) {
            JSONObject audio = new JSONObject().put("type", "input_audio").put("input_audio",
                    new JSONObject().put("data", "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wav)));
            JSONObject body = new JSONObject().put("model", config(id, "asr-model", "qwen3-asr-flash"))
                    .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", new JSONArray().put(audio))))
                    .put("stream", false).put("asr_options", new JSONObject().put("enable_itn", true));
            request = json(endpoint(id, false), body).header("Authorization", "Bearer " + key);
        } else if (id.equals("azure")) {
            request = new Request.Builder().url(endpoint(id, false)).header("Ocp-Apim-Subscription-Key", key)
                    .post(RequestBody.create(MediaType.parse("audio/wav; codecs=audio/pcm; samplerate=16000"), wav));
        } else {
            String model = config(id, "asr-model", id.equals("local") ? "whisper-1" : "FunAudioLLM/SenseVoiceSmall");
            RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("model", model)
                    .addFormDataPart("file", "recording.wav", RequestBody.create(MediaType.parse("audio/wav"), wav)).build();
            request = new Request.Builder().url(endpoint(id, false)).post(body);
            if (!key.isEmpty()) request.header("Authorization", "Bearer " + key);
        }
        JSONObject body = new JSONObject(new String(execute(request.build(), scope).bytes(), java.nio.charset.StandardCharsets.UTF_8));
        String text;
        if (id.equals("dashscope")) text = body.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
        else if (id.equals("azure")) {
            if (!"Success".equals(body.optString("RecognitionStatus"))) throw new IOException("Speech recognition did not produce a result");
            text = body.optString("DisplayText", "");
        } else text = body.optString("text", "");
        if (text.isBlank()) throw new IOException("Speech recognition returned no text");
        return text;
    }

    public AudioResult synthesize(String text, String provider, String voice, CallScope scope) throws Exception {
        String id = provider(provider);
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > 600)
            throw new IllegalArgumentException("Speech text must be 1..600 characters");
        requireActive(scope);
        String key = key(id);
        String selectedVoice = voice == null || voice.isBlank() ? config(id, "voice", defaultVoice(id)) : voice;
        Request.Builder request;
        if (id.equals("dashscope")) {
            JSONObject body = new JSONObject().put("model", config(id, "tts-model", "qwen3-tts-flash"))
                    .put("input", new JSONObject().put("text", text).put("voice", selectedVoice).put("language_type", "Chinese"));
            request = json(endpoint(id, true), body).header("Authorization", "Bearer " + key);
            JSONObject audio = new JSONObject(new String(execute(request.build(), scope).bytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getJSONObject("output").getJSONObject("audio");
            String inline = audio.optString("data", "");
            if (!inline.isBlank()) {
                byte[] bytes;
                try { bytes = Base64.getDecoder().decode(inline); }
                catch (IllegalArgumentException exception) { throw new IOException("Speech provider returned invalid audio"); }
                if (bytes.length == 0 || bytes.length > MAX_RESPONSE) throw new IOException("Speech audio response size invalid");
                return new AudioResult(bytes, "audio/wav");
            }
            String resultUrl = audio.optString("url", "");
            validateResultUrl(resultUrl);
            // A signed URL must never appear in exceptions, logs, or browser events.
            try { return audio(execute(new Request.Builder().url(resultUrl).get().build(), scope)); }
            catch (Exception exception) { throw new IOException(scope.isCancelled() ? "Speech call cancelled" : "Speech audio download failed"); }
        } else if (id.equals("azure")) {
            String ssml = "<speak version=\"1.0\" xml:lang=\"zh-CN\"><voice name=\"" + xml(selectedVoice) + "\">" + xml(text) + "</voice></speak>";
            request = new Request.Builder().url(endpoint(id, true)).header("Ocp-Apim-Subscription-Key", key)
                    .header("X-Microsoft-OutputFormat", "riff-24khz-16bit-mono-pcm")
                    .post(RequestBody.create(MediaType.parse("application/ssml+xml; charset=utf-8"), ssml));
        } else {
            JSONObject body = new JSONObject().put("model", config(id, "tts-model", id.equals("local") ? "tts-1" : "FunAudioLLM/CosyVoice2-0.5B"))
                    .put("input", text).put("voice", selectedVoice).put("response_format", "wav").put("stream", false);
            request = json(endpoint(id, true), body);
            if (!key.isEmpty()) request.header("Authorization", "Bearer " + key);
        }
        return audio(execute(request.build(), scope));
    }

    public Map<String, List<Map<String, Object>>> capabilities() {
        List<Map<String, Object>> asr = new ArrayList<>(), tts = new ArrayList<>();
        for (String id : List.of("dashscope", "siliconflow", "azure", "local")) {
            boolean configured = id.equals("local") || !configuredKey(id).isBlank();
            String reason = configured ? "Configuration present; service availability is not verified" : "API key required";
            asr.add(capability(id, configured, reason)); tts.add(capability(id, configured, reason));
        }
        tts.add(capability("browser", true, "Browser speechSynthesis; voices depend on device"));
        tts.add(capability("xunfei", false, "Legacy endpoint retained; not connected to this gateway"));
        return Map.of("asr", asr, "tts", tts);
    }

    private Map<String, Object> capability(String id, boolean configured, String reason) {
        return Map.of("id", id, "label", id, "configured", configured, "reason", reason, "streaming", false);
    }
    private Request.Builder json(String url, JSONObject body) {
        return new Request.Builder().url(url).post(RequestBody.create(JSON, body.toString()));
    }
    private Payload execute(Request request, CallScope scope) throws IOException {
        requireActive(scope);
        Call call = client.newCall(request);
        scope.register(call);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) throw new IOException("Speech provider HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > MAX_RESPONSE) throw new IOException("Speech response exceeds 8MiB");
            try (InputStream input = body.byteStream(); ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    requireActive(scope);
                    if (result.size() + count > MAX_RESPONSE) throw new IOException("Speech response exceeds 8MiB");
                    result.write(buffer, 0, count);
                }
                requireActive(scope);
                return new Payload(result.toByteArray(), response.header("Content-Type", "application/octet-stream"));
            }
        } finally { scope.unregister(call); }
    }
    private AudioResult audio(Payload result) throws IOException {
        String mime = result.mime().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (result.bytes().length == 0 || !(mime.startsWith("audio/") || mime.equals("application/octet-stream")))
            throw new IOException("Speech provider returned non-audio content");
        return new AudioResult(result.bytes(), mime.equals("application/octet-stream") ? "audio/wav" : mime);
    }
    private void validateResultUrl(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || !RESULT_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) { throw new IOException("Speech audio URL rejected by allowlist"); }
    }
    private static void requireActive(CallScope scope) throws IOException {
        if (scope == null) throw new IllegalArgumentException("CallScope required");
        if (scope.isCancelled()) throw new IOException("Speech call cancelled");
    }
    private String provider(String value) {
        String id = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(id)) throw new IllegalArgumentException("Unsupported speech provider");
        return id;
    }
    private String key(String id) {
        String key = configuredKey(id);
        if (!id.equals("local") && key.isBlank()) throw new IllegalStateException(id + " API key required");
        return key;
    }
    private String configuredKey(String id) {
        if (id.equals("local")) return "";
        return setting("voice." + id + ".api-key", id.equals("azure") ? "AZURE_SPEECH_KEY" : prefix(id) + "_API_KEY", "");
    }
    private String config(String id, String name, String fallback) {
        return setting("voice." + id + "." + name, prefix(id) + "_" + name.replace('-', '_').toUpperCase(Locale.ROOT), fallback);
    }
    private String prefix(String id) { return id.equals("local") ? "LOCAL_SPEECH" : id.equals("azure") ? "AZURE_SPEECH" : id.toUpperCase(Locale.ROOT); }
    private String setting(String property, String variable, String fallback) {
        String value = env.getProperty(property);
        if (value == null || value.isBlank()) value = env.getProperty(variable);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
    private String endpoint(String id, boolean tts) {
        String override = config(id, tts ? "tts-url" : "asr-url", "");
        if (!override.isBlank()) return override;
        String base = config(id, "base-url", "").replaceAll("/+$", "");
        if (!base.isBlank()) {
            if (id.equals("dashscope")) return base + (tts ? "/services/aigc/multimodal-generation/generation" : "/chat/completions");
            if (id.equals("azure")) return base + (tts ? "/cognitiveservices/v1" : "/speech/recognition/conversation/cognitiveservices/v1?language=zh-CN");
            return base + (tts ? "/audio/speech" : "/audio/transcriptions");
        }
        return switch (id) {
            case "dashscope" -> tts ? "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation" : "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            case "azure" -> "https://" + config(id, "region", "eastasia") + (tts ? ".tts.speech.microsoft.com/cognitiveservices/v1" : ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=zh-CN");
            case "siliconflow" -> "https://api.siliconflow.cn/v1" + (tts ? "/audio/speech" : "/audio/transcriptions");
            default -> "http://local-speech:8000/v1" + (tts ? "/audio/speech" : "/audio/transcriptions");
        };
    }
    private String defaultVoice(String id) {
        return switch (id) {
            case "dashscope" -> "Cherry";
            case "azure" -> "zh-CN-XiaoxiaoNeural";
            case "siliconflow" -> "FunAudioLLM/CosyVoice2-0.5B:alex";
            default -> "alloy";
        };
    }
    private String xml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
    private record Payload(byte[] bytes, String mime) {}
}
