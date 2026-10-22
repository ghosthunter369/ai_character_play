package com.character.voice;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Standard SSE client with cancellation wired to the actual network Call. */
@Component
public class ChatCompletionClient {
    private final Environment environment;
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS)
            .readTimeout(30,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).build();
    public ChatCompletionClient(Environment environment) { this.environment=environment; }

    public Flux<String> stream(List<Map<String,String>> messages) {
        return Flux.create(sink -> {
            String key=setting("LLM_API_KEY","api-key","");
            if(key.isBlank()) { sink.error(new IllegalStateException("LLM API key required")); return; }
            String url=setting("LLM_BASE_URL","base-url","http://llm:8080/v1").replaceAll("/+$","")+"/chat/completions";
            JSONObject body=new JSONObject().put("model",setting("LLM_MODEL","model-name","qwen2.5-0.5b-instruct"))
                    .put("messages",new JSONArray(messages)).put("stream",true).put("max_tokens",256);
            final Call call;
            try {
                call=client.newCall(new Request.Builder().url(url).header("Authorization","Bearer "+key)
                        .header("Accept","text/event-stream").post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body.toString())).build());
            } catch(Exception invalid) { sink.error(new IllegalStateException("Invalid LLM configuration")); return; }
            sink.onCancel(call::cancel);
            sink.onDispose(call::cancel);
            if(sink.isCancelled()) return;
            call.enqueue(new Callback() {
                @Override public void onFailure(Call call,IOException error) {
                    if(!sink.isCancelled()) sink.error(new IOException("Model connection failed"));
                }
                @Override public void onResponse(Call call,Response response) {
                    try(response) {
                        if(!response.isSuccessful() || response.body()==null) throw new IOException("Model HTTP "+response.code());
                        var source=response.body().source(); int characters=0;
                        while(!sink.isCancelled()) {
                            String line=source.readUtf8LineStrict(65536);
                            if(!line.startsWith("data:")) continue;
                            String data=line.substring(5).trim();
                            if(data.equals("[DONE]")) { sink.complete(); return; }
                            if(data.isEmpty()) continue;
                            JSONObject chunk=new JSONObject(data);
                            if(chunk.has("error")) throw new IOException("Model returned an error");
                            JSONArray choices=chunk.optJSONArray("choices");
                            if(choices==null || choices.length()==0) continue;
                            JSONObject delta=choices.getJSONObject(0).optJSONObject("delta");
                            String content=delta==null ? "" : delta.optString("content","");
                            if(!content.isEmpty()) {
                                characters+=content.length();
                                if(characters>4000) throw new IOException("Model reply exceeds limit");
                                sink.next(content);
                            }
                        }
                    } catch(java.io.EOFException eof) {
                        if(!sink.isCancelled()) sink.error(new IOException("Model stream ended before completion"));
                    } catch(Exception error) {
                        if(!sink.isCancelled()) sink.error(new IOException("Model response failed"));
                    }
                }
            });
        });
    }
    private String setting(String variable,String name,String fallback) {
        String value=environment.getProperty(variable);
        if(value==null || value.isBlank()) value=environment.getProperty("langchain4j.open-ai.chat-model."+name,fallback);
        return value;
    }
}
