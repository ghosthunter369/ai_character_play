package com.character.model.dto.app;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

@Data
public class AppDTO {
    /**
     * 应用介绍
     */
    private String description;
    /**
     * 应用名称
     */
    private String appName;

    /**
     * 初始化提示
     */
    private String initPrompt;

    /**
     * 开场白
     */
    private String prologue;

    /**
     * 封面图片URL
     */
    private String cover;
}
