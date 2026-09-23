package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizBankFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 银行流水 Mapper
 */
@Mapper
public interface BizBankFlowMapper extends BaseMapper<BizBankFlow> {

    /**
     * 全部账户最新余额快照合计（驾驶舱「账户资金」卡数据源，V2026_59）。
     * <p>单 SQL 取每账户 snapshot_date 最新一条余额求和，避免逐账户 N+1 查询；
     * 口径为手工登记的余额快照（与资金日报头寸同源）。</p>
     *
     * @return 余额合计（无任何登记时为 NULL，调用方按 0 处理并如实展示）
     */
    @Select("SELECT SUM(b.balance) FROM biz_bank_balance b " +
            "JOIN (SELECT account_id, MAX(snapshot_date) AS max_date FROM biz_bank_balance " +
            "WHERE deleted = 0 GROUP BY account_id) t " +
            "ON t.account_id = b.account_id AND t.max_date = b.snapshot_date " +
            "WHERE b.deleted = 0")
    BigDecimal sumLatestBalances();
}
