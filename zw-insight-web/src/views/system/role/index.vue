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
              <div class="role-meta">
                <span class="role-code">{{ role.roleCode }}</span>
                <el-tag size="small" type="info" class="data-scope-tag">
                  {{ getDataScopeLabel(role.dataScope) }}
                </el-tag>
              </div>
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
                <el-button size="small" @click="handleDataScope">配置数据权限</el-button>
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

    <!-- 数据权限配置弹窗 -->
    <el-dialog v-model="dataScopeDialogVisible" title="配置数据权限" width="480px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="角色名称">
          <el-input :model-value="currentRole?.roleName" disabled />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="dataScopeForm.dataScope" placeholder="请选择数据范围" style="width: 100%">
            <el-option
              v-for="item in dataScopeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataScopeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dataScopeLoading" @click="handleSaveDataScope">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getRoleList, createRole, updateRole, deleteRole, getRoleMenuIds, assignRoleMenus, getMenuTree, updateRoleDataScope } from '@/api/system'

const dataScopeOptions = [
  { value: 'ALL', label: '全部数据' },
  { value: 'DEPT_AND_CHILDREN', label: '本部门及下级' },
  { value: 'DEPT', label: '本部门' },
  { value: 'PROJECT', label: '本项目' },
  { value: 'SELF', label: '仅本人' }
]

function getDataScopeLabel(dataScope?: string): string {
  if (!dataScope) return '仅本人'
  const found = dataScopeOptions.find(item => item.value === dataScope)
  return found ? found.label : '仅本人'
}

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

// 数据权限配置
const dataScopeDialogVisible = ref(false)
const dataScopeLoading = ref(false)
const dataScopeForm = ref({
  dataScope: 'SELF'
})

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

// 数据权限相关
function handleDataScope() {
  if (!currentRole.value) return
  dataScopeForm.value.dataScope = currentRole.value.dataScope || 'SELF'
  dataScopeDialogVisible.value = true
}

async function handleSaveDataScope() {
  if (!currentRole.value) return
  dataScopeLoading.value = true
  try {
    await updateRoleDataScope(currentRole.value.id, dataScopeForm.value.dataScope)
    ElMessage.success('数据权限配置成功')
    dataScopeDialogVisible.value = false
    // 更新当前角色的 dataScope
    currentRole.value.dataScope = dataScopeForm.value.dataScope
    // 刷新角色列表
    await loadRoles()
  } finally {
    dataScopeLoading.value = false
  }
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
.role-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}
.role-name {
  font-weight: 500;
}
.role-code {
  font-size: 12px;
  color: #909399;
}
.data-scope-tag {
  font-size: 12px;
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
