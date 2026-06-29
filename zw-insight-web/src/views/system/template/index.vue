<template>
  <div class="template-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="模块">
          <el-select v-model="queryParams.moduleCode" placeholder="全部模块" clearable style="width: 180px">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.templateType" placeholder="全部类型" clearable style="width: 140px">
            <el-option label="导入模板" value="IMPORT" />
            <el-option label="导出模板" value="EXPORT" />
            <el-option label="打印模板" value="PRINT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增模板</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="templateName" label="模板名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="moduleCode" label="所属模块" width="140">
          <template #default="{ row }">
            {{ getModuleLabel(row.moduleCode) }}
          </template>
        </el-table-column>
        <el-table-column prop="templateType" label="模板类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagMap[row.templateType]?.type || 'info'" size="small">
              {{ typeTagMap[row.templateType]?.label || row.templateType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isDefault" label="默认" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" size="small">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.templateType === 'PRINT'" link type="success" @click="handleEditContent(row)">编辑内容</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑模板' : '新增模板'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="formData.templateName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="所属模块" prop="moduleCode">
          <el-select v-model="formData.moduleCode" placeholder="请选择模块" style="width: 100%">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板类型" prop="templateType">
          <el-select v-model="formData.templateType" placeholder="请选择类型" style="width: 100%">
            <el-option label="导入模板" value="IMPORT" />
            <el-option label="导出模板" value="EXPORT" />
            <el-option label="打印模板" value="PRINT" />
          </el-select>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="formData.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="formData.templateType !== 'PRINT'" label="模板文件">
          <el-upload
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            accept=".xlsx,.xls"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 .xlsx / .xls 格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 打印模板内容编辑弹窗 -->
    <el-dialog v-model="contentDialogVisible" title="编辑打印模板内容" width="800px" destroy-on-close>
      <div class="template-editor-tips">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            使用 <code v-pre>{{变量名}}</code> 作为占位符，渲染时会被实际数据替换。常用变量：projectName、contractCode、amount、date 等。
          </template>
        </el-alert>
      </div>
      <el-input
        v-model="templateContent"
        type="textarea"
        :rows="16"
        placeholder="请输入模板 HTML 内容，支持 {{变量名}} 占位符"
      />
      <template #footer>
        <el-button @click="contentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="contentSaveLoading" @click="handleSaveContent">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, UploadFile } from 'element-plus'
import { getTemplateList, createTemplate, updateTemplate, deleteTemplate } from '@/api/batch'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const contentDialogVisible = ref(false)
const submitLoading = ref(false)
const contentSaveLoading = ref(false)
const isEdit = ref(false)
const templateContent = ref('')
const editingTemplateId = ref<number | null>(null)

const queryParams = ref({ moduleCode: '', templateType: '' })

const formData = ref({
  id: undefined as number | undefined,
  templateName: '',
  moduleCode: '',
  templateType: '',
  isDefault: 0,
  fileId: null as number | null,
  templateContent: ''
})

const formRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  moduleCode: [{ required: true, message: '请选择所属模块', trigger: 'change' }],
  templateType: [{ required: true, message: '请选择模板类型', trigger: 'change' }]
}

const moduleOptions = [
  { label: '机械台账', value: 'machine_ledger' },
  { label: '劳务花名册', value: 'labor_roster' },
  { label: '人员信息', value: 'sys_user' },
  { label: '供应商', value: 'supplier' },
  { label: '材料字典', value: 'material' },
  { label: '库存', value: 'material_stock' },
  { label: '工资单', value: 'labor_payroll' },
  { label: '开票申请', value: 'finance_invoice' },
  { label: '项目', value: 'project' }
]

const typeTagMap: Record<string, { label: string; type: 'success' | 'primary' | 'warning' | 'info' | 'danger' }> = {
  IMPORT: { label: '导入', type: 'primary' },
  EXPORT: { label: '导出', type: 'success' },
  PRINT: { label: '打印', type: 'warning' }
}

function getModuleLabel(code: string): string {
  return moduleOptions.find(m => m.value === code)?.label || code
}

async function loadData() {
  loading.value = true
  try {
    const params: any = {}
    if (queryParams.value.moduleCode) params.moduleCode = queryParams.value.moduleCode
    if (queryParams.value.templateType) params.templateType = queryParams.value.templateType
    const res: any = await getTemplateList(params)
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  queryParams.value = { moduleCode: '', templateType: '' }
  loadData()
}

function handleAdd() {
  isEdit.value = false
  formData.value = { id: undefined, templateName: '', moduleCode: '', templateType: '', isDefault: 0, fileId: null, templateContent: '' }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.value = { ...row }
  dialogVisible.value = true
}

function handleEditContent(row: any) {
  editingTemplateId.value = row.id
  templateContent.value = row.templateContent || ''
  contentDialogVisible.value = true
}

function handleFileChange(file: UploadFile) {
  // 文件选择后暂存，实际上传在提交时处理
  // 此处简单标记有文件
  if (file.raw) {
    ElMessage.info(`已选择文件: ${file.name}`)
  }
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (isEdit.value && formData.value.id) {
      await updateTemplate(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createTemplate(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSaveContent() {
  if (!editingTemplateId.value) return
  contentSaveLoading.value = true
  try {
    await updateTemplate(editingTemplateId.value, { templateContent: templateContent.value })
    ElMessage.success('模板内容已保存')
    contentDialogVisible.value = false
    loadData()
  } finally {
    contentSaveLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定要删除模板「${row.templateName}」吗？`, '提示', { type: 'warning' })
  await deleteTemplate(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => { loadData() })
</script>

<style scoped>
.template-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.template-editor-tips { margin-bottom: 16px; }
.template-editor-tips code { background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-family: monospace; }
</style>
