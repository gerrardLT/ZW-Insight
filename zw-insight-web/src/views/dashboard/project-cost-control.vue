<template>
  <div class="project-cost-control-container">
    <!-- 顶部项目选择器 -->
    <el-card shadow="never" class="selector-card">
      <div class="selector-bar">
        <span class="selector-label">选择项目：</span>
        <ProjectSelector
          v-model="selectedProjectId"
          width="320px"
          @change="handleProjectChange"
        />
      </div>
    </el-card>

    <!-- 未选择项目时的引导提示（2026-09-16 插画：空白图纸+圆规——「等待绘制」隐喻） -->
    <el-empty v-if="!selectedProjectId" description="请先选择一个项目以查看成本控制看板数据">
      <template #image>
        <img :src="emptyImg" class="zw-empty-img" alt="" />
      </template>
    </el-empty>

    <!-- KPI 指标卡片区域 -->
    <template v-else>
      <el-row :gutter="16" class="kpi-row">
        <el-col :span="6" v-for="metric in kpiMetrics" :key="metric.key">
          <el-card shadow="hover" class="kpi-card">
            <div class="kpi-content">
              <div class="kpi-label">{{ metric.label }}</div>
              <div class="kpi-value">{{ formatAmount(metric.value) }}</div>
              <div class="kpi-subtitle">{{ metric.subtitle }}</div>
            </div>
          </el-card>
        </el-col>
      <!-- 变更与待办：成本数字会变，这里告诉用户「为什么即将变」与「谁在等处理」 -->
      <el-row :gutter="16" class="change-row">
        <el-col :span="24">
          <el-card shadow="never" class="change-card" v-loading="changeLoading">
            <div class="change-strip">
              <div class="change-item">
                <span class="c-label">待处理变更事件</span>
                <span class="c-value" :class="{ 'is-alert': openChangeCount > 0 }">{{ openChangeCount }}</span>
                <span class="c-hint">草稿 / 评估中 / 审批中</span>
              </div>
              <div class="change-item">
                <span class="c-label">已批准变更累计成本影响</span>
                <span class="c-value" :class="approvedChangeDelta > 0 ? 'is-danger' : approvedChangeDelta < 0 ? 'is-success' : ''">{{ approvedChangeDelta > 0 ? '+' : '-' }}{{ formatAmount(approvedChangeDelta) }}</span>
                <span class="c-hint">已传导至「当前预算」</span>
              </div>
              <div class="change-item">
                <span class="c-label">预测超支账户</span>
                <span class="c-value" :class="{ 'is-alert': overrunAccounts.length > 0 }">{{ overrunAccounts.length }}</span>
                <span class="c-hint">完工预测 &gt; 当前预算</span>
              </div>
              <div class="change-item">
                <span class="c-label">实际已超支账户</span>
                <span class="c-value" :class="{ 'is-alert': actualOverrunAccounts.length > 0 }">{{ actualOverrunAccounts.length }}</span>
                <span class="c-hint">实际成本 &gt; 当前预算</span>
              </div>
              <div class="change-actions">
                <el-button type="primary" plain size="small" @click="goChangeEvents">处理变更事件</el-button>
                <el-button size="small" :icon="Refresh" @click="loadAll">刷新</el-button>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      </el-row>

      <!-- 经营视角 + TOP 偏差（V2026_59）：成本页回答“花了多少”，这里回答“还赚不赚钱、哪里超了” -->
      <el-row :gutter="16" class="biz-row">
        <el-col :span="10">
          <el-card shadow="never" class="biz-card" v-loading="bizLoading">
            <template #header>
              <div class="card-header">
                <span class="card-title">经营视角</span>
                <span class="health-badge" :class="healthMeta.cls">{{ healthMeta.text }}</span>
              </div>
            </template>
            <template v-if="bizHealth">
              <div class="biz-metrics">
                <div class="biz-item">
                  <span class="b-label">合同收入</span>
                  <span class="b-value">{{ formatAmount(bizHealth.contractIncome) }}</span>
                </div>
                <div class="biz-item">
                  <span class="b-label">预计总成本</span>
                  <span class="b-value">{{ formatAmount(bizHealth.forecastTotalCost) }}</span>
                </div>
                <div class="biz-item">
                  <span class="b-label">预计利润</span>
                  <span class="b-value" :class="{ 'is-danger': Number(bizHealth.forecastProfit) < 0 }">
                    {{ formatAmount(bizHealth.forecastProfit) }}
                  </span>
                </div>
                <div class="biz-item">
                  <span class="b-label">预计利润率</span>
                  <span class="b-value">{{ ((Number(bizHealth.profitRate) || 0) * 100).toFixed(1) }}%</span>
                </div>
              </div>
              <!-- 口径如实告知：无 CBS 时“预计”退化为已实现，不能让用户误读 -->
              <el-alert v-if="bizHealth.costBasis === 'FALLBACK_TOTAL_EXPENSE'" type="warning" :closable="false"
                show-icon class="biz-alert"
                title="本项目未建 CBS 成本账户，“预计总成本”已退化为已实现支出，不具备预测意义" />
              <el-alert v-else-if="bizHealth.incomeBasis === 'PROJECT_CONTRACT_AMOUNT'" type="info" :closable="false"
                show-icon class="biz-alert"
                title="无生效施工合同，合同收入已回退为项目合同额" />
              <div v-if="bizHealth.topRisks?.length" class="biz-risks">
                <div class="biz-risks-title">
                  <span>TOP 风险（🔴{{ bizHealth.redCount }}　🟡{{ bizHealth.yellowCount }}）</span>
                  <el-button link type="primary" size="small" @click="goRiskCenter">风险中心 →</el-button>
                </div>
                <div v-for="(t, i) in bizHealth.topRisks" :key="i" class="biz-risk-item">{{ t }}</div>
              </div>
            </template>
            <el-empty v-else-if="!bizLoading"
              description="无经营快照（项目状态不在预计利润计算范围，或成本/合同数据未建立）"
              :image-size="50" />
          </el-card>
        </el-col>
        <el-col :span="14">
          <el-card shadow="never" class="biz-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">TOP 成本偏差</span>
                <span class="card-hint">按超支额降序，最多 8 条；点击下钻至成本账户</span>
              </div>
            </template>
            <div v-if="riskItems.length" class="deviation-list">
              <div v-for="item in riskItems" :key="item.level + item.code" class="deviation-item"
                @click="goAccountByCode(item.code)">
                <el-tag :type="item.level === 'high' ? 'danger' : 'warning'" size="small" effect="plain">
                  {{ item.level === 'high' ? '已超支' : '预测超支' }}
                </el-tag>
                <span class="d-code">{{ item.code }}</span>
                <span class="d-name">{{ item.name }}</span>
                <span class="d-detail">{{ item.detail }}</span>
                <span class="d-over">+{{ formatAmount(item.overAmount) }}</span>
              </div>
            </div>
            <el-empty v-else description="无超支账户（实际与预测均未超当前预算）" :image-size="50" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 主内容区：左侧树形筛选 + 右侧成本账户表格 -->
      <el-row :gutter="16" class="content-row">
        <!-- CBS/WBS 树形筛选 -->
        <el-col :span="8">
          <el-card shadow="never" class="tree-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">CBS/WBS 成本结构</span>
                <el-button type="text" @click="expandAllNodes">展开全部</el-button>
              </div>
            </template>
            <div v-loading="costData.loading" class="tree-body">
              <el-alert
                v-if="costData.error"
                :title="costData.error"
                type="error"
                show-icon
                :closable="false"
              />
              <el-tree
                v-else
                ref="treeRef"
                :data="treeData"
                :props="{ children: 'children', label: 'name' }"
                node-key="accountId"
                highlight-current
                :expand-on-click-node="false"
                @node-click="handleTreeNodeClick"
              >
                <template #default="{ node, data }">
                  <div class="custom-tree-node">
                    <span class="node-label">{{ data.code }} - {{ data.name }}</span>
                    <span class="node-amount">{{ formatAmount(data.current || 0) }}</span>
                  </div>
                </template>
              </el-tree>
            </div>
          </el-card>
        </el-col>

        <!-- 成本账户明细表 -->
        <el-col :span="16">
          <el-card shadow="never" class="table-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">成本账户明细</span>
                <div class="header-actions">
                  <el-input
                    v-model="searchQuery"
                    placeholder="搜索账户编码/名称"
                    prefix-icon="Search"
                    clearable
                    style="width: 200px"
                  />
                  <el-button type="primary" :icon="Refresh" @click="loadCostData">刷新</el-button>
                </div>
              </div>
            </template>
            <div v-loading="costData.loading" class="table-body">
              <el-alert
                v-if="costData.error"
                :title="costData.error"
                type="error"
                show-icon
                :closable="false"
              />
              <el-table
                v-else-if="!costData.loading && costData.data?.accounts?.length"
                :data="filteredAccounts"
                style="width: 100%"
                border
                stripe
                :max-height="500"
              >
                <el-table-column prop="code" label="编码" width="100" fixed="left" />
                <el-table-column prop="name" label="名称" min-width="150" />
                <el-table-column prop="baseline" label="基准预算" width="120" align="right">
                  <template #default="scope">{{ formatAmount(scope.row.baseline) }}</template>
                </el-table-column>
                <el-table-column prop="current" label="当前预算" width="120" align="right">
                  <template #default="scope">{{ formatAmount(scope.row.current) }}</template>
                </el-table-column>
                <el-table-column prop="commitment" label="已承诺" width="120" align="right">
                  <template #default="scope">{{ formatAmount(scope.row.commitment) }}</template>
                </el-table-column>
                <el-table-column prop="actual" label="实际成本" width="120" align="right">
                  <template #default="scope">{{ formatAmount(scope.row.actual) }}</template>
                </el-table-column>
                <el-table-column prop="forecast" label="预测 EAC" width="120" align="right">
                  <template #default="scope">{{ formatAmount(scope.row.forecast) }}</template>
                </el-table-column>
                <el-table-column prop="remaining" label="剩余预算" width="120" align="right">
                  <template #default="scope">{{ formatAmount(scope.row.remaining) }}</template>
                </el-table-column>
                <el-table-column prop="variance" label="偏差" width="100" align="right">
                  <template #default="scope">
                    <span :class="{ 'negative-variance': scope.row.variance < 0 }">
                      {{ formatAmount(scope.row.variance) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100" align="center">
                  <template #default="scope">
                    <el-tag :type="getStatusType(scope.row.status)" size="small">
                      {{ getStatusText(scope.row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" fixed="right" align="center">
                  <template #default="scope">
                    <el-button type="primary" size="small" link @click="viewAccountDetail(scope.row)">
                      详情
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无成本账户数据">
                <template #image>
                  <img :src="emptyImg" class="zw-empty-img" alt="" />
                </template>
              </el-empty>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import ProjectSelector from '@/components/ProjectSelector.vue'
import { getProjectCostControl, type ProjectCostControlDTO, type CostAccountSummary } from '@/api/dashboard'
import { getChangeEventOpenCount, getApprovedCostDelta } from '@/api/change-event'
import { getProjectHealth, type ProjectHealth } from '@/api/cockpit'
import { useAppStore } from '@/stores/app'
import emptyLight from '@/assets/empty-blueprint.png'
import emptyDark from '@/assets/empty-blueprint-dark.png'

const router = useRouter()
const appStore = useAppStore()
// 空状态插画随明暗主题切换（与 project-dashboard / StatChartPanel 同口径；
// 此前本页只引用未定义 emptyImg，导致 <img :src> 绑定 undefined 而显示破损图标）
const emptyImg = computed(() => (appStore.isDark ? emptyDark : emptyLight))

// 变更与待办：成本数字会变，这里告诉用户「为什么即将变」与「谁在等处理」
const changeLoading = ref(false)
const openChangeCount = ref(0)
const approvedChangeDelta = ref(0)

const overrunAccounts = computed(() => {
  const accounts: any[] = []
  costData.data?.accounts?.forEach((a: any) => {
    if (Number(a.forecast) > Number(a.current)) {
      accounts.push({ code: a.code, name: a.name, detail: `预测${formatAmount(a.forecast)} > 当前${formatAmount(a.current)}` })
    }
  })
  return accounts
})

const actualOverrunAccounts = computed(() => {
  const accounts: any[] = []
  costData.data?.accounts?.forEach((a: any) => {
    if (Number(a.actual) > Number(a.current)) {
      accounts.push({ code: a.code, name: a.name, detail: `实际${formatAmount(a.actual)} > 当前${formatAmount(a.current)}` })
    }
  })
  return accounts
})

/**
 * TOP 成本偏差清单（驾驶舱 V1 §6「TOP 成本偏差」）
 * <p>修正两个旧缺陷：① 同一账户实际与预测均超支时会被重复列入；
 * ② 未按超支额排序（文档要求 TOP）。现按超支额降序取前 8 条。</p>
 */
const riskItems = computed(() => {
  const items: { level: 'high' | 'low'; code: string; name: string; detail: string; overAmount: number }[] = []
  const accounts: any[] = costData.data?.accounts || []
  accounts.forEach((a: any) => {
    const current = Number(a.current) || 0
    const actual = Number(a.actual) || 0
    const forecast = Number(a.forecast) || 0
    const actualOver = actual - current
    if (actualOver > 0) {
      // 已实际超支（高优先）；不再重复计入预测超支
      items.push({
        level: 'high', code: a.code, name: a.name,
        detail: `实际${formatAmount(actual)} > 当前${formatAmount(current)}`,
        overAmount: actualOver
      })
      return
    }
    const forecastOver = forecast - current
    if (forecastOver > 0) {
      items.push({
        level: 'low', code: a.code, name: a.name,
        detail: `预测${formatAmount(forecast)} > 当前${formatAmount(current)}`,
        overAmount: forecastOver
      })
    }
  })
  return items.sort((x, y) => y.overAmount - x.overAmount).slice(0, 8)
})

// 经营视角（V2026_59）：预计利润/健康度由驾驶舱规则统一计算，本页只读呈现不重复实现口径
const bizLoading = ref(false)
const bizHealth = ref<ProjectHealth | null>(null)

/** 加载当前项目的经营视角（预计利润 + 健康度 + TOP 风险） */
async function loadBizView() {
  if (!selectedProjectId.value) {
    bizHealth.value = null
    return
  }
  bizLoading.value = true
  try {
    const res: any = await getProjectHealth()
    const list: ProjectHealth[] = res.data || []
    // 未命中（如项目状态不在快照范围）时为 null，模板展示空态而不伪造 0
    bizHealth.value = list.find(p => p.projectId === selectedProjectId.value) || null
  } catch (e: any) {
    bizHealth.value = null
    ElMessage.error(`加载经营视角失败：${e?.message || '接口异常'}`)
  } finally {
    bizLoading.value = false
  }
}

/** 健康度徽章文案（规则自动定级，不允许人工选） */
const healthMeta = computed(() => {
  const h = bizHealth.value?.health
  if (h === 'RED') return { text: '🔴 高风险', cls: 'health-red' }
  if (h === 'YELLOW') return { text: '🟡 关注', cls: 'health-yellow' }
  if (h === 'GREEN') return { text: '🟢 正常', cls: 'health-green' }
  return { text: '—', cls: '' }
})

/** 加载变更信号（待办数 + 累计影响）」 */
async function loadChangeSignals() {
  if (!selectedProjectId.value) {
    openChangeCount.value = 0
    approvedChangeDelta.value = 0
    return
  }
  changeLoading.value = true
  try {
    const [openRes, deltaRes] = await Promise.all([
      getChangeEventOpenCount(selectedProjectId.value),
      getApprovedCostDelta(selectedProjectId.value)
    ])
    openChangeCount.value = openRes.data || 0
    approvedChangeDelta.value = deltaRes.data || 0
  } catch (e: any) {
    ElMessage.error(`加载变更信号失败：${e?.message || '请稍后重试'}`)
  } finally {
    changeLoading.value = false
  }
}

/** 刷新全部数据（成本主线 + 变更信号 + 经营视角） */
async function loadAll() {
  await Promise.all([loadCostData(), loadChangeSignals(), loadBizView()])
}

/** TOP 偏差下钻：携账户编码跳成本账户页定位该账户 */
function goAccountByCode(code: string) {
  router.push({
    path: '/budget/cost-account',
    query: {
      projectId: selectedProjectId.value ? String(selectedProjectId.value) : undefined,
      code
    }
  })
}

/** 风险中心下钻（按项目过滤） */
function goRiskCenter() {
  router.push({
    path: '/cockpit/risk-center',
    query: { projectId: selectedProjectId.value ? String(selectedProjectId.value) : undefined }
  })
}

/** 直接跳转到变更事件列表 */
function goChangeEvents() {
  if (selectedProjectId.value) router.push({ path: '/contract/change-event', query: { projectId: selectedProjectId.value } })
}

const selectedProjectId = ref<number>()
const searchQuery = ref('')
const treeRef = ref<any>()

// 成本数据加载状态
const costData = reactive({
  loading: false,
  error: null as string | null,
  data: null as ProjectCostControlDTO | null
})

// KPI 指标
const kpiMetrics = computed(() => {
  const totals = costData.data?.totals || {}
  return [
    {
      key: 'baseline',
      label: '基准预算',
      value: totals.baselineTotal || 0,
      subtitle: '原始批准预算总额'
    },
    {
      key: 'current',
      label: '当前预算',
      value: totals.currentTotal || 0,
      subtitle: '经变更后预算总额'
    },
    {
      key: 'actual',
      label: '实际成本',
      value: totals.actualTotal || 0,
      subtitle: `使用率 ${(totals.usageRate || 0).toFixed(2)}%`
    },
    {
      key: 'forecast',
      label: '预测完工 (EAC)',
      value: totals.forecastTotal || 0,
      subtitle: `偏差 ${formatAmount(totals.varianceAmount || 0)}`
    }
  ]
})

// 过滤后的账户列表（支持搜索）
const filteredAccounts = computed(() => {
  const accounts = costData.data?.accounts || []
  if (!searchQuery.value) return accounts
  
  const query = searchQuery.value.toLowerCase()
  return accounts.filter(acc => 
    acc.code.toLowerCase().includes(query) || 
    acc.name.toLowerCase().includes(query)
  )
})

// 简单树构建
const treeData = computed(() => {
  const accounts = costData.data?.accounts || []
  
  // 扁平转树（简化版，仅根节点）
  const rootNodes: any[] = accounts.filter((a: any) => !a.parentId)
  return rootNodes.map((acc: any) => ({ ...acc, children: [] }))
})

// 格式化金额（万元单位）
function formatAmount(value: number | string | undefined | null): string {
  if (value === null || value === undefined || value === '') return '-'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '-'
  return (num / 10000).toFixed(2) + '万'
}

// 获取状态类型和文本
function getStatusType(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'success'
    case 'LOCKED': return 'warning'
    case 'CLOSED': return 'info'
    default: return ''
  }
}

function getStatusText(status: string): string {
  switch (status) {
    case 'ACTIVE': return '活跃'
    case 'LOCKED': return '锁定'
    case 'CLOSED': return '关闭'
    default: return status
  }
}

// 处理项目切换
function handleProjectChange(projectId: number | null) {
  selectedProjectId.value = projectId || undefined
  loadAll()
}

// 加载成本数据
async function loadCostData() {
  if (!selectedProjectId.value) return
  
  costData.loading = true
  costData.error = null
  
  try {
    const response = await getProjectCostControl(selectedProjectId.value!)
    costData.data = response.data
  } catch (error: any) {
    costData.error = error.message || '加载失败，请稍后重试'
  } finally {
    costData.loading = false
  }
}

// 处理树节点点击：将账户编码填入搜索框，联动右侧明细表过滤
function handleTreeNodeClick(data: any) {
  searchQuery.value = data?.code || ''
}

// 展开全部节点
function expandAllNodes() {
  treeRef.value?.expandAll(true)
}

// 查看详情：跳转成本账户 CBS 管理页（携带项目上下文，由目标页读取 query 初始化筛选）
function viewAccountDetail(account: CostAccountSummary) {
  router.push({ path: '/budget/cost-account', query: { projectId: selectedProjectId.value ? String(selectedProjectId.value) : undefined } })
}

// 监听项目 ID 变化
watch(selectedProjectId, (newVal) => {
  if (newVal) {
    loadAll()
  } else {
    costData.data = null
    costData.error = null
    openChangeCount.value = 0
    approvedChangeDelta.value = 0
    bizHealth.value = null
  }
})
</script>

<style scoped lang="scss">
.project-cost-control-container {
  padding: var(--zw-space-lg);
  background-color: var(--zw-bg-page);
  min-height: calc(100vh - var(--zw-header-height));
}

.selector-card {
  margin-bottom: var(--zw-space-md);
  
  .selector-bar {
    display: flex;
    align-items: center;
    gap: var(--zw-space-sm-md);
    
    .selector-label {
      font-weight: var(--zw-font-weight-medium);
      color: var(--zw-text-primary);
    }
  }
}

.kpi-row {
  margin-bottom: var(--zw-space-md);
  
  .kpi-card {
    height: 100%;
    
    .kpi-content {
      text-align: center;
      
      .kpi-label {
        font-size: var(--zw-font-size-sm);
        color: var(--zw-text-secondary);
        margin-bottom: var(--zw-space-xs);
      }
      
      .kpi-value {
        font-size: var(--zw-font-size-2xl);
        font-weight: var(--zw-font-weight-semibold);
        color: var(--zw-text-primary);
        margin-bottom: var(--zw-space-xs);
        font-family: var(--zw-font-mono);
      }
      
      .kpi-subtitle {
        font-size: var(--zw-font-size-xs);
        color: var(--zw-text-tertiary);
      }
    }
  }
}

.content-row {
  margin-bottom: var(--zw-space-md);
}

.tree-card, .table-card {
  height: 100%;
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .card-title {
      font-size: var(--zw-font-size-base);
      font-weight: var(--zw-font-weight-semibold);
      color: var(--zw-text-primary);
    }
    
    .header-actions {
      display: flex;
      gap: var(--zw-space-sm);
    }
  }
}

.tree-body {
  max-height: 500px;
  overflow-y: auto;
  
  .custom-tree-node {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
    padding-right: var(--zw-space-xs);
    
    .node-label {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    
    .node-amount {
      font-size: var(--zw-font-size-xs);
      color: var(--zw-text-tertiary);
      margin-left: var(--zw-space-xs);
      font-family: var(--zw-font-mono);
    }
  }
}

.table-body {
  .negative-variance {
    color: var(--zw-danger);
    font-weight: var(--zw-font-weight-medium);
  }
}
.change-row {
  margin-bottom: var(--zw-space-sm-md);
}

.change-card {
  .change-strip {
    display: flex;
    flex-wrap: wrap;
    gap: var(--zw-space-sm); padding: var(--zw-space-sm-md) 0;
    border-bottom: 1px solid var(--zw-border-light);
    margin-bottom: var(--zw-space-sm); align-items: center;
  }
}

.change-item {
  display: flex; flex-direction: column; align-items: flex-start; gap: 2px;
  min-width: 160px; flex: 1;
  .c-label { font-size: var(--zw-font-size-xs); color: var(--zw-text-tertiary); }
  .c-value { font-family: var(--zw-font-mono); font-size: var(--zw-font-size-lg); font-weight: var(--zw-font-weight-semibold); color: var(--zw-text-primary); }
  .c-hint { font-size: var(--zw-font-size-xs); color: var(--zw-text-quaternary); }
  .is-alert { color: var(--zw-danger); animation: blink 1.5s infinite alternate; @keyframes blink { from { opacity: 1 } to { opacity: .4 } }}
}

.change-actions { margin-left: auto; }

/* 经营视角 + TOP 偏差（V2026_59） */
.biz-row { margin-bottom: var(--zw-space-sm-md); }

.biz-card {
  height: 100%;
  .card-hint { font-size: var(--zw-font-size-xs); color: var(--zw-text-quaternary); font-weight: normal; }
}

.health-badge {
  font-size: var(--zw-font-size-xs);
  font-weight: var(--zw-font-weight-semibold);
  padding: var(--zw-space-xs) var(--zw-space-sm);
  border-radius: var(--zw-radius-sm);
  &.health-red { color: var(--zw-danger); background-color: color-mix(in srgb, var(--zw-danger) 10%, transparent); }
  &.health-yellow { color: var(--zw-warning); background-color: color-mix(in srgb, var(--zw-warning) 12%, transparent); }
  &.health-green { color: var(--zw-success); background-color: color-mix(in srgb, var(--zw-success) 10%, transparent); }
}

.biz-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zw-space-sm-md);
}

.biz-item {
  display: flex; flex-direction: column; gap: var(--zw-space-xs);
  .b-label { font-size: var(--zw-font-size-xs); color: var(--zw-text-tertiary); }
  .b-value {
    font-family: var(--zw-font-mono); font-size: var(--zw-font-size-lg);
    font-weight: var(--zw-font-weight-semibold); color: var(--zw-text-primary);
    &.is-danger { color: var(--zw-danger); }
  }
}

.biz-alert { margin-top: var(--zw-space-sm); }

.biz-risks {
  margin-top: var(--zw-space-sm);
  padding-top: var(--zw-space-sm);
  border-top: 1px dashed var(--zw-border-light);
  .biz-risks-title {
    display: flex; align-items: center; justify-content: space-between;
    font-size: var(--zw-font-size-sm); font-weight: var(--zw-font-weight-semibold);
    color: var(--zw-text-secondary); margin-bottom: var(--zw-space-xs);
  }
  .biz-risk-item {
    font-size: var(--zw-font-size-xs); color: var(--zw-text-tertiary);
    padding: var(--zw-space-xs) 0;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
}

.deviation-list { max-height: 260px; overflow-y: auto; }

.deviation-item {
  display: flex; align-items: center; gap: var(--zw-space-sm);
  padding: var(--zw-space-sm) 0;
  border-bottom: 1px solid var(--zw-border-light);
  cursor: pointer;
  font-size: var(--zw-font-size-sm);
  &:last-child { border-bottom: none; }
  &:hover { background-color: var(--zw-bg-hover); }
  .d-code { font-family: var(--zw-font-mono); color: var(--zw-text-tertiary); min-width: 70px; }
  .d-name { font-weight: var(--zw-font-weight-medium); color: var(--zw-text-primary); min-width: 110px; }
  .d-detail {
    flex: 1; min-width: 0; font-size: var(--zw-font-size-xs); color: var(--zw-text-quaternary);
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }
  .d-over {
    font-family: var(--zw-font-mono); font-weight: var(--zw-font-weight-semibold);
    color: var(--zw-danger); white-space: nowrap;
  }
}

.risk-list {
  max-height: 200px; overflow-y: auto; margin-top: var(--zw-space-sm);
  .risk-title { font-size: var(--zw-font-size-sm); font-weight: var(--zw-font-weight-semibold); color: var(--zw-text-secondary); margin-bottom: var(--zw-space-sm); border-bottom: 1px dashed var(--zw-border-light); padding-bottom: 4px; }
  .risk-item {
    display: inline-flex; align-items: center; gap: 8px; margin: 0 8px 8px 0; padding: 6px 12px; background: var(--zw-bg-hover); border-radius: 6px; font-size: var(--zw-font-size-sm); cursor: default;
    &.high { border: 1px solid var(--zw-danger-light); background-color: color-mix(in srgb, var(--zw-danger) 6%, transparent); }
    &.low { border: 1px solid var(--zw-warning-light); background-color: color-mix(in srgb, var(--zw-warning) 6%, transparent); }
    .risk-account { font-weight: var(--zw-font-weight-medium); color: var(--zw-text-primary); }
    .risk-detail { font-size: var(--zw-font-size-xs); color: var(--zw-text-quaternary); }
  }
}

</style>
