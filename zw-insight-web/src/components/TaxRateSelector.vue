<template>
  <div class="tax-rate-selector" :style="{ width }">
    <!-- 预定义税率下拉选择器（el-select filterable） -->
    <el-select
      v-model="selectedId"
      class="tax-rate-selector__select"
      placeholder="选择预定义税率"
      filterable
      clearable
      :loading="loading"
      :disabled="disabled"
      @change="handleSelectChange"
    >
      <el-option
        v-for="item in options"
        :key="item.id"
        :label="formatOptionLabel(item.name, item.rateValue)"
        :value="item.id"
      />
    </el-select>

    <!-- 税率数值字段，支持手动输入（0.00 - 100.00，精度2位小数） -->
    <el-input-number
      v-model="rateValue"
      class="tax-rate-selector__input"
      :min="0"
      :max="100"
      :precision="2"
      :step="0.01"
      :controls="false"
      :disabled="disabled"
      placeholder="税率"
      @change="handleManualChange"
    />
    <span class="tax-rate-selector__suffix">%</span>

    <!-- 税率列表不可用时的提示（Req 6.5） -->
    <div v-if="loadFailed" class="tax-rate-selector__hint">
      税率列表不可用，请手动输入税率
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTaxRateList, type TaxRateDTO } from '@/api/tax-rate'
import {
  normalizeRate,
  formatOptionLabel,
  resolveSelectedValue,
  findMatchingOptionId,
  shouldClearSelection
} from '@/utils/tax-rate-selector'

const props = withDefaults(
  defineProps<{
    /** 税率数值（v-model），精度2位小数 */
    modelValue?: number
    width?: string
    disabled?: boolean
  }>(),
  {
    modelValue: undefined,
    width: '100%',
    disabled: false
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | undefined): void
  (e: 'change', value: number | undefined): void
}>()

const loading = ref(false)
const loadFailed = ref(false)
const options = ref<TaxRateDTO[]>([])
/** 当前选中的预定义税率ID（仅用于回显下拉选中状态） */
const selectedId = ref<number>()
/** 税率数值，与 modelValue 同步 */
const rateValue = ref<number | undefined>(props.modelValue)

watch(
  () => props.modelValue,
  (val) => {
    if (val !== rateValue.value) {
      rateValue.value = val
      // 外部值变化时同步下拉选中状态
      syncSelectedFromValue(val)
    }
  }
)

/** 根据当前数值匹配下拉选项（用于编辑回显），无匹配则不选中 */
function syncSelectedFromValue(val: number | undefined) {
  selectedId.value = findMatchingOptionId(options.value, val)
}

/** 用户选择预定义税率：自动填入税率数值（精度2位小数）Req 6.3 */
function handleSelectChange(id: number | undefined) {
  const val = resolveSelectedValue(options.value, id)
  if (val === undefined) return
  rateValue.value = val
  emit('update:modelValue', val)
  emit('change', val)
}

/**
 * 用户手动修改税率数值。
 * 若新值与当前选中预设税率一致，则保留选中状态；
 * 否则清除下拉选中状态，以手动输入值为准（Req 6.6）。
 * 采用值比对而非事件时序判断，避免对 el-input-number 是否在程序化赋值时
 * 触发 change 的依赖，保证逻辑稳健。
 */
function handleManualChange(val: number | undefined) {
  const normalized = normalizeRate(val)
  if (shouldClearSelection(options.value, selectedId.value, normalized)) {
    selectedId.value = undefined
  }
  emit('update:modelValue', normalized)
  emit('change', normalized)
}

/** 加载启用状态税率列表 */
async function loadTaxRates() {
  loading.value = true
  loadFailed.value = false
  try {
    const res: any = await getTaxRateList()
    options.value = res?.data || []
    if (!options.value.length) {
      loadFailed.value = true
      ElMessage.warning('税率列表为空，请手动输入税率')
    } else {
      // 列表加载成功后，按当前值回显下拉选中
      syncSelectedFromValue(rateValue.value)
    }
  } catch (e) {
    loadFailed.value = true
    options.value = []
    ElMessage.warning('税率列表加载失败，请手动输入税率')
  } finally {
    loading.value = false
  }
}

onMounted(loadTaxRates)
</script>

<style scoped>
.tax-rate-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tax-rate-selector__select {
  flex: 1 1 auto;
  min-width: 0;
}
.tax-rate-selector__input {
  flex: 0 0 110px;
  width: 110px;
}
.tax-rate-selector__suffix {
  color: #909399;
  font-size: 13px;
}
.tax-rate-selector__hint {
  flex-basis: 100%;
  margin-top: 4px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.4;
}
</style>
