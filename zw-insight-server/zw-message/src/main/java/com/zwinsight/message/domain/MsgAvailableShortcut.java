package com.zwinsight.message.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 可选快捷功能定义实体
 */
@Data
@TableName("msg_available_shortcut")
public class MsgAvailableShortcut implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 功能名称
     */
    private String name;

    /**
     * 图标标识
     */
    private String icon;

    /**
     * 路由路径
     */
    private String routePath;

    /**
     * 默认排序
     */
    private Integer sortOrder;

    /**
     * 状态：ENABLED-启用 / DISABLED-停用
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
