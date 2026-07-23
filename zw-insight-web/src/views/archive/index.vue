<template>
  <div class="archive-container">
    <el-card shadow="never">
      <el-alert
        title="项目档案为只读聚合视图，数据实时汇总自项目、合同、财务、分包、机械等模块，此处仅供查询归档，不产生新数据"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-form inline>
        <el-form-item label="选择项目">
          <el-select
            v-model="selectedProjectId"
            filterable
            clearable
            placeholder="请选择要查询的项目"
            style="width: 320px"
            :loading="projectLoading"
            @change="handleProjectChange"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="`${p.projectCode} - ${p.projectName}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-empty v-if="!selectedProjectId" description="请先选择一个项目以查看其档案" />

      <div v-else v-loading="loading">
        <!-- 项目基本信息 -->
        <el-descriptions title="项目基本信息" :column="3" border style="margin-bottom: 16px">
          <el-descriptions-item label="项目编号">{{ project.projectCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="项目名称">{{ project.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="项目状态">{{ project.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="合同金额">{{ formatMoney(fundSummary.contractAmount) }}</el-descriptions-item>
          <el-descriptions-item label="累计收入">{{ formatMoney(fundSummary.totalIncome) }}</el-descriptions-item>
          <el-descriptions-item label="累计支出">{{ formatMoney(fundSummary.totalExpense) }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs>
          <el-tab-pane :label="`项目成员 (${members.length})`">
            <el-table :data="members" border size="small">
              <el-table-column prop="userName" label="成员姓名" min-width="120" />
              <el-table-column label="项目角色" min-width="200">
                <template #default="{ row }">{{ (row.projectRoles || []).join('、') || '-' }}</template>
              </el-table-column>
              <el-table-column prop="joinDate" label="加入日期" width="130" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">{{ row.status === 1 ? '正常' : '已失效' }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`施工合同 (${constructionContracts.length})`">
            <el-table :data="constructionContracts" border size="small">
              <el-table-column prop="contractCode" label="合同编号" min-width="160" show-overflow-tooltip />
              <el-table-column prop="contractType" label="合同类型" width="110" />
              <el-table-column prop="partyAName" label="甲方名称" min-width="160" show-overflow-tooltip />
              <el-table-column prop="signingDate" label="签订日期" width="130" />
              <el-table-column label="合同金额" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.contractAmount) }}</template>
              </el-table-column>
              <el-table-column label="累计产值" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.cumulativeOutput) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`付款记录 (${payments.length})`">
            <el-table :data="payments" border size="small">
              <el-table-column prop="supplierName" label="供应商/收款方" min-width="180" show-overflow-tooltip />
              <el-table-column prop="contractCategory" label="合同分类" width="120" />
              <el-table-column label="付款金额" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.paymentAmount) }}</template>
              </el-table-column>
              <el-table-column prop="paymentDate" label="付款日期" width="130" />
              <el-table-column prop="status" label="状态" width="100" align="center" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`收款记录 (${receivedPayments.length})`">
            <el-table :data="receivedPayments" border size="small">
              <el-table-column prop="receiver" label="收款人" min-width="140" />
              <el-table-column label="收款金额" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.receiveAmount) }}</template>
              </el-table-column>
              <el-table-column prop="receiveDate" label="收款日期" width="130" />
              <el-table-column prop="receiveType" label="收款方式" width="120" />
              <el-table-column prop="status" label="状态" width="100" align="center" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`分包合同 (${subcontracts.length})`">
            <el-table :data="subcontracts" border size="small">
              <el-table-column prop="contractCode" label="合同编号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="contractName" label="合同名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="subcontractor" label="分包方" min-width="150" show-overflow-tooltip />
              <el-table-column label="合同金额" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.contractAmount) }}</template>
              </el-table-column>
              <el-table-column label="累计产值" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.cumulativeOutput) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`机械合同 (${machineContracts.length})`">
            <el-table :data="machineContracts" border size="small">
              <el-table-column prop="contractCode" label="合同编号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="contractName" label="合同名称" min-width="160" show-overflow-tooltip />
              <el-table-column prop="machineName" label="设备名称" min-width="140" show-overflow-tooltip />
              <el-table-column prop="rentalType" label="租赁方式" width="110" />
              <el-table-column label="合同金额" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.contractAmount) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectList } from '@/api/project'
import { getProjectArchive } from '@/api/archive'
import type { Project } from '@/types/project'

const projectLoading = ref(false)
const loading = ref(false)
const projectOptions = ref<Project[]>([])
const selectedProjectId = ref<number | undefined>()

const project = ref<Record<string, any>>({})
const members = ref<any[]>([])
const constructionContracts = ref<any[]>([])
const payments = ref<any[]>([])
const receivedPayments = ref<any[]>([])
const subcontracts = ref<any[]>([])
const machineContracts = ref<any[]>([])
const fundSummary = ref<Record<string, any>>({})

function formatMoney(val: any) {
  if (val === null || val === undefined || val === '') return '-'
  const num = Number(val)
  if (Number.isNaN(num)) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function resetArchive() {
  project.value = {}
  members.value = []
  constructionContracts.value = []
  payments.value = []
  receivedPayments.value = []
  subcontracts.value = []
  machineContracts.value = []
  fundSummary.value = {}
}

async function loadProjects() {
  projectLoading.value = true
  try {
    const res: any = await getProjectList()
    projectOptions.value = res.data || []
  } catch (e: any) {
    ElMessage.error('加载项目列表失败：' + (e?.message || '接口异常'))
  } finally {
    projectLoading.value = false
  }
}

async function handleProjectChange(id: number | undefined) {
  if (!id) {
    resetArchive()
    return
  }
  loading.value = true
  try {
    const res: any = await getProjectArchive(id)
    const data = res.data || {}
    project.value = data.project || {}
    members.value = data.members || []
    constructionContracts.value = data.constructionContracts || []
    payments.value = data.payments || []
    receivedPayments.value = data.receivedPayments || []
    subcontracts.value = data.subcontracts || []
    machineContracts.value = data.machineContracts || []
    fundSummary.value = data.fundSummary || {}
  } catch (e: any) {
    resetArchive()
    ElMessage.error('加载项目档案失败：' + (e?.message || '接口异常'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped>
.archive-container {
  padding: 16px;
}
</style>
