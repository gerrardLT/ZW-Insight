package com.zwinsight.tender.domain;

import com.zwinsight.common.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 投标押证绑定（V2026_82，TI-2 一证多投排他锁定）。
 * <p>
 * 投标报名时绑定拟派项目经理/技术负责人/安全员等关键人员证件，
 * 在报名状态为 REGISTERED/SUBMITTED/WON 期间锁定（LOCKED），
 * 同一证件在解锁前不可被其他在投项目重复绑定。
 * 开标后（不论中标或落标）自动解锁（RELEASED）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_tender_person_binding", autoResultMap = true)
public class BizTenderPersonBinding extends BaseEntity {

    @TableField("register_id")
    private Long registerId;

    @TableField("project_id")
    private Long projectId;

    @TableField("person_certificate_id")
    private Long personCertificateId;

    @TableField("person_name")
    private String personName;

    @TableField("certificate_type")
    private String certificateType;

    @TableField("binding_role")
    private String bindingRole;

    @TableField("status")
    private String status;

    @TableField("released_at")
    private LocalDateTime releasedAt;

    @TableField("released_reason")
    private String releasedReason;

    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_RELEASED = "RELEASED";
}
