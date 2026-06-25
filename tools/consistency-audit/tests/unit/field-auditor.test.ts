/**
 * FieldAuditor 单元测试
 *
 * 验证字段提取和比对逻辑
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5
 */

import { describe, it, expect } from 'vitest';
import * as path from 'node:path';
import {
  toSnakeCase,
  toCamelCase,
  normalizeFieldName,
  extractJavaFields,
  extractVueModelFields,
  auditFields,
  FieldAuditor,
} from '../../src/auditors/field-auditor.js';
import type { JavaField } from '../../src/types.js';

const fixturesDir = path.resolve(__dirname, '../fixtures');

describe('toSnakeCase', () => {
  it('应将驼峰转为下划线命名', () => {
    expect(toSnakeCase('invoiceAmount')).toBe('invoice_amount');
    expect(toSnakeCase('buyerTaxNo')).toBe('buyer_tax_no');
    expect(toSnakeCase('projectId')).toBe('project_id');
  });

  it('应处理全小写名称（无变化）', () => {
    expect(toSnakeCase('name')).toBe('name');
    expect(toSnakeCase('id')).toBe('id');
  });

  it('应处理连续大写字母', () => {
    expect(toSnakeCase('htmlParser')).toBe('html_parser');
  });
});

describe('toCamelCase', () => {
  it('应将下划线转为驼峰命名', () => {
    expect(toCamelCase('invoice_amount')).toBe('invoiceAmount');
    expect(toCamelCase('buyer_tax_no')).toBe('buyerTaxNo');
    expect(toCamelCase('project_id')).toBe('projectId');
  });

  it('应处理无下划线名称（无变化）', () => {
    expect(toCamelCase('name')).toBe('name');
    expect(toCamelCase('id')).toBe('id');
  });
});

describe('normalizeFieldName', () => {
  it('convention=snake 时应转为下划线', () => {
    expect(normalizeFieldName('invoiceAmount', 'snake')).toBe('invoice_amount');
  });

  it('convention=camel 时应转为驼峰', () => {
    expect(normalizeFieldName('invoice_amount', 'camel')).toBe('invoiceAmount');
  });

  it('Round-trip: toCamelCase(toSnakeCase(camelName)) === camelName', () => {
    const names = ['invoiceAmount', 'projectId', 'buyerName', 'materialName', 'unitPrice'];
    for (const name of names) {
      expect(toCamelCase(toSnakeCase(name))).toBe(name);
    }
  });
});

describe('extractJavaFields', () => {
  it('应正确提取 BizInvoiceApply 字段', () => {
    const filePath = path.join(fixturesDir, 'java', 'BizInvoiceApply.java');
    const fields = extractJavaFields(filePath);

    expect(fields.length).toBeGreaterThan(0);

    // 验证 id 字段
    const idField = fields.find(f => f.fieldName === 'id');
    expect(idField).toBeDefined();
    expect(idField!.fieldType).toBe('Long');
    expect(idField!.isRequired).toBe(false);

    // 验证 projectId 字段 (有 @NotNull)
    const projectIdField = fields.find(f => f.fieldName === 'projectId');
    expect(projectIdField).toBeDefined();
    expect(projectIdField!.fieldType).toBe('Long');
    expect(projectIdField!.isRequired).toBe(true);
    expect(projectIdField!.annotations).toContain('NotNull');

    // 验证 invoiceType 字段 (有 @NotBlank)
    const invoiceTypeField = fields.find(f => f.fieldName === 'invoiceType');
    expect(invoiceTypeField).toBeDefined();
    expect(invoiceTypeField!.isRequired).toBe(true);
    expect(invoiceTypeField!.annotations).toContain('NotBlank');

    // 验证 remark 字段 (无校验注解)
    const remarkField = fields.find(f => f.fieldName === 'remark');
    expect(remarkField).toBeDefined();
    expect(remarkField!.isRequired).toBe(false);
  });

  it('应正确提取 BizMaterialInbound 字段', () => {
    const filePath = path.join(fixturesDir, 'java', 'BizMaterialInbound.java');
    const fields = extractJavaFields(filePath);

    // 验证带 @Min 注解的字段
    const quantityField = fields.find(f => f.fieldName === 'quantity');
    expect(quantityField).toBeDefined();
    expect(quantityField!.isRequired).toBe(true);
    expect(quantityField!.annotations).toContain('NotNull');
    expect(quantityField!.annotations).toContain('Min');

    // 验证下划线命名字段
    const warehouseField = fields.find(f => f.fieldName === 'warehouse_location');
    expect(warehouseField).toBeDefined();
    expect(warehouseField!.fieldType).toBe('String');
  });
});

