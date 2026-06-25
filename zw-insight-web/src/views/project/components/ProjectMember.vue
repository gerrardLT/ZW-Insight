<template>
  <div class="project-member">
    <!-- 工具栏 -->
    <div class="member-toolbar">
      <el-select v-model="queryParams.role" placeholder="按角色筛选" clearable style="width: 160px" @change="handleSearch">
        <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" @click="showAddDialog">
        <el-icon><Plus /></el-icon>添加成员
      </el-button>
    </div>

    <!-- 成员列表表格 -->
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="userName" label="姓名" width="120" />
      <el-table-column prop="deptName" label="部门" width="150" show-overflow-tooltip />
      <el-table-column label="项目角色" min-width="200">
        <template #default="{ row }">
          <el-tag
            v-for="role in row.projectRoles"
            :key="role"
            :type="(getRoleTagType(role) as any)"
            size="small"
            style="margin-right: 4px; margin-bottom: 2px"
          >
            {{ getRoleLabel(role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="joinDate" label="加入时间" width="120" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showRoleDialog(row)">变更角色</el-button>
          <el-button link type="danger" @click="handleRemove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <!-- 添加成员弹窗 -->
    <el-dialog v-model="addDialogVisible" title="添加成员" width="500px" destroy-on-close>
      <el-form ref="addFormRef" :model="addForm" :rules="addFormRules" label-width="80px">
        <el-form-item label="选择用户" prop="userId">
          <el-select
            v-model="addForm.userId"
            placeholder="请输入姓名搜索"
            filterable
            remote
            :remote-method="searchUser"
            :loading="userSearchLoading"
            style="width: 100%"
            @change="handleUserChange"
          >
            <el-option
              v-for="item in userOptions"
              :key="item.id"
              :label="`${item.realName} (${item.deptName || '无部门'})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目角色" prop="projectRoles">
          <el-select v-model="addForm.projectRoles" multiple placeholder="请选择角色" style="width: 100%">
            <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addLoading" @click="handleAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 变更角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="变更角色" width="450px" destroy-on-close>
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="成员">
          <el-input :model-value="roleForm.userName" disabled />
        </el-form-item>
        <el-form-item label="项目角色">
          <el-select v-model="roleForm.projectRoles" multiple placeholder="请选择角色" style="width: 100%">
            <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleLoading" @click="handleUpdateRoles">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getProjectMembers, addProjectMember, removeProjectMember, updateMemberRoles } from '@/api/project'
import { getUserPage } from '@/api/system'

const props = defineProps<{
  projectId: number
}>()

// 角色选项
const roleOptions = [
  { label: '项目经理', value: 'PROJECT_MANAGER' },
  { label: '施工员', value: 'CONSTRUCTOR' },
  { label: '安全员', value: 'SAFETY_OFFICER' },
  { label: '质量员', value: 'QUALITY_OFFICER' },
  { label: '材料员', value: 'MATERIAL_OFFICER' },
  { label: '财务人员', value: 'FINANCE_OFFICER' },
  { label: '资料员', value: 'ARCHIVIST' }
]

const roleTagTypeMap: Record<string, string> = {
  PROJECT_MANAGER: '',
  CONSTRUCTOR: 'success',
  SAFETY_OFFICER: 'warning',
  QUALITY_OFFICER: 'info',
  MATERIAL_OFFICER: 'danger',
  FINANCE_OFFICER: 'success',
  ARCHIVIST: 'info'
}

function getRoleLabel(role: string) {
  return roleOptions.find(r => r.value === role)?.label || role
}

function getRoleTagType(role: string) {
  return roleTagTypeMap[role] || 'info'
}

// 列表数据
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  role: ''
})

async function loadData() {
  if (!props.projectId) return
  loading.value = true
  try {
    const params: any = {
      pageNum: queryParams.value.pageNum,
      pageSize: queryParams.value.pageSize
    }
    if (queryParams.value.role) {
      params.role = queryParams.value.role
    }
    const res: any = await getProjectMembers(props.projectId, params)
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

// 添加成员
const addDialogVisible = ref(false)
const addLoading = ref(false)
const addFormRef = ref<FormInstance>()
const userSearchLoading = ref(false)
const userOptions = ref<any[]>([])

const addForm = ref({
  userId: undefined as number | undefined,
  userName: '',
  projectRoles: [] as string[]
})

const addFormRules = {
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }],
  projectRoles: [{ required: true, message: '请选择至少一个角色', trigger: 'change', type: 'array', min: 1 }]
}

function showAddDialog() {
  addForm.value = { userId: undefined, userName: '', projectRoles: [] }
  userOptions.value = []
  addDialogVisible.value = true
}

async function searchUser(query: string) {
  if (!query || query.length < 1) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const res: any = await getUserPage({ realName: query, pageNum: 1, pageSize: 20 })
    userOptions.value = res.data?.records || []
  } finally {
    userSearchLoading.value = false
  }
}

function handleUserChange(userId: number) {
  const user = userOptions.value.find(u => u.id === userId)
  addForm.value.userName = user?.realName || ''
}

async function handleAdd() {
  await addFormRef.value?.validate()
  addLoading.value = true
  try {
    await addProjectMember(props.projectId, {
      userId: addForm.value.userId,
      userName: addForm.value.userName,
      projectRoles: addForm.value.projectRoles
    })
    ElMessage.success('添加成功')
    addDialogVisible.value = false
    loadData()
  } catch (error: any) {
    // 错误已由 request 拦截器处理 Toast 提示
  } finally {
    addLoading.value = false
  }
}

// 移除成员
async function handleRemove(row: any) {
  try {
    await ElMessageBox.confirm(`确定要将 ${row.userName} 从项目中移除吗？`, '移除确认', {
      type: 'warning',
      confirmButtonText: '确定移除',
      cancelButtonText: '取消'
    })
    await removeProjectMember(props.projectId, row.userId)
    ElMessage.success('移除成功')
    loadData()
  } catch (error: any) {
    // 用户取消操作或 API 错误（API 错误由 request 拦截器处理）
    if (error !== 'cancel' && error?.message !== 'cancel') {
      // 额外的错误提示已由 request 拦截器处理
    }
  }
}

// 变更角色
const roleDialogVisible = ref(false)
const roleLoading = ref(false)
const roleForm = ref({
  userId: 0,
  userName: '',
  projectRoles: [] as string[]
})

function showRoleDialog(row: any) {
  roleForm.value = {
    userId: row.userId,
    userName: row.userName,
    projectRoles: [...(row.projectRoles || [])]
  }
  roleDialogVisible.value = true
}

async function handleUpdateRoles() {
  if (roleForm.value.projectRoles.length === 0) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  roleLoading.value = true
  try {
    await updateMemberRoles(props.projectId, roleForm.value.userId, {
      projectRoles: roleForm.value.projectRoles
    })
    ElMessage.success('角色更新成功')
    roleDialogVisible.value = false
    loadData()
  } catch (error: any) {
    // 错误已由 request 拦截器处理 Toast 提示
  } finally {
    roleLoading.value = false
  }
}

// 监听 projectId 变化时重新加载
watch(() => props.projectId, (newVal) => {
  if (newVal) loadData()
})

onMounted(() => {
  if (props.projectId) loadData()
})
</script>

<style scoped>
.project-member {
  padding: 0;
}
.member-toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
