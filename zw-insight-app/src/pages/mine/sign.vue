<template>
  <view class="sign-page">
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
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'

const signing = ref(false)
const todaySigned = ref(false)
const isInRange = ref(true)
const signDays = ref(0)
const currentTime = ref('')
const currentMonth = ref('')
const calendarDays = ref<any[]>([])
const location = ref({ latitude: 0, longitude: 0, address: '' })

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

// 签到
async function handleSign() {
  if (!location.value.latitude) {
    uni.showToast({ title: '请等待定位完成', icon: 'none' }); return
  }
  signing.value = true
  try {
    const token = uni.getStorageSync('token')
    const res: any = await uni.request({
      url: '/api/v1/site/sign',
      method: 'POST',
      header: { Authorization: `Bearer ${token}` },
      data: { projectId: 1, latitude: location.value.latitude, longitude: location.value.longitude, address: location.value.address }
    })
    if (res.data?.code === 200) {
      todaySigned.value = true
      isInRange.value = res.data?.data?.isInRange === 1
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

  try {
    const token = uni.getStorageSync('token')
    const res: any = await uni.request({
      url: '/api/v1/site/sign/monthly',
      method: 'GET',
      header: { Authorization: `Bearer ${token}` },
      data: { projectId: 1, month: monthStr }
    })
    if (res.data?.code === 200 && res.data?.data) {
      const monthlyData = res.data.data
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
  } catch (e) {
    // 接口失败时日历保持空状态，不影响页面渲染
    signDays.value = 0
  }

  calendarDays.value = days
}

onMounted(() => {
  updateTime()
  setInterval(updateTime, 1000)
  getLocation()
  loadCalendar()
})
</script>

<style scoped>
.sign-page { padding: 20rpx; }
.sign-card { background: linear-gradient(135deg, #409eff, #66b1ff); border-radius: 16rpx; padding: 48rpx 32rpx; text-align: center; color: #fff; margin-bottom: 24rpx; }
.sign-time { font-size: 56rpx; font-weight: bold; margin-bottom: 12rpx; }
.sign-location { font-size: 24rpx; opacity: 0.9; margin-bottom: 32rpx; }
.sign-btn { width: 200rpx; height: 200rpx; border-radius: 50%; background: rgba(255,255,255,0.2); border: 4rpx solid rgba(255,255,255,0.6); color: #fff; font-size: 30rpx; display: flex; align-items: center; justify-content: center; margin: 0 auto; }
.sign-btn.signed { background: rgba(103,194,58,0.3); border-color: rgba(103,194,58,0.8); }
.sign-range-tip { margin-top: 16rpx; font-size: 24rpx; color: #ffd666; }
.calendar-section { background: #fff; border-radius: 12rpx; padding: 24rpx; }
.calendar-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.calendar-title { font-size: 30rpx; font-weight: bold; color: #303133; }
.calendar-stat { font-size: 24rpx; color: #409eff; }
.calendar-grid { display: flex; flex-wrap: wrap; gap: 8rpx; }
.calendar-day { width: calc(14.28% - 8rpx); aspect-ratio: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; border-radius: 8rpx; background: #f5f7fa; }
.calendar-day.signed { background: #ecf5ff; }
.calendar-day.today { border: 2rpx solid #409eff; }
.day-num { font-size: 24rpx; color: #606266; }
.day-dot { color: #67c23a; font-size: 16rpx; }
</style>
