<template>
  <div class="contract-form-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? '编辑施工合同' : '新增施工合同' }}</span>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px" style="max-width: 900px">
        <el-divider content-position="left">合同基本信息</el-divider>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属项目" prop="projectId">
              <el-select
                v-model="formData.projectId"
                placeholder="请选择项目"
                filterable
                remote
                :remote-method="searchProject"
                style="width: 100%"
                @change="handleProjectChange"
              >
                <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同编号">
              <el-input v-model="formData.contractCode" placeholder="系统自动生成" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="合同类型">
              <el-select v-model="formData.contractType" style="width: 100%">
                <el-option label="登记合同" value="REGISTER" />
                <el-option label="变更合同" value="CHANGE" />
                <el-option label="补充合同" value="SUPPLEMENT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="甲方名称" prop="partyAName">
              <el-input v-model="formData.partyAName" placeholder="请输入甲方名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="签订日期">
              <el-date-picker v-model="formData.signingDate" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同金额" prop="contractAmount">
              <el-input-number v-model="formData.contractAmount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开工日期">
              <el-date-picker v-model="formData.startDate" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="竣工日期">
              <el-date-picker v-model="formData.endDate" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="税率(%)">
              <el-input-number v-model="formData.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 明细清单 -->
        <el-divider content-position="left">合同明细</el-divider>

        <div class="detail-toolbar">
          <el-button type="primary" size="small" @click="addDetailRow">
            <el-icon><Plus /></el-icon>新增明细
          </el-button>
        </div>

        <el-table :data="detailList" border size="small">
          <el-table-column label="项目名称" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.itemName" placeholder="项目名称" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="规格" width="120">
            <template #default="{ row }">
              <el-input v-model="row.specification" placeholder="规格" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="80">
            <template #default="{ row }">
              <el-input v-model="row.unit" placeholder="单位" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="4" size="small" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="4" size="small" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="合计" width="120">
            <template #default="{ row }">
              {{ ((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeDetailRow($index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-form-item style="margin-top: 24px">
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
          <el-button @click="handleBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getContractDetail, createContract, updateContract, getContractDetails, saveContractDetails } from '@/api/contract'
import { getProjectList } from '@/api/project'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const projectList = ref<any[]>([])
const detailList = ref<any[]>([])

const isEdit = computed(() => !!route.params.id)

const formData = ref({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  projectName: '',
  contractCode: '',
  contractType: 'REGISTER',
  partyAName: '',
  signingDate: '',
  startDate: '',
  endDate: '',
  contractAmount: 0,
  taxRate: 9,
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  partyAName: [{ required: true, message: '请输入甲方名称', trigger: 'blur' }],
  contractAmount: [{ required: true, message: '请输入合同金额', trigger: 'blur' }]
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectList.value = res.data || []
}

function handleProjectChange(id: number) {
  const project = projectList.value.find(p => p.id === id)
  formData.value.projectName = project?.projectName || ''
}

function addDetailRow() {
  detailList.value.push({
    itemName: '',
    specification: '',
    unit: '',
    quantity: 0,
    unitPrice: 0
  })
}

function removeDetailRow(index: number) {
  detailList.value.splice(index, 1)
}

async function loadDetail() {
  if (!route.params.id) return
  const contractId = Number(route.params.id)
  const res: any = await getContractDetail(contractId)
  formData.value = res.data || {}
  // 确保项目出现在选项中
  if (formData.value.projectId) {
    projectList.value = [{ id: formData.value.projectId, projectName: formData.value.projectName }]
  }
  // 加载明细
  const detailRes: any = await getContractDetails(contractId)
  detailList.value = detailRes.data || []
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    let contractId: number
    if (formData.value.id) {
      await updateContract({ ...formData.value, id: formData.value.id!, projectId: formData.value.projectId! })
      contractId = formData.value.id
      ElMessage.success('更新成功')
    } else {
      const res: any = await createContract({ ...formData.value, projectId: formData.value.projectId! })
      contractId = res.data?.id || res.data
      ElMessage.success('新增成功')
    }
    // 保存明细
    if (detailList.value.length > 0) {
      await saveContractDetails(contractId!, detailList.value)
    }
    router.push('/contract/list')
  } finally {
    submitLoading.value = false
  }
}

function handleBack() {
  router.push('/contract/list')
}

onMounted(() => {
  searchProject('')
  loadDetail()
})
</script>

<style scoped>
.contract-form-container {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.detail-toolbar {
  margin-bottom: 12px;
}
</style>
