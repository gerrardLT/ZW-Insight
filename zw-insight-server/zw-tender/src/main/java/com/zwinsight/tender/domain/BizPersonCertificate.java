package com.zwinsight.tender.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 人员证书实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_person_certificate")
public class BizPersonCertificate extends BaseEntity {

    /** 人员姓名 */
    private String personName;

    /** 证书类型 */
    private String certificateType;

    /** 证书编号 */
    private String certificateNo;

    /** 发证日期 */
    private LocalDate issueDate;

    /** 到期日期 */
    private LocalDate expireDate;

    /** 状态（1-有效 0-无效） */
    private Integer status;
}
