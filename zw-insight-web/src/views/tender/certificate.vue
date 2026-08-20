<template>
  <div class="certificate-container">
    <el-card shadow="never">
      <!-- 查询区：后端契约 GET /tender/certificate/person?page&size&personName&certificateType -->
      <el-form :model="queryParams" inline>
        <el-form-item label="持证人">
          <el-input v-model="queryParams.personName" placeholder="持证人" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="证件类型">
          <el-input v-model="queryParams.certificateType" placeholder="证件类型" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <!-- 后端无 status 查询参数（实体 status 为 Integer 启用标记），展示三态由前端基于 expireDate 派生，此处客户端过滤 -->
          <el-select v-model="queryParams.derivedStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="有效" value="VALID" />
            <el-option label="即将过期" value="EXPIRING" />
            <el-option label="已过期" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增证件</el-button>
      </div>

      <el-table :data="filteredData" v-loading="loading" border>
        <el-table-column prop="personName" label="持证人" width="100" />
        <el-table-column prop="certificateType" label="证件类型" min-width="160" />
        <el-table-column prop="certificateNo" label="证件编号" width="160" />
        <el-table-column prop="issueDate" label="发证日期" width="110" />
        <el-table-column prop="expireDate" label="到期日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="derivedStatusTag(row).type" size="small">{{ derivedStatusTag(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑证件' : '新增证件'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="持证人" prop="personName"><el-input v-model="formData.personName" /></el-form-item>
        <el-form-item label="证件类型" prop="certificateType"><el-input v-model="formData.certificateType" placeholder="如：一级建造师 / 安全员C证" /></el-form-item>
        <el-form-item label="证件编号" prop="certificateNo"><el-input v-model="formData.certificateNo" /></el-form-item>
        <el-form-item label="发证日期"><el-date-picker v-model="formData.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="到期日期"><el-date-picker v-model="formData.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" active-text="有效" inactive-text="无效" />
        </el-form-item>
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
import { getCertificatePage, createCertificate, updateCertificate, deleteCertificate } from '@/api/tender'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

/** 查询参数：pageNum/pageSize 为本地状态，调用时映射为后端 page/size */
const queryParams = ref({ pageNum: 1, pageSize: 10, personName: '', certificateType: '', derivedStatus: '' })
const defaultFormData = () => ({ id: undefined as number | undefined, type: 'person', personName: '', certificateType: '', certificateNo: '', issueDate: '', expireDate: '', status: 1 })
const formData = ref(defaultFormData())
const formRules = {
  personName: [{ required: true, message: '请输入持证人', trigger: 'blur' }],
  certificateType: [{ required: true, message: '请输入证件类型', trigger: 'blur' }],
  certificateNo: [{ required: true, message: '请输入证件编号', trigger: 'blur' }]
}

/**
 * 展示三态由前端基于真实 expireDate 派生（后端契约无此字段）：
 * >30天=有效 / ≤30天=即将过期 / 早于今天=已过期；无到期日期视为长期有效
 */
function deriveStatus(row: any): 'VALID' | 'EXPIRING' | 'EXPIRED' {
  if (!row.expireDate) return 'VALID'
  const expire = new Date(row.expireDate)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  if (expire < today) return 'EXPIRED'
  const daysLeft = (expire.getTime() - today.getTime()) / 86400000
  return daysLeft <= 30 ? 'EXPIRING' : 'VALID'
}

function derivedStatusTag(row: any) {
  const s = deriveStatus(row)
  if (s === 'VALID') return { label: '有效', type: 'success' as const }
  if (s === 'EXPIRING') return { label: '即将过期', type: 'warning' as const }
  return { label: '已过期', type: 'danger' as const }
}

/** 状态筛选为客户端过滤（后端无 status 查询参数） */
const filteredData = computed(() => {
  if (!queryParams.value.derivedStatus) return tableData.value
  return tableData.value.filter((row) => deriveStatus(row) === queryParams.value.derivedStatus)
})

async function loadData() {
  loading.value = true
  try {
    // 后端契约：page/size/personName/certificateType（空串参数不传，避免误匹配）
    const params: any = {
      type: 'person',
      page: queryParams.value.pageNum,
      size: queryParams.value.pageSize
    }
    if (queryParams.value.personName) params.personName = queryParams.value.personName
    if (queryParams.value.certificateType) params.certificateType = queryParams.value.certificateType
    const res: any = await getCertificatePage(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, personName: '', certificateType: '', derivedStatus: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = defaultFormData(); dialogVisible.value = true }
function handleEdit(row: any) {
  isEdit.value = true
  formData.value = {
    id: row.id,
    type: 'person',
    personName: row.personName || '',
    certificateType: row.certificateType || '',
    certificateNo: row.certificateNo || '',
    issueDate: row.issueDate || '',
    expireDate: row.expireDate || '',
    status: row.status === 0 ? 0 : 1
  }
  dialogVisible.value = true
}
async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    isEdit.value ? await updateCertificate(formData.value) : await createCertificate(formData.value)
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}
async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deleteCertificate('person', row.id)
  ElMessage.success('删除成功')
  loadData()
}
onMounted(() => { loadData() })
</script>

<style scoped>
.certificate-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
