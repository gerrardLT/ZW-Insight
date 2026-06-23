<template>
  <div class="org-container">
    <el-row :gutter="16">
      <!-- 左侧机构树 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>组织机构</span>
              <el-button type="primary" size="small" @click="handleAdd(0)">
                <el-icon><Plus /></el-icon>新增
              </el-button>
            </div>
          </template>
          <el-input v-model="filterText" placeholder="输入机构名称搜索" clearable style="margin-bottom: 12px" />
          <el-tree
            ref="treeRef"
            :data="orgTree"
            :props="{ label: 'orgName', children: 'children' }"
            :filter-node-method="filterNode"
            node-key="id"
            highlight-current
            default-expand-all
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <div class="tree-node">
                <span>{{ data.orgName }}</span>
                <el-tag v-if="data.status === 0" type="danger" size="small">停用</el-tag>
              </div>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右侧详情/编辑 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ currentOrg ? '机构详情' : '请选择机构' }}</span>
              <div v-if="currentOrg">
                <el-button type="primary" size="small" @click="handleEdit">编辑</el-button>
                <el-button type="success" size="small" @click="handleAdd(currentOrg.id)">新增子机构</el-button>
                <el-button type="warning" size="small" @click="handleToggleStatus">
                  {{ currentOrg.status === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button type="danger" size="small" @click="handleDelete">删除</el-button>
              </div>
            </div>
          </template>

          <el-descriptions v-if="currentOrg" :column="2" border>
            <el-descriptions-item label="机构名称">{{ currentOrg.orgName }}</el-descriptions-item>
            <el-descriptions-item label="机构编码">{{ currentOrg.orgCode }}</el-descriptions-item>
            <el-descriptions-item label="机构类型">
              {{ currentOrg.orgType === 'COMPANY' ? '公司' : '部门' }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="currentOrg.status === 1 ? 'success' : 'danger'">
                {{ currentOrg.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="排序号">{{ currentOrg.sortOrder }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ currentOrg.createdAt }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="请从左侧选择机构查看详情" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="上级机构">
          <el-tree-select
            v-model="formData.parentId"
            :data="orgTree"
            :props="{ label: 'orgName', value: 'id', children: 'children' }"
            placeholder="请选择上级机构"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="机构名称" prop="orgName">
          <el-input v-model="formData.orgName" placeholder="请输入机构名称" />
        </el-form-item>
        <el-form-item label="机构编码" prop="orgCode">
          <el-input v-model="formData.orgCode" placeholder="请输入机构编码" />
        </el-form-item>
        <el-form-item label="机构类型" prop="orgType">
          <el-select v-model="formData.orgType" placeholder="请选择" style="width: 100%">
            <el-option label="公司" value="COMPANY" />
            <el-option label="部门" value="DEPARTMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="formData.sortOrder" :min="0" />
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
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getOrgTree, getOrgDetail, createOrg, updateOrg, deleteOrg, updateOrgStatus } from '@/api/system'

const treeRef = ref()
const formRef = ref<FormInstance>()
const filterText = ref('')
const orgTree = ref<any[]>([])
const currentOrg = ref<any>(null)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)

const formData = ref({
  id: undefined as number | undefined,
  parentId: 0,
  orgName: '',
  orgCode: '',
  orgType: 'DEPARTMENT',
  sortOrder: 0
})

const formRules = {
  orgName: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
  orgType: [{ required: true, message: '请选择机构类型', trigger: 'change' }]
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

function filterNode(value: string, data: any) {
  if (!value) return true
  return data.orgName.includes(value)
}

async function loadOrgTree() {
  const res: any = await getOrgTree()
  orgTree.value = res.data || []
}

function handleNodeClick(data: any) {
  currentOrg.value = data
}

function handleAdd(parentId: number) {
  dialogTitle.value = '新增机构'
  formData.value = {
    id: undefined,
    parentId,
    orgName: '',
    orgCode: '',
    orgType: 'DEPARTMENT',
    sortOrder: 0
  }
  dialogVisible.value = true
}

function handleEdit() {
  if (!currentOrg.value) return
  dialogTitle.value = '编辑机构'
  formData.value = { ...currentOrg.value }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateOrg(formData.value)
      ElMessage.success('更新成功')
    } else {
      await createOrg(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadOrgTree()
    if (formData.value.id) {
      currentOrg.value = { ...formData.value }
    }
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete() {
  if (!currentOrg.value) return
  await ElMessageBox.confirm('确定要删除该机构吗？', '提示', { type: 'warning' })
  await deleteOrg(currentOrg.value.id)
  ElMessage.success('删除成功')
  currentOrg.value = null
  await loadOrgTree()
}

async function handleToggleStatus() {
  if (!currentOrg.value) return
  const newStatus = currentOrg.value.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确定要${action}该机构吗？`, '提示', { type: 'warning' })
  await updateOrgStatus(currentOrg.value.id, newStatus)
  ElMessage.success(`${action}成功`)
  currentOrg.value.status = newStatus
  await loadOrgTree()
}

onMounted(() => {
  loadOrgTree()
})
</script>

<style scoped>
.org-container {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}
</style>
