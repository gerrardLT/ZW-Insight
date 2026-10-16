package com.zwinsight.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 价税分离计算（C3 税务事实链共享工具，V2026_110）。
 * <p>
 * 统一「含税额 + 税率(%) → 不含税额 + 税额」的派生口径，供支出合同、销项/进项
 * 发票等共用；金额 HALF_UP 两位，与财务口径一致。税率未知（null/非正/超100）
 * 时派生值置 null，禁止猜填。
 * </p>
 */
public final class TaxAmounts {

    private TaxAmounts() {
    }

    /**
     * 校验税率（百分比，0-100，允许可空）。
     *
     * @throws IllegalArgumentException 税率越界
     */
    public static void validateRate(BigDecimal taxRate) {
        if (taxRate != null && (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("税率须在0至100之间");
        }
    }

    /**
     * 不含税额 = 含税额 ÷ (1 + 税率/100)，HALF_UP 两位。
     * 税率未知返回 null（不猜）。
     */
    public static BigDecimal withoutTax(BigDecimal amountIncludingTax, BigDecimal taxRate) {
        if (amountIncludingTax == null || taxRate == null || taxRate.signum() < 0) {
            return null;
        }
        return amountIncludingTax.divide(
                BigDecimal.ONE.add(taxRate.movePointLeft(2)), 2, RoundingMode.HALF_UP);
    }

    /**
     * 税额 = 含税额 − 不含税额（差额法，与进项发票 V2026_103 口径一致）。
     */
    public static BigDecimal taxAmount(BigDecimal amountIncludingTax, BigDecimal taxRate) {
        BigDecimal without = withoutTax(amountIncludingTax, taxRate);
        if (without == null) {
            return null;
        }
        return amountIncludingTax.subtract(without);
    }
}
