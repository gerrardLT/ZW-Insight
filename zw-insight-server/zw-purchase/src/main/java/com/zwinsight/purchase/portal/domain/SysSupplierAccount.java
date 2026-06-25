package com.zwinsight.purchase.portal.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商账户实体 - 供应商门户独立认证使用
 */
@Data
@TableName("sys_supplier_account")
public class SysSupplierAccount implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 供应商ID（关联 biz_inquiry_supplier 中的 supplierId）
     */
    private Long supplierId;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 供应商名称（冗余，方便查询展示）
     */
    private String supplierName;

    /**
     * 状态（1-正常 0-停用）
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
