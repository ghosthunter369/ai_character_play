package com.character.voice;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class VoiceTurnTest {
    @Test void slowSpeechDoesNotBlockTextCompletion() throws Exception {
        List<Map<String,Object>> events = new CopyOnWriteArrayList<>();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var gateway = new com.character.voice.provider.SpeechGateway(new org.springframework.mock.env.MockEnvironment()) {
            @Override public com.character.voice.provider.AudioResult synthesize(String text, String provider, String voice,
                    com.character.voice.provider.CallScope scope) throws Exception {
                entered.countDown();
                release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return new com.character.voice.provider.AudioResult(new byte[]{1}, "audio/wav");
            }
        };
        Sinks.Many<String> model = Sinks.many().unicast().onBackpressureBuffer();
        VoiceTurn turn = new VoiceTurn("slow-tts", model.asFlux(), "local", "", gateway, events::add);
        try {
            turn.start();
            model.tryEmitNext("第一句。");
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            for (int i=0; i<60; i++) assertTrue(model.tryEmitNext("后续。").isSuccess());
            model.tryEmitComplete();
            assertEquals(61, events.stream().filter(e -> "reply.delta".equals(e.get("type"))).count());
            assertTrue(events.stream().anyMatch(e -> "reply.done".equals(e.get("type"))));
        } finally { turn.cancel(); release.countDown(); }
    }
    @Test void firstSentenceIsEmittedBeforeTheModelFinishes() {
        List<Map<String,Object>> events = new CopyOnWriteArrayList<>();
        Sinks.Many<String> model = Sinks.many().unicast().onBackpressureBuffer();
        VoiceTurn turn = new VoiceTurn("turn-1", model.asFlux(), "browser", "", null, events::add);
        turn.start();
        model.tryEmitNext("你好。后面");
        assertTrue(events.stream().anyMatch(e -> "reply.sentence".equals(e.get("type")) && "你好。".equals(e.get("text"))));
        assertFalse(events.stream().anyMatch(e -> "reply.done".equals(e.get("type"))));
        model.tryEmitNext("继续。");
        model.tryEmitComplete();
        assertEquals("turn.done", events.getLast().get("type"));
    }

    @Test void cancelledTurnDoesNotEmitLateTextOrCompleteEvents() {
        List<Map<String,Object>> events = new CopyOnWriteArrayList<>();
        Sinks.Many<String> model = Sinks.many().unicast().onBackpressureBuffer();
        VoiceTurn turn = new VoiceTurn("turn-1", model.asFlux(), "browser", "", null, events::add);
        turn.start();
        model.tryEmitNext("第一句。");
        turn.cancel();
        int atCancel = events.size();
        model.tryEmitNext("不该出现。");
        model.tryEmitComplete();
        assertEquals(atCancel, events.size());
        assertEquals("turn.cancelled", events.getLast().get("type"));
    }

    @Test void unfinishedFinalSentenceIsFlushedAndHasMonotonicSequenceNumbers() {
        List<Map<String,Object>> events = new CopyOnWriteArrayList<>();
        new VoiceTurn("turn-2", reactor.core.publisher.Flux.just("没有句号"), "browser", "", null, events::add).start();
        assertTrue(events.stream().anyMatch(e -> "reply.sentence".equals(e.get("type")) && "没有句号".equals(e.get("text"))));
        for (int i=0; i<events.size(); i++) assertEquals(i+1, events.get(i).get("seq"));
    }

    @Test void modelFailureEndsTurnWithExplicitError() {
        List<Map<String,Object>> events = new CopyOnWriteArrayList<>();
        new VoiceTurn("turn-3", reactor.core.publisher.Flux.error(new IllegalStateException("external error")),
                "browser", "", null, events::add).start();
        assertTrue(events.stream().anyMatch(e -> "error".equals(e.get("type"))));
        assertEquals("turn.done", events.getLast().get("type"));
    }
}
