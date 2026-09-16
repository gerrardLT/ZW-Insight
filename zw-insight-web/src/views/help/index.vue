<template>
  <div class="help-container">
    <!-- 业务术语词典 -->
    <el-card shadow="never" class="help-card" data-testid="help-terms">
      <template #header>
        <div class="card-header">
          <span class="card-title">业务术语</span>
          <span class="card-sub">与系统实际校验/催办逻辑一致（术语解释同步自后端实现）</span>
        </div>
      </template>
      <div
        v-for="term in TERMS"
        :key="term.id"
        class="term-item"
        :data-testid="'help-term-' + term.id"
      >
        <div class="term-head">
          <span class="term-name">{{ term.name }}</span>
          <el-tag v-if="term.tag" size="small" type="info" effect="plain">{{ term.tag }}</el-tag>
        </div>
        <p class="term-definition">{{ term.definition }}</p>
        <p class="term-where">相关页面：{{ term.where }}</p>
      </div>
    </el-card>

    <!-- 键盘快捷键 -->
    <el-card shadow="never" class="help-card" data-testid="help-shortcuts">
      <template #header>
        <div class="card-header">
          <span class="card-title">键盘快捷键</span>
          <span class="card-sub">键位与 src/composables/useShortcuts.ts 注册保持一致</span>
        </div>
      </template>
      <el-table :data="SHORTCUTS" size="default" data-testid="help-shortcut-table">
        <el-table-column prop="action" label="操作" min-width="160" />
        <el-table-column label="键位" min-width="200">
          <template #default="{ row }">
            <template v-for="(k, i) in row.keys" :key="k">
              <span v-if="i > 0" class="key-sep">或</span><kbd>{{ k }}</kbd>
            </template>
          </template>
        </el-table-column>
        <el-table-column prop="scope" label="生效范围" min-width="240" />
      </el-table>
    </el-card>

    <!-- 错误码字典 -->
    <el-card shadow="never" class="help-card" data-testid="help-errorcodes">
      <template #header>
        <div class="card-header">
          <span class="card-title">错误码字典</span>
          <span class="card-sub">与后端 GlobalExceptionHandler 及全局响应拦截器行为一致</span>
        </div>
      </template>
      <el-table :data="ERROR_CODES" size="default" data-testid="help-errorcode-table">
        <el-table-column prop="code" label="状态码" width="100">
          <template #default="{ row }">
            <span class="code-badge" :class="row.level">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="meaning" label="含义" min-width="220" />
        <el-table-column prop="behavior" label="系统行为 / 建议操作" min-width="300" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 帮助中心（Phase 1.3）：业务术语词典 + 键盘快捷键 + 错误码字典。
 *
 * 内容为静态产品文档（非业务数据，不接后端接口），但每条内容均锚定真实实现，
 * 修改对应实现时须同步更新：
 * - 术语定义 ← zw-budget/BudgetControlConfigService、zw-finance/RetentionWarningTask
 *   与 PaymentApplyService、zw-dashboard/DashboardService（垫资口径）
 * - 快捷键 ← src/composables/useShortcuts.ts
 * - 错误码 ← zw-common/GlobalExceptionHandler + src/utils/request.ts 响应拦截器
 */

interface HelpTerm {
  id: string
  name: string
  tag?: string
  definition: string
  where: string
}

const TERMS: HelpTerm[] = [
  {
    id: 'budget-block',
    name: '预算 BLOCK 拦截',
    tag: '预算管控',
    definition:
      '项目预算管控的一种模式。BLOCK 模式下，科目未设置预算额度、或预算执行率超过 100% 时，该科目的支出合同保存/付款申请会被直接拒绝（提示「已超预算」）。执行率 =（已签合同金额 + 已审批付款 + 本次新增金额）÷ 预算额度 × 100%；执行率达到预警阈值（50%–99% 可配）时发送站内信预警。对比：WARN_ONLY 模式仅预警不拦截，EXEMPT 模式不做校验。',
    where: '预算管理、成本主线看板、各支出合同新增页'
  },
  {
    id: 'retention-overdue',
    name: '质保金逾期',
    tag: '财务催办',
    definition:
      '质保金到期日早于今天且尚未返还（状态为「未返还」）即视为逾期。系统按催办任务定期发送逾期提醒；逾期超过设定天数后标记为长期逾期并停止催办，需在质保金管理页人工处理返还。',
    where: '财务管理 › 质保金管理'
  },
  {
    id: 'advance-funding',
    name: '垫资',
    tag: '经营指标',
    definition:
      '公司先行垫付的资金规模，口径为：垫资 = 总支出 − 总收款。数值为正表示公司仍在为项目垫资；随回款登记推进，垫资相应减少。',
    where: '首页 › 公司概览'
  },
  {
    id: 'payable-limit',
    name: '累计结算与可付上限',
    tag: '付款校验',
    definition:
      '可付上限 = 累计结算金额 + 净奖惩 − 累计已付金额。提交付款申请时校验「付款金额 ≤ 可付上限」，超出将被拒绝并提示最大可付金额。付款申请单会保存提交当时的累计结算快照，供后续追溯。',
    where: '财务管理 › 付款申请'
  },
  {
    id: 'secondary-confirm',
    name: '二次确认（高危操作）',
    tag: '安全',
    definition:
      '删除等高危操作可能被后端拦截并要求二次确认（HTTP 449）：系统弹出密码输入框，确认后自动携带密码重发原请求。密码错误返回 403；连续错误次数过多将临时锁定（423）。取消输入即终止本次操作。',
    where: '启用二次确认的删除/变更入口'
  }
]

