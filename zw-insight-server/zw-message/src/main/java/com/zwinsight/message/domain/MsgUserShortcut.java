package com.zwinsight.message.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户快捷入口实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_user_shortcut")
public class MsgUserShortcut extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 菜单ID
     */
    private Long menuId;

    /**
     * 菜单名称
     */
    private String menuName;

    /**
     * 菜单路径
     */
    private String menuPath;

    /**
     * 菜单图标
     */
    private String menuIcon;

    /**
     * 排序号
     */
    private Integer sortOrder;
}
