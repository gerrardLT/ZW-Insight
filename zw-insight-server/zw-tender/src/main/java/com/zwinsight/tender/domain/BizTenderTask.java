package com.zwinsight.tender.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 投标任务实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_tender_task")
public class BizTenderTask extends BaseEntity {

    /** 投标登记ID */
    private Long registerId;

    /** 任务类型（COMMERCIAL/TECHNICAL/ECONOMIC/SEAL） */
    private String taskType;

    /** 负责人 */
    private String responsiblePerson;

    /** 截止日期 */
    private LocalDate deadline;

    /** 状态（PENDING/COMPLETED） */
    private String status;
}
