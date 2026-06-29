<template>
  <div class="system-config-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>系统设置</span>
          <el-button type="primary" :loading="saveLoading" @click="handleSave">
            <el-icon><Check /></el-icon>保存设置
          </el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="安全设置" name="security" />
        <el-tab-pane label="审批设置" name="approval" />
        <el-tab-pane label="文件设置" name="file" />
        <el-tab-pane label="通知设置" name="notification" />
      </el-tabs>

      <div v-loading="loading" class="config-form-wrap">
        <el-form
          v-if="configList.length > 0"
          ref="formRef"
          :model="formModel"
          label-width="200px"
          label-position="right"
        >
          <el-form-item
            v-for="item in configList"
            :key="item.configKey"
            :label="item.configName"
          >
            <div class="config-item-content">
              <!-- STRING 类型 -->
              <el-input
                v-if="item.valueType === 'STRING'"
                v-model="formModel[item.configKey]"
                :placeholder="getPlaceholder(item)"
                style="width: 360px"
              />

              <!-- NUMBER 类型 -->
              <el-input-number
                v-else-if="item.valueType === 'NUMBER'"
                v-model="formModel[item.configKey]"
                :min="getNumberMin(item)"
                :max="getNumberMax(item)"
                :placeholder="getPlaceholder(item)"
                style="width: 200px"
              />

              <!-- BOOLEAN 类型 -->
              <el-switch
                v-else-if="item.valueType === 'BOOLEAN'"
                v-model="formModel[item.configKey]"
              />

              <!-- JSON 类型 -->
              <el-input
                v-else-if="item.valueType === 'JSON'"
                v-model="formModel[item.configKey]"
                type="textarea"
                :rows="4"
                :placeholder="getPlaceholder(item)"
                style="width: 480px"
              />

              <!-- 恢复默认值按钮 -->
              <el-button
                class="reset-btn"
                link
                type="warning"
                @click="handleResetDefault(item)"
              >
                恢复默认值
              </el-button>
            </div>

            <!-- 值范围 / 备注提示 -->
            <div class="config-item-hint">
              <span v-if="item.valueRange" class="hint-range">
                允许范围：{{ item.valueRange }}
              </span>
              <span v-if="item.remark" class="hint-remark">
                {{ item.remark }}
              </span>
            </div>
          </el-form-item>
        </el-form>

        <el-empty v-else-if="!loading" description="暂无配置项" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TabPaneName } from 'element-plus'
import {
  getConfigByGroup,
  batchUpdateConfig,
  resetConfigToDefault,
  type SysConfigItem
} from '@/api/system'

const activeTab = ref('security')
const loading = ref(false)
const saveLoading = ref(false)
const configList = ref<SysConfigItem[]>([])
const formModel = reactive<Record<string, any>>({})

// 记录原始值，用于判断哪些配置被修改
const originalValues = ref<Record<string, any>>({})

/** 加载分组配置 */
async function loadGroupConfig(group: string) {
  loading.value = true
  try {
    const res: any = await getConfigByGroup(group)
    const list: SysConfigItem[] = res.data || []
    configList.value = list

    // 清空旧值
    Object.keys(formModel).forEach((key) => delete formModel[key])

    // 初始化表单模型
    list.forEach((item) => {
      const value = parseConfigValue(item)
      formModel[item.configKey] = value
      originalValues.value[item.configKey] = value
    })
  } finally {
    loading.value = false
  }
}

/** 根据 valueType 解析值 */
function parseConfigValue(item: SysConfigItem): any {
  const val = item.configValue
  switch (item.valueType) {
    case 'NUMBER':
      return val ? Number(val) : undefined
    case 'BOOLEAN':
      return val === 'true'
    default:
      return val || ''
  }
}

/** 获取 placeholder */
function getPlaceholder(item: SysConfigItem): string {
  if (item.valueType === 'JSON') {
    return '请输入 JSON 格式内容'
  }
  if (item.valueRange) {
    return `允许范围：${item.valueRange}`
  }
  return `请输入${item.configName}`
}

/** 获取 NUMBER 最小值 */
function getNumberMin(item: SysConfigItem): number | undefined {
  if (!item.valueRange) return undefined
  const match = item.valueRange.match(/^(\d+)/)
  return match ? Number(match[1]) : undefined
}

/** 获取 NUMBER 最大值 */
function getNumberMax(item: SysConfigItem): number | undefined {
  if (!item.valueRange) return undefined
  const match = item.valueRange.match(/(\d+)$/)
  return match ? Number(match[1]) : undefined
}

/** 标签页切换 */
function handleTabChange(name: TabPaneName) {
  loadGroupConfig(String(name))
}

/** 保存配置 */
async function handleSave() {
  // 收集修改过的配置
  const changedConfigs: { configKey: string; configValue: string }[] = []

  configList.value.forEach((item) => {
    const currentVal = formModel[item.configKey]
    const originalVal = originalValues.value[item.configKey]

    if (currentVal !== originalVal) {
      changedConfigs.push({
        configKey: item.configKey,
        configValue: String(currentVal)
      })
    }
  })

  if (changedConfigs.length === 0) {
    ElMessage.info('没有需要保存的修改')
    return
  }

  saveLoading.value = true
  try {
    await batchUpdateConfig(changedConfigs)
    ElMessage.success('保存成功')
    // 重新加载以刷新原始值
    await loadGroupConfig(activeTab.value)
  } catch (error: any) {
    // 后端校验失败时 request.ts 拦截器已弹 ElMessage.error
  } finally {
    saveLoading.value = false
  }
}

/** 恢复默认值 */
async function handleResetDefault(item: SysConfigItem) {
  await ElMessageBox.confirm(
    `确定要将「${item.configName}」恢复为默认值吗？`,
    '恢复默认值',
    { type: 'warning' }
  )

  try {
    await resetConfigToDefault(item.configKey)
    ElMessage.success('已恢复默认值')
    // 刷新当前分组
    await loadGroupConfig(activeTab.value)
  } catch (error: any) {
    // 错误已由拦截器处理
  }
}

onMounted(() => {
  loadGroupConfig(activeTab.value)
})
</script>

<style scoped>
.system-config-container {
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.config-form-wrap {
  min-height: 300px;
  padding-top: 16px;
}

.config-item-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.reset-btn {
  flex-shrink: 0;
}

.config-item-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.hint-range {
  margin-right: 12px;
  color: #e6a23c;
}

.hint-remark {
  color: #909399;
}
</style>
