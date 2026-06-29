<template>
  <div class="business-type-container">
    <el-row :gutter="16">
      <!-- 左侧业务类型树 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>业务类型</span>
              <el-button type="primary" size="small" @click="handleAdd(0)">
                <el-icon><Plus /></el-icon>新增
              </el-button>
            </div>
          </template>
          <el-input v-model="filterText" placeholder="输入名称搜索" clearable style="margin-bottom: 12px" />
          <el-tree
            ref="treeRef"
            :data="typeTree"
            :props="{ label: 'typeName', children: 'children' }"
            :filter-node-method="filterNode"
            node-key="id"
            highlight-current
            default-expand-all
            @node-click="handleNodeClick"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span>{{ data.typeName }}</span>
                <el-tag v-if="data.status === 0" type="danger" size="small">停用</el-tag>
              </div>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右侧详情 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ currentNode ? '类型详情' : '请选择业务类型' }}</span>
              <div v-if="currentNode">
                <el-button type="primary" size="small" @click="handleEdit">编辑</el-button>
                <el-button type="success" size="small" @click="handleAdd(currentNode.id)">新增子类型</el-button>
                <el-button type="danger" size="small" @click="handleDelete">删除</el-button>
              </div>
            </div>
          </template>

          <el-descriptions v-if="currentNode" :column="2" border>
            <el-descriptions-item label="类型名称">{{ currentNode.typeName }}</el-descriptions-item>
            <el-descriptions-item label="类型编码">{{ currentNode.typeCode }}</el-descriptions-item>
            <el-descriptions-item label="关联流程">{{ currentNode.processKey || '未关联' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="currentNode.status === 1 ? 'success' : 'danger'">
                {{ currentNode.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="排序号">{{ currentNode.sortOrder }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ currentNode.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="请从左侧选择业务类型查看详情" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="上级类型">
          <el-tree-select
            v-model="formData.parentId"
            :data="typeTree"
            :props="typeTreeSelectProps"
            placeholder="请选择上级类型（空为顶级）"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="类型名称" prop="typeName">
          <el-input v-model="formData.typeName" placeholder="请输入类型名称" />
        </el-form-item>
        <el-form-item label="类型编码" prop="typeCode">
          <el-input v-model="formData.typeCode" placeholder="请输入类型编码" />
        </el-form-item>
        <el-form-item label="关联流程">
          <el-input v-model="formData.processKey" placeholder="请输入流程标识（可选）" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="formData.sortOrder" :min="0" />
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
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getBusinessTypeTree,
  getBusinessTypeDetail,
  createBusinessType,
  updateBusinessType,
  deleteBusinessType
} from '@/api/workflow'

const treeRef = ref()
const formRef = ref<FormInstance>()
const filterText = ref('')
const typeTree = ref<any[]>([])
const typeTreeSelectProps = { label: 'typeName', value: 'id', children: 'children' }
const currentNode = ref<any>(null)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)

const formData = ref({
  id: undefined as number | undefined,
  parentId: 0,
  typeName: '',
  typeCode: '',
  processKey: '',
  sortOrder: 0,
  remark: ''
})

const formRules = {
  typeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
  typeCode: [{ required: true, message: '请输入类型编码', trigger: 'blur' }]
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

function filterNode(value: string, data: any) {
  if (!value) return true
  return data.typeName.includes(value)
}

async function loadTree() {
  const res: any = await getBusinessTypeTree()
  typeTree.value = res.data || []
}

async function handleNodeClick(data: any) {
  try {
    const res: any = await getBusinessTypeDetail(data.id)
    currentNode.value = res.data || data
  } catch {
    currentNode.value = data
  }
}

function handleAdd(parentId: number) {
  dialogTitle.value = '新增业务类型'
  formData.value = {
    id: undefined,
    parentId,
    typeName: '',
    typeCode: '',
    processKey: '',
    sortOrder: 0,
    remark: ''
  }
  dialogVisible.value = true
}

function handleEdit() {
  if (!currentNode.value) return
  dialogTitle.value = '编辑业务类型'
  formData.value = { ...currentNode.value }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateBusinessType(formData.value)
      ElMessage.success('更新成功')
    } else {
      await createBusinessType(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadTree()
    if (formData.value.id) {
      currentNode.value = { ...formData.value }
    }
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete() {
  if (!currentNode.value) return
  await ElMessageBox.confirm('确定要删除该业务类型吗？', '提示', { type: 'warning' })
  await deleteBusinessType(currentNode.value.id)
  ElMessage.success('删除成功')
  currentNode.value = null
  await loadTree()
}

onMounted(() => {
  loadTree()
})
</script>

<style scoped>
.business-type-container {
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
