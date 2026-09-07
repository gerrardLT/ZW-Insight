<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="760px"
    top="5vh"
    :close-on-click-modal="false"
  >
    <div v-loading="loading" class="form-container">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="formData.projectId"
            placeholder="请选择项目"
            filterable
            remote
            clearable
            :remote-method="searchProject"
            :disabled="isEdit"
            style="width: 100%"
            @change="handleProjectChange"
          >
            <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="来源类型" prop="sourceType">
          <el-select v-model="formData.sourceType" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="o in CHANGE_EVENT_SOURCE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item
          v-if="formData.sourceType === 'FIELD_EVENT'"
          label="关联签证号"
        >
          <el-input
            v-model="formData.sourceRef"
            placeholder="可空；填写后同号签证不可重复登记（防同一业务事实重复录入）"
            maxlength="100"
          />
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="formData.title"
            placeholder="一句话说明变更，如：3#楼地下室顶板加厚 100mm"
            maxlength="300"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="详细描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="发生了什么、在哪里、涉及哪些部位、依据是什么（谁口头/书面提出）"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="影响类别">
          <el-select v-model="formData.category" placeholder="可空" clearable style="width: 100%">
            <el-option
              v-for="o in CHANGE_EVENT_CATEGORY_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>

        <!-- 影响评估：仅评估中/审批中阶段填写 -->
        <template v-if="showAssessment">
          <el-divider content-position="left">影响评估</el-divider>

          <el-alert
            class="assessment-tip"
            title="成本影响不为 0 时，必须在下方指明受影响的成本账户，否则无法提交"
            description="后端会拒绝「有成本影响却无账户明细」的评估——那样批准后无法传导到 CBS，变更主链会断在这里。"
            type="info"
            show-icon
            :closable="false"
          />

          <el-form-item label="成本影响" prop="costDelta">
            <el-input-number
              v-model="assessment.costDelta"
              :precision="2"
              :step="10000"
              :min="-1000000000"
              :max="1000000000"
              style="width: 260px"
            />
            <span class="unit-hint">元（正=增加成本，负=节约；无影响请填 0）</span>
          </el-form-item>

          <el-form-item label="工期影响">
            <el-input-number
              v-model="assessment.scheduleDelayDays"
              :min="-3650"
              :max="3650"
              style="width: 180px"
            />
            <span class="unit-hint">天（正=延误，负=提前）</span>
          </el-form-item>

          <el-form-item label="评估理由" prop="rationale">
            <el-input
              v-model="assessment.rationale"
              type="textarea"
              :rows="3"
              placeholder="测算依据与结论，审批人据此判断（必填）"
              maxlength="2000"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="受影响账户">
            <div class="accounts-editor">
              <div v-for="(item, idx) in affectedAccounts" :key="idx" class="account-row">
                <el-select
                  v-model="item.accountId"
                  placeholder="选择成本账户"
                  filterable
                  style="width: 260px"
                >
                  <el-option
                    v-for="acc in costAccountOptions"
                    :key="acc.id"
                    :label="`${acc.accountCode} · ${acc.accountName}`"
                    :value="acc.id!"
                  />
                </el-select>
                <el-select v-model="item.deltaType" style="width: 110px">
                  <el-option label="增加" value="INCREASE" />
                  <el-option label="减少" value="DECREASE" />
                </el-select>
                <el-input-number
                  v-model="item.deltaAmount"
                  :precision="2"
                  :min="0"
                  :step="10000"
                  style="width: 170px"
                />
                <el-button type="danger" link :icon="Delete" @click="removeAccount(idx)" />
              </div>
              <el-button type="primary" plain :icon="Plus" size="small" @click="addAccount">
                添加账户
              </el-button>
              <div class="accounts-summary">
                明细合计：
                <span :class="accountsSum < 0 ? 'is-success' : 'is-danger'">
                  {{ accountsSum > 0 ? '+' : '' }}{{ toWan(accountsSum) }}
                </span>
                <span
                  v-if="Math.abs(accountsSum - (assessment.costDelta || 0)) > 0.005"
                  class="mismatch-warn"
                >
                  ⚠ 与上方「成本影响」不一致（后端以明细为准）
                </span>
              </div>
            </div>
          </el-form-item>
        </template>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button v-if="showAssessment" type="success" :loading="submitting" @click="handleSubmitAssessment">
        提交评估
      </el-button>
      <el-button v-else type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存' : '登记' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import {
  createChangeEvent, updateChangeEvent, getChangeEvent, submitChangeEventAssessment,
  CHANGE_EVENT_SOURCE_OPTIONS, CHANGE_EVENT_CATEGORY_OPTIONS,
  type BizChangeEvent, type AffectedAccount
} from '@/api/change-event'
import { getCostAccountPage, type CostAccount } from '@/api/cost-account'
import { getProjectList } from '@/api/project'
import { toWan } from '@/utils/chart-format'

