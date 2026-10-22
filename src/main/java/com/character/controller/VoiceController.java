package com.character.controller;

import com.character.common.BaseResponse;
import com.character.common.ResultUtils;
import com.character.voice.provider.SpeechGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
public class VoiceController {
    private final SpeechGateway speech;
    public VoiceController(SpeechGateway speech) { this.speech=speech; }
    @GetMapping("/voice/providers")
    public BaseResponse<Map<String,List<Map<String,Object>>>> providers() { return ResultUtils.success(speech.capabilities()); }
}
