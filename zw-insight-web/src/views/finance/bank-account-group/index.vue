<template>
  <div class="bank-account-group-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="title">银行账户分组（多级树，司库集中管控）</span>
        <el-button type="primary" @click="handleAdd()">新增顶级分组</el-button>
      </div>

      <el-table
        :data="treeData"
        v-loading="loading"
        border
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="groupCode" label="分组编码" width="200" show-overflow-tooltip />
        <el-table-column prop="groupName" label="分组名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="level" label="层级" width="80" align="center" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.level < 4" link type="primary" @click="handleAdd(row as BankAccountGroup)">添加子级</el-button>
            <el-button link type="primary" @click="handleEdit(row as BankAccountGroup)">编辑</el-button>
            <el-button link type="warning" @click="handleToggle(row as BankAccountGroup)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as BankAccountGroup)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="上级分组">
          <el-input :model-value="parentLabel" disabled />
        </el-form-item>
        <el-form-item label="分组编码" prop="groupCode">
          <el-input v-model="formData.groupCode" placeholder="如 GRP-001" maxlength="50" clearable />
        </el-form-item>
        <el-form-item label="分组名称" prop="groupName">
          <el-input v-model="formData.groupName" placeholder="如 集团公司/XX项目部" maxlength="100" clearable />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" maxlength="500" />
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
  getGroupTree,
  saveGroup,
  updateGroup,
  deleteGroup,
  toggleGroupStatus,
  type BankAccountGroup
} from '@/api/bank-account-group'

const formRef = ref<FormInstance>()
const loading = ref(false)
const treeData = ref<BankAccountGroup[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const parent = ref<BankAccountGroup | null>(null)

const formData = ref({
  id: undefined as number | undefined,
  groupCode: '',
  groupName: '',
  parentId: 0,
  sortOrder: 0,
  remark: ''
})

const parentLabel = computed(() => (parent.value ? `${parent.value.groupCode} ${parent.value.groupName}` : '顶级分组'))
const dialogTitle = computed(() => (isEdit.value ? '编辑分组' : '新增分组'))

const formRules: FormRules = {
  groupCode: [{ required: true, message: '请输入分组编码', trigger: 'blur' }],
  groupName: [{ required: true, message: '请输入分组名称', trigger: 'blur' }]
}

async function loadTree() {
  loading.value = true
  try {
    const res = await getGroupTree()
    treeData.value = res.data.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd(row?: BankAccountGroup) {
  isEdit.value = false
  parent.value = row || null
  formData.value = {
    id: undefined,
    groupCode: '',
    groupName: '',
    parentId: row ? (row.id as number) : 0,
    sortOrder: 0,
    remark: ''
  }
  dialogVisible.value = true
}

function handleEdit(row: BankAccountGroup) {
  isEdit.value = true
  parent.value = null
  formData.value = {
    id: row.id,
    groupCode: row.groupCode,
    groupName: row.groupName,
    parentId: row.parentId || 0,
    sortOrder: row.sortOrder || 0,
    remark: row.remark || ''
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (isEdit.value && formData.value.id) {
      await updateGroup(formData.value.id, formData.value)
      ElMessage.success('分组已更新')
    } else {
      await saveGroup(formData.value)
      ElMessage.success('分组已新增')
    }
    dialogVisible.value = false
    await loadTree()
  } finally {
    submitLoading.value = false
  }
}

async function handleToggle(row: BankAccountGroup) {
  const enable = row.status !== 'ENABLED'
  await toggleGroupStatus(row.id as number, enable)
  ElMessage.success(enable ? '已启用' : '已停用')
  await loadTree()
}

async function handleDelete(row: BankAccountGroup) {
  await ElMessageBox.confirm(`确认删除分组「${row.groupName}」？有子分组或关联账户时不可删除`, '删除确认', { type: 'warning' })
  await deleteGroup(row.id as number)
  ElMessage.success('分组已删除')
  await loadTree()
}

onMounted(loadTree)
</script>

<style scoped>
.bank-account-group-container {
  padding: var(--zw-space-md);
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--zw-space-sm-md)
}
.title {
  font-weight: 600;
}
</style>
