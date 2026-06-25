<template>
  <div class="min-h-screen bg-gray-50 p-4">
    <div class="max-w-2xl mx-auto">
      <button @click="$router.back()" class="text-blue-500 text-sm mb-4">← 返回列表</button>
      <div class="bg-white rounded-lg p-6 shadow-sm mb-4">
        <h1 class="text-xl font-bold text-gray-800 mb-2">{{ inquiry.title }}</h1>
        <p class="text-sm text-gray-500">截止时间：{{ inquiry.deadline || '未设置' }}</p>
        <p class="text-sm text-gray-500 mt-1">发布时间：{{ inquiry.publishTime }}</p>
      </div>
      <!-- 材料清单 -->
      <div class="bg-white rounded-lg p-6 shadow-sm mb-4">
        <h2 class="font-medium text-gray-800 mb-4">材料清单及报价</h2>
        <div v-for="(item, idx) in materials" :key="idx" class="border-b py-3 last:border-0">
          <div class="flex justify-between text-sm">
            <span class="text-gray-700">{{ item.materialName }}</span>
            <span class="text-gray-500">{{ item.specification }} × {{ item.quantity }}{{ item.unit }}</span>
          </div>
          <div class="mt-2">
            <input v-model="item.unitPrice" type="number" placeholder="请输入单价"
              class="w-full px-3 py-2 border rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500" />
          </div>
        </div>
      </div>
      <button @click="handleSubmit" :disabled="submitting || isExpired"
        class="w-full py-3 bg-blue-500 text-white rounded-lg font-medium hover:bg-blue-600 disabled:opacity-50">
        {{ isExpired ? '报价已截止' : submitting ? '提交中...' : '提交报价' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getInquiryDetail, submitQuotation } from '../api'

const route = useRoute(); const router = useRouter()
const inquiry = ref<any>({}); const materials = ref<any[]>([]); const submitting = ref(false)
const isExpired = computed(() => inquiry.value.deadline && new Date(inquiry.value.deadline) < new Date())

onMounted(async () => {
  const id = Number(route.params.id)
  try { const res: any = await getInquiryDetail(id); inquiry.value = res.data?.inquiry || {}; materials.value = (res.data?.materials || []).map((m: any) => ({ ...m, unitPrice: '' })) } catch { alert('加载失败') }
})

async function handleSubmit() {
  const details = materials.value.filter(m => m.unitPrice).map(m => ({ materialId: m.id, unitPrice: Number(m.unitPrice), quantity: m.quantity, totalPrice: Number(m.unitPrice) * m.quantity }))
  if (details.length === 0) { alert('请至少填写一项报价'); return }
  submitting.value = true
  try { await submitQuotation({ inquiryId: inquiry.value.id, details }); alert('报价提交成功！'); router.push('/quotation') } catch (e: any) { alert(e.response?.data?.message || '提交失败') } finally { submitting.value = false }
}
</script>
