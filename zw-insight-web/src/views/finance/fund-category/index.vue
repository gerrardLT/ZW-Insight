<template>
  <div class="fund-category-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-radio-group v-model="directionFilter" @change="loadTree" style="margin-right: var(--zw-space-sm-md)">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="INCOME">收入</el-radio-button>
          <el-radio-button value="EXPENSE">支出</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="handleAdd()">新增科目</el-button>
      </div>

      <el-table
        :data="treeData"
        v-loading="loading"
        border
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="code" label="科目编码" width="220" show-overflow-tooltip />
        <el-table-column prop="name" label="科目名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="方向" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'INCOME' ? 'success' : 'danger'" size="small">
              {{ row.direction === 'INCOME' ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="层级" width="80" align="center" />
        <el-table-column label="属性" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isSystem === 1" type="warning" size="small">系统内置</el-tag>
            <el-tag v-else type="info" size="small">自定义</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.level < 3" link type="primary" @click="handleAdd(row as FundCategory)">添加子级</el-button>
            <el-button link type="primary" @click="handleEdit(row as FundCategory)">编辑</el-button>
            <el-button v-if="row.isSystem !== 1 && row.status === 'ENABLED'" link type="warning" @click="handleDisable(row as FundCategory)">停用</el-button>
            <el-button v-if="row.isSystem !== 1" link type="danger" @click="handleDelete(row as FundCategory)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="上级科目">
          <el-input :model-value="parentLabel" disabled />
        </el-form-item>
        <el-form-item label="科目编码" prop="code">
          <el-input v-model="formData.code" placeholder="如 EXP-DIRECT-MATERIAL" maxlength="50" :disabled="isEdit" clearable />
        </el-form-item>
        <el-form-item label="科目名称" prop="name">
          <el-input v-model="formData.name" placeholder="如 材料款" maxlength="100" clearable />
        </el-form-item>
        <el-form-item label="方向" prop="direction">
          <el-radio-group v-model="formData.direction" :disabled="isEdit || parentDirection !== ''">
            <el-radio value="INCOME">收入</el-radio>
            <el-radio value="EXPENSE">支出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" controls-position="right" style="width: 100%" />
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getFundCategoryTree,
  saveFundCategory,
  updateFundCategory,
  disableFundCategory,
  deleteFundCategory,
  type FundCategory
} from '@/api/fund-category'

const formRef = ref<FormInstance>()
const loading = ref(false)
const treeData = ref<FundCategory[]>([])
const directionFilter = ref('')
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const parent = ref<FundCategory | null>(null)

const formData = ref({
  id: undefined as number | undefined,
  code: '',
  name: '',
  direction: 'EXPENSE' as 'INCOME' | 'EXPENSE',
  parentId: 0,
  sortOrder: 0
})

const parentLabel = computed(() => (parent.value ? `${parent.value.code} ${parent.value.name}` : '顶级科目'))
const parentDirection = computed(() => (parent.value ? parent.value.direction : ''))
const dialogTitle = computed(() => (isEdit.value ? '编辑科目' : '新增科目'))

const formRules: FormRules = {
  code: [
    { required: true, message: '请输入科目编码', trigger: 'blur' },
    { max: 50, message: '编码不超过50个字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入科目名称', trigger: 'blur' },
    { max: 100, message: '名称不超过100个字符', trigger: 'blur' }
  ],
  direction: [{ required: true, message: '请选择方向', trigger: 'change' }]
}

async function loadTree() {
  loading.value = true
  try {
    const res = await getFundCategoryTree(directionFilter.value || undefined)
    treeData.value = res.data.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd(row?: FundCategory) {
  isEdit.value = false
  parent.value = row || null
  formData.value = {
    id: undefined,
    code: '',
    name: '',
    direction: row ? row.direction : 'EXPENSE',
    parentId: row ? (row.id as number) : 0,
    sortOrder: 0
  }
  dialogVisible.value = true
}

function handleEdit(row: FundCategory) {
  isEdit.value = true
  parent.value = null
  formData.value = {
    id: row.id,
    code: row.code,
    name: row.name,
    direction: row.direction,
    parentId: row.parentId || 0,
    sortOrder: row.sortOrder || 0
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (isEdit.value && formData.value.id) {
      await updateFundCategory(formData.value.id, formData.value)
      ElMessage.success('科目已更新')
    } else {
      await saveFundCategory(formData.value)
      ElMessage.success('科目已新增')
    }
    dialogVisible.value = false
    await loadTree()
  } finally {
    submitLoading.value = false
  }
}

async function handleDisable(row: FundCategory) {
  await ElMessageBox.confirm(`确认停用科目「${row.name}」？停用后新单据不可选用，历史单据不受影响`, '停用确认', { type: 'warning' })
  await disableFundCategory(row.id as number)
  ElMessage.success('科目已停用')
  await loadTree()
}

async function handleDelete(row: FundCategory) {
  await ElMessageBox.confirm(`确认删除科目「${row.name}」？仅无子级且无付款引用时可删除`, '删除确认', { type: 'warning' })
  await deleteFundCategory(row.id as number)
  ElMessage.success('科目已删除')
  await loadTree()
}

onMounted(loadTree)
</script>

<style scoped>
.fund-category-container {
  padding: var(--zw-space-md);
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--zw-space-sm-md)
}
</style>
