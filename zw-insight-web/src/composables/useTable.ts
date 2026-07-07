import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { PageResult } from '@/types/api'

/**
 * 通用列表表格组合式函数
 * 封装分页查询、刷新、删除确认等常用表格逻辑
 *
 * @example
 * const { tableData, total, loading, pagination, search, reset, handleDelete } = useTable({
 *   fetchApi: getProjectPage,
 *   deleteApi: deleteProject,
 *   defaultParams: { status: 'DRAFT' }
 * })
 */
export interface UseTableOptions<T, P = Record<string, unknown>> {
  /** 分页查询 API 函数 */
  fetchApi: (params: any) => Promise<any>
  /** 删除 API 函数（可选） */
  deleteApi?: (id: number) => Promise<any>
  /** 默认查询参数 */
  defaultParams?: Partial<P>
  /** 默认每页条数 */
  defaultPageSize?: number
  /** 是否立即加载 */
  immediate?: boolean
}

export function useTable<T = Record<string, unknown>, P = Record<string, unknown>>(
  options: UseTableOptions<T, P>
) {
  const { fetchApi, deleteApi, defaultParams = {}, defaultPageSize = 10, immediate = true } = options

  const tableData = ref<T[]>([]) as { value: T[] }
  const total = ref(0)
  const loading = ref(false)

  const pagination = reactive({
    page: 1,
    size: defaultPageSize
  })

  const queryParams = reactive<Record<string, unknown>>({ ...defaultParams })

  /** 执行查询 */
  async function search() {
    loading.value = true
    try {
      const params = {
        ...queryParams,
        page: pagination.page,
        size: pagination.size
      }
      const res = await fetchApi(params)
      const data = res?.data || res
      if (data?.records) {
        tableData.value = data.records as T[]
        total.value = data.total || 0
      } else if (Array.isArray(data)) {
        tableData.value = data as T[]
        total.value = data.length
      }
    } catch {
      tableData.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  /** 重置查询条件并刷新 */
  function reset() {
    Object.keys(queryParams).forEach(key => {
      queryParams[key] = (defaultParams as any)[key] ?? undefined
    })
    pagination.page = 1
    search()
  }

  /** 页码变化 */
  function handlePageChange(page: number) {
    pagination.page = page
    search()
  }

  /** 每页条数变化 */
  function handleSizeChange(size: number) {
    pagination.size = size
    pagination.page = 1
    search()
  }

  /** 删除确认 */
  async function handleDelete(id: number, tip = '确定删除该记录吗？') {
    if (!deleteApi) return
    try {
      await ElMessageBox.confirm(tip, '删除确认', { type: 'warning' })
      await deleteApi(id)
      ElMessage.success('删除成功')
      // 如果当前页只有一条记录且不是第一页，回退一页
      if (tableData.value.length === 1 && pagination.page > 1) {
        pagination.page--
      }
      search()
    } catch {
      // 用户取消或删除失败
    }
  }

  // 立即加载
  if (immediate) {
    search()
  }

  return {
    tableData,
    total,
    loading,
    pagination,
    queryParams,
    search,
    reset,
    handlePageChange,
    handleSizeChange,
    handleDelete
  }
}
