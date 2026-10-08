<template>
  <div class="designer-container">
    <!-- 工具栏 -->
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="handleSave">
          <el-icon><Download /></el-icon>保存 XML
        </el-button>
        <el-button type="success" :loading="deploying" @click="handleDeploy">
          <el-icon><Upload /></el-icon>部署到服务器
        </el-button>
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept=".bpmn,.xml"
          :on-change="handleImport"
        >
          <template #trigger>
            <el-button>
              <el-icon><FolderOpened /></el-icon>导入文件
            </el-button>
          </template>
        </el-upload>
        <el-button @click="handleNewProcess">
          <el-icon><Plus /></el-icon>新建流程
        </el-button>
      </div>

      <!-- 流程基础信息：标识与名称实时配置 -->
      <div class="toolbar-right">
        <el-input
          v-model="processMeta.key"
          placeholder="流程标识（如 labor_contract_approval）"
          style="width: 260px"
          @change="syncProcessMetaToXml"
        >
          <template #prepend>流程Key</template>
        </el-input>
        <el-input
          v-model="processMeta.name"
          placeholder="流程名称（如 劳务合同审批）"
          style="width: 240px"
          @change="syncProcessMetaToXml"
        >
          <template #prepend>流程名称</template>
        </el-input>
      </div>
    </div>

    <!-- BPMN 画布 + 右侧属性面板 -->
    <div class="designer-body">
      <div ref="canvasRef" class="designer-canvas"></div>
      <PropertiesPanel v-if="modelerInstance" :modeler="modelerInstance" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, shallowRef, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import { deployProcess } from '@/api/workflow'
import { flowableDescriptor } from './flowable-moddle'
import PropertiesPanel from './PropertiesPanel.vue'
import { translateModule } from './translate'

const canvasRef = ref<HTMLDivElement>()
let modeler: InstanceType<typeof BpmnModeler> | null = null
const modelerInstance = shallowRef<InstanceType<typeof BpmnModeler> | null>(null)

const deploying = ref(false)
const processMeta = reactive({
  key: 'Process_1',
  name: '新流程'
})

// 默认空白流程模板
const DEFAULT_XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  id="sid-38422fae-e03e-43a3-bef4-bd33b32041b2"
  targetNamespace="http://bpmn.io/bpmn"
  exporter="bpmn-js"
  exporterVersion="17.11.1">
  <process id="Process_1" name="新流程" isExecutable="true">
    <startEvent id="StartEvent_1" name="开始" />
    <userTask id="UserTask_1" name="审批" />
    <endEvent id="EndEvent_1" name="结束" />
    <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="UserTask_1" />
    <sequenceFlow id="Flow_2" sourceRef="UserTask_1" targetRef="EndEvent_1" />
  </process>
  <bpmndi:BPMNDiagram id="BpmnDiagram_1">
    <bpmndi:BPMNPlane id="BpmnPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <omgdc:Bounds x="180" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="UserTask_1_di" bpmnElement="UserTask_1">
        <omgdc:Bounds x="300" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <omgdc:Bounds x="482" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <omgdi:waypoint x="216" y="178" />
        <omgdi:waypoint x="300" y="178" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <omgdi:waypoint x="400" y="178" />
        <omgdi:waypoint x="482" y="178" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

onMounted(() => {
  initModeler()
})

onBeforeUnmount(() => {
  if (modeler) {
    modeler.destroy()
    modeler = null
    modelerInstance.value = null
  }
})

async function initModeler() {
  modeler = new BpmnModeler({
    container: canvasRef.value,
    additionalModules: [translateModule],
    moddleExtensions: { flowable: flowableDescriptor }
  })
  modelerInstance.value = modeler
  try {
    await modeler.importXML(DEFAULT_XML)
    readProcessMetaFromXml()
    const canvas: any = modeler.get('canvas')
    canvas.zoom('fit-viewport')
  } catch (err) {
    console.error('加载流程图失败', err)
    ElMessage.error('加载流程图失败，请刷新页面重试')
  }
}

