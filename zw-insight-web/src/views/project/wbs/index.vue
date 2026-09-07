<template>
  <div class="wbs-container">
    <el-card shadow="never">
      <!-- 项目选择 + 操作栏 -->
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select
            v-model="queryParams.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            clearable
            style="width: 240px"
            @change="handleProjectChange"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px" @change="loadTree">
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTree">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" :disabled="!queryParams.projectId" @click="handleAdd(null)">
          <el-icon><Plus /></el-icon>新增根节点
        </el-button>
        <el-button :disabled="!queryParams.projectId" @click="toggleExpand">
          {{ expandAll ? '收起全部' : '展开全部' }}
        </el-button>
        <span v-if="queryParams.projectId" class="toolbar-hint">
          共 {{ nodeCount }} 个节点，最深 {{ maxLevel }} 级
        </span>
      </div>

      <!-- WBS 树 -->
      <div v-loading="loading" class="tree-wrap">
        <el-empty v-if="!loading && !treeData.length" :description="queryParams.projectId ? '该项目暂无 WBS 节点' : '请先选择项目'" />
        <el-tree
          v-else
          ref="treeRef"
          :data="filteredTree"
          :props="treeProps"
          node-key="id"
          :default-expand-all="expandAll"
          :expand-on-click-node="false"
          highlight-current
        >
          <template #default="{ data }">
            <div class="tree-row">
              <span class="row-code">{{ data.nodeCode }}</span>
              <span class="row-name">{{ data.nodeName }}</span>
              <el-tag size="small" :type="levelTagType(data.nodeLevel)" effect="plain">
                {{ levelLabel(data.nodeLevel) }}
              </el-tag>
              <el-tag size="small" :type="statusTagType(data.status)" effect="plain">
                {{ statusLabel(data.status) }}
              </el-tag>
              <span v-if="data.startDate || data.endDate" class="row-dates">
                {{ data.startDate || '?' }} ~ {{ data.endDate || '?' }}
              </span>
              <span class="row-actions">
                <el-button type="primary" link size="small" @click.stop="handleAdd(data)">加子节点</el-button>
                <el-button type="primary" link size="small" @click.stop="handleEdit(data)">编辑</el-button>
                <el-button type="danger" link size="small" @click.stop="handleDelete(data)">删除</el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item v-if="parentLabel" label="父节点">
          <el-input :model-value="parentLabel" disabled />
        </el-form-item>
        <el-form-item label="节点编号" prop="nodeCode">
          <el-input v-model="formData.nodeCode" placeholder="如 PH-001 / WP-001" maxlength="50" />
        </el-form-item>
        <el-form-item label="节点名称" prop="nodeName">
          <el-input v-model="formData.nodeName" placeholder="如 地下室结构 / 主体混凝土" maxlength="200" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="计划工期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio-button label="ACTIVE">活跃</el-radio-button>
            <el-radio-button label="INACTIVE">停用</el-radio-button>
            <el-radio-button label="CLOSED">已关闭</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getWbsTree, createWbsNode, updateWbsNode, deleteWbsNode,
  type WbsNode
} from '@/api/wbs'
import { getProjectList } from '@/api/project'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const expandAll = ref(true)
const treeRef = ref()
const formRef = ref<FormInstance>()
const treeData = ref<WbsNode[]>([])
const projectList = ref<any[]>([])
const dateRange = ref<[string, string] | null>(null)
const editingId = ref<number | null>(null)
const parentLabel = ref('')

const queryParams = reactive({
  projectId: null as number | null,
  status: ''
})

const formData = reactive({
  nodeCode: '',
  nodeName: '',
  description: '',
  startDate: '' as string | null,
  endDate: '' as string | null,
  sortOrder: 0,
  status: 'ACTIVE',
  parentId: null as number | null
})

const formRules: FormRules = {
  nodeCode: [
    { required: true, message: '请输入节点编号', trigger: 'blur' },
    { max: 50, message: '编号不超过 50 字符', trigger: 'blur' }
  ],
  nodeName: [
    { required: true, message: '请输入节点名称', trigger: 'blur' },
    { max: 200, message: '名称不超过 200 字符', trigger: 'blur' }
  ]
}

const treeProps = { children: 'children', label: 'nodeName' }

const dialogTitle = computed(() => (editingId.value ? '编辑 WBS 节点' : '新增 WBS 节点'))

/** 状态过滤在前端做：树结构必须整体加载才能正确装配父子，服务端过滤会产生孤儿节点 */
const filteredTree = computed(() => {
  if (!queryParams.status) return treeData.value
  const filterNodes = (nodes: WbsNode[]): WbsNode[] => {
    const result: WbsNode[] = []
    for (const n of nodes) {
      const children = n.children ? filterNodes(n.children) : []
      if (n.status === queryParams.status || children.length) {
        result.push({ ...n, children })
      }
    }
    return result
  }
  return filterNodes(treeData.value)
})

const nodeCount = computed(() => countNodes(treeData.value))
const maxLevel = computed(() => maxDepth(treeData.value))

function countNodes(nodes: WbsNode[]): number {
  return nodes.reduce((sum, n) => sum + 1 + (n.children ? countNodes(n.children) : 0), 0)
}

function maxDepth(nodes: WbsNode[]): number {
  if (!nodes.length) return 0
  return 1 + Math.max(0, ...nodes.map(n => (n.children ? maxDepth(n.children) : 0)))
}