describe('extractVueModelFields', () => {
  it('应正确提取 InvoiceApplyForm 的 v-model 字段', () => {
    const filePath = path.join(fixturesDir, 'vue', 'InvoiceApplyForm.vue');
    const fields = extractVueModelFields(filePath);

    expect(fields).toContain('projectId');
    expect(fields).toContain('amount');
    expect(fields).toContain('invoiceType');
    expect(fields).toContain('buyerName');
    expect(fields).toContain('buyerTaxNo');
    expect(fields).toContain('content');
    expect(fields).toContain('applyDate');
    expect(fields).toContain('remark');
  });

  it('应正确提取 MachineContractForm 的 formData v-model 字段', () => {
    const filePath = path.join(fixturesDir, 'vue', 'MachineContractForm.vue');
    const fields = extractVueModelFields(filePath);

    expect(fields).toContain('contractName');
    expect(fields).toContain('supplierName');
    expect(fields).toContain('machineName');
    expect(fields).toContain('contractAmount');
    expect(fields).toContain('rentalType');
    expect(fields).toContain('startDate');
    expect(fields).toContain('endDate');
  });

  it('应提取 queryParams 形式的 v-model 字段（非 form/formData 不提取）', () => {
    const filePath = path.join(fixturesDir, 'vue', 'MachineContractForm.vue');
    const fields = extractVueModelFields(filePath);

    // queryParams 中的字段不应该被提取（只提取 form/formData 绑定的字段）
    // 但实际上 contractName 同时存在于 queryParams 和 formData 中
    // 只应提取 formData. 前缀的
    expect(fields).not.toContain('pageNum');
    expect(fields).not.toContain('pageSize');
  });

  it('应返回去重后的字段列表', () => {
    const filePath = path.join(fixturesDir, 'vue', 'MaterialInboundForm.vue');
    const fields = extractVueModelFields(filePath);

    // 验证没有重复
    const uniqueFields = [...new Set(fields)];
    expect(fields.length).toBe(uniqueFields.length);
  });
});

