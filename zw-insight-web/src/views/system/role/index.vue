<template>
  <div class="role-container">
    <el-row :gutter="16">
      <!-- 左侧角色列表 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>角色列表</span>
              <el-button type="primary" size="small" @click="handleAdd">
                <el-icon><Plus /></el-icon>新增
              </el-button>
            </div>
          </template>
          <el-input v-model="searchName" placeholder="搜索角色名称" clearable style="margin-bottom: 12px" />
          <div class="role-list">
            <div
              v-for="role in filteredRoles"
              :key="role.id"
              class="role-item"
              :class="{ active: currentRole?.id === role.id }"
              @click="handleSelectRole(role)"
            >
              <div class="role-info">
                <span class="role-name">{{ role.roleName }}</span>
                <el-tag size="small" :type="role.status === 1 ? 'success' : 'danger'">
                  {{ role.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </div>
              <span class="role-code">{{ role.roleCode }}</span>
            </div>
            <el-empty v-if="!filteredRoles.length" description="暂无角色" />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧权限配置 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ currentRole ? `权限配置 - ${currentRole.roleName}` : '请选择角色' }}</span>
              <div v-if="currentRole">
                <el-button type="primary" size="small" @click="handleEdit">编辑角色</el-button>
                <el-button type="danger" size="small" @click="handleDelete">删除角色</el-button>
              </div>
            </div>
          </template>

          <template v-if="currentRole">
            <div class="permission-header">
              <el-checkbox v-model="checkAll" :indeterminate="isIndeterminate" @change="handleCheckAll">
                全选/全不选
              </el-checkbox>
              <el-button type="primary" size="small" :loading="saveLoading" @click="handleSaveMenus">
                保存权限
              </el-button>
            </div>
            <el-tree
              ref="menuTreeRef"
              :data="menuTree"
              :props="{ label: 'menuName', children: 'children' }"
              show-checkbox
              node-key="id"
              default-expand-all
              check-strictly
              @check="handleMenuCheck"
            />
          </template>
          <el-empty v-else description="请从左侧选择角色配置权限" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="formData.roleCode" placeholder="请输入角色编码" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" placeholder="请输入备注" :rows="3" />
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
import type { FormInstance } from 'element-plus'
import { getRoleList, createRole, updateRole, deleteRole, getRoleMenuIds, assignRoleMenus, getMenuTree } from '@/api/system'

const formRef = ref<FormInstance>()
const menuTreeRef = ref()
const roleList = ref<any[]>([])
const menuTree = ref<any[]>([])
const currentRole = ref<any>(null)
const searchName = ref('')
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const saveLoading = ref(false)
const checkAll = ref(false)
const isIndeterminate = ref(false)

const formData = ref({
  id: undefined as number | undefined,
  roleName: '',
  roleCode: '',
  remark: ''
})

const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

const filteredRoles = computed(() => {
  if (!searchName.value) return roleList.value
  return roleList.value.filter(r => r.roleName.includes(searchName.value))
})

async function loadRoles() {
  const res: any = await getRoleList()
  roleList.value = res.data || []
}

async function loadMenuTree() {
  const res: any = await getMenuTree()
  menuTree.value = res.data || []
}

async function handleSelectRole(role: any) {
  currentRole.value = role
  const res: any = await getRoleMenuIds(role.id)
  const menuIds = res.data || []
  menuTreeRef.value?.setCheckedKeys(menuIds)
  updateCheckAllState()
}

function handleMenuCheck() {
  updateCheckAllState()
}

function updateCheckAllState() {
  const allKeys = getAllMenuKeys(menuTree.value)
  const checkedKeys = menuTreeRef.value?.getCheckedKeys() || []
  checkAll.value = checkedKeys.length === allKeys.length
  isIndeterminate.value = checkedKeys.length > 0 && checkedKeys.length < allKeys.length
}

function handleCheckAll(val: boolean) {
  if (val) {
    menuTreeRef.value?.setCheckedKeys(getAllMenuKeys(menuTree.value))
  } else {
    menuTreeRef.value?.setCheckedKeys([])
  }
  isIndeterminate.value = false
}

function getAllMenuKeys(tree: any[]): number[] {
  const keys: number[] = []
  function traverse(nodes: any[]) {
    nodes.forEach(node => {
      keys.push(node.id)
      if (node.children?.length) traverse(node.children)
    })
  }
  traverse(tree)
  return keys
}

async function handleSaveMenus() {
  if (!currentRole.value) return
  saveLoading.value = true
  try {
    const menuIds = menuTreeRef.value?.getCheckedKeys() || []
    await assignRoleMenus(currentRole.value.id, menuIds)
    ElMessage.success('权限保存成功')
  } finally {
    saveLoading.value = false
  }
}

function handleAdd() {
  dialogTitle.value = '新增角色'
  formData.value = { id: undefined, roleName: '', roleCode: '', remark: '' }
  dialogVisible.value = true
}

function handleEdit() {
  if (!currentRole.value) return
  dialogTitle.value = '编辑角色'
  formData.value = { ...currentRole.value }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateRole(formData.value)
      ElMessage.success('更新成功')
    } else {
      await createRole(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadRoles()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete() {
  if (!currentRole.value) return
  await ElMessageBox.confirm('确定要删除该角色吗？', '提示', { type: 'warning' })
  await deleteRole(currentRole.value.id)
  ElMessage.success('删除成功')
  currentRole.value = null
  await loadRoles()
}

onMounted(() => {
  loadRoles()
  loadMenuTree()
})
</script>

<style scoped>
.role-container {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.role-list {
  max-height: 500px;
  overflow-y: auto;
}
.role-item {
  padding: 10px 12px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid transparent;
  margin-bottom: 4px;
  transition: all 0.2s;
}
.role-item:hover {
  background-color: #f5f7fa;
}
.role-item.active {
  background-color: #ecf5ff;
  border-color: #409eff;
}
.role-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.role-name {
  font-weight: 500;
}
.role-code {
  font-size: 12px;
  color: #909399;
}
.permission-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
</style>
