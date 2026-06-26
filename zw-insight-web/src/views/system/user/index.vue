<template>
  <div class="user-container">
    <el-card shadow="never">
      <!-- 搜索区域 -->
      <el-form :model="queryParams" inline>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.realName" placeholder="请输入姓名" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="queryParams.username" placeholder="请输入账号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="机构">
          <el-tree-select
            v-model="queryParams.orgId"
            :data="orgTree"
            :props="{ label: 'orgName', value: 'id', children: 'children' }"
            placeholder="请选择机构"
            check-strictly
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 操作栏 -->
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增
        </el-button>
        <el-button type="success" :disabled="!selectedIds.length" @click="handleBatchStatus(1)">
          批量启用
        </el-button>
        <el-button type="warning" :disabled="!selectedIds.length" @click="handleBatchStatus(0)">
          批量停用
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" @selection-change="handleSelectionChange" border>
        <el-table-column type="selection" width="50" />
        <el-table-column prop="username" label="账号" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="所属机构" min-width="150">
          <template #default="{ row }">{{ row.orgName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="handleAssignRole(row)">分配角色</el-button>
            <el-button link type="warning" @click="handleResetPwd(row)">重置密码</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="formData.username" placeholder="请输入用户名" :disabled="!!formData.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="formData.realName" placeholder="请输入真实姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="formData.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="formData.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属机构" prop="orgId">
              <el-tree-select
                v-model="formData.orgId"
                :data="orgTree"
                :props="{ label: 'orgName', value: 'id', children: 'children' }"
                placeholder="请选择机构"
                check-strictly
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-select v-model="formData.postId" placeholder="请选择岗位" clearable style="width: 100%">
                <el-option v-for="item in postList" :key="item.id" :label="item.postName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="!formData.id" label="密码" prop="password">
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="500px" destroy-on-close>
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="role in roleList" :key="role.id" :label="role.id">
          {{ role.roleName }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitLoading" @click="handleRoleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getUserPage, createUser, updateUser, deleteUser,
  updateUserStatus, batchUpdateUserStatus, assignUserRoles, resetUserPassword,
  getOrgTree, getRoleList, getPostList
} from '@/api/system'
import { useUserStore } from '@/stores/user'
import { filterBatchIds } from '@/utils/batch-status'

const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const orgTree = ref<any[]>([])
const roleList = ref<any[]>([])
const postList = ref<any[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const roleDialogVisible = ref(false)
const roleSubmitLoading = ref(false)
const currentUserId = ref<number>(0)
const selectedRoleIds = ref<number[]>([])

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  realName: '',
  username: '',
  status: undefined as number | undefined,
  orgId: undefined as number | undefined
})

const formData = ref({
  id: undefined as number | undefined,
  username: '',
  realName: '',
  phone: '',
  email: '',
  orgId: undefined as number | undefined,
  postId: undefined as number | undefined,
  password: ''
})

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  orgId: [{ required: true, message: '请选择所属机构', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getUserPage(queryParams.value)
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

function handleReset() {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    realName: '',
    username: '',
    status: undefined,
    orgId: undefined
  }
  loadData()
}

function handleSelectionChange(rows: any[]) {
  selectedIds.value = rows.map(r => r.id)
}

function handleAdd() {
  dialogTitle.value = '新增用户'
  formData.value = {
    id: undefined,
    username: '',
    realName: '',
    phone: '',
    email: '',
    orgId: undefined,
    postId: undefined,
    password: ''
  }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑用户'
  formData.value = { ...row, password: '' }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateUser(formData.value)
      ElMessage.success('更新成功')
    } else {
      await createUser(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该用户吗？', '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleBatchStatus(status: number) {
  const action = status === 1 ? '启用' : '停用'
  const currentId = userStore.userInfo?.id

  // 排除当前登录用户
  const filteredIds = filterBatchIds(selectedIds.value, currentId)
  if (filteredIds.length === 0) {
    ElMessage.warning('不能对自己执行此操作')
    return
  }

  await ElMessageBox.confirm(`确认${action}选中的 ${filteredIds.length} 个用户？`, '提示', { type: 'warning' })
  await batchUpdateUserStatus(filteredIds, status)
  ElMessage.success(`批量${action}成功`)
  loadData()
}

async function handleResetPwd(row: any) {
  await ElMessageBox.confirm(`确定要重置用户 ${row.realName} 的密码吗？`, '提示', { type: 'warning' })
  await resetUserPassword(row.id)
  ElMessage.success('重置密码成功')
}

async function handleAssignRole(row: any) {
  currentUserId.value = row.id
  selectedRoleIds.value = row.roleIds || []
  roleDialogVisible.value = true
}

async function handleRoleSubmit() {
  roleSubmitLoading.value = true
  try {
    await assignUserRoles(currentUserId.value, selectedRoleIds.value)
    ElMessage.success('分配角色成功')
    roleDialogVisible.value = false
    loadData()
  } finally {
    roleSubmitLoading.value = false
  }
}

async function loadOrgTree() {
  const res: any = await getOrgTree()
  orgTree.value = res.data || []
}

async function loadRoleList() {
  const res: any = await getRoleList()
  roleList.value = res.data || []
}

async function loadPostList() {
  const res: any = await getPostList()
  postList.value = res.data || []
}

onMounted(() => {
  loadData()
  loadOrgTree()
  loadRoleList()
  loadPostList()
})
</script>

<style scoped>
.user-container {
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
