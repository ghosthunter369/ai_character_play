package com.character.controller;

import lombok.Data;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@Data
@RequestMapping("/chat")
public class AiChatController {

    @RequestMapping("/generate")
    public Flux<String> generateChatMessageStream(String prompt, String userMessage) {
        // 简单示例：将用户消息回显为 AI 回复。实际项目中替换为大模型流式输出。
        String reply = (userMessage == null || userMessage.isEmpty())
                ? "你好，我是AI助手。"
                :  userMessage;
        return Flux.just(reply);
    }
}