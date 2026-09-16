<template>
  <view class="form-page">
    <OfflineBanner />

    <!-- 项目（从列表页带入，允许改） -->
    <view class="form-section">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">所属项目</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 变更基本信息 -->
    <view class="form-section">
      <view class="section-title">变更信息</view>

      <view class="form-item">
        <text class="form-label">来源类型</text>
        <view class="form-input picker" @click="showSourcePicker = true">
          <text>{{ sourceText(form.sourceType) }}</text>
          <text class="arrow">›</text>
        </view>
      </view>

      <view class="form-item vertical">
        <text class="form-label">标题<text class="required">*</text></text>
        <input cursor-spacing="24"
          v-model="form.title"
          placeholder="一句话说明变更，如：3#楼地下室顶板加厚 100mm"
          class="form-input left"
          :maxlength="300"
        />
      </view>

      <view class="form-item vertical">
        <text class="form-label">详细描述<text class="required">*</text></text>
        <textarea
          v-model="form.description"
          placeholder="发生了什么、在哪里、涉及哪些部位、依据是什么（谁口头/书面提出）"
          class="textarea"
          :maxlength="2000"
        />
        <text class="counter">{{ (form.description || '').length }}/2000</text>
      </view>

      <view class="form-item">
        <text class="form-label">影响类别</text>
        <view class="form-input picker" @click="showCategoryPicker = true">
          <text :class="{ placeholder: !form.category }">{{ categoryText(form.category) || '请选择' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>

      <!-- 来源引用：与既有单据绑定，防止同一业务事实重复登记 -->
      <view class="form-item" v-if="form.sourceType === 'FIELD_EVENT'">
        <text class="form-label">关联签证号</text>
        <input cursor-spacing="24"
          v-model="form.sourceRef"
          placeholder="可空；填写后同号签证不可重复登记"
          class="form-input"
          :maxlength="100"
        />
      </view>
    </view>

    <!-- 现场照片：变更最有说服力的证据，必须能就地拍 -->
    <view class="form-section">
      <view class="section-title">现场照片</view>
      <button class="upload-btn" :loading="composing" @click="takePhoto">📷 拍照（自动水印）</button>
      <view class="image-list" v-if="photos.length">
        <view class="image-item" v-for="(img, idx) in photos" :key="idx">
          <image :src="img" mode="aspectFill" class="thumb" @click="previewPhoto(idx)" />
          <text class="remove" @click="removePhoto(idx)">×</text>
        </view>
      </view>
      <text class="hint">照片是变更举证的核心材料；含照片时需联网提交（临时路径无法离线序列化）</text>
    </view>

    <!-- 水印合成离屏画布 -->
    <canvas
      canvas-id="watermarkCanvas"
      :style="{ width: canvasSize.width + 'px', height: canvasSize.height + 'px', position: 'fixed', left: '-9999px', top: '0' }"
    ></canvas>

    <view class="submit-bar">
      <button class="submit-btn" :loading="submitting" @click="handleSubmit(false)">存草稿</button>
      <button class="submit-btn primary" :loading="submitting" @click="handleSubmit(true)">提交评估</button>
    </view>

    <!-- 项目选择弹窗 -->
    <view class="picker-mask" v-if="showProjectPicker" @click="showProjectPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showProjectPicker = false">取消</text>
          <text class="picker-title">选择项目</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)">
            <text>{{ p.projectName }}</text>
          </view>
          <view class="empty" v-if="!projects.length"><text>{{ projectEmptyTip }}</text></view>
        </scroll-view>
      </view>
    </view>

    <!-- 来源类型选择 -->
    <view class="picker-mask" v-if="showSourcePicker" @click="showSourcePicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showSourcePicker = false">取消</text>
          <text class="picker-title">来源类型</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view
            class="picker-item"
            v-for="opt in SOURCE_TYPES"
            :key="opt.value"
            @click="form.sourceType = opt.value; showSourcePicker = false"
          >
            <text>{{ opt.label }}</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- 影响类别选择 -->
    <view class="picker-mask" v-if="showCategoryPicker" @click="showCategoryPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showCategoryPicker = false">取消</text>
          <text class="picker-title">影响类别</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view
            class="picker-item"
            v-for="opt in CATEGORIES"
            :key="opt.value"
            @click="form.category = opt.value; showCategoryPicker = false"
          >
            <text>{{ opt.label }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import OfflineBanner from '@/components/OfflineBanner.vue'
import { saveChangeEvent, startChangeEventAssessment } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { submitOrQueue, rejectIfOffline } from '@/utils/offlineSubmit'
import { watermarkCompositor } from '@/utils/watermarkCompositor'
import { useUserStore } from '@/stores/user'

/** 与后端 BizChangeEvent 白名单严格一致，避免前端造出后端拒绝的值 */
const SOURCE_TYPES = [
  { label: '现场事件', value: 'FIELD_EVENT' },
  { label: '设计变更', value: 'DESIGN_CHANGE' },
  { label: '业主指令', value: 'OWNER_REQUEST' },
  { label: '清单变更', value: 'VARIATION_ORDER' },
  { label: '其他', value: 'OTHER' }
]

const CATEGORIES = [
  { label: '成本影响', value: 'COST_IMPACT' },
  { label: '范围变更', value: 'SCOPE_CHANGE' },
  { label: '工期延误', value: 'SCHEDULE_DELAY' },
  { label: '质量问题', value: 'QUALITY_ISSUE' },
  { label: '其他', value: 'OTHER' }
]

const userStore = useUserStore()

const submitting = ref(false)
const composing = ref(false)
const showProjectPicker = ref(false)
const showSourcePicker = ref(false)
const showCategoryPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')
const photos = ref<string[]>([])
const canvasSize = ref({ width: 1, height: 1 })

const form = reactive({
  projectId: null as number | null,
  projectName: '',
  sourceType: 'FIELD_EVENT',
  sourceRef: '',
  title: '',
  description: '',
  category: ''
})

onLoad(async (options: any) => {
  if (options?.projectId) {
    form.projectId = Number(options.projectId)
    form.projectName = options.projectName ? decodeURIComponent(options.projectName) : ''
  }
  const res = await loadProjectList({ page: 1, size: 100 })
  projects.value = res.records
  projectEmptyTip.value = res.empty && res.fromCache ? NO_OFFLINE_DATA_TIP : '暂无项目'
  if (!form.projectName && form.projectId) {
    const hit = projects.value.find((p: any) => p.id === form.projectId)
    if (hit) form.projectName = hit.projectName
  }
})

function selectProject(p: any) {
  form.projectId = p.id
  form.projectName = p.projectName
  showProjectPicker.value = false
}

function sourceText(v?: string) {
  return SOURCE_TYPES.find(o => o.value === v)?.label || '现场事件'
}

function categoryText(v?: string) {
  return CATEGORIES.find(o => o.value === v)?.label || ''
}

function getGps(): Promise<{ lat: number | null; lng: number | null }> {
  return new Promise((resolve) => {
    uni.getLocation({
      type: 'gcj02',
      success: (res) => resolve({ lat: res.latitude, lng: res.longitude }),
      fail: () => resolve({ lat: null, lng: null })
    })
  })
}

/** 拍照并合成水印（时间/GPS/人员/项目），复用施工日志的既有能力 */
async function takePhoto() {
  if (!form.projectId) {
    uni.showToast({ title: '请先选择项目', icon: 'none' })
    return
  }
  uni.chooseImage({
    count: 1,
    sourceType: ['camera', 'album'],
    success: async (res) => {
      const tempPath = res.tempFilePaths[0]
      if (!tempPath) return
      composing.value = true
      try {
        const info = await new Promise<{ width: number; height: number }>((resolve, reject) => {
          uni.getImageInfo({ src: tempPath, success: (r) => resolve({ width: r.width, height: r.height }), fail: reject })
        })
        canvasSize.value = { width: info.width, height: info.height }
        await new Promise((r) => setTimeout(r, 50))

        const gps = await getGps()
        const userName =
          userStore.userInfo?.realName || userStore.userInfo?.name || userStore.userInfo?.nickName || ''
        const watermarked = await watermarkCompositor.compose(
          tempPath,
          { time: '', gpsLat: gps.lat, gpsLng: gps.lng, userName, projectName: form.projectName },
          'watermarkCanvas'
        )
        photos.value.push(watermarked)
      } catch (e) {
        uni.showToast({ title: '水印合成失败', icon: 'none' })
      } finally {
        composing.value = false
      }
    }
  })
}

function previewPhoto(idx: number) {
  uni.previewImage({ urls: photos.value, current: idx })
}

function removePhoto(idx: number) {
  photos.value.splice(idx, 1)
}

/**
 * 提交。
 * @param toAssess true=存草稿后立即转入评估中（现场希望马上推动）
 */
async function handleSubmit(toAssess: boolean) {
  if (!form.projectId) {
    uni.showToast({ title: '请选择项目', icon: 'none' }); return
  }
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写标题', icon: 'none' }); return
  }
  if (!form.description.trim()) {
    uni.showToast({ title: '请填写详细描述', icon: 'none' }); return
  }

  submitting.value = true
  try {
    // 含照片时临时路径不可序列化，离线无法入队，明确拒绝（不静默丢照片）
    if (photos.value.length > 0 && rejectIfOffline('含照片的变更需联网提交，请联网后重试')) {
      return
    }

    const payload: any = {
      projectId: form.projectId,
      sourceType: form.sourceType,
      sourceRef: form.sourceRef || undefined,
      title: form.title.trim(),
      description: form.description.trim(),
      category: form.category || undefined,
      supportingDocs: photos.value.map((url, i) => ({ url, name: `现场照片${i + 1}`, type: 'IMAGE' }))
    }

    // 无照片时离线入队，联网后由 syncEngine 自动提交
    const { queued } = await submitOrQueue(() => saveChangeEvent(payload), {
      endpoint: '/v1/contract/change-event',
      payload
    })

    if (queued) {
      // 离线只能落草稿；「转入评估」是第二段状态流转，离线无法保证顺序，明确告知
      uni.showToast({ title: '已存离线队列，联网后同步（转评估需联网操作）', icon: 'none', duration: 2500 })
    } else if (toAssess) {
      // 两段式：先落草稿拿到 ID，再转评估中。任一步失败都要显式提示，不留永久草稿
      const created: any = await saveChangeEvent(payload)
      const newId = created?.id
      if (newId) {
        await startChangeEventAssessment(newId)
        uni.showToast({ title: '已提交评估', icon: 'success' })
      } else {
        uni.showToast({ title: '已存草稿，但转评估失败：未返回事件ID', icon: 'none', duration: 2500 })
      }
    } else {
      uni.showToast({ title: '已存草稿', icon: 'success' })
    }

    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch (e: any) {
    // 后端业务异常（如签证号重复登记）原样呈现，不可静默
    uni.showToast({ title: e?.message || '提交失败', icon: 'none', duration: 2500 })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form-page { padding: 20rpx; min-height: 44px; padding-bottom: 180rpx; }
.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg); padding: 0 24rpx; margin-bottom: 20rpx; }
.section-title { font-size: 28rpx; font-weight: bold; color: var(--zw-text-primary); padding: 24rpx; min-height: 44px;  }
.form-item { display: flex; align-items: center; padding: 24rpx;   border-bottom: 1rpx solid var(--zw-border-light); }
.form-item.vertical { flex-direction: column; align-items: flex-start; }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.required { color: var(--zw-danger); margin-left: 4rpx; }
.form-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; }
.form-input.left { text-align: left; width: 100%; }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.placeholder { color: var(--zw-text-quaternary); }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }
.textarea { width: 100%; height: 220rpx; border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-sm); padding: 16rpx; min-height: 44px; font-size: 26rpx; margin-top: 12rpx; box-sizing: border-box; }
.counter { align-self: flex-end; font-size: 22rpx; color: var(--zw-text-quaternary); margin-top: 8rpx; }
.hint { display: block; font-size: 22rpx; color: var(--zw-text-quaternary); padding: 12rpx; min-height: 44px;  line-height: 1.5; }

