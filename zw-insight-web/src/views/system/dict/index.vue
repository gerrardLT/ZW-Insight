<template>
  <div class="dict-container">
    <el-row :gutter="16">
      <!-- 左侧字典列表 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>字典列表</span>
              <el-button type="primary" size="small" @click="handleAddDict">
                <el-icon><Plus /></el-icon>新增
              </el-button>
            </div>
          </template>
          <el-input v-model="searchText" placeholder="搜索字典名称/编码" clearable style="margin-bottom: 12px" />
          <el-table
            :data="filteredDictList"
            highlight-current-row
            @current-change="handleDictSelect"
            v-loading="dictLoading"
            size="small"
          >
            <el-table-column prop="dictName" label="字典名称" min-width="120" />
            <el-table-column prop="dictCode" label="字典编码" min-width="120" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="handleEditDict(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click.stop="handleDeleteDict(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧字典值管理 -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ currentDict ? `字典值 - ${currentDict.dictName}` : '请选择字典' }}</span>
              <el-button v-if="currentDict" type="primary" size="small" @click="handleAddItem(0)">
                <el-icon><Plus /></el-icon>新增
              </el-button>
            </div>
          </template>

          <template v-if="currentDict">
            <el-table
              :data="dictItemTree"
              row-key="id"
              :tree-props="{ children: 'children' }"
              default-expand-all
              border
              v-loading="itemLoading"
            >
              <el-table-column prop="label" label="标签" min-width="150" />
              <el-table-column prop="value" label="值" width="120" />
              <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="handleAddItem(row.id)">新增子项</el-button>
                  <el-button link type="primary" size="small" @click="handleEditItem(row)">编辑</el-button>
                  <el-button link type="danger" size="small" @click="handleDeleteItem(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="请从左侧选择字典" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 字典新增/编辑弹窗 -->
    <el-dialog v-model="dictDialogVisible" :title="dictDialogTitle" width="500px" destroy-on-close>
      <el-form ref="dictFormRef" :model="dictFormData" :rules="dictFormRules" label-width="100px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="dictFormData.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典编码" prop="dictCode">
          <el-input v-model="dictFormData.dictCode" placeholder="请输入字典编码" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="dictFormData.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dictSubmitLoading" @click="handleDictSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典值新增/编辑弹窗 -->
    <el-dialog v-model="itemDialogVisible" :title="itemDialogTitle" width="500px" destroy-on-close>
      <el-form ref="itemFormRef" :model="itemFormData" :rules="itemFormRules" label-width="100px">
        <el-form-item label="标签" prop="label">
          <el-input v-model="itemFormData.label" placeholder="请输入标签" />
        </el-form-item>
        <el-form-item label="值" prop="value">
          <el-input v-model="itemFormData.value" placeholder="请输入值" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="itemFormData.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSubmitLoading" @click="handleItemSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getDictList, createDict, updateDict, deleteDict,
  getDictItemTree, createDictItem, updateDictItem, deleteDictItem
} from '@/api/system'

const dictFormRef = ref<FormInstance>()
const itemFormRef = ref<FormInstance>()
const dictLoading = ref(false)
const itemLoading = ref(false)
const dictList = ref<any[]>([])
const dictItemTree = ref<any[]>([])
const currentDict = ref<any>(null)
const searchText = ref('')

// 字典弹窗
const dictDialogVisible = ref(false)
const dictDialogTitle = ref('')
const dictSubmitLoading = ref(false)
const dictFormData = ref({ id: undefined as number | undefined, dictName: '', dictCode: '', sortOrder: 0 })
const dictFormRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }]
}

// 字典值弹窗
const itemDialogVisible = ref(false)
const itemDialogTitle = ref('')
const itemSubmitLoading = ref(false)
const itemFormData = ref({
  id: undefined as number | undefined,
  dictId: undefined as number | undefined,
  parentId: 0,
  label: '',
  value: '',
  sortOrder: 0
})
const itemFormRules = {
  label: [{ required: true, message: '请输入标签', trigger: 'blur' }],
  value: [{ required: true, message: '请输入值', trigger: 'blur' }]
}

const filteredDictList = computed(() => {
  if (!searchText.value) return dictList.value
  return dictList.value.filter(
    d => d.dictName.includes(searchText.value) || d.dictCode.includes(searchText.value)
  )
})

async function loadDictList() {
  dictLoading.value = true
  try {
    const res: any = await getDictList()
    dictList.value = res.data || []
  } finally {
    dictLoading.value = false
  }
}

async function loadDictItems() {
  if (!currentDict.value) return
  itemLoading.value = true
  try {
    const res: any = await getDictItemTree(currentDict.value.id)
    dictItemTree.value = res.data || []
  } finally {
    itemLoading.value = false
  }
}

function handleDictSelect(row: any) {
  if (!row) return
  currentDict.value = row
  loadDictItems()
}

function handleAddDict() {
  dictDialogTitle.value = '新增字典'
  dictFormData.value = { id: undefined, dictName: '', dictCode: '', sortOrder: 0 }
  dictDialogVisible.value = true
}

function handleEditDict(row: any) {
  dictDialogTitle.value = '编辑字典'
  dictFormData.value = { ...row }
  dictDialogVisible.value = true
}

async function handleDictSubmit() {
  await dictFormRef.value?.validate()
  dictSubmitLoading.value = true
  try {
    if (dictFormData.value.id) {
      await updateDict(dictFormData.value)
      ElMessage.success('更新成功')
    } else {
      await createDict(dictFormData.value)
      ElMessage.success('新增成功')
    }
    dictDialogVisible.value = false
    await loadDictList()
  } finally {
    dictSubmitLoading.value = false
  }
}

async function handleDeleteDict(row: any) {
  await ElMessageBox.confirm('确定要删除该字典吗？', '提示', { type: 'warning' })
  await deleteDict(row.id)
  ElMessage.success('删除成功')
  if (currentDict.value?.id === row.id) {
    currentDict.value = null
    dictItemTree.value = []
  }
  await loadDictList()
}

function handleAddItem(parentId: number) {
  itemDialogTitle.value = parentId === 0 ? '新增字典值' : '新增子项'
  itemFormData.value = {
    id: undefined,
    dictId: currentDict.value.id,
    parentId,
    label: '',
    value: '',
    sortOrder: 0
  }
  itemDialogVisible.value = true
}

function handleEditItem(row: any) {
  itemDialogTitle.value = '编辑字典值'
  itemFormData.value = { ...row, dictId: currentDict.value.id }
  itemDialogVisible.value = true
}

async function handleItemSubmit() {
  await itemFormRef.value?.validate()
  itemSubmitLoading.value = true
  try {
    if (itemFormData.value.id) {
      await updateDictItem(itemFormData.value)
      ElMessage.success('更新成功')
    } else {
      await createDictItem(itemFormData.value)
      ElMessage.success('新增成功')
    }
    itemDialogVisible.value = false
    await loadDictItems()
  } finally {
    itemSubmitLoading.value = false
  }
}

async function handleDeleteItem(row: any) {
  await ElMessageBox.confirm('确定要删除该字典值吗？', '提示', { type: 'warning' })
  await deleteDictItem(row.id)
  ElMessage.success('删除成功')
  await loadDictItems()
}

onMounted(() => {
  loadDictList()
})
</script>

<style scoped>
.dict-container {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
