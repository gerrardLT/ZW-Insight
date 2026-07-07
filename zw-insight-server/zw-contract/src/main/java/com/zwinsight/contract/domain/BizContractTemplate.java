package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同模板
 * <p>
 * 模板内容使用 ${变量名} 占位符标记可替换字段。
 * 创建合同时选择模板→填写变量值→自动生成合同内容。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract_template")
public class BizContractTemplate extends BaseEntity {

    /** 模板名称 */
    private String templateName;

    /** 模板编码（唯一） */
    private String templateCode;

    /** 适用合同类型（CONSTRUCTION/MATERIAL/LABOR/MACHINE/SUBCONTRACT/OTHER） */
    private String contractCategory;

    /** 模板内容（含 ${xxx} 变量占位符） */
    private String templateContent;

    /** 可替换字段列表（JSON 数组格式：["projectName","partyAName",...])） */
    private String templateFields;

    /** 模板说明 */
    private String description;

    /** 状态（1-启用 0-停用） */
    private Integer status;

    /** 使用次数 */
    private Integer usageCount;
}
