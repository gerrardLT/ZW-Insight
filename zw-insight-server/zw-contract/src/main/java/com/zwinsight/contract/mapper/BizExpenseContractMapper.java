package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.BizExpenseContract;
import com.zwinsight.contract.dto.ContractExpiryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 通用支出合同 Mapper
 */
@Mapper
public interface BizExpenseContractMapper extends BaseMapper<BizExpenseContract> {

    /**
     * 扣减合同累计付款金额（退款审批通过后使用）
     *
     * @param contractId   合同ID
     * @param refundAmount 退款金额
     */
    @Update("UPDATE biz_expense_contract " +
            "SET cumulative_paid = cumulative_paid - #{refundAmount}, " +
            "    updated_at = NOW() " +
            "WHERE id = #{contractId} AND deleted = 0")
    void deductPaidAmount(@Param("contractId") Long contractId,
                          @Param("refundAmount") BigDecimal refundAmount);

    /**
     * 查询即将到期的合同（所有类型）
     * <p>
     * 查询 end_date 在 [today, thirtyDaysLater] 范围内的有效合同，
     * 排除已关闭/已结算/已终止状态的合同。
     * </p>
     *
     * @param today            当前日期
     * @param thirtyDaysLater  30天后的日期
     * @return 即将到期的合同列表
     */
    List<ContractExpiryDTO> selectExpiringContracts(@Param("today") LocalDate today,
                                                    @Param("thirtyDaysLater") LocalDate thirtyDaysLater);
}
