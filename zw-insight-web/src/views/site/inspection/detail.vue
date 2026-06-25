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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getInspectionDetail } from '@/api/inspection-scheme'

const route = useRoute()
const router = useRouter()
const detail = ref<any>({})
const detailItems = ref<any[]>([])

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
  } catch (e) {
    ElMessage.error('加载检查详情失败')
  }
}

function handleBack() {
  router.push('/site/inspection')
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.inspection-detail-container {
  padding: 16px;
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
