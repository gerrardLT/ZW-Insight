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
        <el-tab-pane label="品牌设置" name="brand" />
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
              <!-- 图片上传类型 (Logo / Favicon) -->
              <template v-if="isImageConfig(item.configKey)">
                <div class="logo-uploader-box">
                  <div v-if="formModel[item.configKey]" class="logo-preview-wrapper">
                    <img :src="formModel[item.configKey]" class="logo-preview-img" alt="预览" />
                  </div>
                  <el-input
                    v-model="formModel[item.configKey]"
                    placeholder="输入相对路径或上传图片"
                    style="width: 260px"
                  />
                  <el-upload
                    action="#"
                    :show-file-list="false"
                    :auto-upload="false"
                    :on-change="(file: any) => handleUploadImage(file, item.configKey)"
                    accept="image/*"
                  >
                    <el-button type="primary" plain>
                      <el-icon><Upload /></el-icon>上传图片
                    </el-button>
                  </el-upload>
                </div>
              </template>

              <!-- 普通 STRING 类型 -->
              <el-input
                v-else-if="item.valueType === 'STRING'"
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
import { Check, Upload } from '@element-plus/icons-vue'
import type { TabPaneName } from 'element-plus'
import {
  getConfigByGroup,
  batchUpdateConfig,
  resetConfigToDefault,
  type SysConfigItem
} from '@/api/system'
import { uploadBrandImage } from '@/api/brand'
import { useBrandStore } from '@/stores/brand'

const brandStore = useBrandStore()
const activeTab = ref('brand')
const loading = ref(false)
const saveLoading = ref(false)
const configList = ref<SysConfigItem[]>([])
const formModel = reactive<Record<string, any>>({})
const originalValues = ref<Record<string, any>>({})

function isImageConfig(key: string): boolean {
  return ['brand_logo_url', 'brand_logo_light_url', 'brand_favicon_url'].includes(key)
}

/** 触发文件上传 */
async function handleUploadImage(uploadFile: any, configKey: string) {
  const file = uploadFile.raw
  if (!file) return

  let logoType: 'logo' | 'logoLight' | 'favicon' = 'logo'
  if (configKey === 'brand_logo_light_url') logoType = 'logoLight'
  if (configKey === 'brand_favicon_url') logoType = 'favicon'

  try {
    const res: any = await uploadBrandImage(file, logoType)
    if (res?.data) {
      formModel[configKey] = res.data
      ElMessage.success('图片上传成功，保存设置后生效')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '图片上传失败')
  }
}

/** 加载分组配置 */
async function loadGroupConfig(group: string) {
  loading.value = true
  try {
    const res: any = await getConfigByGroup(group)
    const list: SysConfigItem[] = res.data || []
    configList.value = list

    Object.keys(formModel).forEach((key) => delete formModel[key])

    list.forEach((item) => {
      const value = parseConfigValue(item)
      formModel[item.configKey] = value
      originalValues.value[item.configKey] = value
    })
  } finally {
    loading.value = false
  }
}

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

function getPlaceholder(item: SysConfigItem): string {
  if (item.valueType === 'JSON') return '请输入 JSON 格式内容'
  if (item.valueRange) return `允许范围：${item.valueRange}`
  return `请输入${item.configName}`
}

function getNumberMin(item: SysConfigItem): number | undefined {
  if (!item.valueRange) return undefined
  const match = item.valueRange.match(/^(\d+)/)
  return match ? Number(match[1]) : undefined
}

function getNumberMax(item: SysConfigItem): number | undefined {
  if (!item.valueRange) return undefined
  const match = item.valueRange.match(/(\d+)$/)
  return match ? Number(match[1]) : undefined
}

function handleTabChange(name: TabPaneName) {
  loadGroupConfig(String(name))
}

async function handleSave() {
  const changedConfigs: { configKey: string; configValue: string }[] = []

  configList.value.forEach((item) => {
    const currentVal = formModel[item.configKey]
    const originalVal = originalValues.value[item.configKey]

    if (currentVal !== originalVal) {
      changedConfigs.push({
        configKey: item.configKey,
        configValue: String(currentVal ?? '')
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
    await loadGroupConfig(activeTab.value)
    // 如果修改了品牌分组，同步刷新全局品牌 Store
    if (activeTab.value === 'brand') {
      brandStore.loaded = false
      await brandStore.fetchBrandConfig()
    }
  } catch (error: any) {
  } finally {
    saveLoading.value = false
  }
}

async function handleResetDefault(item: SysConfigItem) {
  await ElMessageBox.confirm(
    `确定要将「${item.configName}」恢复为默认值吗？`,
    '恢复默认值',
    { type: 'warning' }
  )

  try {
    await resetConfigToDefault(item.configKey)
    ElMessage.success('已恢复默认值')
    await loadGroupConfig(activeTab.value)
    if (activeTab.value === 'brand') {
      brandStore.loaded = false
      await brandStore.fetchBrandConfig()
    }
  } catch (error: any) {
  }
}

onMounted(() => {
  loadGroupConfig(activeTab.value)
})
</script>

<style scoped>
.system-config-container {
  padding: var(--zw-space-md);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.config-form-wrap {
  min-height: 300px;
  padding-top: var(--zw-space-md);
}

.config-item-content {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm-md);
}

.logo-uploader-box {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm-md);
}

.logo-preview-wrapper {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zw-bg-card);
  border: 1px dashed var(--zw-border-light);
  border-radius: 4px;
  overflow: hidden;
}

.logo-preview-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.reset-btn {
  flex-shrink: 0;
}

.config-item-hint {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-tertiary);
  line-height: 1.4;
}

.hint-range {
  margin-right: var(--zw-space-sm-md);
  color: var(--zw-warning);
}

.hint-remark {
  color: var(--zw-text-tertiary);
}
</style>