/** 从画布中读取 <process id="..." name="..."> 同步到顶部输入框 */
function readProcessMetaFromXml() {
  if (!modeler) return
  try {
    const elementRegistry: any = modeler.get?.('elementRegistry')
    if (!elementRegistry || typeof elementRegistry.filter !== 'function') return
    const processes = elementRegistry.filter((e: any) => e.type === 'bpmn:Process')
    if (processes && processes.length > 0) {
      const p = processes[0].businessObject
      processMeta.key = p.get('id') || 'Process_1'
      processMeta.name = p.get('name') || '新流程'
    }
  } catch {
    // 忽略
  }
}

/** 将用户修改的流程标识与名称写回 BPMN 模型 */
function syncProcessMetaToXml() {
  if (!modeler) return
  try {
    const elementRegistry: any = modeler.get?.('elementRegistry')
    const modeling: any = modeler.get?.('modeling')
    if (!elementRegistry || typeof elementRegistry.filter !== 'function' || !modeling) return
    const processes = elementRegistry.filter((e: any) => e.type === 'bpmn:Process')
    if (processes && processes.length > 0) {
      modeling.updateProperties(processes[0], {
        id: processMeta.key || 'Process_1',
        name: processMeta.name || '新流程'
      })
    }
  } catch {
    // 忽略
  }
}

async function handleSave() {
  if (!modeler) return
  try {
    syncProcessMetaToXml()
    const { xml } = await modeler.saveXML({ format: true })
    const filename = `${processMeta.key || 'process'}.bpmn`
    const blob = new Blob([xml], { type: 'application/xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已保存为 process.bpmn')
  } catch (err) {
    ElMessage.error('导出失败')
  }
}

/** 点击顶部“部署到服务器”：提取流程元数据并直接提交 */
async function handleDeploy() {
  if (!modeler) return
  readProcessMetaFromXml()
  syncProcessMetaToXml()
  deploying.value = true
  try {
    const { xml } = await modeler.saveXML({ format: true })
    const filename = `${processMeta.key ? processMeta.key.trim() : 'process'}.bpmn20.xml`
    const file = new File([xml], filename, { type: 'application/xml' })
    const formData = new FormData()
    formData.append('file', file)
    formData.append('name', processMeta.name ? processMeta.name.trim() : '流程部署')

    await deployProcess(formData)
    ElMessage.success(`流程【${processMeta.name || '流程'}】部署成功（标识：${processMeta.key}）`)
  } catch (err) {
    ElMessage.error('部署失败')
  } finally {
    deploying.value = false
  }
}

async function handleImport(file: UploadFile) {
  if (!file.raw || !modeler) return
  const reader = new FileReader()
  reader.onload = async (e) => {
    const xml = e.target?.result as string
    if (xml && modeler) {
      try {
        await modeler.importXML(xml)
        readProcessMetaFromXml()
        const canvas: any = modeler.get('canvas')
        canvas.zoom('fit-viewport')
        ElMessage.success('导入成功')
      } catch (err) {
        ElMessage.error('导入的文件格式不正确')
      }
    }
  }
  reader.readAsText(file.raw)
}

async function handleNewProcess() {
  if (!modeler) return
  try {
    await modeler.importXML(DEFAULT_XML)
    readProcessMetaFromXml()
    const canvas: any = modeler.get('canvas')
    canvas.zoom('fit-viewport')
    ElMessage.success('已创建新流程')
  } catch (err) {
    ElMessage.error('创建新流程失败')
  }
}
</script>

<style scoped>
.designer-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
}

.designer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-md);
  padding: var(--zw-space-sm-md) var(--zw-space-md);
  border-bottom: 1px solid var(--zw-border-light);
  background: var(--zw-bg-card);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm-md);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm-md);
}

.designer-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.designer-canvas {
  flex: 1;
  overflow: hidden;
}

.deploy-hint {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--zw-text-secondary);
}
</style>
