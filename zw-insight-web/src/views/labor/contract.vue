<template>
  <div class="labor-contract-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="所属项目">
          <ProjectSelector v-model="queryParams.projectId" width="200px" />
        </el-form-item>
        <el-form-item label="合同名称">
          <el-input v-model="queryParams.contractName" placeholder="合同名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="施工队伍">
          <el-input v-model="queryParams.teamName" placeholder="施工队伍" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="生效" value="EFFECTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增劳务合同</el-button>
        <el-button @click="handleOpenImport">批量导入</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="contractCode" label="合同编号" width="150" />
        <el-table-column prop="contractName" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="teamName" label="施工队伍" width="150" />
        <el-table-column prop="contractAmount" label="合同金额(元)" width="140" align="right">
          <template #default="{ row }">{{ row.contractAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="110" />
        <el-table-column prop="endDate" label="结束日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'EFFECTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'EFFECTIVE' ? '生效' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交审批</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <stat-chart-panel
      ref="costPanelRef"
      class="stat-panel"
      title="劳务成本占比"
      :fetch-data="fetchCostRatio"
      :build-option="buildCostRatioOption"
      empty-text="暂无劳务成本数据"
    />

    <batch-import-dialog
      v-model:visible="importVisible"
      module-code="LABOR_CONTRACT"
      :project-id="queryParams.projectId"
      @success="loadData"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑劳务合同' : '新增劳务合同'" width="650px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <!-- 2026-08-17 缺陷#9：表单原无项目字段，DB project_id NOT NULL 导致创建 500 且预算管控跳过 -->
        <el-form-item label="所属项目" prop="projectId"><ProjectSelector v-model="formData.projectId" /></el-form-item>
        <el-form-item label="合同名称" prop="contractName"><el-input v-model="formData.contractName" /></el-form-item>
        <el-form-item label="施工队伍" prop="teamName"><el-input v-model="formData.teamName" /></el-form-item>
        <el-form-item label="合同金额" prop="contractAmount"><el-input-number v-model="formData.contractAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="开始日期"><el-date-picker v-model="formData.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="结束日期"><el-date-picker v-model="formData.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="formData.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getLaborContractPage, createLaborContract, updateLaborContract, deleteLaborContract, submitLaborContract, getLaborCostRatio } from '@/api/labor'
import ProjectSelector from '@/components/ProjectSelector.vue'
import BatchImportDialog from '@/components/BatchImportDialog.vue'
import StatChartPanel from '@/components/StatChartPanel.vue'
import { toWan, clampPercent } from '@/utils/chart-format'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const importVisible = ref(false)

const queryParams = ref({ page: 1, size: 10, projectId: undefined as number | undefined, contractName: '', teamName: '', status: '' })
const formData = ref({ id: undefined as number | undefined, projectId: undefined as number | undefined, contractName: '', teamName: '', contractAmount: 0, startDate: '', endDate: '', remark: '' })
const formRules = { projectId: [{ required: true, message: '请选择项目', trigger: 'change' }], contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }], teamName: [{ required: true, message: '请输入施工队伍', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getLaborContractPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.page = 1; loadData(); costPanelRef.value?.reload() }
function handleReset() { queryParams.value = { page: 1, size: 10, projectId: undefined, contractName: '', teamName: '', status: '' }; loadData(); costPanelRef.value?.reload() }
function handleOpenImport() {
  if (!queryParams.value.projectId) {
    ElMessage.warning('请先选择项目后再进行批量导入')
    return
  }
  importVisible.value = true
}
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, projectId: undefined, contractName: '', teamName: '', contractAmount: 0, startDate: '', endDate: '', remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateLaborContract(formData.value) : await createLaborContract(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteLaborContract(row.id); ElMessage.success('删除成功'); loadData() }
// 盲点 13 修复（2026-08-15 决策 A）：补提交审批入口，合同生效链路 UI 闭环
async function handleSubmit(row: any) { await ElMessageBox.confirm('确定要提交该合同进入审批流程吗？', '提示', { type: 'warning' }); await submitLaborContract(row.id); ElMessage.success('已提交审批'); loadData() }
// ================= 劳务成本占比面板 =================
const costPanelRef = ref<InstanceType<typeof StatChartPanel>>()

async function fetchCostRatio() {
  if (!queryParams.value.projectId) throw new Error('请先选择项目后查看劳务成本占比')
  const res: any = await getLaborCostRatio(queryParams.value.projectId)
  return res.data
}

function buildCostRatioOption(data: any) {
  if (!data || data.contractAmountTotal == null) return null
  const items = [
    { name: '劳务合同金额', value: Number(data.contractAmountTotal) || 0 },
    { name: '结算总额', value: Number(data.settlementTotal) || 0 },
    { name: '已付金额', value: Number(data.paidTotal) || 0 },
    { name: '未付金额', value: Number(data.unpaidTotal) || 0 }
  ]
  const costRatio = data.costRatio == null ? null : clampPercent(Number(data.costRatio))
  return {
    title: costRatio != null ? { text: `成本占比：${costRatio}%`, left: 'center', top: 0, textStyle: { fontSize: 13 } } : undefined,
    tooltip: { trigger: 'axis', valueFormatter: (v: number) => `${v} 万元` },
    grid: { left: '3%', right: '4%', bottom: '3%', top: 40, containLabel: true },
    xAxis: { type: 'category', data: items.map(i => i.name) },
    yAxis: { type: 'value', name: '万元', axisLabel: { formatter: '{value} 万' } },
    series: [{ name: '金额', type: 'bar', barMaxWidth: 48, data: items.map(i => toWan(i.value)) }]
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.labor-contract-container { padding: 16px; }
.stat-panel { margin-bottom: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
