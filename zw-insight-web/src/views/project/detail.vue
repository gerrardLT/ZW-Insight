<template>
  <div class="project-detail-container page-container">
    <!-- 工程铭牌头部 -->
    <div class="plate-header">
      <div class="plate-main">
        <div class="plate-title-wrap">
          <div class="plate-eyebrow">Project Master Dossier // 01-PROJ</div>
          <div class="plate-title">
            <span>{{ projectInfo.projectName || '项目详情' }}</span>
            <el-tag :type="getStatusType(projectInfo.status)" size="small">
              {{ getStatusLabel(projectInfo.status) }}
            </el-tag>
          </div>
          <div class="plate-meta">
            <span>CODE: {{ projectInfo.projectCode || '-' }}</span>
            <span>CREATED: {{ projectInfo.createdAt || '-' }}</span>
          </div>
        </div>
        <div class="plate-actions">
          <!-- 动作按钮区（按状态机动态启用，P1-M1） -->
          <el-button v-if="projectInfo.status === 'DRAFT'" type="primary" @click="handleSubmit">
            提交立项
          </el-button>
          <el-button v-if="projectInfo.status === 'FILED'" type="warning" plain @click="handleWithdraw">
            撤回立项
          </el-button>
          <el-button v-if="projectInfo.status === 'TENDERING'" type="info" plain @click="handleLoseBid">
            落标归档
          </el-button>
          <el-button v-if="projectInfo.status === 'CONSTRUCTION'" type="warning" @click="handlePause">
            暂停施工
          </el-button>
          <el-button v-if="projectInfo.status === 'PAUSED'" type="success" @click="handleResume">
            恢复施工
          </el-button>
          <el-button
            v-if="canTerminate"
            type="danger"
            plain
            @click="handleTerminate"
          >
            项目终止
          </el-button>
          <el-button @click="handleBack">
            <el-icon><ArrowLeft /></el-icon>返回列表
          </el-button>
        </div>
      </div>
    </div>

    <!-- 主体内容卡片（蓝图角标） -->
    <el-card shadow="never" class="card-corner-marked">
      <template #header>
        <div class="card-header">
          <span>项目详情：{{ projectInfo.projectName }}</span>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="custom-tabs">
        <el-tab-pane label="基本信息" name="info">
          <div class="section-title">工程基本概况</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="项目编号">
              <span class="stat-number">{{ projectInfo.projectCode }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="项目名称">{{ projectInfo.projectName }}</el-descriptions-item>
            <el-descriptions-item label="项目性质">{{ projectInfo.projectNature }}</el-descriptions-item>
            <el-descriptions-item label="项目类型">{{ projectInfo.projectType }}</el-descriptions-item>
            <el-descriptions-item label="业主单位">{{ projectInfo.ownerCompanyName }}</el-descriptions-item>
            <el-descriptions-item label="签约公司">{{ projectInfo.signingCompanyName }}</el-descriptions-item>
            <el-descriptions-item label="项目地址">{{ projectInfo.projectAddress }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ projectInfo.contactName }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ projectInfo.contactPhone }}</el-descriptions-item>
            <el-descriptions-item label="预算金额">
              <span class="stat-number text-brand font-semibold">{{ projectInfo.budgetAmount != null ? (projectInfo.budgetAmount === 0 ? '0' : '¥ ' + projectInfo.budgetAmount) : '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(projectInfo.status)" size="small">
                {{ getStatusLabel(projectInfo.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              <span class="stat-number">{{ projectInfo.createdAt }}</span>
            </el-descriptions-item>

            <!-- 周期与时间要素（P1-M1 A2） -->
            <el-descriptions-item label="计划开工日期">
              {{ projectInfo.plannedStartDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="计划竣工日期">
              {{ projectInfo.plannedEndDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="实际开工日期">
              {{ projectInfo.actualStartDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="实际竣工日期">
              {{ projectInfo.actualEndDate || '-' }}
            </el-descriptions-item>

            <!-- 异常分支原因（按需展示） -->
            <el-descriptions-item v-if="projectInfo.pauseReason" label="暂停原因" :span="2">
              <span class="text-danger">{{ projectInfo.pauseReason }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="projectInfo.lostReason" label="落标原因" :span="2">
              <span class="text-danger">{{ projectInfo.lostReason }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="projectInfo.terminateReason" label="终止原因" :span="2">
              <span class="text-danger">{{ projectInfo.terminateReason }}</span>
            </el-descriptions-item>

            <el-descriptions-item label="项目概述" :span="2">{{ projectInfo.projectOverview || '暂无描述' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="项目团队" name="team">
          <ProjectMember :project-id="projectId" />
        </el-tab-pane>

        <!-- B4 流转大事记时间线 -->
        <el-tab-pane label="流转大事记" name="timeline">
          <div v-loading="timelineLoading" class="timeline-pane">
            <el-timeline v-if="statusLogs.length">
              <el-timeline-item
                v-for="log in statusLogs"
                :key="log.id"
                :timestamp="log.createdAt"
                placement="top"
                type="primary"
              >
                <el-card shadow="never" class="timeline-card">
                  <div class="timeline-title">
                    <el-tag size="small" type="info">{{ getStatusLabel(log.fromStatus || '') || '起点' }}</el-tag>
                    <span class="timeline-arrow">➔</span>
                    <el-tag size="small" :type="getStatusType(log.toStatus)">{{ getStatusLabel(log.toStatus) }}</el-tag>
                    <span class="timeline-event">事件：{{ log.event }}</span>
                  </div>
                  <div v-if="log.remark" class="timeline-remark">
                    备注：{{ log.remark }}
                  </div>
                </el-card>
              </el-timeline-item>
            </el-timeline>
            <ZwEmptyState v-else description="暂无状态流转记录" />
          </div>
        </el-tab-pane>

        <!-- I4 关键字段变更台账 -->
        <el-tab-pane label="变更台账" name="changeLogs">
          <div v-loading="changeLogsLoading" class="changelog-pane">
            <el-table v-if="changeLogs.length" :data="changeLogs" border stripe>
              <el-table-column prop="createdAt" label="变更时间" width="180" />
              <el-table-column prop="fieldName" label="变更字段" width="160">
                <template #default="{ row }">
                  <span>{{ fieldNameMap[row.fieldName] || row.fieldName }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="oldValue" label="变更前" min-width="180">
                <template #default="{ row }">
                  <span class="changelog-old">{{ row.oldValue || '(空)' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="newValue" label="变更后" min-width="180">
                <template #default="{ row }">
                  <span class="changelog-new">{{ row.newValue || '(空)' }}</span>
                </template>
              </el-table-column>
            </el-table>
            <ZwEmptyState v-else description="暂无关键字段变更记录" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProjectDetail,
  submitProject,
  withdrawProject,
  loseBidProject,
  pauseProject,
  resumeProject,
  terminateProject,
  getProjectStatusLog,
  getProjectChangeLog
} from '@/api/project'
import type { ProjectStatusLog, ProjectChangeLog } from '@/types/project'
import ProjectMember from './components/ProjectMember.vue'
import ZwEmptyState from '@/components/ZwEmptyState.vue'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => route.params.id as string)
const projectInfo = ref<any>({})
const activeTab = ref('info')

const statusLogs = ref<ProjectStatusLog[]>([])
const timelineLoading = ref(false)

const changeLogs = ref<ProjectChangeLog[]>([])
const changeLogsLoading = ref(false)

const fieldNameMap: Record<string, string> = {
  projectName: '项目名称',
  ownerCompanyId: '业主单位ID',
  ownerCompanyName: '业主单位名称',
  signingCompanyId: '签约公司ID',
  signingCompanyName: '签约公司名称',
  budgetAmount: '预算金额',
  contractAmount: '合同金额',
  plannedStartDate: '计划开工日期',
  plannedEndDate: '计划竣工日期',
  needTender: '是否招标'
}

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  FILED: { label: '已报备', type: 'primary' },
  TENDERING: { label: '招标中', type: 'warning' },
  WON: { label: '已中标', type: 'success' },
  LOST: { label: '已落标', type: 'info' },
  CONSTRUCTION: { label: '施工中', type: '' },
  PAUSED: { label: '已暂停', type: 'warning' },
  COMPLETED: { label: '已竣工', type: 'success' },
  CLOSING: { label: '结项审批中', type: 'warning' },
  CLOSED: { label: '已关闭', type: 'danger' },
  TERMINATING: { label: '终止审批中', type: 'warning' },
  TERMINATED: { label: '已终止', type: 'danger' }
}

const canTerminate = computed(() => {
  const s = projectInfo.value.status
  return s && !['CLOSED', 'LOST', 'TERMINATED', 'CLOSING', 'TERMINATING'].includes(s)
})

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status || ''
}

function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}

async function loadDetail() {
  if (!projectId.value) return
  const res: any = await getProjectDetail(projectId.value)
  projectInfo.value = res.data || {}
}

async function loadTimeline() {
  if (!projectId.value) return
  timelineLoading.value = true
  try {
    const res: any = await getProjectStatusLog(projectId.value)
    statusLogs.value = res.data || []
  } finally {
    timelineLoading.value = false
  }
}

async function loadChangeLogs() {
  if (!projectId.value) return
  changeLogsLoading.value = true
  try {
    const res: any = await getProjectChangeLog(projectId.value)
    changeLogs.value = res.data || []
  } finally {
    changeLogsLoading.value = false
  }
}

// ======================== 操作处理 ========================

async function handleSubmit() {
  await ElMessageBox.confirm('确定提交立项吗？', '提交立项', { type: 'info' })
  await submitProject(projectId.value)
  ElMessage.success('提交立项成功')
  await loadDetail()
}

async function handleWithdraw() {
  await ElMessageBox.confirm('确定撤回立项吗？项目将回退为草稿状态供编辑。', '撤回立项', { type: 'warning' })
  await withdrawProject(projectId.value)
  ElMessage.success('撤回成功，项目已回退草稿')
  await loadDetail()
}

async function handleLoseBid() {
  const { value } = await ElMessageBox.prompt('请输入落标归档原因：', '落标归档', {
    inputPattern: /.+/,
    inputErrorMessage: '落标原因不能为空'
  })
  await loseBidProject(projectId.value, value)
  ElMessage.success('落标归档完成')
  await loadDetail()
}

async function handlePause() {
  const { value } = await ElMessageBox.prompt('请输入暂停施工原因：', '暂停施工', {
    inputPattern: /.+/,
    inputErrorMessage: '暂停原因不能为空'
  })
  await pauseProject(projectId.value, value)
  ElMessage.success('已标记暂停施工')
  await loadDetail()
}

async function handleResume() {
  await ElMessageBox.confirm('确定恢复施工吗？', '恢复施工', { type: 'info' })
  await resumeProject(projectId.value)
  ElMessage.success('已恢复施工')
  await loadDetail()
}

async function handleTerminate() {
  const { value } = await ElMessageBox.prompt(
    '发起项目终止将进入终止审批流程。在途财务单据需已结清。请输入终止原因：',
    '发起项目终止',
    {
      inputPattern: /.+/,
      inputErrorMessage: '终止原因不能为空'
    }
  )
  await terminateProject(projectId.value, value)
  ElMessage.success('终止审批流程已发起')
  await loadDetail()
}

function handleBack() {
  router.push('/project/list')
}

watch(activeTab, (tab) => {
  if (tab === 'timeline') loadTimeline()
  if (tab === 'changeLogs') loadChangeLogs()
})

onMounted(() => {
  loadDetail()
  if (route.query.tab) {
    activeTab.value = route.query.tab as string
  }
})
</script>

<style scoped>
.project-detail-container {
  padding: var(--zw-space-md);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.text-danger {
  color: var(--zw-danger);
}
.plate-header {
  margin-bottom: var(--zw-space-md);
}
.plate-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.plate-title {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  font-size: var(--zw-font-size-xl);
  font-weight: bold;
}
.plate-eyebrow {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
  font-family: var(--zw-font-mono);
}
.plate-meta {
  display: flex;
  gap: var(--zw-space-md);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-secondary);
  margin-top: var(--zw-space-xs);
}

.timeline-pane {
  padding: var(--zw-space-md) var(--zw-space-sm);
}
.timeline-card {
  margin-bottom: var(--zw-space-sm);
}
.timeline-title {
  font-weight: bold;
  margin-bottom: var(--zw-space-xs);
}
.timeline-arrow {
  margin: 0 var(--zw-space-sm);
}
.timeline-event {
  margin-left: var(--zw-space-sm-md);
  font-weight: normal;
  color: var(--zw-text-secondary);
}
.timeline-remark {
  color: var(--zw-text-regular);
  font-size: var(--zw-font-size-sm);
}
.changelog-pane {
  padding: var(--zw-space-sm);
}
.changelog-old {
  color: var(--zw-text-tertiary);
}
.changelog-new {
  font-weight: 500;
  color: var(--zw-brand);
}
</style>
