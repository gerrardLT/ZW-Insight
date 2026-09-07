<template>
  <div class="change-event-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <h3>变更事件</h3>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" :disabled="!filters.projectId" @click="handleCreate">
          登记变更事件
        </el-button>
      </div>
    </div>

    <!-- 搜索筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="项目">
          <el-select
            v-model="filters.projectId"
            placeholder="请选择项目"
            filterable
            remote
            clearable
            :remote-method="searchProject"
            style="width: 240px"
            @change="handleSearch"
          >
            <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px">
            <el-option
              v-for="o in CHANGE_EVENT_STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="filters.sourceType" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="o in CHANGE_EVENT_SOURCE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="影响类别">
          <el-select v-model="filters.category" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="o in CHANGE_EVENT_CATEGORY_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="filters.keyword"
            placeholder="编号 / 标题"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="list"
        border
        stripe
        row-key="id"
        @row-click="viewDetail"
      >
        <el-table-column prop="eventNumber" label="事件编号" width="150" fixed="left">
          <template #default="{ row }">
            <span class="event-number">{{ row.eventNumber }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sourceType" label="来源" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">
              {{ CHANGE_EVENT_SOURCE_LABELS[row.sourceType] || row.sourceType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceRef" label="来源单号" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceRef || '-' }}</template>
        </el-table-column>
        <el-table-column prop="category" label="影响类别" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.category"
              size="small"
              :type="(CHANGE_EVENT_CATEGORY_TAG_TYPES[row.category] as any) || 'info'"
            >
              {{ CHANGE_EVENT_CATEGORY_LABELS[row.category] || row.category }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="成本影响" width="120" align="right" sortable :sort-by="costDeltaOf">
          <template #default="{ row }">
            <span v-if="Number(row.costDelta)" :class="Number(row.costDelta) < 0 ? 'is-success' : 'is-danger'">
              {{ Number(row.costDelta) > 0 ? '+' : '' }}{{ toWan(row.costDelta) }}
            </span>
            <span v-else class="is-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="工期影响" width="100" align="right">
          <template #default="{ row }">
            <span v-if="Number(row.scheduleDelayDays)">{{ row.scheduleDelayDays }} 天</span>
            <span v-else class="is-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="(CHANGE_EVENT_STATUS_TAG_TYPES[row.status] as any) || 'info'">
              {{ CHANGE_EVENT_STATUS_LABELS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="登记时间" width="160" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="viewDetail(row)">详情</el-button>
            <el-button
              v-if="isEditable(row.status)"
              type="primary" link size="small" @click.stop="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="warning" link size="small" @click.stop="handleStartAssessment(row)"
            >转评估</el-button>
            <el-button
              v-if="row.status === 'APPROVING'"
              type="success" link size="small" @click.stop="handleApprove(row)"
            >批准</el-button>
            <el-button
              v-if="row.status === 'APPROVING'"
              type="danger" link size="small" @click.stop="handleReject(row)"
            >驳回</el-button>
            <el-button
              v-if="isCancellable(row.status)"
              type="info" link size="small" @click.stop="handleCancel(row)"
            >作废</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <ChangeEventDetailDrawer
      v-model:visible="detailVisible"
      :event-id="currentEventId"
      @changed="loadList"
    />

    <!-- 新增/编辑表单弹窗 -->
    <ChangeEventFormModal
      v-model:visible="formVisible"
      :event-id="editingEventId"
      :preset-project-id="filters.projectId"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, RefreshLeft } from '@element-plus/icons-vue'
import {
  listChangeEvents, deleteChangeEvent, approveChangeEvent, rejectChangeEvent,
  cancelChangeEvent, startChangeEventAssessment,
  CHANGE_EVENT_STATUS_LABELS, CHANGE_EVENT_STATUS_TAG_TYPES,
  CHANGE_EVENT_SOURCE_LABELS, CHANGE_EVENT_SOURCE_OPTIONS,
  CHANGE_EVENT_CATEGORY_LABELS, CHANGE_EVENT_CATEGORY_TAG_TYPES,
  CHANGE_EVENT_CATEGORY_OPTIONS, CHANGE_EVENT_STATUS_OPTIONS,
  type BizChangeEvent
} from '@/api/change-event'
import { getProjectList } from '@/api/project'
import { toWan } from '@/utils/chart-format'
import ChangeEventDetailDrawer from './detail-drawer.vue'
import ChangeEventFormModal from './form-modal.vue'

const loading = ref(false)
const list = ref<BizChangeEvent[]>([])
const projectList = ref<any[]>([])
const detailVisible = ref(false)
const formVisible = ref(false)
const currentEventId = ref<number>()
const editingEventId = ref<number>()

const filters = reactive({
  projectId: null as number | null,
  status: '',
  sourceType: '',
  category: '',
  keyword: ''
})

const pagination = reactive({ page: 1, size: 20, total: 0 })

/** 可编辑状态：与后端 ChangeEventStatus.isEditable() 一致（终态只读，保证可追溯） */
function isEditable(status?: string) {
  return status === 'DRAFT' || status === 'ASSESSING'
}

/** 可作废状态：与后端状态机出边一致（已批准须走反向变更冲销，不可作废抹历史） */
function isCancellable(status?: string) {
  return status === 'DRAFT' || status === 'ASSESSING' || status === 'APPROVING'
}

function costDeltaOf(row: BizChangeEvent) {
  return Number(row.costDelta) || 0
}

function formatDateTime(s?: string) {
  if (!s) return '-'
  return String(s).replace('T', ' ').slice(0, 16)
}

async function searchProject(keyword: string) {
  try {
    const res: any = await getProjectList({ projectName: keyword })
    projectList.value = res.data || []
  } catch {
    projectList.value = []
  }
}

async function loadList() {
  loading.value = true
  try {
    const res: any = await listChangeEvents({
      page: pagination.page,
      size: pagination.size,
      projectId: filters.projectId || undefined,
      status: filters.status || undefined,
      sourceType: filters.sourceType || undefined,
      category: filters.category || undefined,
      keyword: filters.keyword || undefined
    })
    list.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (e: any) {
    list.value = []
    ElMessage.error(e?.message || '加载变更事件失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  loadList()
}

function handleReset() {
  filters.status = ''
  filters.sourceType = ''
  filters.category = ''
  filters.keyword = ''
  pagination.page = 1
  loadList()
}

function handleCreate() {
  if (!filters.projectId) {
    ElMessage.warning('请先选择项目')
    return
  }
  editingEventId.value = undefined
  formVisible.value = true
}

function viewDetail(row: BizChangeEvent) {
  currentEventId.value = row.id
  detailVisible.value = true
}

function handleEdit(row: BizChangeEvent) {
  editingEventId.value = row.id
  formVisible.value = true
}

async function handleStartAssessment(row: BizChangeEvent) {
  try {
    await startChangeEventAssessment(row.id!)
    ElMessage.success('已转入评估中，请商务/造价填写影响评估')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleApprove(row: BizChangeEvent) {
  let comment: string
  try {
    const r = await ElMessageBox.prompt(
      `批准后将自动调整受影响成本账户的「当前预算」（成本影响 ${toWan(row.costDelta)}）。可填写审批意见：`,
      '批准确认',
      { confirmButtonText: '批准', cancelButtonText: '取消', inputPlaceholder: '审批意见（可空）' }
    )
    comment = r.value || ''
  } catch {
    return
  }
  try {
    await approveChangeEvent(row.id!, comment || undefined)
    ElMessage.success('已批准，成本传导已提交（异步生效）')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '批准失败')
  }
}

async function handleReject(row: BizChangeEvent) {
  let reason: string
  try {
    const r = await ElMessageBox.prompt('请填写驳回原因（必填，作为复盘与知识沉淀输入）：', '驳回', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空')
    })
    reason = r.value
  } catch {
    return
  }
  try {
    await rejectChangeEvent(row.id!, reason)
    ElMessage.success('已驳回')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '驳回失败')
  }
}

async function handleCancel(row: BizChangeEvent) {
  let reason: string
  try {
    const r = await ElMessageBox.prompt(
      '作废后事件不可恢复。已批准的变更须登记反向变更冲销，不能作废抹除历史。请填写作废原因：',
      '作废确认',
      { confirmButtonText: '作废', cancelButtonText: '取消', inputPlaceholder: '原因（可空）' }
    )
    reason = r.value || ''
  } catch {
    return
  }
  try {
    await cancelChangeEvent(row.id!, reason || undefined)
    ElMessage.success('已作废')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '作废失败')
  }
}

function handleSaved() {
  formVisible.value = false
  loadList()
}

onMounted(async () => {
  await searchProject('')
  await loadList()
})

// deleteChangeEvent 由详情抽屉内的删除动作调用，此处保留导入以便列表内联扩展
void deleteChangeEvent
</script>

<style scoped lang="scss">
.change-event-container {
  padding: var(--zw-space-lg);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--zw-space-md);

  h3 {
    font-size: var(--zw-font-size-xl);
    font-weight: var(--zw-font-weight-semibold);
    color: var(--zw-text-primary);
    margin: 0;
  }

  .page-actions {
    display: flex;
    gap: var(--zw-space-sm);
  }
}

.filter-card {
  margin-bottom: var(--zw-space-md);

  .filter-form {
    display: flex;
    flex-wrap: wrap;
    row-gap: var(--zw-space-sm);
  }
}

.table-card {
  .event-number {
    font-family: var(--zw-font-mono);
    font-weight: var(--zw-font-weight-medium);
    color: var(--zw-brand);
  }
}

.pagination-wrap {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}

.is-danger { color: var(--zw-danger); font-weight: var(--zw-font-weight-medium); }
.is-success { color: var(--zw-success); font-weight: var(--zw-font-weight-medium); }
.is-muted { color: var(--zw-text-quaternary); }
</style>
