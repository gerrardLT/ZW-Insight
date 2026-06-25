package com.zwinsight.site.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检查明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inspection_detail")
public class BizInspectionDetail extends BaseEntity {

    /** 检查记录ID */
    private Long inspectionId;

    /** 检查项目名称 */
    private String itemName;

    /** 检查标准 */
    private String checkStandard;

    /** 检查方法 */
    private String checkMethod;

    /** 检查结果(PASS/FAIL/NOT_CHECKED) */
    private String checkResult;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortOrder;
}
