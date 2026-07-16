/* eslint-disable @typescript-eslint/no-explicit-any */
import type { ApiClient } from './api-client'

/**
 * 测试数据常量 + 清理工具
 * 所有测试数据带 E2E_TEST_ 前缀 + 时间戳，避免与正式数据冲突
 */

const TS = Date.now()

// ============ 通用前缀 ============
export const PREFIX = `E2E_TEST_${TS}`

// ============ 项目报备 ============
export const TEST_PROJECT = {
  name: `${PREFIX}_测试项目`,
  code: `${PREFIX}_PRJ`,
  address: '测试地址_自动化',
  description: 'E2E 自动测试创建的项目',
  projectType: 'BUILDING',
  status: 'DRAFT',
}

// ============ 投标管理 ============
export const TEST_TENDER = {
  projectName: `${PREFIX}_投标项目`,
  tenderCode: `${PREFIX}_TND`,
}

// ============ 合同管理 ============
export const TEST_CONTRACT = {
  name: `${PREFIX}_施工合同`,
  code: `${PREFIX}_CTR`,
  contractType: 'CONSTRUCTION',
  amount: 1000000,
}

// ============ 预算管理 ============
export const TEST_BUDGET = {
  name: `${PREFIX}_目标成本`,
}

// ============ 采购管理 ============
export const TEST_PURCHASE = {
  contractName: `${PREFIX}_采购合同`,
  inquiryTitle: `${PREFIX}_询价公告`,
}

// ============ 劳务管理 ============
export const TEST_LABOR = {
  contractName: `${PREFIX}_劳务合同`,
  teamName: `${PREFIX}_班组`,
}

// ============ 材料库存 ============
export const TEST_MATERIAL = {
  name: `${PREFIX}_测试材料`,
  code: `${PREFIX}_MAT`,
}

// ============ 机械管理 ============
export const TEST_MACHINE = {
  contractName: `${PREFIX}_机械合同`,
  ledgerName: `${PREFIX}_机械台账`,
}

// ============ 分包管理 ============
export const TEST_SUBCONTRACT = {
  contractName: `${PREFIX}_分包合同`,
}

// ============ 现场管理 ============
export const TEST_SITE = {
  scheduleName: `${PREFIX}_进度计划`,
  logContent: `${PREFIX}_施工日志内容`,
}

// ============ 财务管理 ============
export const TEST_FINANCE = {
  invoiceAmount: 50000,
  paymentAmount: 30000,
}

// ============ 行政人事 ============
export const TEST_HR = {
  entryName: `${PREFIX}_入职申请`,
  supplyName: `${PREFIX}_办公用品`,
}

// ============ 基础数据 ============
export const TEST_BASEDATA = {
  companyName: `${PREFIX}_测试公司`,
  supplierName: `${PREFIX}_测试供应商`,
  ownerName: `${PREFIX}_甲方单位`,
  materialName: `${PREFIX}_材料字典`,
}

// ============ 系统管理 ============
export const TEST_SYSTEM = {
  orgName: `${PREFIX}_测试机构`,
  postName: `${PREFIX}_测试岗位`,
  userName: `${PREFIX}_testuser`,
  roleName: `${PREFIX}_测试角色`,
  dictType: `${PREFIX}_dict_type`,
}

// ============ 首页 ============
export const TEST_HOME = {
  shortcutName: `${PREFIX}_快捷入口`,
  announcementTitle: `${PREFIX}_测试公告`,
}

// ============ 数据清理器 ============

interface CleanupTask {
  label: string
  fn: () => Promise<any>
}

export class TestDataCleaner {
  private tasks: CleanupTask[] = []

  /** 注册清理任务（后注册的先执行） */
  add(label: string, fn: () => Promise<any>) {
    this.tasks.push({ label, fn })
  }

  /** 按注册顺序反向执行所有清理任务 */
  async cleanup(client: ApiClient) {
    const reversed = [...this.tasks].reverse()
    for (const task of reversed) {
      try {
        await task.fn()
      } catch (e: any) {
        console.warn(`[清理] ${task.label} 失败: ${e?.message || e}`)
      }
    }
    // 清空任务列表
    this.tasks = []
  }

  /** 清空任务列表（不执行） */
  clear() {
    this.tasks = []
  }
}
