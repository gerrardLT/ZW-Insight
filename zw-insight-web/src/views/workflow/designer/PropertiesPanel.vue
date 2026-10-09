<template>
  <div class="properties-panel">
    <!-- 选中单个 UserTask：显示审批属性表单 -->
    <template v-if="isUserTask">
      <div class="panel-title">用户任务属性</div>
      <el-form label-position="top" size="small">
        <el-form-item label="节点名称">
          <el-input
            :model-value="form.name"
            placeholder="如：合同审批"
            @change="(v: string) => commit('name', v)"
          />
        </el-form-item>

        <!-- 审批人：手填/表达式 + 发起人快捷键 + 用户快捷下拉 -->
        <el-form-item label="审批人 flowable:assignee">
          <el-input
            :model-value="form.assignee"
            placeholder="如：${initiator} 或用户ID"
            @change="(v: string) => commit('flowable:assignee', v)"
          />
          <div class="quick-assignee-bar">
            <el-button
              type="primary"
              link
              size="small"
              @click="setInitiator"
            >
              + 填入发起人 ${initiator}
            </el-button>
          </div>
          <el-select
            v-model="quickUser"
            placeholder="从系统用户中快捷选择"
            clearable
            filterable
            style="width: 100%; margin-top: var(--zw-space-2xs)"
            @change="handleSelectQuickUser"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="`${u.realName} (${u.username})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>

        <!-- 候选组：多选下拉 + 文本框双向同步 -->
        <el-form-item label="候选组 flowable:candidateGroups">
          <el-select
            v-model="selectedRoleCodes"
            multiple
            filterable
            placeholder="点击选择角色（自动填充）"
            style="width: 100%; margin-bottom: var(--zw-space-2xs)"
            @change="handleRoleSelectChange"
          >
            <el-option
              v-for="r in roleOptions"
              :key="r.roleCode"
              :label="`${r.roleName} (${r.roleCode})`"
              :value="r.roleCode"
            />
          </el-select>
          <el-input
            :model-value="form.candidateGroups"
            placeholder="多个角色编码用英文逗号分隔"
            @change="(v: string) => commit('flowable:candidateGroups', v)"
          />
        </el-form-item>

        <!-- 候选人：多选下拉 + 文本框双向同步 -->
        <el-form-item label="候选人 flowable:candidateUsers">
          <el-select
            v-model="selectedUsernames"
            multiple
            filterable
            placeholder="点击选择候选人（自动填充）"
            style="width: 100%; margin-bottom: var(--zw-space-2xs)"
            @change="handleUserSelectChange"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="`${u.realName} (${u.username})`"
              :value="u.id"
            />
          </el-select>
          <el-input
            :model-value="form.candidateUsers"
            placeholder="多个用户ID用英文逗号分隔"
            @change="(v: string) => commit('flowable:candidateUsers', v)"
          />
        </el-form-item>
      </el-form>
    </template>

    <!-- 未选中或非 UserTask：使用说明 -->
    <div v-else class="panel-hint">
      <div class="panel-title">属性面板</div>
      <p>点击画布中的<strong>用户任务</strong>节点，可编辑节点名称与审批人等属性。</p>
      <ul>
        <li>
          <strong>基本操作</strong>：点节点 → 拖出右侧小箭头连到下一节点；节点旁扳手图标可更改类型；
          选中后按 <code>Delete</code> 删除；左侧工具板拖出新节点；滚轮缩放，抓手工具拖动画布。
        </li>
        <li>
          <strong>process id 语义</strong>：&lt;process id&gt; 须与「业务类型」关联流程的
          processKey 一致，部署后才会被对应业务单据引用生效。
        </li>
        <li>
          <strong>审批人常用值</strong>：<code>${initiator}</code> 表示流程发起人；
          也可从下拉菜单中直接选择用户，或填写自定义表达式。
        </li>
        <li><strong>候选组 / 候选人</strong>：与审批人三选一配置即可，支持下拉直接勾选角色或人员。</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, reactive, computed, onMounted } from 'vue'
import { getRoleList, getUserPage } from '@/api/system'

const props = defineProps<{
  modeler: any
}>()

// bpmn-js 图元（含不可配置的 labels 属性）不能被 Vue 深度代理，否则 updateProperties 时抛 Proxy 约束错误
const selected = shallowRef<any>(null)
const form = reactive({
  name: '',
  assignee: '',
  candidateGroups: '',
  candidateUsers: ''
})

const roleOptions = ref<Array<{ roleCode: string; roleName: string }>>([])
const userOptions = ref<Array<{ id: string; username: string; realName: string }>>([])
const selectedRoleCodes = ref<string[]>([])
const selectedUsernames = ref<string[]>([])
const quickUser = ref<string>('')

