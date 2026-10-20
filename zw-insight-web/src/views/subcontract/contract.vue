<template>
  <div class="subcontract-container">
    <ZwListPage
      v-model:page="queryParams.pageNum"
      v-model:size="queryParams.pageSize"
      :data="tableData"
      :loading="loading"
      :total="total"
      :page-sizes="[10, 20, 50]"
      @change="loadData"
    >
      <template #toolbar>
        <el-form :model="queryParams" inline>
          <el-form-item label="合同名称">
            <el-input v-model="queryParams.contractName" placeholder="合同名称" clearable style="width: 200px" />
          </el-form-item>
          <el-form-item label="分包方">
            <el-input v-model="queryParams.subcontractor" placeholder="分包方" clearable style="width: 160px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>

        <ZwListToolbar
          :columns="contractColumns"
          :visible="columnVisible"
          @update:visible="setVisible"
          @reset="resetColumns"
          @refresh="loadData"
        >
          <el-button type="primary" @click="handleAdd">新增分包合同</el-button>
        </ZwListToolbar>
      </template>

        <el-table-column v-if="columnVisible[0]" prop="contractCode" label="合同编号" width="150" />
        <el-table-column v-if="columnVisible[1]" prop="contractName" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column v-if="columnVisible[2]" prop="subcontractor" label="分包方" width="150" />
        <el-table-column v-if="columnVisible[3]" prop="contractAmount" label="合同金额(元)" width="140" align="right">
          <template #default="{ row }">{{ row.contractAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column v-if="columnVisible[4]" prop="projectName" label="所属项目" min-width="160" show-overflow-tooltip />
        <el-table-column v-if="columnVisible[5]" prop="signingDate" label="签订日期" width="110" />
        <el-table-column v-if="columnVisible[6]" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'EFFECTIVE' ? 'success' : row.status === 'SUBMITTED' ? 'warning' : 'info'" size="small">
              {{ row.status === 'EFFECTIVE' ? '生效' : row.status === 'SUBMITTED' ? '审批中' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 'EFFECTIVE' && canRevise" link type="warning" @click="openRevision(row)">修订</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
    </ZwListPage>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑分包合同' : '新增分包合同'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <ZwFormPane description="仅草稿可编辑；生效合同金额变更请走「修订」（独立审批，审批前金额不变）。">
        <div class="zw-form-pane__section-title">基本信息</div>
        <el-form-item label="所属项目" prop="projectId"><ProjectSelector v-model="formData.projectId" /></el-form-item>
        <el-form-item label="合同名称" prop="contractName"><el-input v-model="formData.contractName" /></el-form-item>
        <el-form-item label="分包方" prop="subcontractor"><el-input v-model="formData.subcontractor" /></el-form-item>
        <div class="zw-form-pane__section-title">金额与条款</div>
        <el-form-item label="合同金额" prop="contractAmount"><el-input-number v-model="formData.contractAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="税率(%)"><el-input-number v-model="formData.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" placeholder="未知留空" /></el-form-item>
        <el-form-item label="分包内容"><el-input v-model="formData.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="签订日期"><el-date-picker v-model="formData.signingDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        </ZwFormPane>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 支出合同通用修订（V2026_123）：审批通过后回写合同金额与税额 -->
    <ExpenseContractRevisionDialog
      v-model:visible="revisionVisible"
      category="SUBCONTRACT"
      :contract-id="revisionTarget?.id"
      :base-version="revisionTarget?.version"
      :before-amount="revisionTarget?.contractAmount"
      @changed="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { usePermission } from '@/composables/usePermission'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getSubcontractPage, createSubcontract, updateSubcontract, deleteSubcontract, submitSubcontract } from '@/api/subcontract'
import { positiveAmount } from '@/utils/form-rules'
import ProjectSelector from '@/components/ProjectSelector.vue'
import ZwListToolbar from '@/components/ZwListToolbar.vue'
import ZwListPage from '@/components/ZwListPage.vue'
import ExpenseContractRevisionDialog from '@/components/ExpenseContractRevisionDialog.vue'
import ZwFormPane from '@/components/ZwFormPane.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'

// 列显隐配置：按 subcontract-contract-table 持久化 localStorage
const contractColumns = [
  { key: 'contractCode', label: '合同编号' },
  { key: 'contractName', label: '合同名称' },
  { key: 'subcontractor', label: '分包方' },
  { key: 'contractAmount', label: '合同金额(元)' },
  { key: 'projectName', label: '所属项目' },
  { key: 'signingDate', label: '签订日期' },
  { key: 'status', label: '状态' },
]
const { visible: columnVisible, setVisible, reset: resetColumns } = useColumnSetting('subcontract-contract-table', contractColumns)

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
// V2026_125：修订是写动作，只对持分包合同修订写码的角色显示入口
const { hasPermission } = usePermission()
const canRevise = computed(() => hasPermission('subcontract:contract:edit'))
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const revisionVisible = ref(false)
const revisionTarget = ref<any>(null)

const queryParams = ref({ pageNum: 1, pageSize: 10, contractName: '', subcontractor: '' })
const formData = ref({ id: undefined as number | undefined, projectId: undefined as number | undefined, contractName: '', subcontractor: '', contractAmount: 0, taxRate: undefined as number | undefined, content: '', signingDate: '' })
const formRules = { projectId: [{ required: true, message: '请选择项目', trigger: 'change' }], contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }], subcontractor: [{ required: true, message: '请输入分包方', trigger: 'blur' }], contractAmount: positiveAmount('合同金额必须大于 0') }

async function loadData() { loading.value = true; try { const res: any = await getSubcontractPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, contractName: '', subcontractor: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, projectId: undefined, contractName: '', subcontractor: '', contractAmount: 0, taxRate: undefined as number | undefined, content: '', signingDate: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
function openRevision(row: any) { revisionTarget.value = row; revisionVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateSubcontract(formData.value) : await createSubcontract(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteSubcontract(row.id); ElMessage.success('删除成功'); loadData() }
// 盲点 13 修复（2026-08-15 决策 A）：补提交审批入口，合同生效链路 UI 闭环
async function handleSubmit(row: any) { await ElMessageBox.confirm('确定要提交该合同进入审批流程吗？', '提示', { type: 'warning' }); await submitSubcontract(row.id); ElMessage.success('已提交审批'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.subcontract-container { padding: var(--zw-space-md); }
.table-toolbar { margin-bottom: var(--zw-space-md); }
.pagination-wrap { margin-top: var(--zw-space-md); display: flex; justify-content: flex-end; }
</style>
