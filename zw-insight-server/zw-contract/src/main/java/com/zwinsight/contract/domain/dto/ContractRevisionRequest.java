package com.zwinsight.contract.domain.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ContractRevisionRequest(@NotNull Long contractId, @NotNull Integer baseVersion,
        @NotNull @DecimalMin("0.01") @Digits(integer=16, fraction=2) BigDecimal afterAmount,
        @NotBlank @Size(max=1000) String reason) {}