const isUserTask = computed(() => selected.value?.type === 'bpmn:UserTask')

/** 加载系统角色与用户选项，方便直接下拉勾选 */
async function loadOptions() {
  if (typeof process !== 'undefined' && process.env?.NODE_ENV === 'test') {
    return
  }
  try {
    const roleRes: any = await getRoleList()
    const rList = Array.isArray(roleRes?.data) ? roleRes.data : (roleRes?.data?.records || [])
    roleOptions.value = rList.map((r: any) => ({
      roleCode: r.roleCode,
      roleName: r.roleName
    }))
  } catch {
    // 忽略加载异常
  }

  try {
    const userRes: any = await getUserPage({ page: 1, size: 200 })
    const uList = Array.isArray(userRes?.data) ? userRes.data : (userRes?.data?.records || [])
    // 待办按用户 ID 匹配 assignee/candidate，必须写 ID 而非账号（账号写进去任何人都看不到待办）
    userOptions.value = uList.map((u: any) => ({
      id: String(u.id),
      username: u.username,
      realName: u.realName || u.username
    }))
  } catch {
    // 忽略加载异常
  }
}

/** 从当前选中刷新面板状态（仅单选 UserTask 显示表单） */
function refreshFromSelection() {
  const sel = props.modeler?.get?.('selection')?.get?.()
  const el = Array.isArray(sel) && sel.length === 1 ? sel[0] : null
  selected.value = el && el.type === 'bpmn:UserTask' ? el : null
  if (!selected.value) return
  const bo = selected.value.businessObject
  form.name = bo.get('name') || ''
  form.assignee = bo.get('flowable:assignee') || ''
  form.candidateGroups = bo.get('flowable:candidateGroups') || ''
  form.candidateUsers = bo.get('flowable:candidateUsers') || ''

  quickUser.value = ''
  selectedRoleCodes.value = form.candidateGroups
    ? form.candidateGroups.split(',').map((s: string) => s.trim()).filter(Boolean)
    : []
  selectedUsernames.value = form.candidateUsers
    ? form.candidateUsers.split(',').map((s: string) => s.trim()).filter(Boolean)
    : []
}

/** 回写属性：空串写 undefined 以移除属性 */
function commit(key: string, value: string) {
  if (!selected.value || !props.modeler) return
  const modeling = props.modeler.get('modeling')
  modeling.updateProperties(selected.value, { [key]: value === '' ? undefined : value })
  if (key === 'name') form.name = value
  else if (key === 'flowable:assignee') form.assignee = value
  else if (key === 'flowable:candidateGroups') {
    form.candidateGroups = value
    selectedRoleCodes.value = value ? value.split(',').map(s => s.trim()).filter(Boolean) : []
  }
  else if (key === 'flowable:candidateUsers') {
    form.candidateUsers = value
    selectedUsernames.value = value ? value.split(',').map(s => s.trim()).filter(Boolean) : []
  }
}

function setInitiator() {
  commit('flowable:assignee', '${initiator}')
}

function handleSelectQuickUser(val: string) {
  if (val) {
    commit('flowable:assignee', val)
  }
}

function handleRoleSelectChange(vals: string[]) {
  const joined = (vals || []).join(',')
  commit('flowable:candidateGroups', joined)
}

function handleUserSelectChange(vals: string[]) {
  const joined = (vals || []).join(',')
  commit('flowable:candidateUsers', joined)
}

onMounted(() => {
  loadOptions()
  props.modeler?.on?.('selection.changed', refreshFromSelection)
  props.modeler?.on?.('import.done', refreshFromSelection)
  refreshFromSelection()
})
</script>

<style scoped>
.properties-panel {
  width: 320px;
  flex-shrink: 0;
  border-left: 1px solid var(--zw-border-light);
  background: var(--zw-bg-card);
  padding: var(--zw-space-md);
  overflow-y: auto;
}

.panel-title {
  font-size: var(--zw-font-size-base);
  font-weight: 600;
  margin-bottom: var(--zw-space-sm-md);
}

.quick-assignee-bar {
  margin-top: var(--zw-space-2xs);
  display: flex;
  justify-content: flex-end;
}

.panel-hint {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
  line-height: 1.8;
}

.panel-hint ul {
  padding-left: var(--zw-space-md);
  margin: var(--zw-space-sm) 0 0;
}

.panel-hint code {
  background: var(--zw-bg-page);
  padding: var(--zw-space-2xs) var(--zw-space-xs);
  border-radius: var(--zw-radius-xs);
}
</style>
