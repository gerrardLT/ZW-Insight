package com.zwinsight.basedata.service;

import com.zwinsight.basedata.aspect.SupplierBlacklistAspect;
import com.zwinsight.common.exception.BusinessException;
import net.jqwik.api.*;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

/**
 * Property 6: 黑名单拦截一致性
 * <p>
 * For any supplier (blacklisted or not) and any contract type (purchase or subcontract):
 * - If supplier is blacklisted (status=1) → operation MUST be rejected with error message containing the reason
 * - If supplier is NOT blacklisted → operation should be allowed
 * </p>
 * <p>
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.5**
 * </p>
 */
@Label("Property 6: 黑名单拦截一致性")
class BlacklistInterceptionPropertyTest {

    /**
     * 当供应商在黑名单中时，拦截必须抛出 BusinessException，且消息包含黑名单原因。
     * 覆盖采购合同（partyBId）和分包合同（supplierId）两种场景。
     */
    @Property(tries = 100)
    @Label("黑名单供应商 - 操作必须被拒绝并包含原因")
    void blacklistedSupplier_mustBeRejectedWithReason(
            @ForAll("supplierIds") Long supplierId,
            @ForAll("blacklistReasons") String reason,
            @ForAll("contractTypes") ContractType contractType) {

        // 每次 property 调用创建新的 mock 和 aspect 实例
        SupplierBlacklistService blacklistService = Mockito.mock(SupplierBlacklistService.class);
        SupplierBlacklistAspect aspect = new SupplierBlacklistAspect(blacklistService);

        // 配置 mock：供应商在黑名单中
        when(blacklistService.isBlacklisted(supplierId)).thenReturn(true);
        when(blacklistService.getBlacklistReason(supplierId)).thenReturn(reason);

        // 构造 JoinPoint，根据合同类型使用不同的参数对象
        JoinPoint joinPoint = createJoinPoint(supplierId, contractType);

        // 执行切面并验证抛出异常
        Assertions.assertThatThrownBy(() ->
                        aspect.checkBlacklist(joinPoint, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("黑名单")
                .hasMessageContaining(reason)
                .hasMessageContaining("禁止签约");

        // 验证服务方法被调用
        verify(blacklistService).isBlacklisted(supplierId);
        verify(blacklistService).getBlacklistReason(supplierId);
    }

    /**
     * 当供应商不在黑名单中时，操作应正常通过（不抛出异常）。
     * 覆盖采购合同和分包合同两种场景。
     */
    @Property(tries = 100)
    @Label("非黑名单供应商 - 操作应被允许通过")
    void nonBlacklistedSupplier_mustBeAllowed(
            @ForAll("supplierIds") Long supplierId,
            @ForAll("contractTypes") ContractType contractType) {

        // 每次 property 调用创建新的 mock 和 aspect 实例
        SupplierBlacklistService blacklistService = Mockito.mock(SupplierBlacklistService.class);
        SupplierBlacklistAspect aspect = new SupplierBlacklistAspect(blacklistService);

        // 配置 mock：供应商不在黑名单中
        when(blacklistService.isBlacklisted(supplierId)).thenReturn(false);

        // 构造 JoinPoint
        JoinPoint joinPoint = createJoinPoint(supplierId, contractType);

        // 执行切面，验证不抛出异常
        Assertions.assertThatCode(() ->
                        aspect.checkBlacklist(joinPoint, null))
                .doesNotThrowAnyException();

        // 验证只调用了 isBlacklisted，未调用 getBlacklistReason
        verify(blacklistService).isBlacklisted(supplierId);
        verify(blacklistService, never()).getBlacklistReason(anyLong());
    }

    // ========== 辅助方法 ==========

    /**
     * 根据合同类型创建模拟的 JoinPoint
     */
    private JoinPoint createJoinPoint(Long supplierId, ContractType contractType) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("MockService.save(..)");
        when(joinPoint.getSignature()).thenReturn(signature);

        Object arg = switch (contractType) {
            case PURCHASE -> new PurchaseContractArg(supplierId);
            case SUBCONTRACT -> new SubcontractArg(supplierId);
        };
        when(joinPoint.getArgs()).thenReturn(new Object[]{arg});
        return joinPoint;
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<Long> supplierIds() {
        return Arbitraries.longs().between(1L, 999999L);
    }

    @Provide
    Arbitrary<String> blacklistReasons() {
        return Arbitraries.of(
                "质量问题严重",
                "多次违约",
                "资质造假",
                "恶意拖延工期",
                "安全事故",
                "行贿受贿",
                "提供虚假材料",
                "合同欺诈",
                "拖欠工人工资",
                "施工质量不达标"
        );
    }

    @Provide
    Arbitrary<ContractType> contractTypes() {
        return Arbitraries.of(ContractType.class);
    }

    // ========== 内部辅助类 ==========

    /**
     * 合同类型枚举
     */
    enum ContractType {
        PURCHASE,      // 采购合同
        SUBCONTRACT    // 分包合同
    }

    /**
     * 模拟采购合同参数对象（通过 getPartyBId 提取供应商ID）
     * 使用 public 级别确保反射可访问
     */
    public static class PurchaseContractArg {
        private Long partyBId;

        public PurchaseContractArg() {}

        public PurchaseContractArg(Long partyBId) {
            this.partyBId = partyBId;
        }

        public Long getPartyBId() {
            return partyBId;
        }

        public void setPartyBId(Long partyBId) {
            this.partyBId = partyBId;
        }
    }

    /**
     * 模拟分包合同参数对象（通过 getSupplierId 提取供应商ID）
     * 使用 public 级别确保反射可访问
     */
    public static class SubcontractArg {
        private Long supplierId;

        public SubcontractArg() {}

        public SubcontractArg(Long supplierId) {
            this.supplierId = supplierId;
        }

        public Long getSupplierId() {
            return supplierId;
        }

        public void setSupplierId(Long supplierId) {
            this.supplierId = supplierId;
        }
    }
}
