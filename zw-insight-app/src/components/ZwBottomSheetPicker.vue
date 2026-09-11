<template>
  <view class="zw-bottom-sheet-wrapper">
    <!-- 唤起触发槽 -->
    <view class="picker-trigger" @click="openSheet">
      <slot name="trigger">
        <view class="default-trigger">
          <text class="trigger-label">{{ selectedLabel || placeholder }}</text>
          <text class="trigger-arrow">▼</text>
        </view>
      </slot>
    </view>

    <!-- 底部抽屉与遮罩 -->
    <view v-if="visible" class="sheet-mask" @click="closeSheet">
      <view class="sheet-panel" @click.stop>
        <!-- 工业铭牌头部 -->
        <view class="sheet-header plate-header">
          <view class="header-titles">
            <view class="page-eyebrow">FIELD SELECTION // ERGONOMICS</view>
            <text class="sheet-title">{{ title }}</text>
          </view>
          <view class="header-actions">
            <text class="action-cancel" @click="closeSheet">取消</text>
            <button class="action-confirm zw-btn-primary" @click="confirmSelection">确定</button>
          </view>
        </view>

        <!-- 选项滚动列表（48px人体工学触控热区） -->
        <scroll-view scroll-y class="sheet-scroll">
          <view
            v-for="(item, index) in options"
            :key="index"
            class="option-row"
            :class="{ active: tempValue === item.value }"
            @click="handleSelect(item)"
          >
            <view class="option-content">
              <text class="option-label">{{ item.label }}</text>
              <text v-if="item.sub" class="option-sub mono-num">{{ item.sub }}</text>
            </view>
            <view class="option-check">
              <text v-if="tempValue === item.value" class="check-mark">✓</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'

export interface SheetOption {
  label: string
  value: any
  sub?: string
}

const props = withDefaults(defineProps<{
  options: SheetOption[]
  modelValue?: any
  title?: string
  placeholder?: string
}>(), {
  options: () => [],
  title: '请选择项目',
  placeholder: '点击进行选择'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
  (e: 'change', value: any, item: SheetOption | undefined): void
}>()

const visible = ref(false)
const tempValue = ref<any>(props.modelValue)

watch(() => props.modelValue, (val) => {
  tempValue.value = val
})

const selectedLabel = computed(() => {
  const found = props.options.find(o => o.value === props.modelValue)
  return found ? found.label : ''
})

function openSheet() {
  tempValue.value = props.modelValue
  visible.value = true
  triggerHaptic()
}

function closeSheet() {
  visible.value = false
}

function handleSelect(item: SheetOption) {
  tempValue.value = item.value
  triggerHaptic()
}

function triggerHaptic() {
  try {
    uni.vibrateShort({})
  } catch {}
}

function confirmSelection() {
  emit('update:modelValue', tempValue.value)
  const found = props.options.find(o => o.value === tempValue.value)
  emit('change', tempValue.value, found)
  visible.value = false
  triggerHaptic()
}
</script>

<style scoped>
.zw-bottom-sheet-wrapper {
  width: 100%;
}

.default-trigger {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 80rpx;
  padding: 0 20rpx;
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border);
  border-radius: var(--zw-radius-xs);
  box-sizing: border-box;
}

.trigger-label {
  font-size: 28rpx;
  color: var(--zw-text-primary);
}

.trigger-arrow {
  font-size: 20rpx;
  color: var(--zw-text-quaternary);
}

.sheet-mask {
  position: fixed;
  inset: 0;
  background: var(--zw-bg-mask);
  z-index: 9999;
  display: flex;
  align-items: flex-end;
}

.sheet-panel {
  width: 100%;
  max-height: 75vh;
  background: var(--zw-bg-card);
  border-top: 2rpx solid var(--zw-border);
  border-radius: var(--zw-radius-sm) var(--zw-radius-sm) 0 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.sheet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
  border-bottom: 1rpx solid var(--zw-border-light);
  margin-bottom: 0;
}

.header-titles {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.sheet-title {
  font-size: 30rpx;
  font-weight: bold;
  color: var(--zw-text-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.action-cancel {
  font-size: 26rpx;
  color: var(--zw-text-tertiary);
  padding: 8rpx 16rpx;
}

.action-confirm {
  width: 120rpx;
  height: 60rpx;
  line-height: 60rpx;
  font-size: 26rpx;
  padding: 0;
  border-radius: var(--zw-radius-xs);
}

.sheet-scroll {
  max-height: 55vh;
  padding: 12rpx 0;
}

/* 48px 大拇指人体工学触控热区 */
.option-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 96rpx; /* 48px 高 */
  padding: 0 28rpx;
  border-bottom: 1rpx solid var(--zw-border-light);
  box-sizing: border-box;
  transition: background var(--zw-duration-fast);
}

.option-row:active {
  background: var(--zw-bg-hover);
}

.option-row.active {
  background: var(--zw-brand-light);
  border-left: 6rpx solid var(--zw-brand);
}

.option-content {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.option-label {
  font-size: 30rpx;
  font-weight: 500;
  color: var(--zw-text-primary);
}

.option-sub {
  font-size: 22rpx;
  color: var(--zw-text-tertiary);
}

.option-check {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.check-mark {
  font-size: 32rpx;
  color: var(--zw-brand);
  font-weight: bold;
}
</style>
