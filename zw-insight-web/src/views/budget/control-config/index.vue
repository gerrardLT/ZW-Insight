<template>
  <div class="control-config-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-input v-model="queryParams.projectName" placeholder="项目名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="控制模式">
          <el-select v-model="queryParams.controlMode" placeholder="全部" clearable style="width: 140px">
            <el-option label="仅提醒" value="WARN_ONLY" />
            <el-option label="禁止提交" value="BLOCK" />
            <el-option label="免控" value="EXEMPT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新建规则</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.projectName || '全局默认' }}
          </template>
        </el-table-column>
        <el-table-column prop="controlMode" label="控制模式" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="controlModeTagType(row.controlMode)" size="small">
              {{ controlModeLabel(row.controlMode) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warningThreshold" label="预警阈值(%)" width="130" align="center">
          <template #default="{ row }">
            {{ row.warningThreshold }}%
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑规则' : '新建规则'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目" prop="projectId">
          <ProjectSelector v-model="formData.projectId" @change="handleProjectChange" />
          <div class="form-tip">留空表示全局默认配置</div>
        </el-form-item>
        <el-form-item label="控制模式" prop="controlMode">
          <el-select v-model="formData.controlMode" placeholder="请选择控制模式" style="width: 100%">
            <el-option label="仅提醒" value="WARN_ONLY" />
            <el-option label="禁止提交" value="BLOCK" />
            <el-option label="免控" value="EXEMPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警阈值" prop="warningThreshold">
          <el-slider
            v-model="formData.warningThreshold"
            :min="50"
            :max="99"
            :step="1"
            show-input
            :show-input-controls="false"
            input-size="small"
          />
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
  listBudgetControlConfigs,
  createBudgetControlConfig,
  updateBudgetControlConfig,
  deleteBudgetControlConfig
} from '@/api/budget-control-config'
import ProjectSelector from '@/components/ProjectSelector.vue'

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
  projectName: '',
  controlMode: ''
})

const formData = ref({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  projectName: '',
  controlMode: 'BLOCK',
  warningThreshold: 80
})

const formRules = {
  controlMode: [{ required: true, message: '请选择控制模式', trigger: 'change' }],
  warningThreshold: [{ required: true, message: '请设置预警阈值', trigger: 'change' }]
}

function controlModeLabel(mode: string): string {
  const map: Record<string, string> = {
    WARN_ONLY: '仅提醒',
    BLOCK: '禁止提交',
    EXEMPT: '免控'
  }
  return map[mode] || mode
}

function controlModeTagType(mode: string): 'success' | 'primary' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'success' | 'primary' | 'warning' | 'info' | 'danger'> = {
    WARN_ONLY: 'warning',
    BLOCK: 'danger',
    EXEMPT: 'success'
  }
  return map[mode] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await listBudgetControlConfigs(queryParams.value)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.value = { pageNum: 1, pageSize: 10, projectName: '', controlMode: '' }
  loadData()
}

function handleAdd() {
  isEdit.value = false
  formData.value = { id: undefined, projectId: undefined, projectName: '', controlMode: 'BLOCK', warningThreshold: 80 }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.value = { ...row }
  dialogVisible.value = true
}

function handleProjectChange(_val: number | undefined, item: any) {
  formData.value.projectName = item?.projectName || ''
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const payload = {
      projectId: formData.value.projectId || null,
      controlMode: formData.value.controlMode,
      warningThreshold: formData.value.warningThreshold
    }
    if (isEdit.value) {
      await updateBudgetControlConfig(formData.value.id!, payload)
      ElMessage.success('更新成功')
    } else {
      await createBudgetControlConfig(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该控制规则吗？删除后将回落为全局默认规则。', '提示', { type: 'warning' })
  await deleteBudgetControlConfig(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.control-config-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
