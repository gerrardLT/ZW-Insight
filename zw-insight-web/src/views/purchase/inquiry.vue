<template>
  <div class="inquiry-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="询价标题">
          <el-input v-model="queryParams.title" placeholder="询价标题" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待发布" value="DRAFT" />
            <el-option label="报价中" value="PUBLISHED" />
            <el-option label="已报价" value="QUOTED" />
            <el-option label="已定标" value="AWARDED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增询价</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="title" label="询价标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="materialSummary" label="材料摘要" min-width="150" show-overflow-tooltip />
        <el-table-column prop="deadline" label="截止日期" width="110" />
        <el-table-column prop="quotationCount" label="报价数" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'AWARDED' ? 'success' : (row.status === 'PUBLISHED' || row.status === 'QUOTED') ? 'warning' : 'info'" size="small">
              {{ row.status === 'AWARDED' ? '已定标' : row.status === 'ANNOUNCED' ? '已公示' : row.status === 'QUOTED' ? '已报价' : row.status === 'PUBLISHED' ? '报价中' : '待发布' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handlePublish(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED' || row.status === 'QUOTED' || row.status === 'AWARDED'" link type="warning" @click="handleBid(row)">比价定标</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑询价' : '新增询价'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="询价标题" prop="title">
          <el-input v-model="formData.title" />
        </el-form-item>
        <el-form-item label="材料名称" prop="materialName">
          <el-input v-model="formData.materialName" />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="formData.specification" />
        </el-form-item>
        <el-form-item label="采购数量" prop="quantity">
          <el-input-number v-model="formData.quantity" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="截止日期" prop="deadline">
          <el-date-picker v-model="formData.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="要求说明">
          <el-input v-model="formData.requirement" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 比价定标弹窗（2026-08-15 决策 A 补齐：报价列表+排名+定标，B-P-X1 闭环） -->
    <el-dialog v-model="bidDialogVisible" :title="`比价定标 — ${bidRow?.title || ''}`" width="720px" destroy-on-close>
      <div style="margin-bottom: 12px">
        <el-button type="primary" size="small" :loading="rankingLoading" @click="handleCalculateRanking">计算排名</el-button>
        <span v-if="bidRow?.status === 'AWARDED'" style="margin-left: 8px; color: #67c23a">已定标</span>
      </div>
      <el-table :data="quotationList" border size="small" style="margin-bottom: 12px">
        <el-table-column prop="supplierName" label="报价供应商" min-width="160" />
        <el-table-column prop="totalAmount" label="报价总额(元)" width="130" align="right">
          <template #default="{ row }">{{ row.totalAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">{{ row.status === 'SUBMITTED' ? '已提交' : '草稿' }}</template>
        </el-table-column>
        <el-table-column prop="quotationSource" label="来源" width="90" align="center">
          <template #default="{ row }">{{ row.quotationSource === 'PORTAL' ? '门户' : '手动' }}</template>
        </el-table-column>
      </el-table>
      <el-table v-if="rankingList.length" :data="rankingList" border size="small">
        <el-table-column prop="ranking" label="排名" width="70" align="center" />
        <el-table-column prop="supplierName" label="供应商" min-width="160" />
        <el-table-column prop="totalAmount" label="报价总额(元)" width="130" align="right">
          <template #default="{ row }">{{ row.totalAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="中标" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isWinner === 1" type="success" size="small">中标</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="bidRow?.status !== 'AWARDED' && row.isWinner !== 1" link type="primary" @click="handleConfirmWinner(row)">确认定标</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="bidDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getInquiryPage, createInquiry, updateInquiry, deleteInquiry, publishInquiry, getQuotationList, calculateBidRanking, confirmBidWinner, getBidResultByInquiry } from '@/api/purchase'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, title: '', status: '' })
const formData = ref({ id: undefined as number | undefined, title: '', materialName: '', specification: '', quantity: 1, deadline: '', requirement: '' })
const formRules = { title: [{ required: true, message: '请输入询价标题', trigger: 'blur' }], materialName: [{ required: true, message: '请输入材料名称', trigger: 'blur' }], quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getInquiryPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, title: '', status: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, title: '', materialName: '', specification: '', quantity: 1, deadline: '', requirement: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }

// 将单物料表单字段组装为后端期望的 items 数组（后端持久化到 biz_inquiry_item），保持前后端一致
function buildInquiryPayload() {
  const f = formData.value
  // deadline 归一化为 datetime：后端 BizInquiry.deadline 为 LocalDateTime，
  // 日期控件 value-format=YYYY-MM-DD 纯日期会致 Jackson 解析 500（P2 实跑实证缺陷修复）
  const deadline = f.deadline
    ? (f.deadline.includes('T') ? f.deadline : `${f.deadline}T00:00:00`)
    : undefined
  return {
    id: f.id,
    title: f.title,
    deadline,
    description: f.requirement,
    materialSummary: f.materialName,
    items: [
      {
        materialName: f.materialName,
        specification: f.specification,
        quantity: f.quantity,
      },
    ],
  }
}

async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { const payload = buildInquiryPayload(); isEdit.value ? await updateInquiry(payload) : await createInquiry(payload); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handlePublish(row: any) { await ElMessageBox.confirm('确定要发布此询价吗？', '提示', { type: 'warning' }); await publishInquiry(row.id); ElMessage.success('发布成功'); loadData() }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteInquiry(row.id); ElMessage.success('删除成功'); loadData() }

// ======================== 比价定标（B-P-X1，2026-08-15 决策 A 补齐） ========================
const bidDialogVisible = ref(false)
const bidRow = ref<any>(null)
const quotationList = ref<any[]>([])
const rankingList = ref<any[]>([])
const rankingLoading = ref(false)

async function handleBid(row: any) {
  bidRow.value = row
  bidDialogVisible.value = true
  quotationList.value = []
  rankingList.value = []
  const res: any = await getQuotationList(row.id)
  quotationList.value = res.data || []
  // 已定标直接展示定标结果
  if (row.status === 'AWARDED') {
    const bidRes: any = await getBidResultByInquiry(row.id)
    rankingList.value = bidRes.data || []
  }
}

async function handleCalculateRanking() {
  if (!bidRow.value) return
  rankingLoading.value = true
  try {
    const res: any = await calculateBidRanking(bidRow.value.id)
    rankingList.value = res.data || []
    if (!rankingList.value.length) ElMessage.warning('暂无已提交的报价，无法计算排名')
  } finally {
    rankingLoading.value = false
  }
}

async function handleConfirmWinner(row: any) {
  await ElMessageBox.confirm(`确定定标给「${row.supplierName}」吗？`, '定标确认', { type: 'warning' })
  await confirmBidWinner({ inquiryId: bidRow.value.id, supplierId: row.supplierId })
  ElMessage.success('定标成功')
  bidRow.value.status = 'AWARDED'
  const bidRes: any = await getBidResultByInquiry(bidRow.value.id)
  rankingList.value = bidRes.data || []
  loadData()
}
onMounted(() => { loadData() })
</script>

<style scoped>
.inquiry-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
