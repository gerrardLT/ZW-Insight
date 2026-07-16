package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 材料出库单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_outbound")
public class BizMaterialOutbound extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 出库类型（PICK-领料/RETURN-退货） */
    private String outboundType;

    /** 出库日期 */
    private LocalDate outboundDate;

    /** 操作人姓名 */
    private String operatorName;

    /** 合同ID（退货时关联） */
    private Long contractId;

    /** 退货类型（RETURN_ONLY-仅退货/RETURN_REFUND-退货退款） */
    private String returnType;

    /** 状态（DRAFT-草稿/APPROVED-已审批） */
    private String status;

    /** 出库明细（随主表提交，非表字段） */
    @TableField(exist = false)
    private List<BizMaterialOutboundDetail> details;
}