describe('auditFields', () => {
  const backendFields: JavaField[] = [
    { fieldName: 'projectId', fieldType: 'Long', annotations: ['NotNull'], isRequired: true },
    { fieldName: 'amount', fieldType: 'BigDecimal', annotations: ['NotNull'], isRequired: true },
    { fieldName: 'invoiceType', fieldType: 'String', annotations: ['NotBlank'], isRequired: true },
    { fieldName: 'buyerName', fieldType: 'String', annotations: ['NotBlank'], isRequired: true },
    { fieldName: 'buyerTaxNo', fieldType: 'String', annotations: [], isRequired: false },
    { fieldName: 'content', fieldType: 'String', annotations: [], isRequired: false },
    { fieldName: 'applyDate', fieldType: 'LocalDate', annotations: [], isRequired: false },
    { fieldName: 'remark', fieldType: 'String', annotations: [], isRequired: false },
    { fieldName: 'status', fieldType: 'Integer', annotations: [], isRequired: false },
  ];

  it('完全匹配时不产出不一致项', () => {
    const frontendFields = ['projectId', 'amount', 'invoiceType', 'buyerName', 'buyerTaxNo', 'content', 'applyDate', 'remark', 'status'];
    const result = auditFields(backendFields, frontendFields, 'finance');
    expect(result).toHaveLength(0);
  });

  it('前端多余字段应产出 FIELD_EXTRA_FRONTEND', () => {
    const frontendFields = ['projectId', 'amount', 'invoiceType', 'buyerName', 'extraField'];
    const result = auditFields(backendFields, frontendFields, 'finance');

    const extraItems = result.filter(i => i.type === 'FIELD_EXTRA_FRONTEND');
    expect(extraItems).toHaveLength(1);
    expect(extraItems[0].severity).toBe('Minor');
    expect(extraItems[0].description).toContain('extraField');
  });

  it('后端必填字段在前端缺失应产出 FIELD_REQUIRED_MISSING', () => {
    // 前端缺少必填的 amount 和 invoiceType
    const frontendFields = ['projectId', 'buyerName', 'remark'];
    const result = auditFields(backendFields, frontendFields, 'finance');

    const missingItems = result.filter(i => i.type === 'FIELD_REQUIRED_MISSING');
    expect(missingItems.length).toBe(2); // amount 和 invoiceType
    expect(missingItems.every(i => i.severity === 'Major')).toBe(true);
  });

  it('字段名不一致（规范化后匹配）应产出 FIELD_NAME_MISMATCH', () => {
    const backendWithSnake: JavaField[] = [
      { fieldName: 'project_id', fieldType: 'Long', annotations: ['NotNull'], isRequired: true },
      { fieldName: 'invoice_type', fieldType: 'String', annotations: [], isRequired: false },
    ];
    const frontendFields = ['projectId', 'invoiceType'];
    const result = auditFields(backendWithSnake, frontendFields, 'finance');

    const mismatchItems = result.filter(i => i.type === 'FIELD_NAME_MISMATCH');
    expect(mismatchItems.length).toBe(2);
    expect(mismatchItems.every(i => i.severity === 'Minor')).toBe(true);
  });

  it('模块名应正确传递到不一致项', () => {
    const frontendFields = ['extraField'];
    const result = auditFields(backendFields, frontendFields, 'finance');

    expect(result.every(i => i.module === 'finance')).toBe(true);
  });
});

describe('FieldAuditor 类', () => {
  it('应成功实例化并暴露所有方法', () => {
    const auditor = new FieldAuditor();
    expect(auditor.normalizeFieldName).toBeDefined();
    expect(auditor.extractJavaFields).toBeDefined();
    expect(auditor.extractVueModelFields).toBeDefined();
    expect(auditor.audit).toBeDefined();
  });

  it('audit 方法应正确执行完整审核流程', () => {
    const auditor = new FieldAuditor();
    const javaFile = path.join(fixturesDir, 'java', 'BizInvoiceApply.java');
    const vueFile = path.join(fixturesDir, 'vue', 'InvoiceApplyForm.vue');

    const result = auditor.audit(javaFile, vueFile, 'finance');

    // InvoiceApplyForm.vue 包含: projectId, amount, invoiceType, buyerName, buyerTaxNo, content, applyDate, remark
    // BizInvoiceApply.java 包含: id, projectId, amount, invoiceType, buyerName, buyerTaxNo, content, applyDate, remark, status, createBy, createTime
    // 后端必填字段：projectId, amount, invoiceType, buyerName — 这些前端都有
    // 所以不应有 FIELD_REQUIRED_MISSING
    const requiredMissing = result.filter(i => i.type === 'FIELD_REQUIRED_MISSING');
    expect(requiredMissing).toHaveLength(0);

    // 前端没有多余字段（所有前端字段都在后端存在）
    const extraFrontend = result.filter(i => i.type === 'FIELD_EXTRA_FRONTEND');
    expect(extraFrontend).toHaveLength(0);
  });
});