function levelLabel(level?: number) {
  switch (level) {
    case 1: return '阶段'
    case 2: return '工作包'
    case 3: return '任务'
    default: return `L${level ?? 1}`
  }
}

function levelTagType(level?: number) {
  switch (level) {
    case 1: return 'warning'
    case 2: return 'success'
    default: return 'info'
  }
}

function statusLabel(status?: string) {
  return ({ ACTIVE: '活跃', INACTIVE: '停用', CLOSED: '已关闭' } as Record<string, string>)[status || ''] || status || '-'
}

function statusTagType(status?: string) {
  return ({ ACTIVE: 'success', INACTIVE: 'info', CLOSED: 'danger' } as Record<string, string>)[status || ''] || 'info'
}

async function searchProject(keyword: string) {
  try {
    const res: any = await getProjectList({ projectName: keyword })
    projectList.value = res.data || []
  } catch (e) {
    projectList.value = []
  }
}

async function loadTree() {
  if (!queryParams.projectId) {
    treeData.value = []
    return
  }
  loading.value = true
  try {
    const res: any = await getWbsTree(queryParams.projectId)
    treeData.value = res.data || []
  } catch (e: any) {
    treeData.value = []
    ElMessage.error(e?.message || '加载 WBS 失败')
  } finally {
    loading.value = false
  }
}

function handleProjectChange() {
  loadTree()
}

function handleReset() {
  queryParams.status = ''
  loadTree()
}

function toggleExpand() {
  expandAll.value = !expandAll.value
  // el-tree 的 default-expand-all 仅在初始化生效，切换后需重挂载
  const data = treeData.value
  treeData.value = []
  setTimeout(() => { treeData.value = data }, 0)
}

function resetForm() {
  editingId.value = null
  parentLabel.value = ''
  dateRange.value = null
  Object.assign(formData, {
    nodeCode: '', nodeName: '', description: '',
    startDate: null, endDate: null, sortOrder: 0, status: 'ACTIVE', parentId: null
  })
  formRef.value?.clearValidate()
}

function handleAdd(parent: WbsNode | null) {
  if (!queryParams.projectId) {
    ElMessage.warning('请先选择项目')
    return
  }
  resetForm()
  if (parent) {
    formData.parentId = parent.id ?? null
    parentLabel.value = `${parent.nodeCode} ${parent.nodeName}`
    formData.sortOrder = (parent.children?.length ?? 0) + 1
  }
  dialogVisible.value = true
}

function handleEdit(node: WbsNode) {
  resetForm()
  editingId.value = node.id ?? null
  formData.nodeCode = node.nodeCode
  formData.nodeName = node.nodeName
  formData.description = node.description || ''
  formData.startDate = node.startDate || null
  formData.endDate = node.endDate || null
  formData.sortOrder = node.sortOrder ?? 0
  formData.status = node.status || 'ACTIVE'
  formData.parentId = node.parentId ?? null
  dateRange.value = node.startDate && node.endDate ? [node.startDate, node.endDate] : null
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value || !queryParams.projectId) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const [start, end] = dateRange.value || [null, null]
  const payload: Partial<WbsNode> = {
    nodeCode: formData.nodeCode.trim(),
    nodeName: formData.nodeName.trim(),
    description: formData.description || undefined,
    startDate: start || null,
    endDate: end || null,
    sortOrder: formData.sortOrder,
    status: formData.status,
    parentId: formData.parentId
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await updateWbsNode(queryParams.projectId, editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createWbsNode(queryParams.projectId, payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadTree()
  } catch (e: any) {
    // 后端业务异常（编号重复/层级超限/环检测）必须原样呈现，不可静默
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(node: WbsNode) {
  const hasChildren = (node.children?.length ?? 0) > 0
  try {
    await ElMessageBox.confirm(
      hasChildren
        ? `节点「${node.nodeName}」下还有 ${node.children!.length} 个子节点，需先删除子节点。是否仍尝试删除？`
        : `确认删除节点「${node.nodeCode} ${node.nodeName}」？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteWbsNode(queryParams.projectId!, node.id!)
    ElMessage.success('删除成功')
    await loadTree()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(async () => {
  await searchProject('')
})
</script>

<style scoped lang="scss">
.wbs-container {
  padding: var(--zw-space-lg);
}

.table-toolbar {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  margin-bottom: var(--zw-space-sm-md);

  .toolbar-hint {
    margin-left: auto;
    font-size: var(--zw-font-size-xs);
    color: var(--zw-text-tertiary);
  }
}

.tree-wrap {
  min-height: 320px;
  max-height: calc(100vh - 320px);
  overflow: auto;
}

.tree-row {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  width: 100%;
  padding-right: var(--zw-space-sm);

  .row-code {
    font-family: var(--zw-font-mono);
    font-size: var(--zw-font-size-xs);
    color: var(--zw-brand);
    min-width: 84px;
  }

  .row-name {
    font-size: var(--zw-font-size-base);
    color: var(--zw-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .row-dates {
    font-size: var(--zw-font-size-xs);
    color: var(--zw-text-tertiary);
    font-family: var(--zw-font-mono);
  }

  .row-actions {
    margin-left: auto;
    display: none;
    gap: var(--zw-space-xs);
  }
}

/* 悬停才显示操作，避免密集树里满屏按钮 */
:deep(.el-tree-node__content:hover) .row-actions {
  display: inline-flex;
}
</style>
