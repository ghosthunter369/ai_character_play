package com.character.voice;

import com.character.model.entity.User;
import com.character.service.AppService;
import com.character.voice.provider.CallScope;
import com.character.voice.provider.SpeechGateway;
import com.character.websocket.AudioHandshakeInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Authenticated, bounded, explicit-commit voice transport. */
@Component
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {
    private static final int MAX_AUDIO_BYTES = 16000 * 2 * 30;
    private final AppService apps;
    private final SpeechGateway speech;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public VoiceWebSocketHandler(AppService apps, SpeechGateway speech, JdbcTemplate jdbc, ObjectMapper json) {
        this.apps=apps; this.speech=speech; this.jdbc=jdbc; this.json=json;
    }

    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            User user = (User) session.getAttributes().get(AudioHandshakeInterceptor.ATTR_USER);
            var query = UriComponentsBuilder.fromUri(Objects.requireNonNull(session.getUri())).build().getQueryParams();
            long appId = Long.parseLong(query.getFirst("appId"));
            String conversation = UUID.fromString(Objects.requireNonNull(query.getFirst("conversationId"))).toString();
            if (user == null || apps.getById(appId) == null) throw new IllegalArgumentException();
            String asr = Optional.ofNullable(query.getFirst("asrProvider")).orElse("browser");
            String tts = Optional.ofNullable(query.getFirst("ttsProvider")).orElse("browser");
            var capabilities = speech.capabilities();
            if (!available(capabilities, "asr", asr) || !available(capabilities,"tts", tts)) throw new IllegalArgumentException();
            session.setBinaryMessageSizeLimit(65536);
            session.setTextMessageSizeLimit(8192);
            Connection state = new Connection(new ConcurrentWebSocketSessionDecorator(session, 10000, 12*1024*1024), user, appId, conversation, asr, tts);
            connections.put(session.getId(), state);
            state.send(Map.of("type", "session.ready", "conversationId", conversation));
        } catch (Exception error) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid role, conversation or provider"));
        }
    }

    private boolean available(Map<String,List<Map<String,Object>>> caps, String kind, String provider) {
        if ("browser".equals(provider)) return true;
        return caps.getOrDefault(kind, List.of()).stream().anyMatch(p -> provider.equals(p.get("id")) && Boolean.TRUE.equals(p.get("configured")));
    }

    @Override protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Connection state=connections.get(session.getId());
        if (state == null) return;
        synchronized (state) {
            ByteBuffer bytes=message.getPayload();
            if (state.audio.size()+bytes.remaining() > MAX_AUDIO_BYTES || bytes.remaining()%2!=0) {
                state.audio.reset();
                state.error("audio", "每轮录音不能超过30秒，请结束发言后重试");
                return;
            }
            byte[] chunk=new byte[bytes.remaining()]; bytes.get(chunk); state.audio.writeBytes(chunk);
        }
    }

    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Connection state=connections.get(session.getId());
        if (state == null) return;
        try {
            JsonNode event=json.readTree(message.getPayload());
            switch (event.path("type").asText()) {
                case "audio.commit" -> {
                    String requestId=event.path("requestId").asText();
                    if(!validRequestId(requestId)) { state.error("protocol","请求ID无效"); return; }
                    commitAudio(state,requestId);
                }
                case "text.submit" -> {
                    String text=event.path("text").asText().trim();
                    if (text.isEmpty() || text.length()>2000) { state.error("input", "请输入1到2000字的消息"); return; }
                    String requestId=event.path("requestId").asText();
                    if(!validRequestId(requestId)) { state.error("protocol","请求ID无效"); return; }
                    synchronized (state) { state.audio.reset(); String id=state.begin(requestId); reply(state,id,text); }
                }
                case "turn.cancel" -> { synchronized (state) { state.cancel(); state.audio.reset(); } }
                case "feedback" -> feedback(state,event);
                case "playback.started" -> {
                    long elapsed=event.path("elapsedMs").asLong(-1);
                    if (elapsed>=0 && elapsed<300000 && state.ownedTurns.contains(event.path("turnId").asText())) {
                        org.slf4j.LoggerFactory.getLogger(getClass()).info("voice_playback userId={} turnId={} clientElapsedMs={}", state.user.getId(),event.path("turnId").asText(),elapsed);
                    }
                }
                default -> state.error("protocol", "不支持的消息类型");
            }
        } catch (Exception error) { state.error("protocol", "消息格式或操作无效"); }
    }

    private boolean validRequestId(String id) {
        try { return UUID.fromString(id).toString().equals(id); } catch(Exception invalid) { return false; }
    }

    private void commitAudio(Connection state,String requestId) {
        synchronized (state) {
            if (state.audio.size()<3200) { state.audio.reset(); state.error("asr", "录音太短，请重新说话"); return; }
            byte[] wav=wav(state.audio.toByteArray()); state.audio.reset();
            String id=state.begin(requestId);
            CallScope calls=state.asrCalls;
            state.asrTask=Mono.fromCallable(() -> speech.transcribe(wav,state.asr,calls))
                    .subscribeOn(Schedulers.boundedElastic()).subscribe(text -> {
                        synchronized (state) {
                            if (!id.equals(state.current) || state.closed) return;
                            calls.close();
                            reply(state,id,text);
                        }
                    }, error -> {
                        synchronized(state) {
                            if (id.equals(state.current) && !state.closed) {
                                calls.close(); state.error("asr", "语音识别失败，请检查供应商配置或重试");
                                state.send(Map.of("type","turn.done","turnId",id));
                            }
                        }
                    });
        }
    }

    private void reply(Connection state, String id, String text) {
        if (text==null || text.isBlank() || text.length()>2000) {
            state.error("asr", "未识别到有效语音，请重试");
            state.send(Map.of("type","turn.done","turnId",id)); return;
        }
        state.send(Map.of("type","asr.final","turnId",id,"text",text));
        state.turn=new VoiceTurn(id, apps.chatInConversation(state.appId,text,state.user,state.conversation).subscribeOn(Schedulers.boundedElastic()), state.tts,"",speech,event -> {
            if(id.equals(state.current) && !state.closed) state.send(event);
        });
        state.turn.start();
    }

    private void feedback(Connection state, JsonNode event) {
        String category=event.path("category").asText();
        String turn=event.path("turnId").asText();
        if (!Set.of("latency","asr","relevance","character","audio").contains(category) || !state.ownedTurns.contains(turn)) {
            state.error("feedback","反馈分类或轮次无效"); return;
        }
        synchronized(state) {
            if(!state.feedbackKeys.add(turn+":"+category)) return;
        }
        Long elapsed=event.has("elapsedMs") ? event.path("elapsedMs").asLong() : null;
        if(elapsed!=null && (elapsed<0 || elapsed>300000)) elapsed=null;
        jdbc.update("INSERT INTO voice_feedback(user_id,app_id,conversation_id,turn_id,category,elapsed_ms) VALUES(?,?,?,?,?,?)",
                state.user.getId(),state.appId,state.conversation,turn,category,elapsed);
        state.send(Map.of("type","feedback.saved","turnId",turn,"category",category));
    }

    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) { close(session); }
    @Override public void handleTransportError(WebSocketSession session,Throwable error) throws Exception { close(session); if(session.isOpen()) session.close(CloseStatus.SERVER_ERROR); }
    private void close(WebSocketSession session) {
        Connection state=connections.remove(session.getId());
        if(state!=null) synchronized(state) { state.closed=true; state.cancel(); state.audio.reset(); }
    }

    public static byte[] wav(byte[] pcm) {
        ByteBuffer out=ByteBuffer.allocate(44+pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36+pcm.length);
        out.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1);
        out.putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
        out.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return out.array();
    }

    private final class Connection {
        final WebSocketSession socket; final User user; final long appId; final String conversation,asr,tts;
        final ByteArrayOutputStream audio=new ByteArrayOutputStream();
        final Set<String> ownedTurns=ConcurrentHashMap.newKeySet();
        final Set<String> feedbackKeys=ConcurrentHashMap.newKeySet();
        volatile String current=""; VoiceTurn turn; Disposable asrTask; CallScope asrCalls=new CallScope(); volatile boolean closed;
        Connection(WebSocketSession socket,User user,long appId,String conversation,String asr,String tts) { this.socket=socket; this.user=user; this.appId=appId; this.conversation=conversation; this.asr=asr; this.tts=tts; }
        String begin(String requestId) {
            cancel(); current=UUID.randomUUID().toString();
            // Cap per-connection metadata as well as PCM and generated text.
            if(ownedTurns.size()>=100) { ownedTurns.clear(); feedbackKeys.clear(); }
            ownedTurns.add(current); asrCalls=new CallScope();
            send(Map.of("type","turn.started","turnId",current,"requestId",requestId)); return current;
        }
        void cancel() {
            asrCalls.cancel(); if(asrTask!=null) asrTask.dispose();
            if(turn!=null) turn.cancel();
            else if(!current.isEmpty()) send(Map.of("type","turn.cancelled","turnId",current));
            turn=null; current="";
        }
        void error(String stage,String message) { send(Map.of("type","error","stage",stage,"message",message,"turnId",current)); }
        void send(Map<String,Object> event) {
            if(closed || !socket.isOpen()) return;
            try { socket.sendMessage(new TextMessage(json.writeValueAsString(event))); }
            catch(Exception error) { Schedulers.boundedElastic().schedule(() -> close(socket)); }
        }
    }
}
