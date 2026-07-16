<template>
  <div class="inspection-form-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? '编辑检查' : '新增检查' }}</span>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="项目" prop="projectId">
              <ProjectSelector v-model="formData.projectId" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="检查类型" prop="inspectionType">
              <el-select
                v-model="formData.inspectionType"
                placeholder="请选择检查类型"
                style="width: 100%"
                @change="handleInspectionTypeChange"
              >
                <el-option label="质量检查" value="QUALITY" />
                <el-option label="安全检查" value="SAFETY" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检查方案">
              <el-select
                v-model="formData.schemeId"
                placeholder="请选择检查方案（可选）"
                style="width: 100%"
                clearable
                :loading="schemeLoading"
                :disabled="!formData.inspectionType"
                @change="handleSchemeChange"
              >
                <el-option
                  v-for="scheme in schemeList"
                  :key="scheme.id"
                  :label="scheme.schemeName"
                  :value="scheme.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="24">
            <el-form-item label="检查内容">
              <el-input v-model="formData.inspectionContent" type="textarea" :rows="2" placeholder="请输入检查内容（可选）" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 检查明细表格 -->
        <el-divider content-position="left">检查明细</el-divider>

        <div class="detail-toolbar" v-if="!formData.schemeId">
          <el-button type="primary" size="small" @click="handleAddDetail">
            添加检查项
          </el-button>
          <span class="detail-tip">未选择方案时可手动添加检查项（最多100条）</span>
        </div>
        <div class="detail-toolbar" v-else>
          <span class="detail-tip">已关联方案，可编辑检查标准或删除不适用项，不可新增方案外检查项</span>
        </div>

        <el-table :data="detailList" border style="width: 100%; margin-top: 12px">
          <el-table-column prop="itemName" label="项目名称" min-width="160">
            <template #default="{ row, $index }">
              <el-input
                v-if="!formData.schemeId"
                v-model="row.itemName"
                placeholder="请输入项目名称"
                maxlength="200"
              />
              <span v-else>{{ row.itemName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="checkStandard" label="检查标准" min-width="200">
            <template #default="{ row }">
              <el-input
                v-model="row.checkStandard"
                placeholder="请输入检查标准"
                maxlength="500"
              />
            </template>
          </el-table-column>
          <el-table-column prop="checkMethod" label="检查方法" min-width="160">
            <template #default="{ row }">
              <el-input
                v-if="!formData.schemeId"
                v-model="row.checkMethod"
                placeholder="请输入检查方法"
              />
              <span v-else>{{ row.checkMethod }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="checkResult" label="检查结果" width="140" align="center">
            <template #default="{ row }">
              <el-select v-model="row.checkResult" placeholder="请选择" style="width: 100%">
                <el-option label="合格" value="PASS" />
                <el-option label="不合格" value="FAIL" />
                <el-option label="未检查" value="NOT_CHECKED" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="handleDeleteDetail($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <div class="form-footer">
        <el-button @click="handleBack">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  createInspection,
  getInspectionPage
} from '@/api/site'
import ProjectSelector from '@/components/ProjectSelector.vue'
import {
  listInspectionSchemes,
  getSchemeItems,
  applyScheme,
  getInspectionDetail,
  updateInspectionDetails
} from '@/api/inspection-scheme'

interface InspectionDetail {
  id?: number
  itemName: string
  checkStandard: string
  checkMethod: string
  checkResult: string
}

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const schemeLoading = ref(false)
const schemeList = ref<any[]>([])
const detailList = ref<InspectionDetail[]>([])

const isEdit = computed(() => !!route.params.id)

const formData = ref({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  inspectionType: '' as string,
  schemeId: undefined as number | undefined,
  inspectionContent: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  inspectionType: [{ required: true, message: '请选择检查类型', trigger: 'change' }]
}

/** 检查类型变更时重新加载方案列表 */
async function handleInspectionTypeChange() {
  formData.value.schemeId = undefined
  detailList.value = []
  await loadSchemeList()
}

/** 加载方案列表 */
async function loadSchemeList() {
  if (!formData.value.inspectionType) {
    schemeList.value = []
    return
  }
  schemeLoading.value = true
  try {
    const res: any = await listInspectionSchemes({
      inspectionType: formData.value.inspectionType,
      pageNum: 1,
      pageSize: 50
    })
    schemeList.value = res.data?.records || res.data || []
  } catch (e) {
    schemeList.value = []
  } finally {
    schemeLoading.value = false
  }
}

/** 选择方案后自动填充检查明细 */
async function handleSchemeChange(schemeId: number | undefined) {
  if (!schemeId) {
    detailList.value = []
    return
  }

  // 如果是编辑模式且已有检查记录ID，调用后端关联方案接口
  if (isEdit.value && formData.value.id) {
    try {
      await applyScheme(formData.value.id, schemeId)
      // 重新加载详情获取填充后的明细
      await loadInspectionDetail(formData.value.id)
      ElMessage.success('方案关联成功，检查明细已自动填充')
    } catch (e) {
      ElMessage.error('方案关联失败')
    }
    return
  }

  // 新增模式：获取方案检查项预览填充
  try {
    const res: any = await getSchemeItems(schemeId)
    const items = res.data || []
    detailList.value = items.map((item: any) => ({
      itemName: item.itemName,
      checkStandard: item.checkStandard || '',
      checkMethod: item.checkMethod || '',
      checkResult: 'NOT_CHECKED'
    }))
  } catch (e) {
    ElMessage.error('获取方案检查项失败')
  }
}

/** 手动添加检查项 */
function handleAddDetail() {
  if (detailList.value.length >= 100) {
    ElMessage.warning('检查项最多添加100条')
    return
  }
  detailList.value.push({
    itemName: '',
    checkStandard: '',
    checkMethod: '',
    checkResult: 'NOT_CHECKED'
  })
}

/** 删除检查项 */
function handleDeleteDetail(index: number) {
  detailList.value.splice(index, 1)
}

/** 加载检查记录详情 */
async function loadInspectionDetail(id: number) {
  try {
    const res: any = await getInspectionDetail(id)
    const data = res.data
    formData.value = {
      id: data.id,
      projectId: data.projectId || undefined,
      inspectionType: data.inspectionType || '',
      schemeId: data.schemeId || undefined,
      inspectionContent: data.inspectionContent || ''
    }
    // 如果有方案快照，从快照加载明细
    if (data.details && data.details.length > 0) {
      detailList.value = data.details.map((d: any) => ({
        id: d.id,
        itemName: d.itemName || '',
        checkStandard: d.checkStandard || '',
        checkMethod: d.checkMethod || '',
        checkResult: d.checkResult || 'NOT_CHECKED'
      }))
    } else if (data.schemeSnapshot) {
      // 从快照恢复
      try {
        const snapshot = typeof data.schemeSnapshot === 'string'
          ? JSON.parse(data.schemeSnapshot)
          : data.schemeSnapshot
        detailList.value = (snapshot.items || []).map((item: any) => ({
          itemName: item.itemName || '',
          checkStandard: item.checkStandard || '',
          checkMethod: item.checkMethod || '',
          checkResult: 'NOT_CHECKED'
        }))
      } catch (e) {
        detailList.value = []
      }
    }
    // 加载方案列表
    if (formData.value.inspectionType) {
      await loadSchemeList()
    }
  } catch (e) {
    ElMessage.error('加载检查记录失败')
  }
}

/** 提交表单 */
async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const submitData = {
      ...formData.value,
      details: detailList.value.filter(d => d.itemName)
    }
    if (isEdit.value) {
      // 编辑模式：更新明细
      if (formData.value.id) {
        await updateInspectionDetails(formData.value.id, submitData.details)
      }
    } else {
      // 新增模式
      await createInspection(submitData)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    handleBack()
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    submitLoading.value = false
  }
}

/** 返回列表 */
function handleBack() {
  router.push('/site/inspection')
}

onMounted(async () => {
  const id = route.params.id as string
  if (id) {
    await loadInspectionDetail(Number(id))
  }
  // 如果来自列表页带有检查类型参数
  const type = route.query.type as string
  if (type && !formData.value.inspectionType) {
    formData.value.inspectionType = type.toUpperCase()
    await loadSchemeList()
  }
})
</script>

<style scoped>
.inspection-form-container {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.detail-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detail-tip {
  color: #909399;
  font-size: 13px;
}
.form-footer {
  margin-top: 24px;
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
