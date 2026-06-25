<template>
  <el-button link type="primary" :loading="loading" @click="handlePreview">
    <el-icon><View /></el-icon>预览
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import { getFilePreviewUrl } from '@/api/batch'

const props = defineProps<{ fileId: number }>()
const loading = ref(false)

async function handlePreview() {
  loading.value = true
  try {
    const res: any = await getFilePreviewUrl(props.fileId)
    const url = res.data
    if (url) {
      window.open(url, '_blank')
    } else {
      ElMessage.warning('无法生成预览链接')
    }
  } catch {
    ElMessage.error('获取预览地址失败')
  } finally {
    loading.value = false
  }
}
</script>
