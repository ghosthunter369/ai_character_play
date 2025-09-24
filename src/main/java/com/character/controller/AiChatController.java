package com.character.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import com.character.common.BaseResponse;
import com.character.common.ResultUtils;
import com.character.exception.ErrorCode;
import com.character.exception.ThrowUtils;
import com.character.model.dto.app.AppDTO;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@Data
@RequestMapping("/chat")
@RequiredArgsConstructor
public class AiChatController {

    private  final AppService appService;
    private final UserService userService;
    private final ChatHistoryService chatHistoryService;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

//    @GetMapping("/generate")
//    public Flux<String>  generateChatMessageStream(@RequestParam("prompt") String prompt, @RequestParam("userMessage") String userMessage) {
//        StreamingChatModel streamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
//        AiChatService aiChatService = AiServices.builder(AiChatService.class)
//                .streamingChatModel(streamingChatModel)
//                .build();
//        return aiChatService.generateChatMessageStream(prompt,userMessage);
//    }

//    @GetMapping("/generate")
//    public Flux<String>  generateChatMessageStream(@RequestParam("prompt") String prompt, @RequestParam("prologue") String prologue) {
//        StreamingChatModel streamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
//        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
//                .builder()
//                .id(1 + "_" + 2)
//                .chatMemoryStore(redisChatMemoryStore)
//                .maxMessages(25)
//                .build();
//        AiChatService aiChatService = AiServices.builder(AiChatService.class)
//                .streamingChatModel(streamingChatModel)
//                .chatMemoryProvider(memoryId -> chatMemory)
//
//                .build();
//        return aiChatService.generateChatMessageStreamFirst(prompt,prologue,"1");
//    }

    /**
     *  创建应用
     * @param appDTO
     * @return
     */
    @PostMapping("/create")
    public BaseResponse<String> createApp(@RequestBody AppDTO appDTO, HttpServletRequest request) {
        App app = new App();
        BeanUtil.copyProperties(appDTO, app);
        app.setCreateTime(LocalDateTime.now());
        User loginUser = userService.getLoginUser(request);
        app.setUserId(loginUser.getId());
        appService.save(app);
        return ResultUtils.success("创建成功");
    }

    @GetMapping("/chat")
    public Flux<ServerSentEvent<String>> chat(@RequestParam Long appId,
                                              @RequestParam(required = false) String message,
                                              HttpServletRequest request){
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（流式）
        Flux<String> contentFlux = appService.chat(appId, message, loginUser);
        return contentFlux
                .map(chunk -> {
                    // 将内容包装成JSON对象
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }
}
