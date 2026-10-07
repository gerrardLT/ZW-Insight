<template>
  <div class="material-inventory-container">
    <el-card shadow="never">
      <!-- 搜索区 -->
      <el-form :model="queryParams" inline class="search-form">
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" width="220px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="APPROVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新建盘点单</el-button>
      </div>

      <!-- 盘点表格 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="盘点单ID" width="120" />
        <el-table-column prop="projectName" label="所属项目" min-width="160" show-overflow-tooltip />
        <el-table-column prop="inventoryDate" label="盘点日期" width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已确认' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">确认生效</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 盘点编辑/新建弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="项目" prop="projectId">
          <ProjectSelector v-model="formData.projectId" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="盘点日期" prop="inventoryDate">
          <el-date-picker v-model="formData.inventoryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-divider content-position="left">盘点明细</el-divider>
        <div class="mb-2">
          <el-button v-if="!isEdit" type="primary" plain size="small" :disabled="!formData.projectId" @click="loadStockRows">
            从项目库存载入
          </el-button>
        </div>
        <el-table :data="formData.details" border size="small" max-height="260">
          <el-table-column label="材料名称" min-width="140">
            <template #default="{ row }">
              <span>{{ row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="specification" label="规格型号" width="110" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="账面数量" width="100">
            <template #default="{ row }">
              <span>{{ Number(row.bookQuantity || 0).toLocaleString() }}</span>
            </template>
          </el-table-column>
          <el-table-column label="实盘数量" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.actualQuantity" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column v-if="!isEdit" label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeDetailRow($index)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSaveForm">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  getMaterialCheckPage,
  getMaterialCheckDetail,
  createMaterialCheck,
  updateMaterialCheck,
  deleteMaterialCheck,
  submitMaterialCheck,
  getMaterialStockPage
} from '@/api/material'
import ProjectSelector from '@/components/ProjectSelector.vue'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const queryParams = reactive({
  page: 1,
  size: 10,
  projectId: undefined as number | undefined,
  status: ''
})

const defaultFormData = () => ({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  inventoryDate: new Date().toISOString().slice(0, 10),
  details: [] as any[]
})

const formData = ref(defaultFormData())

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  inventoryDate: [{ required: true, message: '请选择盘点日期', trigger: 'change' }]
}

const dialogTitle = computed(() => isEdit.value ? '编辑盘点单' : '新建盘点单')

async function loadData() {
  loading.value = true
  try {
    const res: any = await getMaterialCheckPage({
      page: queryParams.page,
      size: queryParams.size,
      projectId: queryParams.projectId,
      status: queryParams.status || undefined
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.page = 1
  loadData()
}

function handleReset() {
  queryParams.page = 1
  queryParams.size = 10
  queryParams.projectId = undefined
  queryParams.status = ''
  loadData()
}

function handleAdd() {
  isEdit.value = false
  formData.value = defaultFormData()
  dialogVisible.value = true
}

async function handleView(row: any) {
  isEdit.value = true
  const res: any = await getMaterialCheckDetail(row.id)
  formData.value = {
    id: row.id,
    projectId: row.projectId,
    inventoryDate: row.inventoryDate,
    details: res.data?.details || []
  }
  dialogVisible.value = true
}

async function handleEdit(row: any) {
  // 后端 update 仅允许修改日期；明细是登记时的不可变盘点快照
  await handleView(row)
}

async function loadStockRows() {
  if (!formData.value.projectId) return
  const res: any = await getMaterialStockPage({ page: 1, size: 1000, projectId: formData.value.projectId })
  formData.value.details = (res.data?.records || []).map((stock: any) => ({
    stockId: stock.id,
    materialName: stock.materialName,
    specification: stock.specification,
    unit: stock.unit,
    bookQuantity: Number(stock.stockQuantity || 0),
    actualQuantity: Number(stock.stockQuantity || 0)
  }))
}

function removeDetailRow(index: number) {
  formData.value.details.splice(index, 1)
}

async function handleSaveForm() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (isEdit.value && formData.value.id) {
      await updateMaterialCheck({
        id: formData.value.id,
        inventoryDate: formData.value.inventoryDate
      })
      ElMessage.success('更新成功')
    } else {
      if (!formData.value.details.length) {
        ElMessage.warning('请先从项目库存载入至少一条盘点明细')
        return
      }
      const adjustments: Record<string, number> = {}
      for (const detail of formData.value.details) {
        adjustments[String(detail.stockId)] = Number(detail.actualQuantity || 0)
      }
      await createMaterialCheck({
        projectId: formData.value.projectId,
        inventoryDate: formData.value.inventoryDate,
        adjustments
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确认提交盘点单吗？生效后将以实盘数据刷新库存账面！', '提示', { type: 'warning' })
  await submitMaterialCheck(row.id)
  ElMessage.success('盘点单已生效并更新库存')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该草稿盘点单吗？', '提示', { type: 'warning' })
  await deleteMaterialCheck(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.material-inventory-container {
  padding: var(--zw-space-md);
}

.table-toolbar {
  margin-bottom: var(--zw-space-md);
}

.pagination-wrap {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}

.mb-2 {
  margin-bottom: var(--zw-space-xs);
}
</style>
