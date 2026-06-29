<template>
  <div class="finance-lock-container">
    <el-card shadow="never">
      <div v-if="canOperate" class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增封账</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="period" label="期间" width="140" align="center" />
        <el-table-column label="封账类型" width="120" align="center">
          <template #default="{ row }">{{ lockTypeLabel(row.lockType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'LOCKED' ? 'danger' : 'success'" size="small">
              {{ row.status === 'LOCKED' ? '已封账' : '已解封' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lockBy" label="操作人" width="120" align="center" show-overflow-tooltip />
        <el-table-column prop="lockTime" label="操作时间" width="180" align="center" />
        <el-table-column v-if="canOperate" label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'LOCKED'" link type="primary" @click="handleUnlock(row as FinanceLockDTO)">解封</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增封账" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="封账期间" prop="period">
          <el-date-picker
            v-model="formData.period"
            type="month"
            format="YYYY-MM"
            value-format="YYYY-MM"
            placeholder="请选择年月"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="封账类型" prop="lockType">
          <el-radio-group v-model="formData.lockType">
            <el-radio value="MONTHLY">月度</el-radio>
            <el-radio value="QUARTERLY">季度</el-radio>
          </el-radio-group>
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
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  getLockPage,
  createLock,
  unlockPeriod,
  type FinanceLockDTO,
  type FinanceLockType
} from '@/api/finance-lock'

const userStore = useUserStore()

/** 财务管理员可操作角色编码 */
const OPERATE_ROLES = ['FINANCE_ADMIN', 'ADMIN']

/** 当前用户是否具备封账/解封操作权限（仅财务管理员或管理员可见操作按钮） */
const canOperate = computed(() => {
  // 超级管理员拥有全部权限
  if (userStore.permissions.includes('*:*:*')) return true
  const roles = userStore.userInfo?.roles
  if (!Array.isArray(roles)) return false
  return roles.some((role: any) => {
    const code = typeof role === 'string' ? role : role?.roleCode ?? role?.code
    return OPERATE_ROLES.includes(code)
  })
})

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<FinanceLockDTO[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10 })

interface FinanceLockForm {
  period: string
  lockType: FinanceLockType
}

const formData = ref<FinanceLockForm>({ period: '', lockType: 'MONTHLY' })

const formRules: FormRules = {
  period: [{ required: true, message: '请选择封账期间', trigger: 'change' }],
  lockType: [{ required: true, message: '请选择封账类型', trigger: 'change' }]
}

function lockTypeLabel(type: FinanceLockType) {
  return type === 'QUARTERLY' ? '季度' : '月度'
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getLockPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  formData.value = { period: '', lockType: 'MONTHLY' }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createLock({ period: formData.value.period, lockType: formData.value.lockType })
    ElMessage.success('封账成功')
    dialogVisible.value = false
    queryParams.value.pageNum = 1
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleUnlock(row: FinanceLockDTO) {
  await ElMessageBox.confirm(
    `确定要解封期间「${row.period}」吗？解封后该期间将允许财务单据操作。`,
    '解封确认',
    {
      type: 'warning',
      confirmButtonText: '确定解封',
      cancelButtonText: '取消'
    }
  )
  await unlockPeriod(row.id)
  ElMessage.success('解封成功')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.finance-lock-container {
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
</style>
