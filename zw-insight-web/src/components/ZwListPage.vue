<template>
  <el-card shadow="never" class="zw-list-page">
    <div v-if="$slots.toolbar" class="zw-list-page__toolbar">
      <slot name="toolbar" />
    </div>

    <el-skeleton v-if="skeleton && loading && !data.length" :rows="5" animated />
    <el-table
      v-else
      v-bind="$attrs"
      :data="data"
      :border="border"
      v-loading="loading"
      class="zw-list-page__table"
    >
      <slot />
      <template v-if="$slots['table-append']" #append>
        <slot name="table-append" />
      </template>
      <template #empty>
        <slot name="empty">
          <el-empty :description="emptyText" :image-size="60" />
        </slot>
      </template>
    </el-table>

    <!-- 表格脚注（如「本页合计（非全量）」）：渲染在表格之后、分页之前，不随表体横向滚动 -->
    <div v-if="$slots.footer" class="zw-list-page__footer">
      <slot name="footer" />
    </div>

    <div v-if="!hidePagination" class="zw-list-page__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="currentSize"
        :page-sizes="pageSizes"
        :total="total"
        :layout="layout"
        @size-change="emit('change')"
        @current-change="emit('change')"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
/**
 * 列表页装配壳（3.8：ZwListPage 第二批铺设）。
 *
 * 只统一「卡片 + 骨架 + el-table + 分页」四段样板，不接管列定义与业务交互：
 * - 列与单元格模板通过默认插槽注入（`el-table-column`），
 * - 工具栏通过 `#toolbar` 插槽注入（可放 ZwListToolbar / 业务按钮），
 * - 其它 el-table 属性（stripe / row-key / summary-method / height / @row-click…）
 *   经 attrs 透传（inheritAttrs: false + v-bind="$attrs"），页面无需改写用法。
 *
 * 设计纪律（3e 回退教训）：本组件不依赖页面的 useColumnSetting；
 * 需要列显隐的页面把 ZwListToolbar 放进 #toolbar 插槽即可，禁止在壳内隐式读写列配置。
 */
import { computed } from 'vue'

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    /** 当前页数据 */
    data?: any[]
    loading?: boolean
    total?: number
    /** 当前页码（v-model:page） */
    page?: number
    /** 每页条数（v-model:size） */
    size?: number
    pageSizes?: number[]
    layout?: string
    /** 隐藏分页（明细型列表用） */
    hidePagination?: boolean
    /** 首屏骨架（无数据且加载中时显示；翻页保留 v-loading 遮罩） */
    skeleton?: boolean
    border?: boolean
    emptyText?: string
  }>(),
  {
    data: () => [],
    loading: false,
    total: 0,
    page: 1,
    size: 10,
    pageSizes: () => [10, 20, 50, 100],
    layout: 'total, sizes, prev, pager, next, jumper',
    hidePagination: false,
    skeleton: true,
    border: true,
    emptyText: '暂无数据'
  }
)

const emit = defineEmits<{
  (e: 'update:page', page: number): void
  (e: 'update:size', size: number): void
  (e: 'change'): void
}>()

const currentPage = computed({
  get: () => props.page,
  set: (value: number) => emit('update:page', value)
})

const currentSize = computed({
  get: () => props.size,
  set: (value: number) => emit('update:size', value)
})
</script>

<style scoped>
.zw-list-page__toolbar {
  margin-bottom: var(--zw-space-md);
}

.zw-list-page__table {
  width: 100%;
}

.zw-list-page__footer {
  margin-top: var(--zw-space-sm);
}

.zw-list-page__pagination {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}
</style>
