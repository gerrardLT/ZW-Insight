package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 离职申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_resign_apply")
public class BizResignApply extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 用户姓名 */
    private String userName;

    /** 离职日期 */
    private LocalDate resignDate;

    /** 交接人 */
    private String handoverPerson;

    /** 是否已交接（0-否 1-是） */
    private Integer isHandover;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