.upload-btn { margin: 16rpx 0; height: 80rpx; line-height: 80rpx; background: var(--zw-bg-hover); color: var(--zw-text-primary); font-size: 28rpx; border-radius: var(--zw-radius-sm); border: 1rpx dashed var(--zw-border); }
.image-list { display: flex; flex-wrap: wrap; gap: 16rpx; padding-bottom: 16rpx; }
.image-item { position: relative; width: 180rpx; height: 180rpx; }
.thumb { width: 180rpx; height: 180rpx; border-radius: var(--zw-radius-sm); }
.remove { position: absolute; top: -12rpx; right: -12rpx; width: 40rpx; height: 40rpx; line-height: 36rpx; text-align: center; background: var(--zw-danger); color: #fff; border-radius: 50%; font-size: 28rpx; }

.submit-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; gap: 16rpx; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); line-height: 1; background: var(--zw-bg-card); border-top: 1rpx solid var(--zw-border-light); } /* P0 safe-area：底部安全区 */
.submit-btn { flex: 1; min-height: 44px; display: flex; align-items: center; justify-content: center; line-height: 1;  background: var(--zw-bg-hover); color: var(--zw-text-primary); font-size: 30rpx; border-radius: var(--zw-radius-sm); border: none; }
.submit-btn.primary { background: var(--zw-brand); color: var(--zw-on-primary); }

.empty { padding: 80rpx;   text-align: center; font-size: 26rpx; color: var(--zw-text-quaternary); }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; min-height: 44px; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 28rpx; min-height: 44px; font-size: 28rpx; color: var(--zw-text-primary); border-bottom: 1rpx solid var(--zw-border-light); }
</style>
