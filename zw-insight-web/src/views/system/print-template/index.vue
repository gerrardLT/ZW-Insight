<template>
  <div class="print-template-container">
    <el-card shadow="never">
      <!-- 查询栏 -->
      <el-form :model="queryParams" inline>
        <el-form-item label="模板名称">
          <el-input v-model="queryParams.templateName" placeholder="模板名称" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="queryParams.businessType" placeholder="全部业务类型" clearable style="width: 160px">
            <el-option v-for="item in businessTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增模板
        </el-button>
      </div>

      <!-- 列表 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="templateName" label="模板名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="businessType" label="业务类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ getBusinessTypeLabel(row.businessType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="engineType" label="渲染引擎" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.engineType === 'THYMELEAF' ? 'success' : 'warning'">
              {{ row.engineType || 'SIMPLE' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="moduleCode" label="所属模块" width="140" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" :loading="previewLoadingId === row.id" @click="handlePreview(row)">预览</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑打印模板' : '新增打印模板'" width="860px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" v-loading="detailLoading">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板名称" prop="templateName">
              <el-input v-model="formData.templateName" placeholder="请输入模板名称" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务类型" prop="businessType">
              <el-select v-model="formData.businessType" placeholder="请选择业务类型" style="width: 100%">
                <el-option v-for="item in businessTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="渲染引擎" prop="engineType">
              <el-select v-model="formData.engineType" placeholder="请选择渲染引擎" style="width: 100%">
                <el-option label="占位符 (SIMPLE)" value="SIMPLE" />
                <el-option label="Thymeleaf" value="THYMELEAF" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属模块">
              <el-input v-model="formData.moduleCode" placeholder="可选，如 finance_invoice" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="模板内容" prop="templateContent">
          <div class="editor-tips">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>
                <span v-if="formData.engineType === 'THYMELEAF'">
                  支持 Thymeleaf 语法（<code v-pre>th:text</code>、<code v-pre>th:each</code>、<code v-pre>th:if</code> 等），编辑区为等宽字体 HTML 编辑器。
                </span>
                <span v-else>
                  使用 <code v-pre>{{变量名}}</code> 作为占位符，渲染时由实际业务数据替换。编辑区为等宽字体 HTML 编辑器。
                </span>
              </template>
            </el-alert>
          </div>
          <el-input
            v-model="formData.templateContent"
            type="textarea"
            class="code-editor"
            :rows="16"
            spellcheck="false"
            placeholder="请输入模板 HTML 内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getPrintTemplatePage,
  getPrintTemplateDetail,
  createPrintTemplate,
  updatePrintTemplate,
  deletePrintTemplate,
  renderPrintTemplate
} from '@/api/print-template'
import type { PrintTemplate } from '@/api/print-template'

const formRef = ref<FormInstance>()
const loading = ref(false)
const detailLoading = ref(false)
const submitLoading = ref(false)
const previewLoadingId = ref<number | null>(null)
const tableData = ref<PrintTemplate[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)

const businessTypeOptions = [
  { label: '合同', value: 'CONTRACT' },
  { label: '预算', value: 'BUDGET' },
  { label: '材料', value: 'MATERIAL' },
  { label: '财务', value: 'FINANCE' },
  { label: '劳务', value: 'LABOR' },
  { label: '机械', value: 'MACHINE' }
]

function getBusinessTypeLabel(value?: string): string {
  if (!value) return '-'
  return businessTypeOptions.find(item => item.value === value)?.label || value
}

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  templateName: '',
  businessType: ''
})

function createEmptyForm(): PrintTemplate {
  return {
    id: undefined,
    templateName: '',
    templateType: 'PRINT',
    businessType: '',
    engineType: 'THYMELEAF',
    moduleCode: '',
    templateContent: ''
  }
}

const formData = ref<PrintTemplate>(createEmptyForm())

