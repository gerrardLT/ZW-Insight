/**
 * 字段一致性审核器 (FieldAuditor)
 *
 * 比对后端 Java 实体/DTO 字段与前端 Vue 组件 v-model 绑定字段，
 * 检测字段名不匹配、前端多余字段、必填字段前端缺失等问题。
 *
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5
 */

import * as fs from 'node:fs';
import type { JavaField, InconsistencyItem } from '../types.js';

/**
 * 驼峰命名转下划线命名
 * invoiceAmount → invoice_amount
 */
export function toSnakeCase(name: string): string {
  return name.replace(/([A-Z])/g, '_$1').toLowerCase();
}

/**
 * 下划线命名转驼峰命名
 * invoice_amount → invoiceAmount
 */
export function toCamelCase(name: string): string {
  return name.replace(/_([a-z])/g, (_, char) => char.toUpperCase());
}

/**
 * 规范化字段名
 * @param name - 字段名
 * @param convention - 目标命名约定：'camel' 驼峰 | 'snake' 下划线
 */
export function normalizeFieldName(name: string, convention: 'camel' | 'snake'): string {
  if (convention === 'snake') {
    return toSnakeCase(name);
  }
  return toCamelCase(name);
}

/**
 * 提取 Java 实体/DTO 类的字段信息
 *
 * 识别 private Type fieldName; 模式及字段上方的校验注解
 * @param filePath - Java 源文件路径
 * @returns JavaField 数组
 */
export function extractJavaFields(filePath: string): JavaField[] {
  const content = fs.readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  const fields: JavaField[] = [];

  // 用于匹配 private 字段声明
  // private Type fieldName;
  // private Type fieldName = ...;
  const fieldRegex = /^\s*private\s+(\S+)\s+(\w+)\s*[;=]/;

  // 用于匹配校验注解
  const annotationRegex = /^\s*@(NotNull|NotBlank|NotEmpty|Size|Min|Max|Pattern|Email|Valid)\b/;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const fieldMatch = line.match(fieldRegex);

    if (fieldMatch) {
      const fieldType = fieldMatch[1];
      const fieldName = fieldMatch[2];

      // 收集该字段上方的注解（向上搜索直到遇到非注解行）
      const annotations: string[] = [];
      let j = i - 1;
      while (j >= 0) {
        const prevLine = lines[j].trim();
        // 检查是否为注解行
        const annoMatch = prevLine.match(annotationRegex);
        if (annoMatch) {
          annotations.push(annoMatch[1]);
          j--;
        } else if (prevLine.startsWith('@') || prevLine === '' || prevLine.startsWith('//') || prevLine.startsWith('/*') || prevLine.startsWith('*')) {
          // 跳过其他注解、空行、注释
          j--;
        } else {
          break;
        }
      }

      const isRequired = annotations.includes('NotNull') || annotations.includes('NotBlank') || annotations.includes('NotEmpty');

      fields.push({
        fieldName,
        fieldType,
        annotations,
        isRequired,
      });
    }
  }

  return fields;
}

/**
 * 提取 Vue 组件中 v-model 绑定的字段名
 *
 * 支持以下模式：
 * - v-model="form.fieldName"
 * - v-model="formData.fieldName"
 * - v-model:value="form.fieldName"
 * - v-model:modelValue="form.fieldName"
 *
 * @param filePath - Vue 组件文件路径
 * @returns 字段名数组（去重后）
 */
export function extractVueModelFields(filePath: string): string[] {
  const content = fs.readFileSync(filePath, 'utf-8');

  // 匹配 v-model 各种写法中绑定到 form/formData 对象的字段
  // v-model="form.fieldName"
  // v-model="formData.fieldName"
  // v-model:value="form.fieldName"
  // v-model:modelValue="formData.fieldName"
  const vModelRegex = /v-model(?::\w+)?\s*=\s*"(?:form|formData)\.(\w+)"/g;

  const fields: Set<string> = new Set();
  let match: RegExpExecArray | null;

  while ((match = vModelRegex.exec(content)) !== null) {
    fields.add(match[1]);
  }

  return Array.from(fields);
}

