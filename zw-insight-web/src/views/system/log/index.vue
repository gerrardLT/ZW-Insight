<template>
  <div class="log-container">
    <el-card shadow="never">
      <!-- Tab 切换 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="操作日志" name="oper" />
        <el-tab-pane label="登录日志" name="login" />
        <el-tab-pane label="异常日志" name="exception" />
      </el-tabs>

      <!-- 搜索区域 -->
      <el-form :model="queryParams" inline>
        <el-form-item label="操作人">
          <el-input v-model="queryParams.operator" placeholder="请输入操作人" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryParams.timeRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 操作日志表格（列名对齐后端 SysOperLog 实体字段；原 operator/ip/status 均非实体字段，列恒空） -->
      <el-table v-if="activeTab === 'oper'" :data="tableData" v-loading="loading" border>
        <el-table-column prop="module" label="操作模块" width="150" />
        <el-table-column prop="operType" label="操作类型" width="120" />
        <el-table-column prop="operName" label="操作人" width="120" />
        <el-table-column prop="operAccount" label="操作账号" width="130" />
        <el-table-column prop="operTime" label="操作时间" width="170" />
        <el-table-column prop="ipAddress" label="IP" width="140" />
        <el-table-column prop="description" label="操作描述" min-width="200" show-overflow-tooltip />
      </el-table>

      <!-- 登录日志表格（列名对齐后端 SysLoginLog 实体字段） -->
      <el-table v-if="activeTab === 'login'" :data="tableData" v-loading="loading" border>
        <el-table-column prop="loginName" label="登录人" width="150" />
        <el-table-column prop="loginAccount" label="登录账号" width="150" />
        <el-table-column prop="loginTime" label="登录时间" width="170" />
        <el-table-column prop="ipAddress" label="IP" width="140" />
      </el-table>

      <!-- 异常日志表格：后端无独立异常日志接口（getExceptionLogPage 复用 /log/oper），
           实体亦无 exceptionMsg 字段，此 Tab 暂无法展示真实异常数据，待产品决策。 -->
      <el-table v-if="activeTab === 'exception'" :data="tableData" v-loading="loading" border>
        <el-table-column prop="module" label="操作模块" width="150" />
        <el-table-column prop="operType" label="操作类型" width="120" />
        <el-table-column prop="operName" label="操作人" width="120" />
        <el-table-column prop="operTime" label="操作时间" width="170" />
        <el-table-column prop="ipAddress" label="IP" width="140" />
        <el-table-column prop="description" label="操作描述" min-width="250" show-overflow-tooltip />
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getOperLogPage, getLoginLogPage, getExceptionLogPage } from '@/api/system'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const activeTab = ref('oper')

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  operator: '',
  timeRange: null as string[] | null
})

const logApis: Record<string, (params: any) => Promise<any>> = {
  oper: getOperLogPage,
  login: getLoginLogPage,
  exception: getExceptionLogPage
}

async function loadData() {
  loading.value = true
  try {
    const params: any = {
      pageNum: queryParams.value.pageNum,
      pageSize: queryParams.value.pageSize,
      operator: queryParams.value.operator
    }
    if (queryParams.value.timeRange && queryParams.value.timeRange.length === 2) {
      params.startTime = queryParams.value.timeRange[0]
      params.endTime = queryParams.value.timeRange[1]
    }
    const api = logApis[activeTab.value]
    const res: any = await api(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    operator: '',
    timeRange: null
  }
  loadData()
}

function handleTabChange() {
  queryParams.value.pageNum = 1
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.log-container {
  padding: var(--zw-space-md);
}
.pagination-wrap {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}
</style>
