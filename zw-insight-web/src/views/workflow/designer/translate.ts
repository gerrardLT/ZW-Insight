// bpmn-js 界面文案汉化（键取自 bpmn-js 17.11.1 源码 translate('...') 与 ReplaceOptions）
// ponytail: 仅覆盖常用词条，未收录的词条回退英文；新增词条在此追加即可
const zh: Record<string, string> = {
  // 左侧工具板
  'Activate hand tool': '抓手工具（拖动画布）',
  'Activate lasso tool': '框选工具',
  'Activate create/remove space tool': '调整间距工具',
  'Activate global connect tool': '连线工具',
  'Create start event': '创建开始事件',
  'Create end event': '创建结束事件',
  'Create gateway': '创建网关',
  'Create task': '创建任务',
  'Create expanded sub-process': '创建子流程',
  'Create data object reference': '创建数据对象',
  'Create data store reference': '创建数据存储',
  'Create pool/participant': '创建泳池/参与者',
  'Create group': '创建分组',
  'Create intermediate/boundary event': '创建中间/边界事件',
  // 节点快捷菜单
  'Append end event': '追加结束事件',
  'Append gateway': '追加网关',
  'Append task': '追加任务',
  'Append receive task': '追加接收任务',
  'Append intermediate/boundary event': '追加中间/边界事件',
  'Add text annotation': '添加文本注释',
  'Change element': '更改类型',
  'Connect to other element': '连线到其他节点',
  'Connect using association': '使用关联连线',
  'Delete': '删除',
  // 更改类型菜单
  'Start event': '开始事件',
  'End event': '结束事件',
  'Task': '任务',
  'User task': '用户任务',
  'Service task': '服务任务',
  'Script task': '脚本任务',
  'Manual task': '手工任务',
  'Send task': '发送任务',
  'Receive task': '接收任务',
  'Business rule task': '业务规则任务',
  'Call activity': '调用活动',
  'Sub-process': '子流程',
  'Sub-process (collapsed)': '子流程（折叠）',
  'Sub-process (expanded)': '子流程（展开）',
  'Exclusive gateway': '排他网关（条件分支，只走一条）',
  'Parallel gateway': '并行网关（同时走多条）',
  'Inclusive gateway': '包容网关（满足条件的都走）',
  'Event-based gateway': '事件网关',
  'Complex gateway': '复杂网关',
  'Sequence flow': '顺序流',
  'Default flow': '默认流',
  'Conditional flow': '条件流',
  'Intermediate throw event': '中间抛出事件',
  'Timer intermediate catch event': '定时中间捕获事件',
  'Message intermediate catch event': '消息中间捕获事件',
  'Terminate end event': '终止结束事件',
  'Error end event': '错误结束事件'
}

export function customTranslate(template: string, replacements: Record<string, string> = {}) {
  return (zh[template] || template).replace(/{([^}]+)}/g, (_, k) => replacements[k] || `{${k}}`)
}

export const translateModule = { translate: ['value', customTranslate] }
