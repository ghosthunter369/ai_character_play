package com.lxw.aichat.controller;

import lombok.Data;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@Data
@RequestMapping("/chat")
public class AiChatController {

    @RequestMapping("/generate")
    public Flux<String>  generateChatMessageStream(String prompt, String userMessage) {
        return null;
    }
}
