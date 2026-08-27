<template>
  <div class="menu-container">
    <el-card shadow="never">
      <!-- 操作栏 -->
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd(0)">
          <el-icon><Plus /></el-icon>新增菜单
        </el-button>
        <el-button @click="toggleExpandAll">
          {{ isExpandAll ? '折叠全部' : '展开全部' }}
        </el-button>
      </div>

      <!-- 树形表格 -->
      <el-table
        v-if="refreshTable"
        :data="menuTree"
        row-key="id"
        :default-expand-all="isExpandAll"
        :tree-props="{ children: 'children' }"
        border
        v-loading="loading"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="180" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.menuType === 'DIR'" type="warning" size="small">目录</el-tag>
            <el-tag v-else-if="row.menuType === 'MENU'" type="success" size="small">菜单</el-tag>
            <el-tag v-else type="info" size="small">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图标" width="100" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon" class="icon-fallback-marked"><component :is="resolveMenuIcon(row.icon)" /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" width="160" />
        <el-table-column prop="component" label="组件路径" width="200" />
        <el-table-column prop="permission" label="权限标识" width="160" />
        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleAdd(row.id)">新增</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="formData.parentId"
            :data="menuTreeForSelect"
            :props="menuTreeSelectProps"
            placeholder="顶级菜单"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="formData.menuType">
            <el-radio value="DIR">目录</el-radio>
            <el-radio value="MENU">菜单</el-radio>
            <el-radio value="BUTTON">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item v-if="formData.menuType !== 'BUTTON'" label="路由路径">
          <el-input v-model="formData.path" placeholder="请输入路由路径" />
        </el-form-item>
        <el-form-item v-if="formData.menuType === 'MENU'" label="组件路径">
          <el-input v-model="formData.component" placeholder="请输入组件路径" />
        </el-form-item>
        <el-form-item v-if="formData.menuType !== 'BUTTON'" label="图标">
          <el-select
            v-model="formData.icon"
            filterable
            clearable
            placeholder="请选择图标"
            style="width: 100%"
            data-testid="icon-select"
          >
            <!-- 编辑时若为未知图标名，先显示该选项作为兜底 -->
            <el-option
              v-if="formData.icon && !MENU_ICON_OPTIONS.includes(formData.icon)"
              :value="formData.icon"
              :label="`${formData.icon} (unidentified)`"
              disabled
            />
            <el-option v-for="name in MENU_ICON_OPTIONS" :key="name" :value="name" :label="name">
              <span class="icon-option">
                <el-icon><component :is="name" /></el-icon>
                <span>{{ name }}</span>
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.menuType === 'BUTTON'" label="权限标识">
          <el-input v-model="formData.permission" placeholder="如：sys:user:add" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序号">
              <el-input-number v-model="formData.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="formData.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { MENU_ICON_OPTIONS, resolveMenuIcon } from '@/components/icons/registry'
import { getMenuTree, createMenu, updateMenu, deleteMenu } from '@/api/system'
import { listToTree } from '@/utils/tree'

const formRef = ref<FormInstance>()
const loading = ref(false)
const menuTree = ref<any[]>([])
const menuTreeForSelect = ref<any[]>([])
const menuTreeSelectProps = { label: 'menuName', value: 'id', children: 'children' }
const isExpandAll = ref(true)
const refreshTable = ref(true)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)

const formData = ref({
  id: undefined as number | undefined,
  parentId: 0,
  menuName: '',
  menuType: 'DIR',
  path: '',
  component: '',
  icon: '',
  permission: '',
  sortOrder: 0,
  status: 1
})

const formRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}

async function loadMenuTree() {
  loading.value = true
  try {
    const res: any = await getMenuTree()
    // 后端返回平铺列表，前端按 parentId 建树（SysMenuService.list 注释约定）
    const tree = listToTree(res.data || [])
    menuTree.value = tree
    menuTreeForSelect.value = [{ id: 0, menuName: '顶级菜单', children: tree }]
  } finally {
    loading.value = false
  }
}

function toggleExpandAll() {
  refreshTable.value = false
  isExpandAll.value = !isExpandAll.value
  nextTick(() => {
    refreshTable.value = true
  })
}

function handleAdd(parentId: number) {
  dialogTitle.value = '新增菜单'
  formData.value = {
    id: undefined,
    parentId,
    menuName: '',
    menuType: 'DIR',
    path: '',
    component: '',
    icon: '',
    permission: '',
    sortOrder: 0,
    status: 1
  }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑菜单'
  formData.value = { ...row }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateMenu(formData.value)
      ElMessage.success('更新成功')
    } else {
      await createMenu(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadMenuTree()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该菜单吗？删除后子菜单也将被删除。', '提示', { type: 'warning' })
  await deleteMenu(row.id)
  ElMessage.success('删除成功')
  await loadMenuTree()
}

onMounted(() => {
  loadMenuTree()
})
</script>

<style scoped>
.menu-container {
  padding: 16px;
}
.table-toolbar {
  margin-bottom: 16px;
}
/* 图标选项预览 */
.icon-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.icon-option :deep(.el-icon) {
  font-size: 16px;
}
/* 解析失败显式标记（警告三角形，避免静默丢失） */
.icon-fallback-marked {
  color: var(--zw-warning);
}
</style>
