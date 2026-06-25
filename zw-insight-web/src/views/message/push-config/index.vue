<template>
  <div class="push-config-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="业务类型">
          <el-input v-model="queryParams.businessType" placeholder="业务类型编码" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增配置</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="businessTypeName" label="业务类型" min-width="160" show-overflow-tooltip />
        <el-table-column prop="businessType" label="类型编码" width="140" show-overflow-tooltip />
        <el-table-column label="站内信" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enableInApp ? 'success' : 'info'" size="small">
              {{ row.enableInApp ? '开启' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="短信" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enableSms ? 'success' : 'info'" size="small">
              {{ row.enableSms ? '开启' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="邮件" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enableEmail ? 'success' : 'info'" size="small">
              {{ row.enableEmail ? '开启' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="APP推送" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enableAppPush ? 'success' : 'info'" size="small">
              {{ row.enableAppPush ? '开启' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑推送渠道配置' : '新增推送渠道配置'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="业务类型编码" prop="businessType">
          <el-input v-model="formData.businessType" placeholder="请输入业务类型编码" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="业务类型名称" prop="businessTypeName">
          <el-input v-model="formData.businessTypeName" placeholder="请输入业务类型名称" />
        </el-form-item>
        <el-divider content-position="left">推送渠道开关</el-divider>
        <el-form-item label="站内信">
          <el-switch v-model="formData.enableInApp" />
        </el-form-item>
        <el-form-item label="短信">
          <el-switch v-model="formData.enableSms" />
        </el-form-item>
        <el-form-item label="邮件">
          <el-switch v-model="formData.enableEmail" />
        </el-form-item>
        <el-form-item label="APP推送">
          <el-switch v-model="formData.enableAppPush" />
        </el-form-item>
        <el-divider content-position="left">模板配置</el-divider>
        <el-form-item label="站内信模板" v-show="formData.enableInApp">
          <el-select v-model="formData.inAppTemplateId" placeholder="请选择站内信模板" clearable style="width: 100%">
            <el-option
              v-for="item in templateList"
              :key="item.id"
              :label="item.templateName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="短信模板" v-show="formData.enableSms">
          <el-select v-model="formData.smsTemplateId" placeholder="请选择短信模板" clearable style="width: 100%">
            <el-option
              v-for="item in templateList"
              :key="item.id"
              :label="item.templateName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="邮件模板" v-show="formData.enableEmail">
          <el-select v-model="formData.emailTemplateId" placeholder="请选择邮件模板" clearable style="width: 100%">
            <el-option
              v-for="item in templateList"
              :key="item.id"
              :label="item.templateName"
              :value="item.id"
            />
          </el-select>
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
  getPushConfigPage,
  createPushConfig,
  updatePushConfig,
  deletePushConfig,
  getTemplatePage
} from '@/api/message'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const templateList = ref<any[]>([])

const queryParams = ref({
  page: 1,
  size: 10,
  businessType: ''
})

const defaultFormData = () => ({
  id: undefined as number | undefined,
  businessType: '',
  businessTypeName: '',
  enableInApp: true,
  enableSms: false,
  enableEmail: false,
  enableAppPush: false,
  inAppTemplateId: undefined as number | undefined,
  smsTemplateId: undefined as number | undefined,
  emailTemplateId: undefined as number | undefined
})

const formData = ref(defaultFormData())

const formRules = {
  businessType: [{ required: true, message: '请输入业务类型编码', trigger: 'blur' }],
  businessTypeName: [{ required: true, message: '请输入业务类型名称', trigger: 'blur' }]
}

/** 加载推送配置列表 */
async function loadData() {
  loading.value = true
  try {
    const res: any = await getPushConfigPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

/** 加载模板列表（用于下拉选择） */
async function loadTemplates() {
  try {
    const res: any = await getTemplatePage({ page: 1, size: 200 })
    templateList.value = res.data?.records || []
  } catch {
    templateList.value = []
  }
}

function handleSearch() {
  queryParams.value.page = 1
  loadData()
}

function handleReset() {
  queryParams.value = { page: 1, size: 10, businessType: '' }
  loadData()
}

function handleAdd() {
  isEdit.value = false
  formData.value = defaultFormData()
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.value = {
    id: row.id,
    businessType: row.businessType,
    businessTypeName: row.businessTypeName,
    enableInApp: row.enableInApp ?? false,
    enableSms: row.enableSms ?? false,
    enableEmail: row.enableEmail ?? false,
    enableAppPush: row.enableAppPush ?? false,
    inAppTemplateId: row.inAppTemplateId,
    smsTemplateId: row.smsTemplateId,
    emailTemplateId: row.emailTemplateId
  }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (isEdit.value && formData.value.id) {
      await updatePushConfig(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createPushConfig(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(
    `确定要删除业务类型「${row.businessTypeName}」的推送配置吗？`,
    '提示',
    { type: 'warning' }
  )
  await deletePushConfig(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadData()
  loadTemplates()
})
</script>

<style scoped>
.push-config-container {
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
