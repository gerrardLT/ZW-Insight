<template>
  <div class="announcement-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="标题">
          <el-input v-model="queryParams.title" placeholder="公告标题" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已撤回" value="REVOKED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增公告</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="发布状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column prop="publishTime" label="发布时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)" :disabled="row.status === 'PUBLISHED'">编辑</el-button>
            <el-button link type="success" @click="handlePublish(row)" :disabled="row.status === 'PUBLISHED'">发布</el-button>
            <el-button link type="warning" @click="handleRevoke(row)" :disabled="row.status !== 'PUBLISHED'">撤回</el-button>
            <el-button link type="danger" @click="handleDelete(row)" :disabled="row.status === 'PUBLISHED'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑公告' : '新增公告'" width="650px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="formData.content" type="textarea" :rows="5" placeholder="请输入公告内容" />
        </el-form-item>
        <el-form-item label="发布范围" prop="scope">
          <el-select v-model="formData.scope" style="width: 100%">
            <el-option label="全部" value="ALL" />
            <el-option label="指定部门" value="DEPARTMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="formData.isTop" />
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
  getAnnouncementPage,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  publishAnnouncement,
  revokeAnnouncement
} from '@/api/message'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  title: '',
  status: ''
})

const formData = ref({
  id: undefined as number | undefined,
  title: '',
  content: '',
  scope: 'ALL',
  isTop: false
})

const formRules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
  scope: [{ required: true, message: '请选择发布范围', trigger: 'change' }]
}

function statusLabel(status: string) {
  const map: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', REVOKED: '已撤回' }
  return map[status] || status
}

function statusTagType(status: string) {
  const map: Record<string, string> = { DRAFT: 'info', PUBLISHED: 'success', REVOKED: 'warning' }
  return map[status] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getAnnouncementPage(queryParams.value)
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
  queryParams.value = { pageNum: 1, pageSize: 10, title: '', status: '' }
  loadData()
}

function handleAdd() {
  isEdit.value = false
  formData.value = { id: undefined, title: '', content: '', scope: 'ALL', isTop: false }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.value = { id: row.id, title: row.title, content: row.content, scope: row.scope || 'ALL', isTop: row.isTop || false }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (isEdit.value && formData.value.id) {
      await updateAnnouncement(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createAnnouncement(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handlePublish(row: any) {
  await ElMessageBox.confirm('确定要发布该公告吗？', '提示', { type: 'warning' })
  await publishAnnouncement(row.id)
  ElMessage.success('发布成功')
  loadData()
}

async function handleRevoke(row: any) {
  await ElMessageBox.confirm('确定要撤回该公告吗？', '提示', { type: 'warning' })
  await revokeAnnouncement(row.id)
  ElMessage.success('撤回成功')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该公告吗？', '提示', { type: 'warning' })
  await deleteAnnouncement(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.announcement-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
