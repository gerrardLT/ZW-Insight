<template>
  <div class="settlement-create-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>新建机械结算单</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" style="max-width: 600px">
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="formData.projectId"
            placeholder="请选择项目"
            filterable
            style="width: 100%"
            @change="handleProjectChange"
          >
            <el-option
              v-for="item in projectList"
              :key="item.id"
              :label="item.projectName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="结算周期" prop="period">
          <el-date-picker
            v-model="formData.period"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            @change="handlePeriodChange"
          />
        </el-form-item>
      </el-form>

      <!-- 机械明细预览 -->
      <div v-if="previewVisible" class="preview-section">
        <el-divider content-position="left">机械使用明细预览</el-divider>
        <el-table :data="previewData" v-loading="previewLoading" border size="small">
          <el-table-column prop="machineName" label="机械名称" min-width="140" />
          <el-table-column prop="machineModel" label="规格型号" width="120" />
          <el-table-column prop="workDays" label="工作天数" width="100" align="center" />
          <el-table-column prop="unitPrice" label="单价(元)" width="120" align="right">
            <template #default="{ row }">{{ row.unitPrice?.toLocaleString() }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额(元)" width="120" align="right">
            <template #default="{ row }">{{ row.amount?.toLocaleString() }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        </el-table>
        <div class="preview-total">
          <span>合计金额：</span>
          <span class="total-amount">¥ {{ totalAmount.toLocaleString() }}</span>
        </div>
      </div>

      <div class="form-actions">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSave">保存结算单</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { createMachineSettlement, getMachineUsagePage } from '@/api/machine'
import { getProjectList } from '@/api/project'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const previewLoading = ref(false)
const previewVisible = ref(false)
const previewData = ref<any[]>([])
const projectList = ref<any[]>([])

const formData = ref({
  projectId: undefined as number | undefined,
  period: null as string[] | null
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  period: [{ required: true, message: '请选择结算周期', trigger: 'change' }]
}

const totalAmount = computed(() => {
  return previewData.value.reduce((sum, item) => sum + (item.amount || 0), 0)
})

async function loadProjects() {
  try {
    const res: any = await getProjectList()
    projectList.value = res.data || []
  } catch {
    // 加载失败不阻断
  }
}

function handleProjectChange() {
  loadPreview()
}

function handlePeriodChange() {
  loadPreview()
}

async function loadPreview() {
  if (!formData.value.projectId || !formData.value.period || formData.value.period.length !== 2) {
    previewVisible.value = false
    previewData.value = []
    return
  }
  previewVisible.value = true
  previewLoading.value = true
  try {
    const res: any = await getMachineUsagePage({
      projectId: formData.value.projectId,
      startDate: formData.value.period[0],
      endDate: formData.value.period[1],
      pageNum: 1,
      pageSize: 200
    })
    previewData.value = res.data?.records || []
  } catch {
    previewData.value = []
  } finally {
    previewLoading.value = false
  }
}

async function handleSave() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createMachineSettlement({
      projectId: formData.value.projectId,
      periodStart: formData.value.period![0],
      periodEnd: formData.value.period![1]
    })
    ElMessage.success('结算单创建成功')
    router.push('/machine/settlement')
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
.settlement-create-container {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.preview-section {
  margin-top: 24px;
}
.preview-total {
  margin-top: 12px;
  text-align: right;
  font-size: 16px;
}
.total-amount {
  font-weight: bold;
  color: #409eff;
  font-size: 18px;
}
.form-actions {
  margin-top: 32px;
  text-align: center;
}
</style>
