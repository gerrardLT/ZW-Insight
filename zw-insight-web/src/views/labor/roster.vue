<template>
  <div class="labor-roster-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select v-model="queryParams.projectId" placeholder="全部项目" clearable filterable style="width: 180px" @change="handleProjectChange">
            <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班组">
          <el-select v-model="queryParams.teamId" placeholder="全部班组" clearable style="width: 150px">
            <el-option v-for="t in teamList" :key="t.id" :label="t.teamName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.workerName" placeholder="工人姓名" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="工种">
          <el-input v-model="queryParams.workType" placeholder="工种" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="进退场状态">
          <el-select v-model="queryParams.entryStatus" placeholder="全部状态" clearable style="width: 120px" @change="handleSearch">
            <el-option label="在场" value="ON_SITE" />
            <el-option label="退场" value="OFF_SITE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增人员</el-button>
        <el-button @click="handleOpenImport">批量导入</el-button>
        <el-button @click="exportVisible = true">导出</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="workerName" label="姓名" width="100" />
        <el-table-column prop="idCard" label="身份证号" width="180" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column prop="teamName" label="所属班组" width="130" />
        <el-table-column prop="workType" label="工种" width="100" />
        <el-table-column prop="entryDate" label="进场日期" width="110" />
        <el-table-column prop="exitDate" label="退场日期" width="110" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '在场' : '退场' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link type="warning" @click="handleExit(row)">退场登记</el-button>
            <el-button v-else link type="success" @click="handleEntry(row)">进场登记</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <batch-import-dialog v-model:visible="importVisible" module-code="LABOR_ROSTER" :project-id="queryParams.projectId" :extra-query="queryParams.teamId ? { teamId: queryParams.teamId } : undefined" @success="loadData" />

    <async-export-dialog v-model:visible="exportVisible" module-code="LABOR_ROSTER" :params="exportParams" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑人员' : '新增人员'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="姓名" prop="workerName"><el-input v-model="formData.workerName" /></el-form-item>
        <el-form-item label="身份证号" prop="idCard"><el-input v-model="formData.idCard" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="formData.phone" /></el-form-item>
        <el-form-item label="所属班组"><el-input v-model="formData.teamName" /></el-form-item>
        <el-form-item label="工种"><el-input v-model="formData.workType" /></el-form-item>
        <el-form-item label="进场日期"><el-date-picker v-model="formData.entryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getLaborRosterPage, createLaborRoster, updateLaborRoster, deleteLaborRoster, entryLaborRoster, exitLaborRoster, getLaborTeamPage } from '@/api/labor'
import { getProjectList } from '@/api/project'
import BatchImportDialog from '@/components/BatchImportDialog.vue'
import AsyncExportDialog from '@/components/AsyncExportDialog.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const projectList = ref<any[]>([])
const teamList = ref<any[]>([])
const importVisible = ref(false)
const exportVisible = ref(false)

// 导出口径与当前筛选一致（项目/班组），未选则全量导出
const exportParams = computed(() => {
  const params: Record<string, number> = {}
  if (queryParams.value.projectId) params.projectId = queryParams.value.projectId
  if (queryParams.value.teamId) params.teamId = queryParams.value.teamId
  return params
})

const queryParams = ref({ page: 1, size: 10, projectId: undefined as number | undefined, teamId: undefined as number | undefined, workerName: '', workType: '', entryStatus: '' })
const formData = ref({ id: undefined as number | undefined, workerName: '', idCard: '', phone: '', teamName: '', workType: '', entryDate: '' })
const formRules = { workerName: [{ required: true, message: '请输入姓名', trigger: 'blur' }], idCard: [{ required: true, message: '请输入身份证号', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getLaborRosterPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
async function loadProjects() { try { const res: any = await getProjectList(); projectList.value = res.data || [] } catch (e: any) { ElMessage.error(e?.response?.data?.message || '项目列表加载失败') } }
async function loadTeams(projectId?: number) {
  teamList.value = []
  if (!projectId) return
  try { const res: any = await getLaborTeamPage({ page: 1, size: 100, projectId }); teamList.value = res.data?.records || [] } catch (e: any) { ElMessage.error(e?.response?.data?.message || '班组列表加载失败') }
}
function handleProjectChange(val?: number) { queryParams.value.teamId = undefined; loadTeams(val) }
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, projectId: undefined, teamId: undefined, workerName: '', workType: '', entryStatus: '' }; teamList.value = []; loadData() }
function handleOpenImport() {
  if (!queryParams.value.projectId) { ElMessage.warning('请先选择项目后再进行批量导入'); return }
  importVisible.value = true
}
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, workerName: '', idCard: '', phone: '', teamName: '', workType: '', entryDate: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateLaborRoster(formData.value) : await createLaborRoster(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteLaborRoster(row.id); ElMessage.success('删除成功'); loadData() }
async function handleEntry(row: any) {
  try { await ElMessageBox.confirm(`确定为「${row.workerName || ''}」登记进场吗？`, '进场登记', { type: 'warning' }) } catch { return }
  try { await entryLaborRoster(row.id); ElMessage.success('进场登记成功'); loadData() } catch (e: any) { ElMessage.error(e?.response?.data?.message || '进场登记失败') }
}
async function handleExit(row: any) {
  try { await ElMessageBox.confirm(`确定为「${row.workerName || ''}」登记退场吗？`, '退场登记', { type: 'warning' }) } catch { return }
  try { await exitLaborRoster(row.id); ElMessage.success('退场登记成功'); loadData() } catch (e: any) { ElMessage.error(e?.response?.data?.message || '退场登记失败') }
}
onMounted(() => { loadData(); loadProjects() })
</script>

<style scoped>
.labor-roster-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