const props = defineProps<{
  visible: boolean
  eventId?: number
  presetProjectId?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const projectList = ref<any[]>([])
const costAccountOptions = ref<CostAccount[]>([])
const currentStatus = ref<string>('')
const affectedAccounts = ref<AffectedAccount[]>([])

const formData = reactive({
  projectId: null as number | null,
  sourceType: 'FIELD_EVENT',
  sourceRef: '',
  title: '',
  description: '',
  category: ''
})

const assessment = reactive({
  costDelta: 0,
  scheduleDelayDays: 0,
  rationale: '',
  assessmentNotes: ''
})

const rules = reactive<FormRules>({
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 5, max: 300, message: '长度 5-300 字符', trigger: 'blur' }
  ],
  description: [{ required: true, message: '请填写详细描述', trigger: 'blur' }]
})

const isEdit = computed(() => !!props.eventId)

/** 评估表单只在「评估中」出现：草稿阶段先说清发生了什么，评估是商务的职责 */
const showAssessment = computed(() => currentStatus.value === 'ASSESSING')

const dialogTitle = computed(() => {
  if (showAssessment.value) return '填写影响评估'
  return isEdit.value ? '编辑变更事件' : '登记变更事件'
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

/** 明细合计（带符号）：与 costDelta 交叉校验，提前暴露自相矛盾的评估 */
const accountsSum = computed(() =>
  affectedAccounts.value.reduce((sum, a) => {
    const amt = Number(a.deltaAmount) || 0
    return sum + (a.deltaType === 'DECREASE' ? -amt : amt)
  }, 0)
)

watch(() => [props.visible, props.eventId], async ([visible, id]) => {
  if (!visible) return
  if (id) {
    await loadEvent(Number(id))
  } else {
    resetForm()
    if (props.presetProjectId) {
      formData.projectId = props.presetProjectId
      await loadCostAccounts(props.presetProjectId)
    }
  }
  await searchProject('')
}, { immediate: true })

async function searchProject(keyword: string) {
  try {
    const res: any = await getProjectList({ projectName: keyword })
    projectList.value = res.data || []
  } catch {
    projectList.value = []
  }
}

/** 载入项目下的 CBS 账户供「受影响账户」选择 */
async function loadCostAccounts(projectId: number) {
  try {
    const res: any = await getCostAccountPage({ page: 1, size: 200, projectId })
    costAccountOptions.value = res.data?.records || []
  } catch {
    costAccountOptions.value = []
  }
}

async function handleProjectChange(projectId: number | null) {
  affectedAccounts.value = []
  if (projectId) await loadCostAccounts(projectId)
  else costAccountOptions.value = []
}

async function loadEvent(id: number) {
  loading.value = true
  try {
    const res: any = await getChangeEvent(id)
    const data: BizChangeEvent = res.data
    currentStatus.value = data.status || ''
    formData.projectId = data.projectId
    formData.sourceType = data.sourceType
    formData.sourceRef = data.sourceRef || ''
    formData.title = data.title
    formData.description = data.description || ''
    formData.category = data.category || ''

    if (data.impactAssessment) {
      assessment.costDelta = Number(data.impactAssessment.costDelta) || 0
      assessment.scheduleDelayDays = Number(data.impactAssessment.scheduleDelayDays) || 0
      assessment.rationale = data.impactAssessment.rationale || ''
      assessment.assessmentNotes = data.impactAssessment.assessmentNotes || ''
    }
    affectedAccounts.value = (data.affectedAccounts || []).map(a => ({ ...a }))

    if (data.projectId) await loadCostAccounts(data.projectId)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载变更事件失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  currentStatus.value = ''
  formData.projectId = props.presetProjectId ?? null
  formData.sourceType = 'FIELD_EVENT'
  formData.sourceRef = ''
  formData.title = ''
  formData.description = ''
  formData.category = ''
  assessment.costDelta = 0
  assessment.scheduleDelayDays = 0
  assessment.rationale = ''
  assessment.assessmentNotes = ''
  affectedAccounts.value = []
  costAccountOptions.value = []
  formRef.value?.clearValidate()
}

function addAccount() {
  affectedAccounts.value.push({
    accountId: undefined as any,
    deltaType: 'INCREASE',
    deltaAmount: 0
  })
}

function removeAccount(idx: number) {
  affectedAccounts.value.splice(idx, 1)
}

/** 登记/保存基本信息 */
async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload: Partial<BizChangeEvent> = {
    projectId: formData.projectId!,
    sourceType: formData.sourceType,
    sourceRef: formData.sourceRef || undefined,
    title: formData.title.trim(),
    description: formData.description.trim(),
    category: formData.category || undefined,
    affectedAccounts: affectedAccounts.value.filter(a => a.accountId)
  }

  submitting.value = true
  try {
    if (isEdit.value && props.eventId) {
      await updateChangeEvent(props.eventId, payload)
      ElMessage.success('已保存')
    } else {
      await createChangeEvent(payload)
      ElMessage.success('已登记为草稿')
    }
    emit('saved')
    dialogVisible.value = false
  } catch (e: any) {
    // 后端业务异常（如签证号重复登记）必须原样呈现
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

/** 提交影响评估 → 流转至审批中 */
async function handleSubmitAssessment() {
  if (!props.eventId) return

  if (!assessment.rationale.trim()) {
    ElMessage.warning('请填写评估理由，审批人需据此判断')
    return
  }
  const validAccounts = affectedAccounts.value.filter(a => a.accountId && Number(a.deltaAmount) !== 0)
  // 前端先拦一道：后端同样会拒，提前提示省一次往返
  if (Math.abs(assessment.costDelta) > 0.005 && !validAccounts.length) {
    ElMessage.warning('成本影响不为 0，请指明受影响的成本账户')
    return
  }

  submitting.value = true
  try {
    // 评估明细与评估结论一并提交：先存明细，再提交评估触发流转
    await updateChangeEvent(props.eventId, { affectedAccounts: validAccounts })
    await submitChangeEventAssessment(props.eventId, {
      costDelta: assessment.costDelta,
      scheduleDelayDays: assessment.scheduleDelayDays,
      rationale: assessment.rationale.trim(),
      assessmentNotes: assessment.assessmentNotes || undefined
    })
    ElMessage.success('评估已提交，事件进入审批中')
    emit('saved')
    dialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '提交评估失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.form-container {
  max-height: 66vh;
  overflow-y: auto;
  padding-right: var(--zw-space-sm);
}

.assessment-tip {
  margin-bottom: var(--zw-space-md);
}

.unit-hint {
  margin-left: var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
}

.accounts-editor {
  width: 100%;

  .account-row {
    display: flex;
    align-items: center;
    gap: var(--zw-space-sm);
    margin-bottom: var(--zw-space-sm);
  }

  .accounts-summary {
    margin-top: var(--zw-space-sm);
    font-size: var(--zw-font-size-sm);
    color: var(--zw-text-secondary);

    .mismatch-warn {
      margin-left: var(--zw-space-sm);
      color: var(--zw-warning);
      font-size: var(--zw-font-size-xs);
    }
  }
}

.is-danger { color: var(--zw-danger); font-weight: var(--zw-font-weight-medium); }
.is-success { color: var(--zw-success); font-weight: var(--zw-font-weight-medium); }
</style>
