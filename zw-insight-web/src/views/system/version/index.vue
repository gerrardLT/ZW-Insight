<template>
  <div class="version-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">版本管理</span>
            <el-tag v-if="currentVersion" type="success" effect="plain" class="current-tag">
              当前版本 v{{ currentVersion.versionNo }}
            </el-tag>
          </div>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建版本</el-button>
        </div>
      </template>

      <el-table :data="versionList" v-loading="loading" border>
        <el-table-column label="版本号" width="160">
          <template #default="{ row }">
            <el-tag effect="dark">v{{ row.versionNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="releaseDate" label="发布日期" width="160" />
        <el-table-column label="更新日志" min-width="320">
          <template #default="{ row }">
            <span class="changelog-summary">{{ summarize(row.changelog) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row as SysVersion)">查看日志</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无版本记录" />
        </template>
      </el-table>
    </el-card>

    <!-- 新建版本对话框 -->
    <el-dialog v-model="dialogVisible" title="新建版本" width="640px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="版本号" prop="versionNo">
          <el-input v-model="form.versionNo" placeholder="语义化版本，如 1.2.0" maxlength="20" />
          <div class="form-hint">需符合语义化版本规范 x.y.z（如 1.0.0）</div>
        </el-form-item>
        <el-form-item label="发布日期" prop="releaseDate">
          <el-date-picker
            v-model="form.releaseDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择发布日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="更新日志" prop="changelog">
          <el-input
            v-model="form.changelog"
            type="textarea"
            :rows="10"
            placeholder="支持 Markdown 格式，例如：&#10;## 新增&#10;- 功能A&#10;## 修复&#10;- 问题B"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 更新日志详情 -->
    <el-dialog v-model="detailVisible" :title="`v${detailRow?.versionNo || ''} 更新日志`" width="640px">
      <div class="changelog-detail">
        <pre>{{ detailRow?.changelog || '（无更新日志）' }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createVersion,
  getVersionList,
  getCurrentVersion,
  type SysVersion,
  type VersionCreateRequest
} from '@/api/version'

defineOptions({ name: 'VersionManage' })

const loading = ref(false)
const submitting = ref(false)
const versionList = ref<SysVersion[]>([])
const currentVersion = ref<SysVersion | null>(null)

const dialogVisible = ref(false)
const detailVisible = ref(false)
const detailRow = ref<SysVersion | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<VersionCreateRequest>({
  versionNo: '',
  releaseDate: '',
  changelog: ''
})

const rules: FormRules = {
  versionNo: [
    { required: true, message: '请输入版本号', trigger: 'blur' },
    {
      pattern: /^\d+\.\d+\.\d+$/,
      message: '版本号格式无效，需符合语义化规范 x.y.z',
      trigger: 'blur'
    }
  ],
  releaseDate: [{ required: true, message: '请选择发布日期', trigger: 'change' }]
}

/** 截取更新日志摘要（首行非空内容） */
function summarize(changelog?: string): string {
  if (!changelog) return '-'
  const firstLine = changelog
    .split('\n')
    .map((l) => l.trim())
    .find((l) => l.length > 0)
  if (!firstLine) return '-'
  return firstLine.length > 60 ? firstLine.slice(0, 60) + '…' : firstLine
}

async function loadData() {
  loading.value = true
  try {
    const [listRes, currentRes] = await Promise.all([getVersionList(), getCurrentVersion()])
    versionList.value = (listRes as any).data || []
    currentVersion.value = (currentRes as any).data || null
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.versionNo = ''
  form.releaseDate = ''
  form.changelog = ''
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function openDetail(row: SysVersion) {
  detailRow.value = row
  detailVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    await createVersion({
      versionNo: form.versionNo.trim(),
      releaseDate: form.releaseDate,
      changelog: form.changelog
    })
    ElMessage.success('版本创建成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.version-container {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.title {
  font-weight: 600;
  font-size: 15px;
}
.changelog-summary {
  color: var(--el-text-color-regular);
}
.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.changelog-detail pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  margin: 0;
  line-height: 1.7;
  color: var(--el-text-color-primary);
}
</style>
