<template>
  <div class="inspection-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="质量检查" name="quality" />
        <el-tab-pane label="安全检查" name="safety" />
      </el-tabs>

      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-input v-model="queryParams.projectName" placeholder="项目名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="检查结果">
          <el-select v-model="queryParams.result" placeholder="全部" clearable style="width: 120px">
            <el-option label="合格" value="PASS" />
            <el-option label="不合格" value="FAIL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增{{ activeTab === 'quality' ? '质量' : '安全' }}检查</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="inspectionNo" label="检查编号" width="150" />
        <el-table-column prop="projectName" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="checkItem" label="检查项" min-width="160" show-overflow-tooltip />
        <el-table-column prop="inspector" label="检查人" width="90" />
        <el-table-column prop="inspectionDate" label="检查日期" width="110" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.result === 'PASS' ? 'success' : 'danger'" size="small">{{ row.result === 'PASS' ? '合格' : '不合格' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="整改说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getQualityInspectionPage, getSafetyInspectionPage, deleteQualityInspection, deleteSafetyInspection } from '@/api/site'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const activeTab = ref('quality')

const queryParams = ref({ pageNum: 1, pageSize: 10, projectName: '', result: '' })

const getPageApi = () => activeTab.value === 'quality' ? getQualityInspectionPage : getSafetyInspectionPage
const getDeleteApi = () => activeTab.value === 'quality' ? deleteQualityInspection : deleteSafetyInspection

async function loadData() {
  loading.value = true
  try {
    const res: any = await getPageApi()(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleTabChange() { queryParams.value.pageNum = 1; loadData() }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, projectName: '', result: '' }; loadData() }

function handleAdd() {
  router.push({ path: '/site/inspection/form', query: { type: activeTab.value } })
}

function handleEdit(row: any) {
  router.push({ path: `/site/inspection/form/${row.id}`, query: { type: activeTab.value } })
}

function handleDetail(row: any) {
  router.push({ path: `/site/inspection/detail/${row.id}` })
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await getDeleteApi()(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => { loadData() })
</script>

<style scoped>
.inspection-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
