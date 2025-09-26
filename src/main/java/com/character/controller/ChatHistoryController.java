package com.character.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.character.common.BaseResponse;
import com.character.common.ResultUtils;
import com.character.exception.ErrorCode;
import com.character.exception.ThrowUtils;
import com.character.model.entity.ChatHistory;
import com.character.model.entity.User;
import com.character.model.vo.ChatHistoryResponse;
import com.character.service.AppService;
import com.character.service.ChatHistoryService;
import com.character.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@Data
@RequestMapping("/chatHistory")
@RequiredArgsConstructor
public class ChatHistoryController {


    private final UserService userService;
    private final ChatHistoryService chatHistoryService;
    private final AppService appService;
    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param messageType    消息类型（USER/AI），可选参数
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<ChatHistoryResponse> listAppChatHistory(@PathVariable Long appId,
                                                                @RequestParam(defaultValue = "10") int pageSize,
                                                                @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                                @RequestParam(required = false) String messageType,
                                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ChatHistoryResponse result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, messageType, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 导出会话历史
     * @param appId
     * @param userId
     * @param response
     * @throws IOException
     */
    @GetMapping("/exportChatHistoryTxt")
    public void exportChatHistory(
            @RequestParam Long appId,
            @RequestParam Long userId,
            HttpServletResponse response) throws IOException {
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR);
        List<ChatHistory> chatHistoryList = chatHistoryService.list(new QueryWrapper<ChatHistory>()
                .eq("app_id", appId).eq("user_id", userId).orderByAsc("create_time"));
        // 拼接文本
        StringBuilder sb = new StringBuilder();
        for (ChatHistory msg : chatHistoryList) {
            sb.append(msg.getMessageType())
                    .append("说：")
                    .append(msg.getMessage())
                    .append("\t---- ")
                    .append(msg.getCreateTime())
                    .append("\n");
        }

        // 设置响应头
        response.setContentType("text/plain;charset=UTF-8");
        String  fileName = "chat_history_" + appService.getById(appId).getAppName() + "_" + userService.getById(userId).getUserName() + ".txt";
        fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);

        // 写入输出流
        try (OutputStream os = response.getOutputStream()) {
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }
}
