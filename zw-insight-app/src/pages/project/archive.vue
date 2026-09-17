<template>
  <view class="archive-page">
    <view class="loading-mask" v-if="loading">
      <text>加载中...</text>
    </view>

    <!-- S3.1：失败态走 ZwiEmptyState error 变体（critique P3——空态要能区分「没数据」与「取数失败」） -->
    <ZwiEmptyState v-else-if="loadFailed" type="error" description="项目档案加载失败">
      <template #action>
        <text class="retry-btn" @click="retryLoad">重试</text>
      </template>
    </ZwiEmptyState>

    <!-- 后端 ArchiveService#getProjectArchive 返回嵌套结构 { project, fundSummary, members, ... }，
         字段名以 BizProject 实体为准（ownerCompanyName 等；无 manager/startDate/endDate/progress 数据源，不编造） -->
    <template v-else-if="project.id">
      <ZwiSectionCard eyebrow="Project" title="项目信息">
        <ZwiCell variant="kv" title="项目名称" :value="project.projectName || '-'" :ellipsis="false" />
        <ZwiCell variant="kv" title="项目编号" :value="project.projectCode || '-'" />
        <ZwiCell variant="kv" title="项目状态" :value="statusLabel(project.status)" />
        <ZwiCell variant="kv" title="业主单位" :value="project.ownerCompanyName || '-'" :ellipsis="false" />
        <ZwiCell variant="kv" title="合同金额" :value="fund.contractAmount ? formatWan(fund.contractAmount) + '万' : '-'" />
        <ZwiCell variant="kv" title="项目性质" :value="project.projectNature || '-'" />
        <ZwiCell variant="kv" title="项目类型" :value="project.projectType || '-'" last />
      </ZwiSectionCard>

      <ZwiSectionCard eyebrow="Finance" title="财务概况">
        <ZwiCell variant="kv" title="已收款" :value="formatWan(fund.totalIncome) + '万'" />
        <ZwiCell variant="kv" title="已付款" :value="formatWan(fund.totalExpense) + '万'" />
        <ZwiCell variant="kv" title="利润" :value="formatWan(profit) + '万'" last />
      </ZwiSectionCard>
    </template>

    <ZwiEmptyState v-if="!loading && !loadFailed && !project.id" description="暂无项目档案信息" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProjectArchive } from '@/api/common'
import ZwiSectionCard from '@/components/zwi/ZwiSectionCard.vue'
import ZwiCell from '@/components/zwi/ZwiCell.vue'
import ZwiEmptyState from '@/components/zwi/ZwiEmptyState.vue'

const archive = ref<any>(null)
const loading = ref(true)
const loadFailed = ref(false)
let currentProjectId = 0

// 后端嵌套契约解构：project / fundSummary
const project = computed<any>(() => archive.value?.project || {})
const fund = computed<any>(() => archive.value?.fundSummary || {})
const profit = computed(() => Number(fund.value.totalIncome || 0) - Number(fund.value.totalExpense || 0))

// 项目状态 8 态中文映射（与 PC views/project/index.vue statusMap 同口径）
const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿', FILED: '已报备', TENDERING: '招标中', WON: '已中标',
  CONSTRUCTION: '施工中', COMPLETED: '已竣工', CLOSING: '结项审批中', CLOSED: '已关闭',
}
function statusLabel(s?: string) {
  return (s && STATUS_LABEL[s]) || s || '-'
}

function formatWan(val: number) {
  if (!val) return '0'
  return (val / 10000).toFixed(2)
}

onLoad((options: any) => {
  const projectId = Number(options.projectId)
  currentProjectId = projectId || 0
  if (projectId) {
    loadArchive(projectId)
  } else {
    loading.value = false
  }
})

async function loadArchive(projectId: number) {
  loading.value = true
  loadFailed.value = false
  try {
    const res: any = await getProjectArchive(projectId)
    archive.value = res.data
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function retryLoad() {
  if (currentProjectId) {
    loadArchive(currentProjectId)
  }
}
</script>

<style scoped>
.archive-page { padding: 20rpx; }
.loading-mask { display: flex; justify-content: center; align-items: center; height: 400rpx; font-size: 28rpx; color: var(--zw-text-tertiary); }
/* 失败态重试 CTA（ZwiEmptyState #action 插槽，仍属本页作用域） */
.retry-btn { padding: 8rpx 28rpx; min-height: 44px; display: inline-flex; align-items: center; box-sizing: border-box; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 26rpx; border-radius: var(--zw-radius-xs); }
</style>
