package com.character.model.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.character.model.entity.ChatHistory;
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