/**
 * 字段一致性审核
 *
 * 规范化后比对前后端字段，产出不一致项列表。
 *
 * @param backendFields - 后端 Java 字段列表
 * @param frontendFields - 前端 v-model 绑定字段名列表
 * @param module - 业务模块名
 * @returns InconsistencyItem 数组
 */
export function auditFields(
  backendFields: JavaField[],
  frontendFields: string[],
  module: string
): InconsistencyItem[] {
  const inconsistencies: InconsistencyItem[] = [];

  // 将后端字段规范化为驼峰命名，建立映射
  const backendNormalizedMap = new Map<string, JavaField>();
  for (const field of backendFields) {
    const normalized = normalizeFieldName(field.fieldName, 'camel');
    backendNormalizedMap.set(normalized, field);
  }

  // 将前端字段规范化为驼峰命名
  const frontendNormalizedSet = new Set<string>();
  const frontendOriginalMap = new Map<string, string>(); // normalized → original
  for (const field of frontendFields) {
    const normalized = normalizeFieldName(field, 'camel');
    frontendNormalizedSet.add(normalized);
    frontendOriginalMap.set(normalized, field);
  }

  // 检查前端字段在后端是否存在
  for (const [normalizedFront, originalFront] of frontendOriginalMap) {
    if (!backendNormalizedMap.has(normalizedFront)) {
      // 前端多余字段
      inconsistencies.push({
        type: 'FIELD_EXTRA_FRONTEND',
        severity: 'Minor',
        module,
        description: `前端字段 "${originalFront}" 在后端 DTO 中不存在对应字段`,
        suggestion: `确认前端字段 "${originalFront}" 是否为后端未定义的冗余字段，或后端需补充该字段定义`,
      });
    } else {
      // 检查字段名是否完全一致（原始名不同说明命名风格不一致）
      const backendField = backendNormalizedMap.get(normalizedFront)!;
      if (backendField.fieldName !== originalFront && normalizedFront === normalizeFieldName(backendField.fieldName, 'camel')) {
        // 规范化后匹配但原始名不同 → 命名风格不一致
        if (backendField.fieldName !== originalFront) {
          inconsistencies.push({
            type: 'FIELD_NAME_MISMATCH',
            severity: 'Minor',
            module,
            description: `字段命名不一致：后端为 "${backendField.fieldName}"，前端为 "${originalFront}"（规范化后相同）`,
            suggestion: `统一字段命名风格，建议前端使用驼峰命名 "${normalizeFieldName(backendField.fieldName, 'camel')}"`,
          });
        }
      }
    }
  }

  // 检查后端必填字段在前端是否存在
  for (const [normalizedBack, field] of backendNormalizedMap) {
    if (field.isRequired && !frontendNormalizedSet.has(normalizedBack)) {
      inconsistencies.push({
        type: 'FIELD_REQUIRED_MISSING',
        severity: 'Major',
        module,
        description: `后端必填字段 "${field.fieldName}" (${field.annotations.join(', ')}) 在前端表单中未体现`,
        suggestion: `前端表单需要添加 "${normalizeFieldName(field.fieldName, 'camel')}" 字段并设置为必填`,
      });
    }
  }

  return inconsistencies;
}

/**
 * FieldAuditor 类
 *
 * 封装字段一致性审核的完整流程：
 * 1. 扫描后端 DTO/Entity 类字段
 * 2. 扫描前端 Vue 组件 v-model 绑定字段
 * 3. 执行驼峰/下划线转换后比对
 */
export class FieldAuditor {
  /**
   * 驼峰/下划线命名转换
   */
  normalizeFieldName = normalizeFieldName;

  /**
   * 提取 Java 实体类字段
   */
  extractJavaFields = extractJavaFields;

  /**
   * 提取 Vue 组件 v-model 绑定字段
   */
  extractVueModelFields = extractVueModelFields;

  /**
   * 执行字段一致性审核
   * @param backendFilePath - 后端 Java DTO/Entity 文件路径
   * @param frontendFilePath - 前端 Vue 组件文件路径
   * @param module - 业务模块名
   */
  audit(backendFilePath: string, frontendFilePath: string, module: string): InconsistencyItem[] {
    const backendFields = this.extractJavaFields(backendFilePath);
    const frontendFields = this.extractVueModelFields(frontendFilePath);
    return auditFields(backendFields, frontendFields, module);
  }
}