const formRules: FormRules = {
  templateName: [
    { required: true, message: '请输入模板名称', trigger: 'blur' },
    { max: 100, message: '模板名称长度不超过100个字符', trigger: 'blur' }
  ],
  businessType: [{ required: true, message: '请选择业务类型', trigger: 'change' }],
  engineType: [{ required: true, message: '请选择渲染引擎', trigger: 'change' }],
  templateContent: [{ required: true, message: '请输入模板内容', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const params: any = {
      pageNum: queryParams.value.pageNum,
      pageSize: queryParams.value.pageSize,
      templateType: 'PRINT'
    }
    if (queryParams.value.businessType) params.businessType = queryParams.value.businessType
    const res: any = await getPrintTemplatePage(params)
    let records: PrintTemplate[] = res.data?.records || []
    // 模板名称为前端过滤（后端列表接口未提供名称模糊查询参数）
    const keyword = queryParams.value.templateName?.trim()
    if (keyword) {
      records = records.filter(item => item.templateName?.includes(keyword))
    }
    tableData.value = records
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
  queryParams.value = { pageNum: 1, pageSize: 10, templateName: '', businessType: '' }
  loadData()
}

function handleAdd() {
  isEdit.value = false
  formData.value = createEmptyForm()
  dialogVisible.value = true
}

async function handleEdit(row: PrintTemplate) {
  isEdit.value = true
  dialogVisible.value = true
  detailLoading.value = true
  formData.value = { ...createEmptyForm(), ...row }
  try {
    // 列表不含 templateContent，编辑时拉取完整详情
    const res: any = await getPrintTemplateDetail(row.id as number)
    if (res.data) {
      formData.value = { ...createEmptyForm(), ...res.data }
    }
  } finally {
    detailLoading.value = false
  }
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const payload: PrintTemplate = { ...formData.value, templateType: 'PRINT' }
    if (isEdit.value && formData.value.id) {
      await updatePrintTemplate(formData.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createPrintTemplate(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

/**
 * 预览模板：拉取完整模板内容 → 调用渲染接口 → 新窗口展示 HTML
 * 从模板占位符中提取变量名生成示例值，便于直观查看排版效果
 */
async function handlePreview(row: PrintTemplate) {
  previewLoadingId.value = row.id ?? null
  try {
    // 列表数据不含 templateContent，需先拉取完整详情
    const detailRes: any = await getPrintTemplateDetail(row.id as number)
    const template: PrintTemplate = detailRes.data || row
    const content = template.templateContent || ''

    // 从模板内容中提取占位符 {{var}} 作为预览示例变量
    const variables: Record<string, any> = {}
    const matches = content.match(/\{\{\s*([\w.]+)\s*\}\}/g) || []
    matches.forEach(m => {
      const key = m.replace(/[{}\s]/g, '')
      variables[key] = `示例-${key}`
    })

    const res: any = await renderPrintTemplate({
      templateId: row.id as number,
      variables
    })
    const html: string = res.data
    if (!html) {
      ElMessage.error('渲染结果为空，无法预览')
      return
    }

    const previewWindow = window.open('', '_blank')
    if (!previewWindow) {
      ElMessage.warning('浏览器拦截了弹出窗口，请允许后重试')
      return
    }
    previewWindow.document.open()
    previewWindow.document.write(html)
    previewWindow.document.close()
  } finally {
    previewLoadingId.value = null
  }
}

async function handleDelete(row: PrintTemplate) {
  await ElMessageBox.confirm(`确定要删除模板「${row.templateName}」吗？`, '提示', { type: 'warning' })
  await deletePrintTemplate(row.id as number)
  ElMessage.success('删除成功')
  // 删除后若当前页已无数据，回退一页
  if (tableData.value.length === 1 && queryParams.value.pageNum > 1) {
    queryParams.value.pageNum--
  }
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.print-template-container {
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
.editor-tips {
  width: 100%;
  margin-bottom: 8px;
}
.editor-tips code {
  background: #f5f5f5;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: 'Consolas', 'Monaco', monospace;
}
/* 等宽字体 HTML 编辑区（无现成 CodeMirror/Monaco 依赖时的替代方案） */
.code-editor :deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre;
  overflow-wrap: normal;
  overflow-x: auto;
  tab-size: 2;
}
</style>
