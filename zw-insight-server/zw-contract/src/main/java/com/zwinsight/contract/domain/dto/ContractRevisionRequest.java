package com.zwinsight.contract.domain.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * 合同修订申请（V2026_123 起支持支出合同族）。
 * contractCategory 为空视为 CONSTRUCTION（兼容既有施工合同调用）。
 */
public record ContractRevisionRequest(@NotNull Long contractId,
        String contractCategory,
        @NotNull Integer baseVersion,
        @NotNull @DecimalMin("0.01") @Digits(integer=16, fraction=2) BigDecimal afterAmount,
        @NotBlank @Size(max=1000) String reason) {}
