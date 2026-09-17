<template>
  <view class="sign-page">
    <!-- 项目选择：.project-selector → ZwiPickerField（label 上置常驻，与 Stage 2 表单页同构） -->
    <ZwiPickerField
      label="打卡项目"
      :displayValue="projectName"
      placeholder="请选择项目"
      required
      @open="showProjectPicker = true"
    />

    <!-- 签到卡片 -->
    <view class="sign-card">
      <view class="sign-time">{{ currentTime }}</view>
      <view class="sign-location" v-if="location.address">📍 {{ location.address }}</view>
      <view class="sign-location" v-else>正在获取位置...</view>
      <button class="sign-btn" :class="{ signed: todaySigned }" :disabled="signing || todaySigned" @click="handleSign">
        {{ todaySigned ? '今日已签到 ✓' : signing ? '签到中...' : '签到打卡' }}
      </button>
      <view class="sign-range-tip" v-if="!isInRange && location.latitude">
        ⚠️ 当前不在项目签到范围内
      </view>
    </view>

    <!-- 本月日历 -->
    <view class="calendar-section">
      <view class="calendar-header">
        <text class="calendar-title">{{ currentMonth }} 签到记录</text>
        <text class="calendar-stat">本月签到 {{ signDays }} 天</text>
      </view>
      <view class="calendar-grid">
        <view class="calendar-day" v-for="day in calendarDays" :key="day.date"
          :class="{ signed: day.signed, today: day.isToday }">
          <text class="day-num">{{ day.day }}</text>
          <text class="day-dot" v-if="day.signed">●</text>
        </view>
      </view>
    </view>

    <!-- 项目选择弹窗 -->
    <view class="picker-mask" v-if="showProjectPicker" @click="showProjectPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text class="picker-cancel" @click="showProjectPicker = false">取消</text>
          <text class="picker-title">选择打卡项目</text>
          <text class="picker-cancel"></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)">
            <text>{{ p.projectName }}</text>
          </view>
          <view class="empty" v-if="!projects.length"><text>暂无可打卡项目</text></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BASE_URL } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { loadProjectList } from '@/utils/offlineData'
import ZwiPickerField from '@/components/zwi/ZwiPickerField.vue'

const userStore = useUserStore()

const signing = ref(false)
const todaySigned = ref(false)
const isInRange = ref(true)
const signDays = ref(0)
const currentTime = ref('')
const currentMonth = ref('')
const calendarDays = ref<any[]>([])
const location = ref({ latitude: 0, longitude: 0, address: '' })

const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectId = ref<number | null>(1)
const projectName = ref('')

// 更新时间
function updateTime() {
  const now = new Date()
  currentTime.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
  currentMonth.value = `${now.getFullYear()}年${now.getMonth() + 1}月`
}

// 获取位置
function getLocation() {
  uni.getLocation({
    type: 'gcj02',
    success: (res) => {
      location.value.latitude = res.latitude
      location.value.longitude = res.longitude
      location.value.address = `${res.latitude.toFixed(5)}, ${res.longitude.toFixed(5)}`
    },
    fail: () => { location.value.address = '定位失败，请检查权限' }
  })
}

function selectProject(p: any) {
  projectId.value = p.id
  projectName.value = p.projectName
  showProjectPicker.value = false
  loadCalendar()
}

// 签到
async function handleSign() {
  if (!location.value.latitude) {
    uni.showToast({ title: '请等待定位完成', icon: 'none' }); return
  }
  signing.value = true
  try {
    const token = uni.getStorageSync('token')
    const res: any = await uni.request({
      url: `${BASE_URL}/v1/site/sign`,
      method: 'POST',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      data: {
        projectId: projectId.value || 1,
        latitude: location.value.latitude,
        longitude: location.value.longitude,
        address: location.value.address
      }
    })
    const body = res?.data || res
    if (body?.code === 200) {
      todaySigned.value = true
      isInRange.value = body?.data?.isInRange === 1
      uni.showToast({ title: '签到成功', icon: 'success' })
      loadCalendar()
    }
  } catch {} finally { signing.value = false }
}

