<template>
  <div class="inspection-detail-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>检查详情</span>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="检查编号">{{ detail.inspectionNo }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ detail.projectName }}</el-descriptions-item>
        <el-descriptions-item label="检查类型">
          {{ detail.inspectionType === 'QUALITY' ? '质量检查' : '安全检查' }}
        </el-descriptions-item>
        <el-descriptions-item label="检查方案">
          <span v-if="schemeName">{{ schemeName }}</span>
          <span v-else class="text-muted">未关联方案</span>
        </el-descriptions-item>
        <el-descriptions-item label="检查人">{{ detail.inspector }}</el-descriptions-item>
        <el-descriptions-item label="检查日期">{{ detail.inspectionDate }}</el-descriptions-item>
        <el-descriptions-item label="检查结果">
          <el-tag :type="detail.result === 'PASS' ? 'success' : 'danger'" size="small">
            {{ detail.result === 'PASS' ? '合格' : '不合格' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="整改说明">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="整改状态">
          <el-tag v-if="detail.rectificationStatus" :type="rectTagType(detail.rectificationStatus)" size="small">{{ rectText(detail.rectificationStatus) }}</el-tag>
          <span v-else class="text-muted">无需整改</span>
        </el-descriptions-item>
        <el-descriptions-item label="整改期限">{{ detail.rectificationDeadline || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 方案快照中的检查项展示 -->
      <el-divider content-position="left">检查明细</el-divider>

      <el-table :data="detailItems" border>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="itemName" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="checkStandard" label="检查标准" min-width="200" show-overflow-tooltip />
        <el-table-column prop="checkMethod" label="检查方法" min-width="160" show-overflow-tooltip />
        <el-table-column prop="result" label="检查结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.result === 'PASS'"
              type="success"
              size="small"
            >合格</el-tag>
            <el-tag
              v-else-if="row.result === 'FAIL'"
              type="danger"
              size="small"
            >不合格</el-tag>
            <el-tag
              v-else
              type="info"
              size="small"
            >未检查</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="detailItems.length === 0" description="暂无检查明细" />
    </el-card>

    <!-- 整改闭环区：仅有问题的检查记录展示 -->
    <el-card v-if="detail.hasProblem === 1" shadow="never" class="rect-card">
      <template #header>
        <div class="card-header">
          <span>整改闭环</span>
        </div>
      </template>

      <!-- 提交整改表单：仅待整改状态可提交 -->
      <template v-if="detail.rectificationStatus === 'PENDING'">
        <el-form ref="submitFormRef" :model="submitForm" :rules="submitRules" label-width="100px" style="max-width: 640px">
          <el-form-item label="整改内容" prop="rectificationContent">
            <el-input v-model="submitForm.rectificationContent" type="textarea" :rows="3" placeholder="请描述整改措施与结果" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitLoading" @click="handleSubmitRectification">提交整改</el-button>
          </el-form-item>
        </el-form>
        <el-divider />
      </template>

      <!-- 整改记录列表 -->
      <el-table :data="rectifications" v-loading="rectLoading" border>
        <el-table-column prop="rectificationContent" label="整改内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="rectTagType(row.status)" size="small">{{ rectText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" link type="success" @click="handleApprove(row)">复查通过</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rectLoading && rectifications.length === 0" description="暂无整改记录" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getInspectionDetail } from '@/api/inspection-scheme'
import { getRectifications, submitRectification, approveRectification } from '@/api/site'

const route = useRoute()
const router = useRouter()
const detail = ref<any>({})
const detailItems = ref<any[]>([])

const RECT_MAP: Record<string, { text: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  PENDING: { text: '待整改', type: 'warning' },
  SUBMITTED: { text: '待复查', type: 'info' },
  APPROVED: { text: '已闭环', type: 'success' },
  REJECTED: { text: '已驳回', type: 'danger' }
}
function rectText(s: string) { return RECT_MAP[s]?.text ?? s }
function rectTagType(s: string) { return RECT_MAP[s]?.type ?? 'info' }

// ================= 整改闭环 =================
const rectifications = ref<any[]>([])
const rectLoading = ref(false)
const submitFormRef = ref<FormInstance>()
const submitLoading = ref(false)
const submitForm = ref({ rectificationContent: '' })
const submitRules: FormRules = {
  rectificationContent: [{ required: true, message: '请填写整改内容', trigger: 'blur' }]
}

async function loadRectifications() {
  const id = route.params.id as string
  if (!id) return
  rectLoading.value = true
  try {
    const res: any = await getRectifications(Number(id))
    rectifications.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '整改记录加载失败')
  } finally {
    rectLoading.value = false
  }
}

async function handleSubmitRectification() {
  await submitFormRef.value?.validate()
  submitLoading.value = true
  try {
    await submitRectification(Number(route.params.id), { rectificationContent: submitForm.value.rectificationContent })
    ElMessage.success('整改已提交，等待复查')
    submitForm.value.rectificationContent = ''
    await Promise.all([loadDetail(), loadRectifications()])
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '提交整改失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleApprove(row: any) {
  await ElMessageBox.confirm('确认该整改已通过复查？', '复查确认', { type: 'warning' })
  try {
    await approveRectification(row.id)
    ElMessage.success('复查通过，整改已闭环')
    await Promise.all([loadDetail(), loadRectifications()])
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '复查操作失败')
  }
}

/** 从快照或明细中获取方案名称 */
const schemeName = computed(() => {
  if (detail.value.schemeSnapshot) {
    try {
      const snapshot = typeof detail.value.schemeSnapshot === 'string'
        ? JSON.parse(detail.value.schemeSnapshot)
        : detail.value.schemeSnapshot
      return snapshot.schemeName || ''
    } catch {
      return ''
    }
  }
  return detail.value.schemeName || ''
})

/** 加载检查详情 */
async function loadDetail() {
  const id = route.params.id as string
  if (!id) return
  try {
    const res: any = await getInspectionDetail(Number(id))
    detail.value = res.data || {}

    // 优先从details字段获取明细
    if (detail.value.details && detail.value.details.length > 0) {
      detailItems.value = detail.value.details
    } else if (detail.value.schemeSnapshot) {
      // 从快照恢复检查项
      try {
        const snapshot = typeof detail.value.schemeSnapshot === 'string'
          ? JSON.parse(detail.value.schemeSnapshot)
          : detail.value.schemeSnapshot
        detailItems.value = (snapshot.items || []).map((item: any) => ({
          itemName: item.itemName || '',
          checkStandard: item.checkStandard || '',
          checkMethod: item.checkMethod || '',
          result: item.result || 'UNCHECKED'
        }))
      } catch {
        detailItems.value = []
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载检查详情失败')
  }
}

function handleBack() {
  router.push('/site/inspection')
}

onMounted(() => {
  loadDetail()
  loadRectifications()
})
</script>

<style scoped>
.inspection-detail-container {
  padding: 16px;
}
.rect-card {
  margin-top: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.text-muted {
  color: #909399;
}
</style>
