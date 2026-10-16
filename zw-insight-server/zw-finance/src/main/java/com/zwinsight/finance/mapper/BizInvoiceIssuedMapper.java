package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizInvoiceIssued;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 销项发票台账 Mapper（V2026_110）。
 * <p>必须带 {@code @Mapper}：@MapperScan 只注册带注解接口，漏注解会让依赖方启动失败。</p>
 */
@Mapper
public interface BizInvoiceIssuedMapper extends BaseMapper<BizInvoiceIssued> {

    /**
     * 原票剩余可红冲额 = |原票价税合计| − Σ同原票红字|金额|（不含本单）。
     */
    @Select("SELECT COALESCE(ABS(i.amount), 0) - COALESCE((" +
            " SELECT SUM(ABS(r.amount)) FROM biz_invoice_issued r" +
            " WHERE r.original_id = #{originalId} AND r.deleted = 0 AND r.direction = 'RED'" +
            " AND r.id != #{excludeId}), 0) FROM biz_invoice_issued i" +
            " WHERE i.id = #{originalId} AND i.deleted = 0 AND i.direction = 'BLUE'")
    java.math.BigDecimal remainingRedAmount(@Param("originalId") Long originalId,
                                            @Param("excludeId") Long excludeId);
}
