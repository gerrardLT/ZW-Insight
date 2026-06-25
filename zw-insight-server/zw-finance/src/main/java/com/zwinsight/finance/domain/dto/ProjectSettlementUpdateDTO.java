package com.zwinsight.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 项目结算单编辑请求 DTO
 * <p>
 * 仅在结算单状态为 DRAFT 或 REJECTED 时允许编辑。
 * 支持手动调整其他支出和备注，或选择重新汇总数据。
 * </p>
 */
@Data
public class ProjectSettlementUpdateDTO {

    /**
     * 其他支出（可手动调整）
     */
    private BigDecimal otherExpense;

    /**
     * 是否重新汇总项目收支数据
     * <p>true: 重新从合同数据汇总计算; false: 仅更新手动调整的字段</p>
     */
    private Boolean resummarize;
}
