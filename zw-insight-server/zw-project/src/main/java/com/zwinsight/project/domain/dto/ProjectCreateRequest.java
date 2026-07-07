package com.zwinsight.project.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 项目创建/编辑请求 DTO
 */
@Data
public class ProjectCreateRequest {

    /**
     * 项目名称
     */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称不能超过200个字符")
    private String projectName;

    /**
     * 项目性质
     */
    @Size(max = 50, message = "项目性质不能超过50个字符")
    private String projectNature;

    /**
     * 项目类型
     */
    @Size(max = 50, message = "项目类型不能超过50个字符")
    private String projectType;

    /**
     * 业主单位ID
     */
    private Long ownerCompanyId;

    /**
     * 业主单位名称
     */
    @Size(max = 200, message = "业主单位名称不能超过200个字符")
    private String ownerCompanyName;

    /**
     * 签约公司ID
     */
    private Long signingCompanyId;

    /**
     * 签约公司名称
     */
    @Size(max = 200, message = "签约公司名称不能超过200个字符")
    private String signingCompanyName;

    /**
     * 项目概述
     */
    private String projectOverview;

    /**
     * 项目地址
     */
    @Size(max = 300, message = "项目地址不能超过300个字符")
    private String projectAddress;

    /**
     * 联系人
     */
    @Size(max = 50, message = "联系人不能超过50个字符")
    private String contactName;

    /**
     * 联系电话
     */
    @Size(max = 20, message = "联系电话不能超过20个字符")
    private String contactPhone;

    /**
     * 是否需要招标（1-是 0-否）
     */
    private Integer needTender;

    /**
     * 预算金额
     */
    private BigDecimal budgetAmount;
}
