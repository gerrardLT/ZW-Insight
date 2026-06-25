<template>
  <div class="min-h-screen bg-gray-50 p-4">
    <div class="max-w-2xl mx-auto">
      <div class="flex justify-between items-center mb-6">
        <h1 class="text-xl font-bold text-gray-800">询价公告</h1>
        <button @click="$router.push('/quotation')" class="text-blue-500 text-sm">我的报价 →</button>
      </div>
      <div v-if="loading" class="text-center text-gray-400 py-12">加载中...</div>
      <div v-else-if="list.length === 0" class="text-center text-gray-400 py-12">暂无询价公告</div>
      <div v-else class="space-y-4">
        <div v-for="item in list" :key="item.id" @click="$router.push(`/inquiry/${item.id}`)"
          class="bg-white rounded-lg p-4 shadow-sm cursor-pointer hover:shadow-md transition">
          <div class="flex justify-between items-start">
            <h3 class="font-medium text-gray-800">{{ item.title }}</h3>
            <span class="text-xs px-2 py-1 rounded" :class="item.status==='PUBLISHED'?'bg-green-100 text-green-700':'bg-gray-100 text-gray-500'">
              {{ item.status === 'PUBLISHED' ? '报价中' : '已截止' }}
            </span>
          </div>
          <p class="text-sm text-gray-500 mt-2">截止时间：{{ item.deadline || '未设置' }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getInquiryList } from '../api'
const list = ref<any[]>([]); const loading = ref(true)
onMounted(async () => { try { const res: any = await getInquiryList(); list.value = res.data?.records || [] } finally { loading.value = false } })
</script>
