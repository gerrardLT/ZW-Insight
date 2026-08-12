package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.BizExpenseContract;
import com.zwinsight.contract.dto.ContractExpiryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 通用支出合同 Mapper
 */
@Mapper
public interface BizExpenseContractMapper extends BaseMapper<BizExpenseContract> {

    // 注：退款扣减累计已付款原在本 Mapper（deductPaidAmount），2026-08-13 已移除：
    // 退款关联的是采购合同，付款回写 biz_purchase_contract，扣减改由 zw-purchase
    // PurchaseContractPayMapper.deductPaid 原子执行（批次二 L4 stage_9K 修复）。

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
