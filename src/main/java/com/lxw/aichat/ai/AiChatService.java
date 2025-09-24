package com.lxw.aichat.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

public interface AiChatService {


    @SystemMessage(value = "{prompt}")
    Flux<String> generateChatMessageStream(@V ("prompt") String prompt,@UserMessage String userMessage,@MemoryId String appIdAndUserId);



}
