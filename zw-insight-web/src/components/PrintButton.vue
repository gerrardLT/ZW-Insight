<template>
  <el-button
    :type="type"
    :size="size"
    :link="link"
    :plain="plain"
    :loading="loading"
    @click="handlePrint"
  >
    <el-icon v-if="showIcon" style="margin-right: 4px"><Printer /></el-icon>
    {{ text }}
  </el-button>
</template>

<script setup lang="ts">
/**
 * 通用打印按钮组件
 *
 * 用法：
 *   <PrintButton :template-id="1" :variables="{ contractName: row.contractName }" />
 *   <PrintButton business-type="CONTRACT" :variables="{ ... }" />
 *
 * 工作流程：
 *   1. 解析模板：优先使用 templateId；否则根据 businessType 查询该业务类型下的打印模板
 *   2. 调用真实渲染接口 POST /api/v1/print-template/render 获取 HTML（取 res.data）
 *   3. 将 HTML 写入隐藏 iframe 并触发 window.print()
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Printer } from '@element-plus/icons-vue'
import {
  renderPrintTemplate,
  getPrintTemplatePage
} from '@/api/print-template'
import type { PrintTemplate } from '@/api/print-template'

const props = withDefaults(
  defineProps<{
    /** 指定打印模板 ID（与 businessType 二选一，优先级更高） */
    templateId?: number
    /** 业务类型（CONTRACT/BUDGET/MATERIAL...），未指定 templateId 时按此查询模板 */
    businessType?: string
    /** 业务数据主键，可传给后端用于动态取数 */
    businessDataId?: number | string
    /** 渲染变量（业务数据），与模板占位符/Thymeleaf 表达式对应 */
    variables?: Record<string, any>
    /** 按钮文案 */
    text?: string
    /** 按钮类型 */
    type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'
    /** 按钮尺寸 */
    size?: 'large' | 'default' | 'small'
    /** 是否为 link 风格 */
    link?: boolean
    /** 是否为 plain 风格 */
    plain?: boolean
    /** 是否显示打印图标 */
    showIcon?: boolean
  }>(),
  {
    templateId: undefined,
    businessType: undefined,
    businessDataId: undefined,
    variables: () => ({}),
    text: '打印',
    type: 'primary',
    size: 'default',
    link: false,
    plain: false,
    showIcon: true
  }
)

const loading = ref(false)

/** 根据业务类型解析出可用的打印模板 ID */
async function resolveTemplateId(): Promise<number | null> {
  if (props.templateId) return props.templateId
  if (!props.businessType) return null

  const res: any = await getPrintTemplatePage({
    pageNum: 1,
    pageSize: 50,
    templateType: 'PRINT',
    businessType: props.businessType
  })
  const records: PrintTemplate[] = res.data?.records || []
  if (records.length === 0) return null
  // 优先选择默认模板，否则取第一个
  const defaultTpl = records.find(item => item.isDefault === 1)
  return (defaultTpl?.id ?? records[0].id) ?? null
}

/** 将 HTML 写入隐藏 iframe 并触发浏览器打印 */
function printHtml(html: string) {
  const iframe = document.createElement('iframe')
  iframe.style.position = 'fixed'
  iframe.style.right = '0'
  iframe.style.bottom = '0'
  iframe.style.width = '0'
  iframe.style.height = '0'
  iframe.style.border = '0'
  document.body.appendChild(iframe)

  const doc = iframe.contentWindow?.document
  if (!doc) {
    document.body.removeChild(iframe)
    ElMessage.error('无法创建打印窗口')
    return
  }

  doc.open()
  doc.write(html)
  doc.close()

  const triggerPrint = () => {
    const win = iframe.contentWindow
    if (!win) return
    win.focus()
    win.print()
    // 打印对话框关闭后清理 iframe
    setTimeout(() => {
      if (iframe.parentNode) iframe.parentNode.removeChild(iframe)
    }, 1000)
  }

  // 等待 iframe 内容（图片/样式）加载完成后再打印
  if (iframe.contentWindow) {
    iframe.onload = triggerPrint
    // 兜底：部分浏览器 document.write 不触发 onload
    setTimeout(triggerPrint, 500)
  }
}

async function handlePrint() {
  loading.value = true
  try {
    const templateId = await resolveTemplateId()
    if (!templateId) {
      ElMessage.warning(
        props.businessType
          ? `未找到「${props.businessType}」业务类型对应的打印模板，请先在打印模板管理中配置`
          : '未指定打印模板'
      )
      return
    }

    const res: any = await renderPrintTemplate({
      templateId,
      businessDataId: props.businessDataId,
      variables: props.variables
    })

    const html: string = res.data
    if (!html) {
      ElMessage.error('渲染结果为空，无法打印')
      return
    }

    printHtml(html)
  } finally {
    loading.value = false
  }
}
</script>
