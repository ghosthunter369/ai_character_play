package com.character.voice;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.test.StepVerifier;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ChatCompletionClientTest {
    @Test void compatibleSseIsParsedAndCancellationClosesTheActualCall() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        CountDownLatch disconnected=new CountDownLatch(1);
        server.createContext("/v1/chat/completions", exchange -> {
            String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().readTree(request).path("stream").asBoolean());
            exchange.getResponseHeaders().set("Content-Type","text/event-stream");
            exchange.sendResponseHeaders(200,0);
            try(var output=exchange.getResponseBody()) {
                output.write("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n".getBytes(StandardCharsets.UTF_8)); output.flush();
                // Real TCP writes detect a cancelled response, rather than asserting a mock was cancelled.
                byte[] padding=(": heartbeat"+" ".repeat(8192)+"\n\n").getBytes(StandardCharsets.UTF_8);
                for(int i=0;i<1000;i++) { output.write(padding); output.flush(); Thread.sleep(5); }
            } catch(Exception expected) { disconnected.countDown(); }
        });
        server.start();
        try {
            var env=new MockEnvironment().withProperty("LLM_BASE_URL","http://127.0.0.1:"+server.getAddress().getPort()+"/v1")
                    .withProperty("LLM_API_KEY","test-key").withProperty("LLM_MODEL","test-model");
            StepVerifier.create(new ChatCompletionClient(env).stream(List.of(Map.of("role","user","content","你好"))))
                    .expectNext("你好").thenCancel().verify(java.time.Duration.ofSeconds(5));
            assertTrue(disconnected.await(5,TimeUnit.SECONDS),"Cancelling the Flux must terminate the actual HTTP response");
        } finally { server.stop(0); }
    }

    @Test void finishesOnExplicitDone() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type","text/event-stream"); exchange.sendResponseHeaders(200,0);
            try(var out=exchange.getResponseBody()) { out.write("data: {\"choices\":[{\"delta\":{\"content\":\"好。\"}}]}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8)); }
        }); server.start();
        try {
            var env=new MockEnvironment().withProperty("LLM_BASE_URL","http://127.0.0.1:"+server.getAddress().getPort()+"/v1")
                    .withProperty("LLM_API_KEY","test-key").withProperty("LLM_MODEL","test-model");
            StepVerifier.create(new ChatCompletionClient(env).stream(List.of(Map.of("role","user","content","问"))))
                    .expectNext("好。").expectComplete().verify(java.time.Duration.ofSeconds(5));
        } finally { server.stop(0); }
    }

    @Test void truncatedSseAndHttpErrorsNeverCompleteSuccessfully() throws Exception {
        for(int status : new int[]{200,429}) {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/chat/completions",exchange->{
                exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(status,0);
                try(var out=exchange.getResponseBody()) { out.write("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n".getBytes(StandardCharsets.UTF_8)); }
            }); server.start();
            try {
                var env=new MockEnvironment().withProperty("LLM_BASE_URL","http://127.0.0.1:"+server.getAddress().getPort()+"/v1")
                        .withProperty("LLM_API_KEY","test");
                var verify=StepVerifier.create(new ChatCompletionClient(env).stream(List.of(Map.of("role","user","content","问"))));
                if(status==200) verify.expectNext("partial").expectError(java.io.IOException.class).verify(java.time.Duration.ofSeconds(5));
                else verify.expectError(java.io.IOException.class).verify(java.time.Duration.ofSeconds(5));
            } finally { server.stop(0); }
        }
    }
}
