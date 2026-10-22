package com.character.voice;

import com.character.voice.provider.AudioResult;
import com.character.voice.provider.CallScope;
import com.character.voice.provider.SpeechGateway;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** One cancellable model -> sentence -> speech pipeline. Transport owns authentication. */
public final class VoiceTurn {
    private final String id;
    private final Flux<String> model;
    private final String provider;
    private final String voice;
    private final SpeechGateway speech;
    private final Consumer<Map<String,Object>> output;
    private final CallScope calls = new CallScope();
    private final long started = System.nanoTime();
    private final StringBuilder pending = new StringBuilder();
    private Disposable subscription;
    private Disposable audioSubscription;
    private final Sinks.Many<String> queue = Sinks.many().unicast()
            .onBackpressureBuffer(new java.util.concurrent.ArrayBlockingQueue<>(128));
    private boolean cancelled;
    private boolean finished;
    private int sequence;
    private int characters;

    public VoiceTurn(String id, Flux<String> model, String provider, String voice,
                     SpeechGateway speech, Consumer<Map<String,Object>> output) {
        this.id = id;
        this.model = model;
        this.provider = provider;
        this.voice = voice;
        this.speech = speech;
        this.output = output;
    }

    public synchronized void start() {
        if (cancelled || finished || subscription != null) return;
        audioSubscription = queue.asFlux().concatMap(this::synthesize, 1)
                .subscribe(ignored -> {}, error -> {
                    emit("error", Map.of("stage", "tts", "message", "语音队列处理失败"));
                    finish();
                }, this::finish);
        subscription = model.timeout(Duration.ofSeconds(60))
                .subscribe(text -> {
                    characters += text.length();
                    if (characters > 4000) { fail(); return; }
                    emit("reply.delta", Map.of("text", text));
                    for (String sentence : sentences(text)) enqueue(sentence);
                }, error -> fail(), () -> {
                    emit("reply.done", Map.of());
                    for (String sentence : flush()) enqueue(sentence);
                    queue.tryEmitComplete();
                });
        // subscribe() can finish synchronously; cancelling during subscription is also safe.
        if (cancelled && subscription != null) subscription.dispose();
    }

    private void enqueue(String sentence) {
        if (cancelled || finished) return;
        if (!queue.tryEmitNext(sentence).isSuccess()) fail();
    }

    private synchronized void fail() {
        if (cancelled || finished) return;
        emit("error", Map.of("stage", "reply", "message", "回复生成失败或语音队列已满，请重试"));
        if (subscription != null) subscription.dispose();
        if (audioSubscription != null) audioSubscription.dispose();
        finish();
    }

    private List<String> sentences(String delta) {
        List<String> ready = new ArrayList<>();
        pending.append(delta);
        int cut = 0;
        for (int i=0; i<pending.length(); i++) {
            char ch = pending.charAt(i);
            boolean punctuation = "。！？!?；;\n".indexOf(ch) >= 0;
            boolean limit = i-cut+1 >= 80 && !Character.isHighSurrogate(ch);
            if (punctuation || limit) {
                String part = pending.substring(cut, i+1).trim();
                if (!part.isEmpty()) ready.add(part);
                cut = i+1;
            }
        }
        pending.delete(0, cut);
        return ready;
    }

    private List<String> flush() {
        String tail = pending.toString().trim();
        pending.setLength(0);
        return tail.isEmpty() ? List.of() : List.of(tail);
    }

    private Mono<String> synthesize(String sentence) {
        if ("browser".equals(provider)) {
            emit("reply.sentence", Map.of("text", sentence));
            return Mono.just(sentence);
        }
        return Mono.fromCallable(() -> {
                    if (calls.isCancelled()) return sentence;
                    AudioResult audio = speech.synthesize(sentence, provider, voice, calls);
                    emit("audio.chunk", Map.of("text", sentence, "mimeType", audio.mimeType(),
                            "data", Base64.getEncoder().encodeToString(audio.data())));
                    return sentence;
                }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    emit("error", Map.of("stage", "tts", "message", "语音合成失败，文字回复仍可阅读"));
                    return Mono.just(sentence);
                });
    }

    private synchronized void emit(String type, Map<String,Object> payload) {
        if (cancelled || finished) return;
        Map<String,Object> event = new LinkedHashMap<>(payload);
        event.put("type", type);
        event.put("turnId", id);
        event.put("seq", ++sequence);
        event.put("elapsedMs", (System.nanoTime()-started)/1_000_000);
        output.accept(event);
    }

    private synchronized void finish() {
        if (cancelled || finished) return;
        emit("turn.done", Map.of());
        finished = true;
        calls.close();
    }

    public synchronized void cancel() {
        if (cancelled) return;
        if (!finished) emit("turn.cancelled", Map.of());
        cancelled = true;
        calls.cancel();
        if (subscription != null) subscription.dispose();
        if (audioSubscription != null) audioSubscription.dispose();
    }
}
