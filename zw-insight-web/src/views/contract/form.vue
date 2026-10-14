<template>
  <div class="contract-form-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isView ? '施工合同详情' : (isEdit ? '编辑施工合同' : '新增施工合同') }}</span>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" v-loading="pageLoading" :model="formData" :rules="formRules" label-width="120px" style="max-width: 900px" :disabled="isView">
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

        <div class="detail-toolbar" v-if="!isView">
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
          <el-table-column v-if="!isView" label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeDetailRow($index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">合同附件与正本扫描件</el-divider>

        <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
          <span style="font-size: 13px; color: var(--zw-text-secondary);">支持上传 PDF 扫描件、签字盖章图片或合同电子档</span>
          <el-upload
            v-if="!isView"
            :show-file-list="false"
            :before-upload="handleUploadContractFile"
            :disabled="uploadingFile"
          >
            <el-button size="small" type="primary" :loading="uploadingFile">
              上传合同附件
            </el-button>
          </el-upload>
        </div>

        <el-table :data="contractFileList" border size="small" style="width: 100%; margin-bottom: 16px;">
          <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="fileSize" label="大小 (Bytes)" width="120" align="right" />
          <el-table-column prop="createdAt" label="上传时间" width="160" />
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row, $index }">
              <el-link type="primary" :underline="false" style="margin-right: 12px;" @click="openContractFile(row)">预览</el-link>
              <el-link v-if="!isView" type="danger" :underline="false" @click="removeContractFile(row, $index)">删除</el-link>
            </template>
          </el-table-column>
        </el-table>

        <el-form-item v-if="!isView" style="margin-top: var(--zw-space-lg)">
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
import { uploadFile, deleteFile, getFileListByBusiness, bindBusinessFiles } from '@/api/file'
import { getFilePreviewUrl } from '@/api/batch'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const pageLoading = ref(false) // 编辑模式详情回填期间整体遮罩防误操作
const uploadingFile = ref(false)
const projectList = ref<any[]>([])
const detailList = ref<any[]>([])
const contractFileList = ref<any[]>([])

const isEdit = computed(() => !!route.params.id)
// 只读查看模式（2026-08-14 P0 修复盲点 3：列表「查看」携 ?mode=view 进入，
// 表单整体禁用 + 隐藏保存/新增明细入口；缺省行为不变）
const isView = computed(() => route.query.mode === 'view')

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

function handleProjectChange(id: number | string) {
  const project = projectList.value.find(p => String(p.id) === String(id))
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
  pageLoading.value = true
  try {
    // 雪花 ID 超出 Number 安全整数范围，必须以字符串传递避免精度丢失
    const contractId = route.params.id as string
    const res: any = await getContractDetail(contractId)
    formData.value = res.data || {}
    // 确保项目出现在选项中
    if (formData.value.projectId) {
      projectList.value = [{ id: formData.value.projectId, projectName: formData.value.projectName }]
    }
    // 加载明细
    const detailRes: any = await getContractDetails(contractId)
    detailList.value = detailRes.data || []
    // 加载合同已上传文件
    await loadContractFiles(contractId)
  } finally {
    pageLoading.value = false
  }
}

async function loadContractFiles(contractId: number | string) {
  try {
    const res: any = await getFileListByBusiness('CONTRACT', contractId)
    contractFileList.value = res.data || []
  } catch {
    contractFileList.value = []
  }
}

async function handleUploadContractFile(file: File) {
  uploadingFile.value = true
  try {
    const contractId = formData.value.id || undefined
    const projectId = formData.value.projectId || undefined
    const res: any = await uploadFile(file, 'CONTRACT', contractId, projectId)
    if (res.data) {
      contractFileList.value.push(res.data)
      ElMessage.success('合同附件上传成功')
    }
  } catch (e: any) {
    ElMessage.error('附件上传失败：' + (e?.message || '网络异常'))
  } finally {
    uploadingFile.value = false
  }
  return false
}

async function removeContractFile(row: any, idx: number) {
  if (row.id) {
    try {
      await deleteFile(row.id)
      contractFileList.value.splice(idx, 1)
      ElMessage.success('附件已删除')
    } catch (e: any) {
      ElMessage.error('删除失败：' + (e?.message || '网络异常'))
    }
  } else {
    contractFileList.value.splice(idx, 1)
  }
}

async function openContractFile(row: any) {
  if (row.id) {
    try {
      const res: any = await getFilePreviewUrl(row.id)
      if (res.data) window.open(res.data, '_blank')
      else ElMessage.warning('暂无可用的预览地址')
    } catch (e: any) {
      ElMessage.error('获取预览地址失败：' + (e?.message || '接口异常'))
    }
  }
}

async function handleSubmit() {
  if (isView.value) return // 只读模式守卫：双保险，防绕过 UI 直接调用
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    let contractId: number | string
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
    // 闭环：回填绑定新建期间上传的合同附件（消除孤儿文件）
    const uncommittedFileIds = contractFileList.value
      .map(f => f.id)
      .filter((id): id is number => id !== undefined)
    if (uncommittedFileIds.length > 0) {
      try {
        await bindBusinessFiles({
          fileIds: uncommittedFileIds,
          businessType: 'CONTRACT',
          businessId: contractId!,
          projectId: formData.value.projectId
        })
      } catch (err: any) {
        console.warn('回填绑定合同附件异常', err)
      }
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
  padding: var(--zw-space-md);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.detail-toolbar {
  margin-bottom: var(--zw-space-sm-md);
}
</style>
