package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * CBS 成本账户绑定（源单据 → 成本账户的显式映射）。
 *
 * <h3>为什么需要显式绑定</h3>
 * <p>
 * 成本归集有两种粒度：
 * </p>
 * <ol>
 *   <li><b>科目级</b>：一份采购合同 → MATERIAL 科目。当项目下 MATERIAL 只有一个 CBS 账户时，
 *       归集无歧义，可自动完成；</li>
 *   <li><b>账户级</b>：项目把材料拆成「01.02 混凝土」「01.03 钢筋」两个账户时，
 *       一份合同该记到哪个账户<b>无法从数据推断</b>——只能由业务人员显式指定。</li>
 * </ol>
 * <p>
 * 关键原则：<b>歧义时不猜</b>。猜错会造成成本归属错误，比「暂未归集」危害大得多
 * （后者只是看板少个数，前者会让项目核算得出错误结论）。
 * 因此本表承载人工绑定，未绑定且存在歧义的金额由归集服务报告为 {@code unmapped}，
 * 在 UI 上提示待处理，而不是随意分摊。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_cost_account_link")
public class BizCostAccountLink extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 成本账户ID */
    private Long accountId;

    /**
     * 来源类型。
     * <p>取值见 {@link com.zwinsight.budget.service.CostRollUpService} 的 SRC_* 常量。</p>
     */
    private String sourceType;

    /** 来源业务ID（合同ID/结算单ID等，字符串以兼容非数字单号） */
    private String sourceId;

    /** 该绑定贡献的承诺额 */
    private BigDecimal commitmentAmount;

    /** 该绑定贡献的实际成本 */
    private BigDecimal actualAmount;

    /** 备注 */
    private String remark;
}
