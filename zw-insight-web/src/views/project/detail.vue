<template>
  <div class="project-detail-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>项目详情：{{ projectInfo.projectName }}</span>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="项目编号">{{ projectInfo.projectCode }}</el-descriptions-item>
            <el-descriptions-item label="项目名称">{{ projectInfo.projectName }}</el-descriptions-item>
            <el-descriptions-item label="项目性质">{{ projectInfo.projectNature }}</el-descriptions-item>
            <el-descriptions-item label="项目类型">{{ projectInfo.projectType }}</el-descriptions-item>
            <el-descriptions-item label="业主单位">{{ projectInfo.ownerCompanyName }}</el-descriptions-item>
            <el-descriptions-item label="签约公司">{{ projectInfo.signingCompanyName }}</el-descriptions-item>
            <el-descriptions-item label="项目地址">{{ projectInfo.projectAddress }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ projectInfo.contactName }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ projectInfo.contactPhone }}</el-descriptions-item>
            <el-descriptions-item label="预算金额">{{ projectInfo.budgetAmount }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(projectInfo.status)" size="small">
                {{ getStatusLabel(projectInfo.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ projectInfo.createdAt }}</el-descriptions-item>
            <el-descriptions-item label="项目概述" :span="2">{{ projectInfo.projectOverview }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="项目团队" name="team">
          <ProjectMember :project-id="projectId" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProjectDetail } from '@/api/project'
import ProjectMember from './components/ProjectMember.vue'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => Number(route.params.id))
const projectInfo = ref<any>({})
const activeTab = ref('info')

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  FILED: { label: '已报备', type: 'primary' },
  TENDERING: { label: '招标中', type: 'warning' },
  WON: { label: '已中标', type: 'success' },
  CONSTRUCTION: { label: '施工中', type: '' },
  COMPLETED: { label: '已竣工', type: 'success' },
  CLOSED: { label: '已关闭', type: 'danger' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status || ''
}

function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}

async function loadDetail() {
  if (!projectId.value) return
  const res: any = await getProjectDetail(projectId.value)
  projectInfo.value = res.data || {}
}

function handleBack() {
  router.push('/project/list')
}

onMounted(() => {
  loadDetail()
  // 支持 URL query 直接跳转到指定 tab
  if (route.query.tab) {
    activeTab.value = route.query.tab as string
  }
})
</script>

<style scoped>
.project-detail-container {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