interface HelpShortcut {
  action: string
  keys: string[]
  scope: string
}

const SHORTCUTS: HelpShortcut[] = [
  {
    action: '唤起命令面板',
    keys: ['Ctrl + K', '⌘ + K'],
    scope: '全局任意位置（含输入框内）'
  },
  {
    action: '唤起命令面板',
    keys: ['/'],
    scope: '非输入态（聚焦输入框/文本域时不拦截，避免打断输入）'
  },
  {
    action: '保存当前表单/弹窗',
    keys: ['Ctrl + S', '⌘ + S'],
    scope: '拦截浏览器默认「保存页面」，通知当前表单执行保存'
  },
  {
    action: '提交当前弹窗表单',
    keys: ['Ctrl + Enter', '⌘ + Enter'],
    scope: '弹窗内（与表单底部按钮提示一致）'
  },
  {
    action: '关闭浮层',
    keys: ['Esc'],
    scope: '命令面板等自绘浮层（Element 弹窗由自身关闭逻辑处理）'
  }
]

interface HelpErrorCode {
  code: number
  level: 'ok' | 'warn' | 'error'
  meaning: string
  behavior: string
}

const ERROR_CODES: HelpErrorCode[] = [
  {
    code: 200,
    level: 'ok',
    meaning: '成功',
    behavior: '正常返回数据'
  },
  {
    code: 400,
    level: 'warn',
    meaning: '参数校验失败，或删除被引用校验拦截',
    behavior: '顶部提示具体原因；删除拦截会列出被引用对象，请先处理引用数据'
  },
  {
    code: 401,
    level: 'error',
    meaning: '未登录或登录已过期',
    behavior: '自动清除凭证并跳转登录页，重新登录后继续操作'
  },
  {
    code: 403,
    level: 'error',
    meaning: '无数据权限，或二次确认密码错误',
    behavior: '提示具体原因；密码错误可重新输入，请确认账号权限范围'
  },
  {
    code: 404,
    level: 'warn',
    meaning: '资源未找到',
    behavior: '检查访问对象是否已被删除或链接是否有效'
  },
  {
    code: 405,
    level: 'warn',
    meaning: '请求方法不支持',
    behavior: '接口方法不匹配，通常由页面版本不一致引起，刷新页面重试'
  },
  {
    code: 413,
    level: 'warn',
    meaning: '文件大小超过上限（最大 100MB）',
    behavior: '压缩或拆分文件后重新上传'
  },
  {
    code: 423,
    level: 'error',
    meaning: '二次确认密码错误次数过多，临时锁定',
    behavior: '等待锁定解除后重试，或联系管理员'
  },
  {
    code: 449,
    level: 'warn',
    meaning: '高危操作需二次确认',
    behavior: '弹出密码输入框，确认后自动重发请求；取消则终止本次操作'
  },
  {
    code: 500,
    level: 'error',
    meaning: '业务异常或系统内部错误',
    behavior: '提示具体业务原因；若为「系统内部错误」请稍后重试或联系管理员（详见操作日志）'
  }
]
</script>

<style scoped>
.help-container {
  max-width: var(--zw-content-max-width);
  margin: 0 auto;
  padding: var(--zw-space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}

.help-card {
  border-radius: var(--zw-radius-sm);
}

.card-header {
  display: flex;
  align-items: baseline;
  gap: var(--zw-space-sm);
}

.card-title {
  font-size: var(--zw-font-size-md);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.card-sub {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

/* ===== 业务术语 ===== */
.term-item {
  padding: var(--zw-space-md) 0;
  border-bottom: 1px solid var(--zw-border-light);
}

.term-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.term-head {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
}

.term-name {
  font-size: var(--zw-font-size-base);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.term-definition {
  margin: var(--zw-space-xs) 0;
  font-size: var(--zw-font-size-sm);
  line-height: var(--zw-line-height-relaxed);
  color: var(--zw-text-secondary);
}

.term-where {
  margin: 0;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

/* ===== 快捷键 ===== */
kbd {
  font-family: var(--zw-font-mono);
  font-size: var(--zw-font-size-xs);
  padding: var(--zw-space-2xs) var(--zw-space-sm);
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-sm);
  background: var(--zw-bg-hover);
  color: var(--zw-text-secondary);
  white-space: nowrap;
}

.key-sep {
  margin: 0 var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

/* ===== 错误码 ===== */
.code-badge {
  display: inline-block;
  min-width: 44px;
  padding: var(--zw-space-2xs) var(--zw-space-sm);
  text-align: center;
  font-family: var(--zw-font-mono);
  font-size: var(--zw-font-size-sm);
  border-radius: var(--zw-radius-xs);
  border: 1px solid var(--zw-border);
}

.code-badge.ok {
  color: var(--zw-success);
  border-color: var(--zw-success);
}

.code-badge.warn {
  color: var(--zw-warning);
  border-color: var(--zw-warning);
}

.code-badge.error {
  color: var(--zw-danger);
  border-color: var(--zw-danger);
}
</style>