// 加载日历 — 调用后端签到月度统计接口
async function loadCalendar() {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const today = now.getDate()
  const monthStr = `${year}-${String(month + 1).padStart(2, '0')}`

  // 初始化空日历
  const days: any[] = []
  for (let d = 1; d <= daysInMonth; d++) {
    days.push({ day: d, date: `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`, signed: false, isToday: d === today })
  }

  const pid = projectId.value || 1
  const uid = userStore.userInfo?.id || 1

  try {
    const token = uni.getStorageSync('token')
    const res: any = await uni.request({
      url: `${BASE_URL}/v1/site/sign/monthly`,
      method: 'GET',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      data: {
        projectId: pid,
        userId: uid,
        month: monthStr
      }
    })
    const body = res?.data || res
    if (body?.code === 200 && body?.data) {
      const monthlyData = body.data
      // 用后端返回的 dailyRecords 填充日历签到状态
      if (monthlyData.dailyRecords && Array.isArray(monthlyData.dailyRecords)) {
        for (const record of monthlyData.dailyRecords) {
          const dayIndex = new Date(record.date).getDate() - 1
          if (dayIndex >= 0 && dayIndex < days.length) {
            days[dayIndex].signed = record.signed === true
          }
        }
      }
      signDays.value = monthlyData.signDays || days.filter(d => d.signed).length
      // 检查今天是否已签到
      if (days[today - 1]?.signed) {
        todaySigned.value = true
      }
    }
  } catch {
    signDays.value = 0
  }

  calendarDays.value = days
}

onMounted(() => {
  updateTime()
  setInterval(updateTime, 1000)
  getLocation()
  loadCalendar()
  loadProjectList({ page: 1, size: 50 }).then((res) => {
    projects.value = res?.records || []
    if (projects.value.length > 0) {
      projectId.value = projects.value[0].id
      projectName.value = projects.value[0].projectName
    }
  }).catch(() => {})
})
</script>

<style scoped>
.sign-page { padding: 20rpx; padding-bottom: calc(20rpx + var(--zw-safe-bottom)); }
/* 签到卡：品牌橙纯色块（去渐变纪律）+ 深字承重规则 */
.sign-card { background: var(--zw-brand); border-radius: var(--zw-radius-lg); padding: 48rpx; text-align: center; color: var(--zw-on-primary); margin-bottom: 24rpx; }
.sign-time { font-size: 56rpx; font-weight: bold; margin-bottom: 12rpx; font-family: var(--zw-font-mono); }
.sign-location { font-size: 24rpx; opacity: 0.85; margin-bottom: 32rpx; }
.sign-btn { width: 200rpx; height: 200rpx; border-radius: var(--zw-radius-lg); background: rgba(20,22,26,0.15); border: 4rpx solid var(--zw-on-primary); color: var(--zw-on-primary); font-size: 30rpx; font-weight: 600; display: flex; align-items: center; justify-content: center; margin: 0 auto; }
.sign-btn.signed { background: var(--zw-success-light); border-color: var(--zw-success); color: var(--zw-success); }
.sign-range-tip { margin-top: 16rpx; font-size: 24rpx; font-weight: 600; color: var(--zw-on-primary); }
.calendar-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg); padding: 24rpx; }
.calendar-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.calendar-title { font-size: 30rpx; font-weight: bold; color: var(--zw-text-primary); }
.calendar-stat { font-size: 24rpx; color: var(--zw-brand); }
.calendar-grid { display: flex; flex-wrap: wrap; gap: 8rpx; }
.calendar-day { width: calc(14.28% - 8rpx); aspect-ratio: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; border-radius: var(--zw-radius-sm); background: var(--zw-bg-hover); }
.calendar-day.signed { background: var(--zw-brand-light); }
.calendar-day.today { border: 2rpx solid var(--zw-brand); }
.day-num { font-size: 24rpx; color: var(--zw-text-secondary); font-family: var(--zw-font-mono); }
.day-dot { color: var(--zw-success); font-size: 16rpx; }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; padding-bottom: var(--zw-safe-bottom); box-sizing: border-box; } /* S3.1：底部弹层补安全区 */
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; min-height: 44px; box-sizing: border-box; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx; min-height: 44px; box-sizing: border-box; display: flex; align-items: center; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.picker-cancel { min-width: 44px; min-height: 44px; display: inline-flex; align-items: center; font-size: 28rpx; color: var(--zw-text-secondary); } /* P0 触控热区 */
.empty { text-align: center; padding: 40rpx; color: var(--zw-text-quaternary); }
</style>
