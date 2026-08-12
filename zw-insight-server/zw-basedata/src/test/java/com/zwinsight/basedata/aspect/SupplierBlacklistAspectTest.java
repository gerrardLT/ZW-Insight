package com.zwinsight.basedata.aspect;

import com.zwinsight.basedata.service.SupplierBlacklistService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SupplierBlacklistAspect 降级分支单元测试（P1 PUR-CON-04 补测，2026-08-13）
 * <p>
 * 主拦截路径（黑名单拒绝/非黑名单放行，supplierId/partyBId 双提取策略）已由
 * BlacklistInterceptionPropertyTest 属性测试覆盖；本类补提取失败降级分支：
 * 无法从参数提取供应商ID时跳过校验不阻断业务。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SupplierBlacklistAspectTest {

    /** 无 getSupplierId/getPartyBId 方法的参数对象 */
    public static class NoSupplierArg {
        private final String name;

        public NoSupplierArg(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private JoinPoint joinPoint(Object... args) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("MockService.save(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    @Test
    @DisplayName("参数无供应商ID提取入口 → 跳过校验不阻断（P1 PUR-CON-04 降级）")
    void noSupplierIdGetter_skips() {
        SupplierBlacklistService blacklistService = mock(SupplierBlacklistService.class);
        SupplierBlacklistAspect aspect = new SupplierBlacklistAspect(blacklistService);

        assertThatCode(() -> aspect.checkBlacklist(joinPoint(new NoSupplierArg("某合同")), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(blacklistService);
    }

    @Test
    @DisplayName("空参数列表 → 跳过校验不阻断（P1 PUR-CON-04 降级）")
    void emptyArgs_skips() {
        SupplierBlacklistService blacklistService = mock(SupplierBlacklistService.class);
        SupplierBlacklistAspect aspect = new SupplierBlacklistAspect(blacklistService);

        assertThatCode(() -> aspect.checkBlacklist(joinPoint(), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(blacklistService);
    }

    @Test
    @DisplayName("参数为 null → 跳过校验不阻断（P1 PUR-CON-04 降级）")
    void nullArg_skips() {
        SupplierBlacklistService blacklistService = mock(SupplierBlacklistService.class);
        SupplierBlacklistAspect aspect = new SupplierBlacklistAspect(blacklistService);

        assertThatCode(() -> aspect.checkBlacklist(joinPoint((Object) null), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(blacklistService);
    }
}
