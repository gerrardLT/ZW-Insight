<template>
  <div class="project-form-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? '编辑项目' : '新增项目' }}</span>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px" style="max-width: 900px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目名称" prop="projectName">
              <el-input v-model="formData.projectName" placeholder="请输入项目名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目编号">
              <el-input v-model="formData.projectCode" placeholder="系统自动生成" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目性质" prop="projectNature">
              <el-select v-model="formData.projectNature" placeholder="请选择项目性质" style="width: 100%">
                <el-option label="新建" value="新建" />
                <el-option label="改造" value="改造" />
                <el-option label="扩建" value="扩建" />
                <el-option label="维修" value="维修" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目类型" prop="projectType">
              <el-select v-model="formData.projectType" placeholder="请选择项目类型" style="width: 100%">
                <el-option label="市政工程" value="市政工程" />
                <el-option label="房建工程" value="房建工程" />
                <el-option label="公路工程" value="公路工程" />
                <el-option label="水利工程" value="水利工程" />
                <el-option label="装饰工程" value="装饰工程" />
                <el-option label="其他" value="其他" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="业主单位" prop="ownerCompanyId">
              <el-select
                v-model="formData.ownerCompanyId"
                placeholder="请选择业主单位"
                filterable
                remote
                :remote-method="searchOwner"
                :loading="ownerLoading"
                style="width: 100%"
                @change="handleOwnerChange"
              >
                <el-option v-for="item in ownerList" :key="item.id" :label="item.ownerName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签约公司" prop="signingCompanyId">
              <el-select
                v-model="formData.signingCompanyId"
                placeholder="请选择签约公司"
                filterable
                style="width: 100%"
                @change="handleCompanyChange"
              >
                <el-option v-for="item in companyList" :key="item.id" :label="item.companyName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="项目概述">
          <el-input v-model="formData.projectOverview" type="textarea" :rows="3" placeholder="请输入项目概述" />
        </el-form-item>

        <el-form-item label="项目地址">
          <el-input v-model="formData.projectAddress" placeholder="请输入项目地址" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="formData.contactName" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="预算金额">
              <el-input-number v-model="formData.budgetAmount" :min="0" :precision="2" style="width: 100%" placeholder="请输入预算金额" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否招标">
              <el-radio-group v-model="formData.needTender">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item>
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
import { getProjectDetail, createProject, updateProject, getOwnerList, getCompanyList } from '@/api/project'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const ownerLoading = ref(false)
const ownerList = ref<any[]>([])
const companyList = ref<any[]>([])

const isEdit = computed(() => !!route.params.id)

const formData = ref({
  id: undefined as number | undefined,
  projectCode: '',
  projectName: '',
  projectNature: '',
  projectType: '',
  ownerCompanyId: undefined as number | undefined,
  ownerCompanyName: '',
  signingCompanyId: undefined as number | undefined,
  signingCompanyName: '',
  projectOverview: '',
  projectAddress: '',
  contactName: '',
  contactPhone: '',
  budgetAmount: 0,
  needTender: 0
})

const formRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectNature: [{ required: true, message: '请选择项目性质', trigger: 'change' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  ownerCompanyId: [{ required: true, message: '请选择业主单位', trigger: 'change' }],
  signingCompanyId: [{ required: true, message: '请选择签约公司', trigger: 'change' }]
}

async function searchOwner(query: string) {
  ownerLoading.value = true
  try {
    const res: any = await getOwnerList({ ownerName: query })
    ownerList.value = res.data || []
  } finally {
    ownerLoading.value = false
  }
}

function handleOwnerChange(id: number) {
  const owner = ownerList.value.find(o => o.id === id)
  formData.value.ownerCompanyName = owner?.ownerName || ''
}

function handleCompanyChange(id: number) {
  const company = companyList.value.find(c => c.id === id)
  formData.value.signingCompanyName = company?.companyName || ''
}

async function loadCompanyList() {
  const res: any = await getCompanyList()
  companyList.value = res.data || []
}

async function loadDetail() {
  if (!route.params.id) return
  const res: any = await getProjectDetail(Number(route.params.id))
  formData.value = res.data || {}
  // 确保业主单位出现在选项中
  if (formData.value.ownerCompanyId) {
    ownerList.value = [{ id: formData.value.ownerCompanyId, ownerName: formData.value.ownerCompanyName }]
  }
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateProject(formData.value)
      ElMessage.success('更新成功')
    } else {
      await createProject(formData.value)
      ElMessage.success('新增成功')
    }
    router.push('/project/list')
  } finally {
    submitLoading.value = false
  }
}

function handleBack() {
  router.push('/project/list')
}

onMounted(() => {
  loadCompanyList()
  loadDetail()
})
</script>

<style scoped>
.project-form-container {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
