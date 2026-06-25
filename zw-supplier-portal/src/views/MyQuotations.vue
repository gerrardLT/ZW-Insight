<template>
  <div class="min-h-screen bg-gray-50 p-4">
    <div class="max-w-2xl mx-auto">
      <div class="flex justify-between items-center mb-6">
        <h1 class="text-xl font-bold text-gray-800">我的报价</h1>
        <button @click="$router.push('/inquiry')" class="text-blue-500 text-sm">← 询价列表</button>
      </div>
      <div v-if="list.length === 0" class="text-center text-gray-400 py-12">暂无报价记录</div>
      <div v-else class="space-y-4">
        <div v-for="item in list" :key="item.id" class="bg-white rounded-lg p-4 shadow-sm">
          <div class="flex justify-between items-start">
            <h3 class="font-medium text-gray-800">询价ID: {{ item.inquiryId }}</h3>
            <span class="text-xs px-2 py-1 rounded"
              :class="item.status==='SUBMITTED'?'bg-blue-100 text-blue-700':item.status==='WON'?'bg-green-100 text-green-700':'bg-gray-100 text-gray-500'">
              {{ statusMap[item.status] || item.status }}
            </span>
          </div>
          <p class="text-sm text-gray-500 mt-2">报价总额：¥{{ item.totalAmount }}</p>
          <p class="text-xs text-gray-400 mt-1">提交时间：{{ item.submitTime }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMyQuotations } from '../api'
const list = ref<any[]>([])
const statusMap: Record<string, string> = { SUBMITTED: '已提交', WON: '已中标', LOST: '未中标' }
onMounted(async () => { try { const res: any = await getMyQuotations(); list.value = res.data?.records || [] } catch {} })
</script>
