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

    <template v-else-if="archive">
      <!-- 项目基本信息：.info-row → ZwiCell kv 变体（字段名走弱/字段值走强） -->
      <ZwiSectionCard eyebrow="Project" title="项目信息">
        <ZwiCell variant="kv" title="项目名称" :value="archive.projectName || '-'" :ellipsis="false" />
        <ZwiCell variant="kv" title="项目编号" :value="archive.projectCode || '-'" />
        <ZwiCell variant="kv" title="项目状态" :value="archive.statusText || '-'" />
        <ZwiCell variant="kv" title="业主单位" :value="archive.ownerName || '-'" :ellipsis="false" />
        <ZwiCell variant="kv" title="合同金额" :value="archive.contractAmount ? (archive.contractAmount / 10000).toFixed(2) + '万' : '-'" />
        <ZwiCell variant="kv" title="项目经理" :value="archive.managerName || '-'" />
        <ZwiCell variant="kv" title="开工日期" :value="archive.startDate || '-'" />
        <ZwiCell variant="kv" title="计划竣工" :value="archive.endDate || '-'" last />
      </ZwiSectionCard>

      <!-- 财务概况 -->
      <ZwiSectionCard eyebrow="Finance" title="财务概况" v-if="archive.finance">
        <ZwiCell variant="kv" title="已收款" :value="formatWan(archive.finance.totalIncome) + '万'" />
        <ZwiCell variant="kv" title="已付款" :value="formatWan(archive.finance.totalExpense) + '万'" />
        <ZwiCell variant="kv" title="利润" :value="formatWan(archive.finance.profit) + '万'" last />
      </ZwiSectionCard>

      <!-- 进度概况 -->
      <ZwiSectionCard eyebrow="Progress" title="进度概况" v-if="archive.progress !== undefined">
        <view class="progress-bar-wrap">
          <view class="progress-bar">
            <view class="progress-inner" :style="{ width: (archive.progress || 0) + '%' }"></view>
          </view>
          <text class="progress-text">{{ archive.progress || 0 }}%</text>
        </view>
      </ZwiSectionCard>
    </template>

    <ZwiEmptyState v-if="!loading && !loadFailed && !archive" description="暂无项目档案信息" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProjectArchive } from '@/api/common'
import ZwiSectionCard from '@/components/zwi/ZwiSectionCard.vue'
import ZwiCell from '@/components/zwi/ZwiCell.vue'
import ZwiEmptyState from '@/components/zwi/ZwiEmptyState.vue'

const archive = ref<any>(null)
const loading = ref(true)
const loadFailed = ref(false)
let currentProjectId = 0

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
.progress-bar-wrap { display: flex; align-items: center; gap: 16rpx; }
.progress-bar { flex: 1; height: 16rpx; background: var(--zw-bg-hover); border-radius: 2rpx; overflow: hidden; }
.progress-inner { height: 100%; width: 100%; background: var(--zw-brand); border-radius: 2rpx; }
.progress-text { font-size: 26rpx; color: var(--zw-brand); font-weight: bold; font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
</style>
