<template>
  <div class="budget-change-form-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ pageTitle }}</span>
          <el-button @click="handleBack">返回列表</el-button>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
        :disabled="isViewMode"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属项目" prop="projectId">
              <ProjectSelector
                v-model="formData.projectId"
                @change="handleProjectChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原预算">
              <el-select
                v-model="formData.budgetId"
                placeholder="请选择原预算"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="item in budgetOptions"
                  :key="item.id"
                  :label="`${item.budgetYear} - ${item.totalAmount?.toLocaleString()}元`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="变更原因" prop="changeReason">
          <el-input
            v-model="formData.changeReason"
            type="textarea"
            :rows="3"
            placeholder="请输入变更原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <!-- 变更明细表格 -->
        <el-form-item label="变更明细" required>
          <div class="detail-table-wrap">
            <div v-if="!isViewMode" class="detail-toolbar">
              <el-button type="primary" size="small" @click="handleAddDetail">
                <el-icon><Plus /></el-icon> 添加明细行
              </el-button>
            </div>

            <el-table :data="formData.details" border size="small">
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column label="科目名称" min-width="180">
                <template #default="{ row }">
                  <el-input
                    v-if="!isViewMode"
                    v-model="row.subjectName"
                    placeholder="请输入科目名称"
                  />
                  <span v-else>{{ row.subjectName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="原金额(元)" width="150">
                <template #default="{ row }">
                  <el-input-number
                    v-if="!isViewMode"
                    v-model="row.originalAmount"
                    :precision="2"
                    :controls="false"
                    style="width: 100%"
                    @change="calcAdjustedAmount(row as DetailRow)"
                  />
                  <span v-else>{{ row.originalAmount?.toLocaleString() }}</span>
                </template>
              </el-table-column>
              <el-table-column label="调整金额(元)" width="150">
                <template #default="{ row }">
                  <el-input-number
                    v-if="!isViewMode"
                    v-model="row.adjustAmount"
                    :precision="2"
                    :controls="false"
                    style="width: 100%"
                    @change="calcAdjustedAmount(row as DetailRow)"
                  />
                  <span v-else :class="{ 'text-danger': row.adjustAmount < 0, 'text-success': row.adjustAmount > 0 }">
                    {{ row.adjustAmount?.toLocaleString() }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="调整后金额(元)" width="150">
                <template #default="{ row }">
                  <span>{{ row.adjustedAmount?.toLocaleString() }}</span>
                </template>
              </el-table-column>
              <el-table-column v-if="!isViewMode" label="操作" width="80" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="handleRemoveDetail($index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="detail-summary">
              <span>调整总额：</span>
              <span :class="{ 'text-danger': totalAdjustAmount < 0, 'text-success': totalAdjustAmount > 0 }">
                <strong>{{ totalAdjustAmount.toLocaleString() }} 元</strong>
              </span>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <!-- 底部操作按钮 -->
      <div v-if="!isViewMode" class="form-actions">
        <el-button @click="handleBack">取消</el-button>
        <el-button type="info" :loading="saveLoading" @click="handleSaveDraft">
          保存草稿
        </el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitApproval">
          提交审批
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import ProjectSelector from '@/components/ProjectSelector.vue'
import {
  getBudgetChange,
  getBudgetChangeDetails,
  createBudgetChange,
  updateBudgetChange,
  submitBudgetChange
} from '@/api/budget-change'
import { getBudgetPage } from '@/api/budget'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const saveLoading = ref(false)
const submitLoading = ref(false)
const budgetOptions = ref<any[]>([])

const changeId = computed(() => {
  const id = route.query.id
  return id ? Number(id) : undefined
})
const isViewMode = computed(() => route.query.mode === 'view')
const isEdit = computed(() => !!changeId.value && !isViewMode.value)
const pageTitle = computed(() => {
  if (isViewMode.value) return '查看变更单'
  if (isEdit.value) return '编辑变更单'
  return '新建变更单'
})

interface DetailRow {
  subjectId?: number
  subjectName: string
  originalAmount: number
  adjustAmount: number
  adjustedAmount: number
}

const formData = ref<{
  id?: number
  projectId?: number
  budgetId?: number
  changeReason: string
  details: DetailRow[]
}>({
  id: undefined,
  projectId: undefined,
  budgetId: undefined,
  changeReason: '',
  details: []
})

const formRules = {
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
  changeReason: [{ required: true, message: '请输入变更原因', trigger: 'blur' }]
}

/** 调整后金额 = 原金额 + 调整金额 */
function calcAdjustedAmount(row: DetailRow) {
  row.adjustedAmount = Number(((row.originalAmount || 0) + (row.adjustAmount || 0)).toFixed(2))
}

/** 调整总额 = 所有明细行调整金额之和 */
const totalAdjustAmount = computed(() => {
  return Number(
    formData.value.details
      .reduce((sum, row) => sum + (row.adjustAmount || 0), 0)
      .toFixed(2)
  )
})

/** 添加明细行 */
function handleAddDetail() {
  formData.value.details.push({
    subjectName: '',
    originalAmount: 0,
    adjustAmount: 0,
    adjustedAmount: 0
  })
}

/** 删除明细行 */
function handleRemoveDetail(index: number) {
  formData.value.details.splice(index, 1)
}

/** 项目变更时加载该项目下的已批准预算 */
async function handleProjectChange(projectId: number | undefined) {
  budgetOptions.value = []
  formData.value.budgetId = undefined
  if (!projectId) return
  try {
    const res: any = await getBudgetPage({ projectId, status: 'APPROVED', pageNum: 1, pageSize: 100 })
    budgetOptions.value = res.data?.records || []
  } catch {
    // 加载预算列表失败不阻塞操作
  }
}

/** 加载变更单详情（编辑/查看模式） */
async function loadChangeDetail() {
  if (!changeId.value) return
  const res: any = await getBudgetChange(changeId.value)
  const data = res.data
  formData.value.id = data.id
  formData.value.projectId = data.projectId
  formData.value.budgetId = data.budgetId
  formData.value.changeReason = data.changeReason

  // 加载项目对应的预算选项
  if (data.projectId) {
    await handleProjectChange(data.projectId)
  }

  // 加载明细
  const detailRes: any = await getBudgetChangeDetails(changeId.value)
  formData.value.details = (detailRes.data || []).map((item: any) => ({
    subjectId: item.subjectId,
    subjectName: item.subjectName,
    originalAmount: item.originalAmount || 0,
    adjustAmount: item.adjustAmount || 0,
    adjustedAmount: item.adjustedAmount || 0
  }))
}

/** 表单校验 */
async function validateForm() {
  await formRef.value?.validate()
  if (formData.value.details.length === 0) {
    ElMessage.warning('请至少添加一条变更明细')
    return false
  }
  const hasEmptySubject = formData.value.details.some(d => !d.subjectName?.trim())
  if (hasEmptySubject) {
    ElMessage.warning('请填写所有明细行的科目名称')
    return false
  }
  return true
}

/** 构建提交数据 */
function buildSubmitData() {
  return {
    id: formData.value.id,
    projectId: formData.value.projectId,
    budgetId: formData.value.budgetId,
    changeReason: formData.value.changeReason,
    totalAdjustAmount: totalAdjustAmount.value,
    details: formData.value.details.map(d => ({
      subjectId: d.subjectId,
      subjectName: d.subjectName,
      originalAmount: d.originalAmount,
      adjustAmount: d.adjustAmount,
      adjustedAmount: d.adjustedAmount
    }))
  }
}

/** 保存草稿 */
async function handleSaveDraft() {
  const valid = await validateForm()
  if (!valid) return

  saveLoading.value = true
  try {
    const data = buildSubmitData()
    if (isEdit.value && formData.value.id) {
      await updateBudgetChange(formData.value.id, data)
      ElMessage.success('保存成功')
    } else {
      const res: any = await createBudgetChange(data)
      formData.value.id = res.data?.id
      ElMessage.success('创建成功')
    }
    router.push('/budget/change')
  } finally {
    saveLoading.value = false
  }
}

/** 提交审批 */
async function handleSubmitApproval() {
  const valid = await validateForm()
  if (!valid) return

  await ElMessageBox.confirm('提交后将进入审批流程，确定提交吗？', '提示', { type: 'warning' })

  submitLoading.value = true
  try {
    const data = buildSubmitData()
    if (isEdit.value && formData.value.id) {
      await updateBudgetChange(formData.value.id, data)
    } else {
      const res: any = await createBudgetChange(data)
      formData.value.id = res.data?.id
    }
    // 提交审批
    await submitBudgetChange(formData.value.id!)
    ElMessage.success('提交成功')
    router.push('/budget/change')
  } finally {
    submitLoading.value = false
  }
}

function handleBack() {
  router.push('/budget/change')
}

onMounted(() => {
  loadChangeDetail()
})
</script>

<style scoped>
.budget-change-form-container {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.detail-table-wrap {
  width: 100%;
}
.detail-toolbar {
  margin-bottom: 8px;
}
.detail-summary {
  margin-top: 12px;
  text-align: right;
  font-size: 14px;
}
.form-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
  text-align: center;
}
.text-danger {
  color: #f56c6c;
}
.text-success {
  color: #67c23a;
}
</style>
