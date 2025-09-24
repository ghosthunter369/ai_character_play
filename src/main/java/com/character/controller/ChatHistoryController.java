package com.character.controller;


import com.character.common.BaseResponse;
import com.character.common.ResultUtils;
import com.character.model.entity.User;
import com.character.model.vo.ChatHistoryResponse;
import com.character.service.ChatHistoryService;
import com.character.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@Data
@RequestMapping("/chatHistory")
@RequiredArgsConstructor
public class ChatHistoryController {


    private final UserService userService;
    private final ChatHistoryService chatHistoryService;
    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<ChatHistoryResponse> listAppChatHistory(@PathVariable Long appId,
                                                                @RequestParam(defaultValue = "10") int pageSize,
                                                                @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ChatHistoryResponse result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }
}
