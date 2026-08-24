// Flowable 扩展属性 moddle descriptor（bpmn-js moddleExtensions 用）
// 命名空间与仓库既有 BPMN 一致：purchase_contract_approval.bpmn20.xml 等
// 均使用 xmlns:flowable="http://flowable.org/bpmn"（后端为 Flowable 引擎）。
// 仅声明 UserTask 审批相关属性：assignee / candidateUsers / candidateGroups
export const flowableDescriptor = {
  name: 'Flowable',
  uri: 'http://flowable.org/bpmn',
  prefix: 'flowable',
  xml: {
    tagAlias: 'lowerCase'
  },
  types: [
    {
      name: 'UserTask',
      extends: ['bpmn:UserTask'],
      properties: [
        { name: 'assignee', type: 'String', isAttr: true },
        { name: 'candidateUsers', type: 'String', isAttr: true },
        { name: 'candidateGroups', type: 'String', isAttr: true }
      ]
    }
  ],
  enumerations: {},
  associations: {}
}

export default flowableDescriptor
