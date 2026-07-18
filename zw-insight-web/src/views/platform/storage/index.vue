<template>
  <div class="platform-container">
    <el-card shadow="never">
      <el-alert
        title="存储配置用于文件上传的目标存储（本地/MinIO/阿里云/腾讯云/七牛），密钥请妥善保管"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增存储配置
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column label="存储类型" width="140">
          <template #default="{ row }">{{ storageTypeMap[row.storageType] || row.storageType }}</template>
        </el-table-column>
        <el-table-column prop="endpoint" label="端点地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="bucket" label="存储桶" width="150" show-overflow-tooltip />
        <el-table-column prop="basePath" label="基础路径" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="存储类型" prop="storageType">
          <el-select v-model="formData.storageType" placeholder="请选择存储类型" style="width: 100%">
            <el-option label="本地存储" value="LOCAL" />
            <el-option label="MinIO" value="MINIO" />
            <el-option label="阿里云 OSS" value="ALIYUN" />
            <el-option label="腾讯云 COS" value="TENCENT" />
            <el-option label="七牛云" value="QINIU" />
          </el-select>
        </el-form-item>
        <el-form-item label="端点地址">
          <el-input v-model="formData.endpoint" placeholder="如 https://oss-cn-hangzhou.aliyuncs.com" />
        </el-form-item>
        <el-form-item label="AccessKey">
          <el-input v-model="formData.accessKey" placeholder="访问密钥" />
        </el-form-item>
        <el-form-item label="SecretKey">
          <el-input v-model="formData.secretKey" type="password" show-password placeholder="密钥" />
        </el-form-item>
        <el-form-item label="存储桶">
          <el-input v-model="formData.bucket" placeholder="Bucket 名称" />
        </el-form-item>
        <el-form-item label="基础路径">
          <el-input v-model="formData.basePath" placeholder="如 /uploads" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" />
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getStoragePage,
  createStorage,
  updateStorage,
  deleteStorage,
  type FileStorage
} from '@/api/file'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('新增存储配置')
const submitLoading = ref(false)

const storageTypeMap: Record<string, string> = {
  LOCAL: '本地存储',
  MINIO: 'MinIO',
  ALIYUN: '阿里云 OSS',
  TENCENT: '腾讯云 COS',
  QINIU: '七牛云'
}

const queryParams = ref({
  page: 1,
  size: 10
})

const formData = ref<FileStorage>({
  id: undefined,
  storageType: 'LOCAL',
  endpoint: '',
  accessKey: '',
  secretKey: '',
  bucket: '',
  basePath: '',
  status: 1
})

const formRules = {
  storageType: [{ required: true, message: '请选择存储类型', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getStoragePage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  dialogTitle.value = '新增存储配置'
  formData.value = { id: undefined, storageType: 'LOCAL', endpoint: '', accessKey: '', secretKey: '', bucket: '', basePath: '', status: 1 }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑存储配置'
  formData.value = { ...row }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateStorage(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createStorage(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该存储配置吗？', '提示', { type: 'warning' })
  await deleteStorage(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.platform-container {
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
