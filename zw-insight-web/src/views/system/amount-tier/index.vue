<template>
  <div class="amount-tier-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>金额分级审批配置（付款申请）</span>
          <el-button type="primary" size="small" @click="handleAdd">新增档位</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: var(--zw-space-sm-md)"
        title="档位等级作为流程变量 approvalTier 传入审批流程，由 BPMN 条件网关路由到对应审批节点；区间为左闭右开 [下限, 上限)，上限留空表示无上限"
      />

      <el-table :data="tierList" v-loading="loading" border>
        <el-table-column prop="tierLevel" label="档位等级" width="100" align="center" />
        <el-table-column prop="tierName" label="档位名称" min-width="160" />
        <el-table-column label="金额区间" min-width="220">
          <template #default="{ row }">
            {{ formatAmount(row.minAmount) }} ~ {{ row.maxAmount ? formatAmount(row.maxAmount) : '无上限' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row as AmountTierConfig)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 匹配预览 -->
    <el-card shadow="never" style="margin-top: var(--zw-space-md)">
      <template #header><span>档位匹配预览（该金额将走哪个审批档）</span></template>
      <div class="match-preview">
        <el-input-number
          v-model="previewAmount"
          :min="0"
          :precision="2"
          controls-position="right"
          placeholder="输入付款金额"
          style="width: 220px"
        />
        <el-button type="primary" style="margin-left: var(--zw-space-sm-md)" @click="handleMatch">匹配</el-button>
        <el-tag v-if="matchedTier" :type="matchedTag" size="large" style="margin-left: var(--zw-space-sm-md)">
          等级 {{ matchedTier.tierLevel }}：{{ matchedTier.tierName }}
        </el-tag>
        <el-tag v-else-if="matched === false" type="info" size="large" style="margin-left: var(--zw-space-sm-md)">未匹配（走默认审批链）</el-tag>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑档位' : '新增档位'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="档位等级" prop="tierLevel">
          <el-input-number v-model="formData.tierLevel" :min="1" :max="9" controls-position="right" style="width: 100%" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="档位名称" prop="tierName">
          <el-input v-model="formData.tierName" maxlength="100" clearable placeholder="如 财务负责人审批" />
        </el-form-item>
        <el-form-item label="金额下限" prop="minAmount">
          <el-input-number v-model="formData.minAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="金额上限">
          <el-input-number v-model="formData.maxAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" placeholder="留空表示无上限" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="enabledBool" />
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getTierList,
  matchTier,
  saveTier,
  updateTier,
  type AmountTierConfig
} from '@/api/amount-tier'

const MODULE = 'PAYMENT_APPLY'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tierList = ref<AmountTierConfig[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const previewAmount = ref(500000)
const matchedTier = ref<AmountTierConfig | null>(null)
const matched = ref<boolean | null>(null)

const formData = ref({
  id: undefined as number | undefined,
  tierLevel: 1,
  tierName: '',
  minAmount: 0,
  maxAmount: undefined as number | undefined
})
const enabledBool = ref(true)

const matchedTag = computed<'primary' | 'success' | 'warning' | 'info' | 'danger'>(() => {
  if (!matchedTier.value) return 'info'
  const map: Record<number, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { 1: 'success', 2: 'primary', 3: 'warning', 4: 'danger' }
  return map[matchedTier.value.tierLevel] || 'info'
})

const formRules: FormRules = {
  tierLevel: [{ required: true, message: '请输入档位等级', trigger: 'blur' }],
  tierName: [{ required: true, message: '请输入档位名称', trigger: 'blur' }],
  minAmount: [{ required: true, message: '请输入金额下限', trigger: 'blur' }]
}

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}

async function loadTiers() {
  loading.value = true
  try {
    const res = await getTierList(MODULE)
    tierList.value = res.data.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  formData.value = { id: undefined, tierLevel: tierList.value.length + 1, tierName: '', minAmount: 0, maxAmount: undefined }
  enabledBool.value = true
  dialogVisible.value = true
}

function handleEdit(row: AmountTierConfig) {
  isEdit.value = true
  formData.value = {
    id: row.id,
    tierLevel: row.tierLevel,
    tierName: row.tierName,
    minAmount: row.minAmount,
    maxAmount: row.maxAmount ?? undefined
  }
  enabledBool.value = row.enabled === 1
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const payload = {
      module: MODULE,
      tierLevel: formData.value.tierLevel,
      tierName: formData.value.tierName,
      minAmount: formData.value.minAmount,
      maxAmount: formData.value.maxAmount ?? null,
      enabled: enabledBool.value ? 1 : 0
    }
    if (isEdit.value && formData.value.id) {
      await updateTier(formData.value.id, payload)
      ElMessage.success('档位已更新')
    } else {
      await saveTier(payload)
      ElMessage.success('档位已新增')
    }
    dialogVisible.value = false
    await loadTiers()
  } finally {
    submitLoading.value = false
  }
}

async function handleMatch() {
  const res = await matchTier(MODULE, previewAmount.value)
  matchedTier.value = res.data.data || null
  matched.value = matchedTier.value !== null
}

onMounted(loadTiers)
</script>

<style scoped>
.amount-tier-container {
  padding: var(--zw-space-md);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.match-preview {
  display: flex;
  align-items: center;
}
</style>
