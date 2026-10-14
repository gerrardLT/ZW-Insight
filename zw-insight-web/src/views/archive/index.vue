<template>
  <div class="archive-container">
    <el-card shadow="never">
      <el-alert
        title="项目档案为只读聚合视图，数据实时汇总自项目、合同、财务、分包、机械等模块，此处仅供查询归档，不产生新数据"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: var(--zw-space-md)"
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

      <ZwEmptyState type="data" v-if="!selectedProjectId" description="请先选择一个项目以查看其档案" />

      <div v-else v-loading="loading">
        <!-- 项目基本信息 -->
        <el-descriptions title="项目基本信息" :column="3" border style="margin-bottom: var(--zw-space-md)">
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

          <el-tab-pane :label="`劳务合同 (${laborContracts.length})`">
            <el-table :data="laborContracts" border size="small">
              <el-table-column prop="contractCode" label="合同编号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="contractName" label="合同名称" min-width="160" show-overflow-tooltip />
              <el-table-column prop="teamName" label="施工队伍" min-width="140" show-overflow-tooltip />
              <el-table-column label="合同金额" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.contractAmount) }}</template>
              </el-table-column>
              <el-table-column label="累计结算" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.cumulativeSettlement) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`变更签证 (${changeEvents.length})`">
            <el-table :data="changeEvents" border size="small">
              <el-table-column prop="eventNumber" label="事件编号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="title" label="事件标题" min-width="180" show-overflow-tooltip />
              <el-table-column prop="category" label="变更类别" width="120" />
              <el-table-column label="成本影响" width="150" align="right">
                <template #default="{ row }">{{ formatMoney(row.costDelta) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center" />
              <el-table-column prop="createdAt" label="登记时间" width="160" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`附件卷宗 (${filteredAttachments.length})`">
            <el-alert
              v-if="isProjectSealed"
              type="info"
              show-icon
              :closable="false"
              style="margin-bottom: var(--zw-space-sm-md);"
              title="该项目已竣工结案（封卷），卷宗处于只读保护状态，禁止新增、篡改或删除附件"
            />
            <div style="margin-bottom: var(--zw-space-sm-md); display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: var(--zw-space-sm);">
              <div style="display: flex; align-items: center; gap: var(--zw-space-sm);">
                <span style="font-size: var(--zw-font-size-sm); color: var(--zw-text-secondary);">案卷分类：</span>
                <el-radio-group v-model="selectedCategory" size="small">
                  <el-radio-button label="ALL">全部 ({{ attachments.length }})</el-radio-button>
                  <el-radio-button label="CONTRACT">合同卷</el-radio-button>
                  <el-radio-button label="CHANGE_EVENT">变更签证卷</el-radio-button>
                  <el-radio-button label="PROJECT">项目综合卷</el-radio-button>
                  <el-radio-button label="OTHER">其他业务卷</el-radio-button>
                </el-radio-group>
              </div>
              <div style="display: flex; gap: var(--zw-space-sm);">
                <el-button
                  type="default"
                  size="small"
                  :disabled="!selectedProjectId || attachments.length === 0"
                  @click="handleDownloadProjectZip"
                >
                  📦 打包下载全卷 (ZIP)
                </el-button>
                <el-upload
                  v-if="!isProjectSealed"
                  :show-file-list="false"
                  :before-upload="handleUploadArchiveAttachment"
                  :disabled="!selectedProjectId || uploadingFile"
                >
                  <el-button type="primary" size="small" :loading="uploadingFile" :disabled="!selectedProjectId">
                    上传附件
                  </el-button>
                </el-upload>
              </div>
            </div>
            <el-table :data="filteredAttachments" border size="small">
              <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
              <el-table-column prop="businessType" label="业务来源" width="130">
                <template #default="{ row }">
                  <el-tag size="small" :type="getCategoryTagType(row.businessType)">
                    {{ getCategoryLabel(row.businessType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="fileSize" label="大小 (Bytes)" width="120" align="right" />
              <el-table-column prop="fileHash" label="SHA-256 指纹" width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  <span style="font-family: monospace; font-size: var(--zw-font-size-xs);">{{ row.fileHash || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="上传时间" width="160" />
              <el-table-column label="操作" width="140" align="center">
                <template #default="{ row }">
                  <el-link type="primary" :underline="false" style="margin-right: var(--zw-space-sm-md);" @click="openAttachment(row.id)">预览</el-link>
                  <el-popconfirm v-if="!isProjectSealed" title="确定删除该附件吗？" @confirm="handleDeleteAttachment(row.id)">
                    <template #reference>
                      <el-link type="danger" :underline="false">删除</el-link>
                    </template>
                  </el-popconfirm>
                  <span v-else style="color: var(--zw-text-secondary); font-size: var(--zw-font-size-xs);">已封卷</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import ZwEmptyState from '@/components/ZwEmptyState.vue'
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectList } from '@/api/project'
import { getProjectArchive } from '@/api/archive'
import { getFilePreviewUrl } from '@/api/batch'
import { uploadFile, deleteFile } from '@/api/file'
import type { Project } from '@/types/project'

const projectLoading = ref(false)
const loading = ref(false)
const uploadingFile = ref(false)
const projectOptions = ref<Project[]>([])
// 雪花 ID 经 Jackson 序列化为 string，故兼容 number|string
const selectedProjectId = ref<number | string | undefined>()
const selectedCategory = ref<string>('ALL')

const project = ref<Record<string, any>>({})
const members = ref<any[]>([])
const constructionContracts = ref<any[]>([])
const payments = ref<any[]>([])
const receivedPayments = ref<any[]>([])
const subcontracts = ref<any[]>([])
const machineContracts = ref<any[]>([])
const laborContracts = ref<any[]>([])
const changeEvents = ref<any[]>([])
const attachments = ref<any[]>([])
const fundSummary = ref<Record<string, any>>({})

/** 项目是否已结案封卷（CLOSED 或 TERMINATED）：WORM 语义，封卷后剥夺删除与上传入口 */
const isProjectSealed = computed(() => {
  const s = project.value?.status
  return s === 'CLOSED' || s === 'TERMINATED'
})

/** 按案卷分类过滤附件 */
const filteredAttachments = computed(() => {
  if (selectedCategory.value === 'ALL') return attachments.value
  return attachments.value.filter(a => {
    const t = a.businessType || ''
    if (selectedCategory.value === 'CONTRACT') return t.includes('CONTRACT')
    if (selectedCategory.value === 'CHANGE_EVENT') return t === 'CHANGE_EVENT'
    if (selectedCategory.value === 'PROJECT') return t === 'PROJECT' || t === 'PROJECT_DOCUMENT'
    if (selectedCategory.value === 'OTHER') return !t.includes('CONTRACT') && t !== 'CHANGE_EVENT' && t !== 'PROJECT'
    return true
  })
})

function getCategoryLabel(type?: string): string {
  if (!type) return '综合卷'
  if (type.includes('CONTRACT')) return '合同卷'
  if (type === 'CHANGE_EVENT') return '签证卷'
  if (type === 'PROJECT') return '立项卷'
  return type
}

function getCategoryTagType(type?: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  if (!type) return 'info'
  if (type.includes('CONTRACT')) return 'success'
  if (type === 'CHANGE_EVENT') return 'warning'
  if (type === 'PROJECT') return ''
  return 'info'
}

function formatMoney(val: any) {
  if (val === null || val === undefined || val === '') return '-'
  const num = Number(val)
  if (Number.isNaN(num)) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function openAttachment(fileId: number | string) {
  try {
    const res: any = await getFilePreviewUrl(fileId as number)
    if (res.data) window.open(res.data, '_blank')
    else ElMessage.warning('暂无可用的预览地址')
  } catch (e: any) {
    ElMessage.error('获取预览地址失败：' + (e?.message || '接口异常'))
  }
}

async function handleUploadArchiveAttachment(file: File) {
  if (!selectedProjectId.value) {
    ElMessage.warning('请先选择一个项目')
    return false
  }
  uploadingFile.value = true
  try {
    await uploadFile(file, 'PROJECT', selectedProjectId.value, selectedProjectId.value)
    ElMessage.success('附件上传成功')
    await handleProjectChange(selectedProjectId.value)
  } catch (e: any) {
    ElMessage.error('附件上传失败：' + (e?.message || '网络异常'))
  } finally {
    uploadingFile.value = false
  }
  return false // 阻止 el-upload 默认行为
}

async function handleDeleteAttachment(fileId: number | string) {
  try {
    await deleteFile(fileId)
    ElMessage.success('附件删除成功')
    if (selectedProjectId.value) {
      await handleProjectChange(selectedProjectId.value)
    }
  } catch (e: any) {
    ElMessage.error('附件删除失败：' + (e?.message || '网络异常'))
  }
}

async function handleDownloadProjectZip() {
  if (!selectedProjectId.value) return
  const token = localStorage.getItem('token')
  const downloadUrl = `/api/v1/file/package/project/${selectedProjectId.value}${token ? `?token=${encodeURIComponent(token)}` : ''}`
  window.open(downloadUrl, '_blank')
}

function resetArchive() {
  project.value = {}
  members.value = []
  constructionContracts.value = []
  payments.value = []
  receivedPayments.value = []
  subcontracts.value = []
  machineContracts.value = []
  laborContracts.value = []
  changeEvents.value = []
  attachments.value = []
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

async function handleProjectChange(id: number | string | undefined) {
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
    laborContracts.value = data.laborContracts || []
    changeEvents.value = data.changeEvents || []
    attachments.value = data.attachments || []
    fundSummary.value = data.fundSummary || {}
  } catch (e: any) {
    resetArchive()
    ElMessage.error('加载项目档案失败：' + (e?.message || '接口异常'))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadProjects()
  // 项目档案需项目：未选时默认选中首个可访问项目，避免出现「请先选择项目」空白引导
  if (!selectedProjectId.value && projectOptions.value.length) {
    handleProjectChange(projectOptions.value[0].id)
  }
})
</script>

<style scoped>
.archive-container {
  padding: var(--zw-space-md);
}
</style>
