package com.lxw.aichat.model.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lxw.aichat.model.entity.ChatHistory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatHistoryResponse {

    private Page<ChatHistory> history;
    private String prologue;
}
