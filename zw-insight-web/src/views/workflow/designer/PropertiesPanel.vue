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
        <el-form-item label="审批人 flowable:assignee">
          <el-input
            :model-value="form.assignee"
            placeholder="如：${initiator}"
            @change="(v: string) => commit('flowable:assignee', v)"
          />
        </el-form-item>
        <el-form-item label="候选组 flowable:candidateGroups">
          <el-input
            :model-value="form.candidateGroups"
            placeholder="多个用英文逗号分隔"
            @change="(v: string) => commit('flowable:candidateGroups', v)"
          />
        </el-form-item>
        <el-form-item label="候选人 flowable:candidateUsers">
          <el-input
            :model-value="form.candidateUsers"
            placeholder="多个用英文逗号分隔"
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
          <strong>process id 语义</strong>：&lt;process id&gt; 须与「业务类型」关联流程的
          processKey 一致，部署后才会被对应业务单据引用生效。
        </li>
        <li>
          <strong>审批人常用值</strong>：<code>${initiator}</code> 表示流程发起人；
          也可填写具体用户名或表达式。
        </li>
        <li><strong>候选组 / 候选人</strong>：与审批人三选一配置即可，多值用英文逗号分隔。</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'

const props = defineProps<{
  modeler: any
}>()

const selected = ref<any>(null)
const form = reactive({
  name: '',
  assignee: '',
  candidateGroups: '',
  candidateUsers: ''
})

const isUserTask = computed(() => selected.value?.type === 'bpmn:UserTask')

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
}

/** 回写属性：空串写 undefined 以移除属性 */
function commit(key: string, value: string) {
  if (!selected.value || !props.modeler) return
  const modeling = props.modeler.get('modeling')
  modeling.updateProperties(selected.value, { [key]: value === '' ? undefined : value })
  if (key === 'name') form.name = value
  else if (key === 'flowable:assignee') form.assignee = value
  else if (key === 'flowable:candidateGroups') form.candidateGroups = value
  else if (key === 'flowable:candidateUsers') form.candidateUsers = value
}

onMounted(() => {
  props.modeler?.on?.('selection.changed', refreshFromSelection)
  props.modeler?.on?.('import.done', refreshFromSelection)
  refreshFromSelection()
})
</script>

<style scoped>
.properties-panel {
  width: 320px;
  flex-shrink: 0;
  border-left: 1px solid #ebeef5;
  background: #fff;
  padding: 16px;
  overflow-y: auto;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.panel-hint {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
}

.panel-hint ul {
  padding-left: 18px;
  margin: 8px 0 0;
}

.panel-hint code {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
}
</style>
