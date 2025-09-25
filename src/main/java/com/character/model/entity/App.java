package com.character.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 应用表
 * @TableName app
 */
@TableName(value ="app")
@Data
public class App implements Serializable {
    /**
     * 应用ID
     */
    @TableId(value = "app_id", type = IdType.ASSIGN_ID)
    private Long appId;

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
     * 封面图片URL
     */
    @TableField(value = "cover")
    private String cover;

    /**
     * 所属用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 开场白
     */
    @TableField(value = "prologue")
    private String prologue;

    /**
     * 优先级
     */
    @TableField(value = "priority")
    private Integer priority;
    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;
    /**
     * 修改时间
     */
    @TableField(value = "edit_time")
    private LocalDateTime editTime;

    /**
     * 是否删除（0=未删，1=已删）
     */
    @TableField(value = "is_delete")
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        App other = (App) that;
        return (this.getAppId() == null ? other.getAppId() == null : this.getAppId().equals(other.getAppId()))
            && (this.getDescription() == null ? other.getDescription() == null : this.getDescription().equals(other.getDescription()))
            && (this.getAppName() == null ? other.getAppName() == null : this.getAppName().equals(other.getAppName()))
            && (this.getInitPrompt() == null ? other.getInitPrompt() == null : this.getInitPrompt().equals(other.getInitPrompt()))
            && (this.getCover() == null ? other.getCover() == null : this.getCover().equals(other.getCover()))
            && (this.getUserId() == null ? other.getUserId() == null : this.getUserId().equals(other.getUserId()))
            && (this.getPrologue() == null ? other.getPrologue() == null : this.getPrologue().equals(other.getPrologue()))
            && (this.getCreateTime() == null ? other.getCreateTime() == null : this.getCreateTime().equals(other.getCreateTime()))
            && (this.getIsDelete() == null ? other.getIsDelete() == null : this.getIsDelete().equals(other.getIsDelete()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getAppId() == null) ? 0 : getAppId().hashCode());
        result = prime * result + ((getDescription() == null) ? 0 : getDescription().hashCode());
        result = prime * result + ((getAppName() == null) ? 0 : getAppName().hashCode());
        result = prime * result + ((getInitPrompt() == null) ? 0 : getInitPrompt().hashCode());
        result = prime * result + ((getCover() == null) ? 0 : getCover().hashCode());
        result = prime * result + ((getUserId() == null) ? 0 : getUserId().hashCode());
        result = prime * result + ((getPrologue() == null) ? 0 : getPrologue().hashCode());
        result = prime * result + ((getCreateTime() == null) ? 0 : getCreateTime().hashCode());
        result = prime * result + ((getIsDelete() == null) ? 0 : getIsDelete().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", appid=").append(appId);
        sb.append(", description=").append(description);
        sb.append(", appName=").append(appName);
        sb.append(", initPrompt=").append(initPrompt);
        sb.append(", cover=").append(cover);
        sb.append(", userid=").append(userId);
        sb.append(", prologue=").append(prologue);
        sb.append(", createTime=").append(createTime);
        sb.append(", isDelete=").append(isDelete);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}