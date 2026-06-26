package com.zwinsight.file.template;

import lombok.Data;

import java.util.Map;

/**
 * 打印模板渲染 / PDF 导出请求体。
 *
 * <p>渲染契约：调用方传入 {@code templateId} 与真实业务数据变量 {@code variables}。
 * {@code businessDataId} 为可选字段，用于标识业务数据来源；按业务类型自动装载数据
 * 涉及各业务模块的具体接线，作为后续增强，当前以调用方传入的 {@code variables} 为准。</p>
 */
@Data
public class PrintRenderRequest {

    /**
     * 模板 ID（必填）
     */
    private Long templateId;

    /**
     * 业务数据 ID（可选，标识业务数据记录）
     */
    private Long businessDataId;

    /**
     * 业务数据变量 Map（模板中引用的变量名 -> 真实值）
     */
    private Map<String, Object> variables;
}
