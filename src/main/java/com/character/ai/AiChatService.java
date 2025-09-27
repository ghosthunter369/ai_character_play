package com.character.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiChatService {


//    @SystemMessage(value = "{prompt}")
//    Flux<String> generateChatMessageStream(@V ("prompt") String prompt,@UserMessage String userMessage,@MemoryId String appIdAndUserId);
    Flux<String> generateChatMessageStream(@UserMessage String userMessage, @MemoryId String appIdAndUserId);



}
