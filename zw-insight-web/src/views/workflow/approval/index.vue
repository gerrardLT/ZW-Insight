<template>
  <div class="approval-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="待办任务" name="todo" /><el-tab-pane label="已办任务" name="done" />
      </el-tabs>
      <div class="table-toolbar">
        <el-button v-if="activeTab === 'todo'" :disabled="!selectedRows.length || loading || submitLoading" @click="handleBatchApprove">核对批量通过</el-button>
        <ColumnSettingPopover :columns="approvalColumns" :visible="columnVisible" @update:visible="setVisible" @reset="resetColumns" />
      </div>
      <div v-if="listError" role="alert">{{ listError }} <el-button @click="loadData">重新加载列表</el-button></div>
      <div ref="tableRegion" tabindex="0" aria-label="审批任务表格，方向键导航，空格勾选，Enter查看详情" @keydown="handleTableKeydown">
        <el-skeleton :loading="loading" :rows="5" animated>
          <el-table ref="tableRef" :data="tableData" row-key="taskId" border highlight-current-row @selection-change="selectedRows = $event">
            <el-table-column v-if="activeTab === 'todo'" type="selection" width="50" />
            <el-table-column v-if="columnVisible[0]" prop="taskName" label="任务名称" min-width="150" />
            <el-table-column v-if="columnVisible[1]" prop="businessType" label="业务类型" width="150" :formatter="(row: any) => businessTypeName(row.businessType)" />
            <el-table-column v-if="columnVisible[2]" prop="startUserName" label="发起人" width="110" :formatter="(row: any) => row.startUserName || row.initiator || '—'" />
            <el-table-column v-if="columnVisible[3]" prop="createTime" label="创建时间" width="170" :formatter="(row: any) => formatTime(row.createTime)" />
            <el-table-column label="操作" width="130" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">查看详情 / 审批</el-button></template></el-table-column>
          </el-table>
        </el-skeleton>
      </div>
      <div class="pagination-wrap"><el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next" @size-change="loadData" @current-change="loadData" /></div>
    </el-card>

    <el-drawer v-model="drawerVisible" title="审批详情与意见" size="min(680px, 100vw)" @closed="invalidateDetail">
      <template #header>
        <div class="drawer-head">
          <span class="drawer-title">{{ detail?.taskName || detail?.processName || '审批详情' }}</span>
          <el-tag v-if="detail" :type="detail.status === 'pending' ? 'warning' : 'info'" size="small">{{ detail.status === 'pending' ? '待我审批' : '已办结' }}</el-tag>
        </div>
      </template>
      <p v-if="detailLoading" role="status">正在核对审批详情及业务源单…</p>
      <div v-if="detailError" role="alert" class="alert-line">{{ detailError }} <el-button @click="retryDetail">重新加载详情</el-button></div>
      <template v-if="detail">
        <section class="block">
          <h3>流程信息</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="单据类型">{{ businessTypeName(detail.businessType) }}</el-descriptions-item>
            <el-descriptions-item label="单据ID">{{ detail.businessId }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ detail.startUserName || '未提供' }}</el-descriptions-item>
            <el-descriptions-item label="申请时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="block">
          <h3>业务详情</h3>
          <template v-if="detail.businessType === PAYMENT_TYPE">
            <el-descriptions v-if="payment" :column="2" border size="small">
              <el-descriptions-item label="项目" :span="2">{{ projectName || '未提供' }}（ID：{{ payment.projectId || '未提供' }}）</el-descriptions-item>
              <el-descriptions-item label="付款金额">{{ money(payment.paymentAmount) }}</el-descriptions-item>
              <el-descriptions-item label="收款单位">{{ payment.supplierName || '未提供' }}</el-descriptions-item>
              <el-descriptions-item label="合同分类 / ID">{{ payment.contractCategory || '未提供' }} / {{ payment.contractId || '未提供' }}</el-descriptions-item>
              <el-descriptions-item label="源单状态">{{ payment.status }}</el-descriptions-item>
              <el-descriptions-item label="累计结算快照">{{ money(payment.cumulativeSettlementSnapshot) }}</el-descriptions-item>
              <el-descriptions-item label="可付快照（未付金额）">{{ money(payment.unpaidAmountSnapshot) }}</el-descriptions-item>
            </el-descriptions>
            <p class="secondary">预算校验说明：当前接口未提供预算校验结果，不代表已通过预算校验。附件：当前接口未提供。快照不代表实时余额。</p>
          </template>
          <template v-else>
            <p v-if="bizLoading" role="status">正在加载业务详情…</p>
            <div v-else-if="bizError" role="alert" class="alert-line">{{ bizError }} <el-button @click="retryBusiness">重试</el-button></div>
            <p v-else-if="biz && biz.supported === false" class="secondary">该类型暂无结构化详情，请到对应业务模块核对单据后再审批。</p>
            <p v-else-if="biz && biz.found === false" class="secondary">未找到对应业务单据（可能已删除），请谨慎审批。</p>
            <el-descriptions v-else-if="biz?.fields?.length" :column="2" border size="small">
              <el-descriptions-item v-for="(f, i) in biz.fields" :key="i" :label="f.label" :span="bizSpan(f)">{{ fieldText(f) }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </section>

        <section class="block">
          <h3>审批记录</h3>
          <p v-if="!detail.approvalRecords?.length" class="secondary">暂无审批记录</p>
          <el-timeline v-else>
            <el-timeline-item v-for="record in detail.approvalRecords" :key="record.id" :timestamp="formatTime(record.endTime)" placement="top">
              <strong>{{ record.taskName }}</strong> · {{ record.assigneeName || '未提供' }} · {{ record.resultText }}
              <p class="note">{{ record.comment || '未提供意见' }}</p>
            </el-timeline-item>
          </el-timeline>
        </section>

        <section v-if="activeTab === 'todo' && detail.status === 'pending'" class="block">
          <h3>审批意见</h3>
          <p v-if="blocked" role="alert" class="alert-line">{{ blocked }}</p>
          <el-form label-position="top">
            <el-form-item label="意见（退回、终止必填）"><el-input v-model="comment" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
            <el-form-item label="退回方式"><el-radio-group v-model="rejectType"><el-radio value="previous">退回上一步</el-radio><el-radio value="start">退回发起人</el-radio></el-radio-group></el-form-item>
          </el-form>
          <p class="secondary">操作权限以服务端校验为准；前端核对不构成授权。</p>
        </section>
      </template>
      <template #footer>
        <div v-if="detail && activeTab === 'todo' && detail.status === 'pending'" class="actions">
          <el-button type="success" :disabled="!!blocked || submitLoading" @click="submitAction('approve')">确认本单通过</el-button>
          <el-button type="warning" :disabled="!!blocked || submitLoading" @click="submitAction('reject')">退回</el-button>
          <el-button type="danger" :disabled="!!blocked || submitLoading" @click="submitAction('terminate')">终止流程</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="batchVisible" title="批量核对确认" width="min(680px, 95vw)" :close-on-click-modal="false">
      <p v-if="batchLoading" role="status">正在逐条加载审批详情…</p><p v-if="batchError" role="alert">{{ batchError }}</p>
      <ul class="history"><li v-for="item in batchDetails" :key="item.taskId">{{ item.taskName }} · {{ businessTypeName(item.businessType) }} / {{ item.businessId }}<p>{{ item.businessTitle || '未提供摘要' }}</p><dl><template v-for="field in item.sourceFields" :key="field.label"><dt>{{ field.label }}</dt><dd>{{ field.value ?? '未提供' }}</dd></template></dl></li></ul>
      <p>金额合计：未提供。当前同类型详情无可核验源单金额；付款申请禁止批量通过，须逐单确认。</p>
      <template #footer><el-button :disabled="submitLoading" @click="batchVisible = false">取消</el-button><el-button type="primary" :disabled="batchLoading || !!batchError || !batchDetails.length || submitLoading" @click="submitBatch">确认以上同类型任务通过</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ColumnSettingPopover from '@/components/ColumnSettingPopover.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { getTodoTasks, getDoneTasks, getApprovalDetail, getBusinessDetail, claimTask, completeTask, rejectToPrevious, rejectToStart, terminateProcess, batchApprove } from '@/api/workflow'
import { getPaymentApplyDetail } from '@/api/finance'
import { getProjectDetail } from '@/api/project'
import { approvalBlock, batchBlock, money, validId, businessTypeName, formatTime, PAYMENT_TYPE } from './approval'
const approvalColumns = [{ key: 'taskName', label: '任务名称' }, { key: 'businessType', label: '业务类型' }, { key: 'initiator', label: '发起人' }, { key: 'createTime', label: '创建时间' }]
const { visible: columnVisible, setVisible, reset: resetColumns } = useColumnSetting('approval-table', approvalColumns)
const activeTab = ref('todo'), loading = ref(false), listError = ref(''), tableData = ref<any[]>([]), total = ref(0), selectedRows = ref<any[]>([])
const queryParams = ref({ page: 1, size: 10 }), tableRef = ref<any>(), tableRegion = ref<HTMLElement>(), currentRowIndex = ref(-1)
const drawerVisible = ref(false), detailLoading = ref(false), detailError = ref(''), detail = ref<any>(null), payment = ref<any>(null), projectName = ref(''), currentTaskId = ref(''), comment = ref(''), rejectType = ref('previous'), submitLoading = ref(false)
const biz = ref<any>(null), bizLoading = ref(false), bizError = ref('')
const batchVisible = ref(false), batchLoading = ref(false), batchError = ref(''), batchDetails = ref<any[]>([])
let listVersion = 0, detailVersion = 0, batchVersion = 0
const blocked = computed(() => detailLoading.value || bizLoading.value ? '详情尚未加载完成' : detailError.value || bizError.value || (detail.value?.businessType !== PAYMENT_TYPE && (!biz.value?.supported || !biz.value?.found) ? '业务源单未成功加载，不可审批' : '') || approvalBlock(detail.value, currentTaskId.value, payment.value))
async function loadData() {
  const version = ++listVersion
  loading.value = true; listError.value = ''; selectedRows.value = []; currentRowIndex.value = -1
  try {
    const res: any = await (activeTab.value === 'todo' ? getTodoTasks : getDoneTasks)({ ...queryParams.value })
    if (version !== listVersion) return
    if (!Array.isArray(res.data?.records) || typeof res.data?.total !== 'number') throw new Error('invalid response')
    tableData.value = res.data.records; total.value = res.data.total
  } catch { if (version === listVersion) { tableData.value = []; total.value = 0; listError.value = '审批列表加载失败，请重试。' } }
  finally { if (version === listVersion) loading.value = false }
}
function invalidateDetail() { ++detailVersion; detail.value = null; payment.value = null; biz.value = null; bizError.value = ''; bizLoading.value = false }
function handleTabChange() { queryParams.value.page = 1; drawerVisible.value = false; invalidateDetail(); batchVisible.value = false; ++batchVersion; loadData() }
/** 源单加载成功方可办理；异步过期结果不得覆盖当前任务。 */
async function loadBusiness(taskId: string, version: number) {
  bizLoading.value = true; bizError.value = ''; biz.value = null
  try {
    const res: any = await getBusinessDetail(taskId)
    if (version !== detailVersion) return
    if (!res?.data) throw new Error('empty')
    if (res.data.error) { bizError.value = res.data.error; return }
    biz.value = res.data
  } catch { if (version === detailVersion) bizError.value = '业务详情加载失败，审批动作已阻断，请重试。' }
  finally { if (version === detailVersion) bizLoading.value = false }
}
function retryBusiness() { if (currentTaskId.value) loadBusiness(currentTaskId.value, detailVersion) }
function bizSpan(f: any) { return f?.kind === 'T' && String(f.value ?? '').length > 20 ? 2 : 1 }
function fieldText(f: any): string {
  if (f?.value === null || f?.value === undefined || f.value === '') return '—'
  if (f.kind === 'M') return money(f.value)
  if (f.kind === 'P') return `${f.value}%`
  return String(f.value)
}
async function openDetail(row: any, afterClaim = false) {
  if (submitLoading.value && !afterClaim) return
  const version = ++detailVersion
  currentTaskId.value = typeof row.taskId === 'string' ? row.taskId : ''; drawerVisible.value = true; detailLoading.value = true; detailError.value = ''; detail.value = null; payment.value = null; projectName.value = ''; comment.value = ''; biz.value = null; bizError.value = ''
  try {
    if (!currentTaskId.value) throw new Error('missing task')
    const res: any = await getApprovalDetail(currentTaskId.value)
    if (version !== detailVersion) return
    const data = res.data
    if (!data || data.taskId !== currentTaskId.value || !['pending', 'done'].includes(data.status) || !validId(data.businessId) || !data.businessType) throw new Error('invalid detail')
    if ((row.businessType && row.businessType !== data.businessType) || (row.businessId != null && String(row.businessId) !== String(data.businessId))) throw new Error('business mismatch')
    detail.value = data
    if (data.businessType === PAYMENT_TYPE) {
      // API路径保持原有接口；不把雪花ID转为Number，避免精度丢失。
      const source: any = await getPaymentApplyDetail(data.businessId)
      if (version !== detailVersion) return
      if (!source.data || String(source.data.id) !== String(data.businessId)) throw new Error('invalid payment')
      payment.value = source.data; projectName.value = source.data.projectName || ''
      if (!validId(source.data.projectId)) throw new Error('invalid projectId')
      if (!projectName.value) {
        const project: any = await getProjectDetail(source.data.projectId)
        if (version !== detailVersion) return
        if (String(project.data?.id) !== String(source.data.projectId) || !project.data?.projectName?.trim()) throw new Error('invalid project detail')
        projectName.value = project.data.projectName
      }
    } else {
      await loadBusiness(currentTaskId.value, version)
    }
  } catch { if (version === detailVersion) detailError.value = '审批详情或付款源单加载失败，全部审批动作已阻断。请重试。' }
  finally { if (version === detailVersion) detailLoading.value = false }
}
function retryDetail() { openDetail({ taskId: currentTaskId.value }) }
async function submitAction(action: 'approve' | 'reject' | 'terminate') {
  if (submitLoading.value || blocked.value || activeTab.value !== 'todo') return
  if (action !== 'approve' && !comment.value.trim()) { ElMessage.warning('请填写退回或终止原因'); return }
  const taskId = currentTaskId.value, version = detailVersion
  submitLoading.value = true
  try {
    if (action === 'terminate') await ElMessageBox.confirm('确定终止此流程？终止后不可恢复。', '终止确认', { type: 'warning' })
    else if (action === 'approve') await ElMessageBox.confirm(`确认通过本单 ${businessTypeName(detail.value.businessType)} / ${detail.value.businessId}？${payment.value ? '付款金额：' + money(payment.value.paymentAmount) : ''}`, '逐单确认', { type: 'warning' })
    if (version !== detailVersion || blocked.value) return
    if (detail.value.assignee === null || detail.value.assignee === '') {
      const savedComment = comment.value
      await claimTask(taskId)
      if (version !== detailVersion) return
      await openDetail({ taskId }, true)
      comment.value = savedComment
      if (blocked.value || currentTaskId.value !== taskId) return
    }
    const payload = { taskId, comment: comment.value.trim() }
    if (action === 'approve') await completeTask(payload)
    else if (action === 'terminate') await terminateProcess(payload)
    else await (rejectType.value === 'previous' ? rejectToPrevious : rejectToStart)(payload)
    ElMessage.success('操作成功'); drawerVisible.value = false; invalidateDetail(); await loadData()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error('操作未完成，请刷新详情核对状态后重试') }
  finally { submitLoading.value = false }
}
async function handleBatchApprove() {
  if (submitLoading.value || loading.value) return
  const rows = [...selectedRows.value], version = ++batchVersion
  batchVisible.value = true; batchLoading.value = true; batchError.value = ''; batchDetails.value = []
  try {
    const results = await Promise.all(rows.map(async row => {
      if (!row.taskId) throw new Error('missing task')
      const res: any = await getApprovalDetail(row.taskId)
      if (approvalBlock(res.data, row.taskId) || (row.businessType && row.businessType !== res.data.businessType)) throw new Error('invalid detail or payment requires individual confirmation')
      const source: any = await getBusinessDetail(row.taskId)
      if (!source.data || source.data.supported !== true || source.data.found !== true
        || source.data.error || !Array.isArray(source.data.fields)) throw new Error('invalid business source')
      return { ...res.data, sourceFields: source.data.fields }
    }))
    if (version !== batchVersion) return
    batchDetails.value = results; batchError.value = batchBlock(results)
  } catch { if (version === batchVersion) batchError.value = '有详情加载失败、状态不一致或付款任务。批量操作已阻断；付款申请请逐单确认。' }
  finally { if (version === batchVersion) batchLoading.value = false }
}
async function submitBatch() {
  if (!batchVisible.value || batchLoading.value || batchError.value || submitLoading.value || batchBlock(batchDetails.value) || batchDetails.value.some(d => approvalBlock(d, d.taskId))) return
  submitLoading.value = true
  try { await batchApprove({ taskIds: batchDetails.value.map(d => d.taskId) }); ElMessage.success('批量审批成功'); batchVisible.value = false; await loadData() }
  catch { batchError.value = '批量操作未完成，可能已有部分任务改变状态。请刷新列表逐条核对。'; await loadData() }
  finally { submitLoading.value = false }
}
function handleTableKeydown(event: KeyboardEvent) {
  if (event.target !== tableRegion.value || loading.value || drawerVisible.value || batchVisible.value || activeTab.value !== 'todo') return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault(); currentRowIndex.value = Math.max(0, Math.min(tableData.value.length - 1, currentRowIndex.value + (event.key === 'ArrowDown' ? 1 : -1))); tableRef.value?.setCurrentRow(tableData.value[currentRowIndex.value])
  } else if (event.key === ' ' && currentRowIndex.value >= 0) { event.preventDefault(); tableRef.value?.toggleRowSelection(tableData.value[currentRowIndex.value]) }
  else if (event.key === 'Enter' && currentRowIndex.value >= 0) { event.preventDefault(); openDetail(tableData.value[currentRowIndex.value]) }
}
onMounted(loadData)
onBeforeUnmount(() => { ++listVersion; ++detailVersion; ++batchVersion })
</script>
<style scoped>
.approval-container { padding: var(--zw-space-md); }
.table-toolbar, .actions { display: flex; flex-wrap: wrap; gap: var(--zw-space-sm); margin-bottom: var(--zw-space-md); }
.actions { margin-bottom: 0; justify-content: flex-end; }
.pagination-wrap { margin-top: var(--zw-space-md); display: flex; justify-content: flex-end; overflow-x: auto; }
.drawer-head { display: flex; align-items: center; gap: var(--zw-space-sm); }
.drawer-title { font-size: var(--zw-font-size-lg); font-weight: 600; color: var(--zw-text-primary); }
.block { margin-bottom: var(--zw-space-lg); }
.block h3 { margin: 0 0 var(--zw-space-sm); font-size: var(--zw-font-size-base); font-weight: 600; color: var(--zw-text-primary); }
.secondary, .note { color: var(--zw-text-tertiary); font-size: var(--zw-font-size-sm); }
.note { margin: var(--zw-space-xs) 0 0; }
.alert-line { color: var(--el-color-danger); margin-bottom: var(--zw-space-sm); }
.history { padding-inline-start: var(--zw-space-lg); overflow-wrap: anywhere; }
:deep(.el-descriptions__label) { width: 120px; color: var(--zw-text-secondary); }
:deep(.el-descriptions__content) { overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
[tabindex]:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
@media (max-width: 600px) { .approval-container { padding: var(--zw-space-sm); } }
</style>
