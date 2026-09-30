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
            <el-table-column v-if="columnVisible[1]" prop="businessType" label="业务类型" width="150" />
            <el-table-column v-if="columnVisible[2]" prop="initiator" label="发起人" width="100" />
            <el-table-column v-if="columnVisible[3]" prop="createTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="130" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">查看详情 / 审批</el-button></template></el-table-column>
          </el-table>
        </el-skeleton>
      </div>
      <div class="pagination-wrap"><el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next" @size-change="loadData" @current-change="loadData" /></div>
    </el-card>
    <el-drawer v-model="drawerVisible" title="审批详情与意见" size="min(640px, 100vw)" @closed="invalidateDetail">
      <p v-if="detailLoading" role="status">正在核对审批详情及业务源单…</p>
      <div v-if="detailError" role="alert">{{ detailError }} <el-button @click="retryDetail">重新加载详情</el-button></div>
      <template v-if="detail">
        <h2>{{ detail.taskName || detail.processName || '审批详情' }}</h2>
        <dl class="summary"><dt>业务类型 / ID</dt><dd>{{ detail.businessType }} / {{ detail.businessId }}</dd><dt>任务状态（详情接口）</dt><dd>{{ detail.status }}</dd><dt>申请人</dt><dd>{{ detail.startUserName || '未提供' }}</dd><dt>申请时间</dt><dd>{{ detail.createTime || '未提供' }}</dd></dl>
        <template v-if="detail.businessType === PAYMENT_TYPE">
          <h3>付款源单摘要</h3>
          <dl v-if="payment" class="summary">
            <dt>项目</dt><dd>{{ projectName || '未提供' }}（ID：{{ payment.projectId || '未提供' }}）</dd>
            <dt>付款金额</dt><dd>{{ money(payment.paymentAmount) }}</dd><dt>收款单位</dt><dd>{{ payment.supplierName || '未提供' }}</dd>
            <dt>合同分类 / ID</dt><dd>{{ payment.contractCategory || '未提供' }} / {{ payment.contractId || '未提供' }}</dd>
            <dt>累计结算快照</dt><dd>{{ money(payment.cumulativeSettlementSnapshot) }}</dd><dt>可付快照（未付金额）</dt><dd>{{ money(payment.unpaidAmountSnapshot) }}</dd>
            <dt>源单状态</dt><dd>{{ payment.status }}</dd>
          </dl>
          <p>预算校验说明：当前接口未提供预算校验结果，不代表已通过预算校验。附件：当前接口未提供。快照不代表实时余额。</p>
        </template>
        <p v-else>业务摘要：{{ detail.businessTitle || '未提供' }}。金额合计：未提供（详情未提供可核验源单金额）。</p>
        <h3>审批历史</h3>
        <p v-if="!detail.approvalRecords?.length">暂无审批记录</p>
        <ol v-else class="history"><li v-for="record in detail.approvalRecords" :key="record.id">{{ record.assigneeName || '未提供' }} · {{ record.resultText }} · {{ record.endTime }}<p>{{ record.comment || '未提供意见' }}</p></li></ol>
        <template v-if="activeTab === 'todo' && detail.status === 'pending'">
          <p v-if="blocked" role="alert">{{ blocked }}</p>
          <el-form label-position="top"><el-form-item label="审批意见（退回、终止必填）"><el-input v-model="comment" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item><el-form-item label="退回方式"><el-radio-group v-model="rejectType"><el-radio value="previous">退回上一步</el-radio><el-radio value="start">退回发起人</el-radio></el-radio-group></el-form-item></el-form>
          <div class="actions"><el-button type="success" :disabled="!!blocked || submitLoading" @click="submitAction('approve')">确认本单通过</el-button><el-button type="warning" :disabled="!!blocked || submitLoading" @click="submitAction('reject')">退回</el-button><el-button type="danger" :disabled="!!blocked || submitLoading" @click="submitAction('terminate')">终止流程</el-button></div>
          <p class="secondary">操作权限以服务端校验为准；前端核对不构成授权。</p>
        </template>
      </template>
    </el-drawer>
    <el-dialog v-model="batchVisible" title="批量核对确认" width="min(680px, 95vw)" :close-on-click-modal="false">
      <p v-if="batchLoading" role="status">正在逐条加载审批详情…</p><p v-if="batchError" role="alert">{{ batchError }}</p>
      <ul class="history"><li v-for="item in batchDetails" :key="item.taskId">{{ item.taskName }} · {{ item.businessType }} / {{ item.businessId }}<p>{{ item.businessTitle || '未提供摘要' }} · 金额：未提供</p></li></ul>
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
import { getTodoTasks, getDoneTasks, getApprovalDetail, completeTask, rejectToPrevious, rejectToStart, terminateProcess, batchApprove } from '@/api/workflow'
import { getPaymentApplyDetail } from '@/api/finance'
import { getProjectDetail } from '@/api/project'
import { approvalBlock, batchBlock, money, validId, PAYMENT_TYPE } from './approval'
const approvalColumns = [{ key: 'taskName', label: '任务名称' }, { key: 'businessType', label: '业务类型' }, { key: 'initiator', label: '发起人' }, { key: 'createTime', label: '创建时间' }]
const { visible: columnVisible, setVisible, reset: resetColumns } = useColumnSetting('approval-table', approvalColumns)
const activeTab = ref('todo'), loading = ref(false), listError = ref(''), tableData = ref<any[]>([]), total = ref(0), selectedRows = ref<any[]>([])
const queryParams = ref({ page: 1, size: 10 }), tableRef = ref<any>(), tableRegion = ref<HTMLElement>(), currentRowIndex = ref(-1)
const drawerVisible = ref(false), detailLoading = ref(false), detailError = ref(''), detail = ref<any>(null), payment = ref<any>(null), projectName = ref(''), currentTaskId = ref(''), comment = ref(''), rejectType = ref('previous'), submitLoading = ref(false)
const batchVisible = ref(false), batchLoading = ref(false), batchError = ref(''), batchDetails = ref<any[]>([])
let listVersion = 0, detailVersion = 0, batchVersion = 0
const blocked = computed(() => detailLoading.value ? '详情尚未加载完成' : detailError.value || approvalBlock(detail.value, currentTaskId.value, payment.value))
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
function invalidateDetail() { ++detailVersion; detail.value = null; payment.value = null }
function handleTabChange() { queryParams.value.page = 1; drawerVisible.value = false; invalidateDetail(); batchVisible.value = false; ++batchVersion; loadData() }
async function openDetail(row: any) {
  if (submitLoading.value) return
  const version = ++detailVersion
  currentTaskId.value = typeof row.taskId === 'string' ? row.taskId : ''; drawerVisible.value = true; detailLoading.value = true; detailError.value = ''; detail.value = null; payment.value = null; projectName.value = ''; comment.value = ''
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
    else if (action === 'approve') await ElMessageBox.confirm(`确认通过本单 ${detail.value.businessType} / ${detail.value.businessId}？${payment.value ? '付款金额：' + money(payment.value.paymentAmount) : ''}`, '逐单确认', { type: 'warning' })
    if (version !== detailVersion || blocked.value) return
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
      return res.data
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
.pagination-wrap { margin-top: var(--zw-space-md); display: flex; justify-content: flex-end; overflow-x: auto; }
.summary { display: grid; grid-template-columns: minmax(110px, 1fr) minmax(0, 2fr); gap: var(--zw-space-sm); }
.summary dt, .secondary { color: var(--el-text-color-secondary); }
.summary dd { margin: 0; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
.history { padding-inline-start: var(--zw-space-lg); overflow-wrap: anywhere; }
[tabindex]:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
@media (max-width: 600px) { .approval-container { padding: var(--zw-space-sm); } .summary { grid-template-columns: 1fr; } .summary dd { margin-bottom: var(--zw-space-sm); } }
</style>
