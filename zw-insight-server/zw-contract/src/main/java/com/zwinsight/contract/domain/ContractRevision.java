package com.zwinsight.contract.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract_revision")
public class ContractRevision extends BaseEntity {
    private Long contractId;
    private Long projectId;
    private Integer baseVersion;
    private BigDecimal beforeAmount;
    private BigDecimal afterAmount;
    private BigDecimal deltaAmount;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String reason;
    private String status;
    private String workflowInstanceId;
}
