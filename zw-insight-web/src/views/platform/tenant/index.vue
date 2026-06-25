<template>
  <div class="tenant-container">
    <el-card shadow="never">
      <!-- 搜索区域 -->
      <el-form :model="queryParams" inline>
        <el-form-item label="租户名称">
          <el-input v-model="queryParams.tenantName" placeholder="请输入租户名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="1" />
            <el-option label="已停用" :value="2" />
            <el-option label="已过期" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户类型">
          <el-select v-model="queryParams.userType" placeholder="全部" clearable style="width: 120px">
            <el-option label="试用" value="TRIAL" />
            <el-option label="标准" value="STANDARD" />
            <el-option label="企业" value="ENTERPRISE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 操作栏 -->
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增租户
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="tenantName" label="租户名称" min-width="150" />
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column label="用户类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="userTypeTagMap[row.userType]" size="small">
              {{ userTypeNameMap[row.userType] || row.userType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="200">
          <template #default="{ row }">
            {{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="使用量" width="100" align="center">
          <template #default="{ row }">
            {{ row.currentUsers ?? 0 }} / {{ row.maxUsers ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagMap[row.status]" size="small">
              {{ statusNameMap[row.status] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status === 1"
              link type="warning"
              @click="handleDisable(row)"
            >停用</el-button>
            <el-button
              v-if="row.status === 2 || row.status === 3"
              link type="success"
              @click="handleEnable(row)"
            >启用</el-button>
            <el-button link type="primary" @click="handleRenew(row)">续期</el-button>
            <el-button link type="primary" @click="handleModules(row)">模块配置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑租户弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="formData.id ? '编辑租户' : '新增租户'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="formData.tenantName" placeholder="请输入租户名称" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="formData.contactName" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="用户类型" prop="userType">
          <el-select v-model="formData.userType" placeholder="请选择" style="width: 100%">
            <el-option label="试用" value="TRIAL" />
            <el-option label="标准" value="STANDARD" />
            <el-option label="企业" value="ENTERPRISE" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大用户数" prop="maxUsers">
          <el-input-number v-model="formData.maxUsers" :min="1" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效天数" prop="durationDays">
          <el-input-number v-model="formData.durationDays" :min="1" :max="3650" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formSubmitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 续期弹窗 -->
    <el-dialog v-model="renewDialogVisible" title="租户续期" width="400px" destroy-on-close>
      <el-form ref="renewFormRef" :model="renewForm" :rules="renewRules" label-width="80px">
        <el-form-item label="租户名称">
          <el-input :model-value="renewForm.tenantName" disabled />
        </el-form-item>
        <el-form-item label="续期天数" prop="durationDays">
          <el-input-number v-model="renewForm.durationDays" :min="1" :max="1095" style="width: 100%" />
          <div class="form-tip">续期范围：1 - 1095 天（约3年）</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="renewSubmitting" @click="submitRenew">确定续期</el-button>
      </template>
    </el-dialog>

    <!-- 功能模块配置弹窗 -->
    <el-dialog v-model="moduleDialogVisible" title="功能模块配置" width="520px" destroy-on-close>
      <p class="module-desc">请选择该租户可使用的功能模块：</p>
      <el-checkbox-group v-model="moduleForm.modules">
        <el-checkbox
          v-for="item in moduleOptions"
          :key="item.value"
          :label="item.value"
          style="width: 45%; margin-bottom: 8px"
        >
          {{ item.label }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="moduleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="moduleSubmitting" @click="submitModules">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import {
  getTenantPage,
  createTenant,
  updateTenant,
  disableTenant,
  enableTenant,
  renewTenant,
  updateTenantModules
} from '@/api/platform'

// ==================== 常量 ====================
type TagType = '' | 'success' | 'warning' | 'info' | 'danger'

const userTypeTagMap: Record<string, TagType> = {
  TRIAL: 'info',
  STANDARD: '',
  ENTERPRISE: 'success'
}

const userTypeNameMap: Record<string, string> = {
  TRIAL: '试用',
  STANDARD: '标准',
  ENTERPRISE: '企业'
}

const statusTagMap: Record<number, TagType> = {
  1: 'success',
  2: 'danger',
  3: 'warning'
}

const statusNameMap: Record<number, string> = {
  1: '正常',
  2: '已停用',
  3: '已过期'
}

const moduleOptions = [
  { value: 'TENDER', label: '投标管理' },
  { value: 'BUDGET', label: '预算管理' },
  { value: 'PURCHASE', label: '采购管理' },
  { value: 'LABOR', label: '劳务管理' },
  { value: 'MATERIAL', label: '材料管理' },
  { value: 'MACHINE', label: '机械管理' },
  { value: 'SUBCONTRACT', label: '分包管理' },
  { value: 'SITE', label: '现场管理' },
  { value: 'FINANCE', label: '财务管理' },
  { value: 'HR', label: '行政人事' },
  { value: 'PRICE_COMPARE', label: '三方比价' },
  { value: 'DASHBOARD', label: '看板分析' }
]

// ==================== 列表状态 ====================
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  tenantName: '',
  status: undefined as number | undefined,
  userType: undefined as string | undefined
})

// ==================== 表单弹窗 ====================
const formDialogVisible = ref(false)
const formSubmitting = ref(false)
const formRef = ref<FormInstance>()

const formData = ref({
  id: undefined as number | undefined,
  tenantName: '',
  contactName: '',
  contactPhone: '',
  userType: 'STANDARD',
  maxUsers: 10,
  durationDays: 365
})

const formRules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  userType: [{ required: true, message: '请选择用户类型', trigger: 'change' }],
  maxUsers: [{ required: true, message: '请输入最大用户数', trigger: 'blur' }],
  durationDays: [{ required: true, message: '请输入有效天数', trigger: 'blur' }]
}

// ==================== 续期弹窗 ====================
const renewDialogVisible = ref(false)
const renewSubmitting = ref(false)
const renewFormRef = ref<FormInstance>()

const renewForm = ref({
  tenantId: undefined as number | undefined,
  tenantName: '',
  durationDays: 30
})

const renewRules = {
  durationDays: [
    { required: true, message: '请输入续期天数', trigger: 'blur' },
    { type: 'number' as const, min: 1, max: 1095, message: '续期天数范围为 1-1095', trigger: 'blur' }
  ]
}

// ==================== 模块配置弹窗 ====================
const moduleDialogVisible = ref(false)
const moduleSubmitting = ref(false)

const moduleForm = ref({
  tenantId: undefined as number | undefined,
  modules: [] as string[]
})

// ==================== 方法 ====================
async function loadData() {
  loading.value = true
  try {
    const res: any = await getTenantPage({
      pageNum: queryParams.value.pageNum,
      pageSize: queryParams.value.pageSize,
      tenantName: queryParams.value.tenantName || undefined,
      status: queryParams.value.status,
      userType: queryParams.value.userType
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.value = { pageNum: 1, pageSize: 10, tenantName: '', status: undefined, userType: undefined }
  loadData()
}

function handleAdd() {
  formData.value = { id: undefined, tenantName: '', contactName: '', contactPhone: '', userType: 'STANDARD', maxUsers: 10, durationDays: 365 }
  formDialogVisible.value = true
}

function handleEdit(row: any) {
  formData.value = {
    id: row.id,
    tenantName: row.tenantName,
    contactName: row.contactName,
    contactPhone: row.contactPhone,
    userType: row.userType,
    maxUsers: row.maxUsers,
    durationDays: row.durationDays || 365
  }
  formDialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  formSubmitting.value = true
  try {
    if (formData.value.id) {
      await updateTenant(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createTenant(formData.value)
      ElMessage.success('创建成功')
    }
    formDialogVisible.value = false
    loadData()
  } finally {
    formSubmitting.value = false
  }
}

async function handleDisable(row: any) {
  await ElMessageBox.confirm(`确定要停用租户「${row.tenantName}」吗？停用后该租户下所有用户将无法登录。`, '停用确认', { type: 'warning' })
  await disableTenant(row.id)
  ElMessage.success('已停用')
  loadData()
}

async function handleEnable(row: any) {
  await ElMessageBox.confirm(`确定要启用租户「${row.tenantName}」吗？`, '启用确认', { type: 'info' })
  await enableTenant(row.id)
  ElMessage.success('已启用')
  loadData()
}

function handleRenew(row: any) {
  renewForm.value = { tenantId: row.id, tenantName: row.tenantName, durationDays: 30 }
  renewDialogVisible.value = true
}

async function submitRenew() {
  await renewFormRef.value?.validate()
  renewSubmitting.value = true
  try {
    await renewTenant(renewForm.value.tenantId!, renewForm.value.durationDays)
    ElMessage.success('续期成功')
    renewDialogVisible.value = false
    loadData()
  } finally {
    renewSubmitting.value = false
  }
}

function handleModules(row: any) {
  moduleForm.value = {
    tenantId: row.id,
    modules: row.modules || []
  }
  moduleDialogVisible.value = true
}

async function submitModules() {
  moduleSubmitting.value = true
  try {
    await updateTenantModules(moduleForm.value.tenantId!, moduleForm.value.modules)
    ElMessage.success('模块配置已更新')
    moduleDialogVisible.value = false
    loadData()
  } finally {
    moduleSubmitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.tenant-container {
  padding: 16px;
}
.table-toolbar {
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.module-desc {
  margin-bottom: 16px;
  color: #606266;
}
</style>
