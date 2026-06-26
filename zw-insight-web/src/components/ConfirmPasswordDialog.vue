<template>
  <el-dialog
    v-model="visible"
    title="安全验证"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
    append-to-body
    @closed="handleClosed"
  >
    <div class="confirm-tip">
      <el-icon class="tip-icon"><Lock /></el-icon>
      <span>{{ message }}</span>
    </div>
    <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent>
      <el-form-item prop="password" label="登录密码" label-width="80px">
        <el-input
          ref="inputRef"
          v-model="form.password"
          type="password"
          show-password
          placeholder="请输入当前账号的登录密码"
          autocomplete="off"
          @keyup.enter="handleConfirm"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 全局二次确认密码对话框。
 *
 * 在 App.vue 中全局挂载一次。组件挂载时向 secondaryConfirm 协调器注册自身的 open 方法，
 * axios 拦截器在收到 449 时调用该方法弹出本对话框。用户确认返回密码、取消返回 null。
 */
import { ref, reactive, nextTick, onMounted, onBeforeUnmount } from 'vue'
import type { FormInstance, FormRules, InputInstance } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import {
  registerConfirmOpener,
  unregisterConfirmOpener,
  type ConfirmOpener
} from '@/utils/secondaryConfirm'

const visible = ref(false)
const message = ref('此操作需要二次确认')
const formRef = ref<FormInstance>()
const inputRef = ref<InputInstance>()
const form = reactive({ password: '' })

const rules: FormRules = {
  password: [{ required: true, message: '请输入登录密码', trigger: 'submit' }]
}

/** 当前待 resolve 的 Promise 回调；null 表示对话框处于关闭态。 */
let resolver: ((password: string | null) => void) | null = null
/** 标记用户是否已通过「确认」提交（用于区分关闭原因）。 */
let confirmed = false

const open: ConfirmOpener = (msg: string) => {
  message.value = msg || '此操作需要二次确认'
  form.password = ''
  confirmed = false
  visible.value = true
  nextTick(() => {
    inputRef.value?.focus()
  })
  return new Promise<string | null>((resolve) => {
    resolver = resolve
  })
}

async function handleConfirm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  confirmed = true
  const pwd = form.password
  visible.value = false
  // resolve 在 closed 钩子里统一处理，确保对话框完成关闭动画后再返回
  resolveOnce(pwd)
}

function handleCancel() {
  confirmed = false
  visible.value = false
  resolveOnce(null)
}

/** 对话框被关闭（含右上角 X / ESC / 点击取消）后兜底，确保 Promise 必定 resolve。 */
function handleClosed() {
  if (resolver) {
    resolveOnce(confirmed ? form.password : null)
  }
  form.password = ''
}

function resolveOnce(value: string | null) {
  if (resolver) {
    const r = resolver
    resolver = null
    r(value)
  }
}

onMounted(() => registerConfirmOpener(open))
onBeforeUnmount(() => unregisterConfirmOpener(open))
</script>

<style scoped>
.confirm-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}
.tip-icon {
  color: var(--el-color-warning);
  font-size: 18px;
}
</style>
