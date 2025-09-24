package com.lxw.aichat.model.dto.app;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

@Data
public class AppDTO {
    /**
     * 应用介绍
     */
    @TableField(value = "description")
    private String description;
    /**
     * 应用名称
     */
    @TableField(value = "app_name")
    private String appName;

    /**
     * 初始化提示
     */
    @TableField(value = "init_prompt")
    private String initPrompt;

    /**
     * 开场白
     */
    @TableField(value = "prologue")
    private String prologue;
}
