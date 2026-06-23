package com.zwinsight.labor.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 劳务花名册
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_labor_roster")
public class BizLaborRoster extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 班组ID */
    private Long teamId;

    /** 工人姓名 */
    private String workerName;

    /** 身份证号 */
    private String idCard;

    /** 联系电话 */
    private String phone;

    /** 用工类型（FIXED-固定/TEMPORARY-临时） */
    private String workerType;

    /** 状态（1-在岗 0-离岗） */
    private Integer status;
}
