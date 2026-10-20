package com.zwinsight.common.revision;

import java.math.BigDecimal;

/**
 * 支出合同修订处理器（V2026_123）。
 * <p>采购/劳务/机械/分包四类支出合同各自实现本接口，由 zw-contract 的
 * ContractRevisionService 按 contractCategory 分派；接口放在 common 模块，
 * 避免 zw-contract 反向依赖各支出合同模块形成循环依赖。</p>
 * <p>实现方必须保证：修订后合同额不低于已发生产值/结算/收票/付款；
 * 回写使用乐观锁（BaseEntity.version），并发冲突抛业务异常。</p>
 */
public interface ExpenseContractRevisionHandler {

    /** 合同类别码：PURCHASE / LABOR / MACHINE / SUBCONTRACT */
    String category();

    /**
     * 锁定并校验合同，返回创建修订申请所需的基准信息与快照。
     *
     * @param contractId  合同ID
     * @param baseVersion 客户端读取到的合同版本
     * @param afterAmount 修订后含税金额
     */
    RevisionContract lockAndValidate(Long contractId, Integer baseVersion, BigDecimal afterAmount);

    /**
     * 审批通过后回写合同金额与税额三件套（乐观锁校验基准版本与修订前金额）。
     */
    void applyAmount(Long contractId, Integer baseVersion, BigDecimal beforeAmount, BigDecimal afterAmount);

    /** 合同是否存在（用于修订列表/详情的可见性校验） */
    boolean exists(Long contractId);
}
