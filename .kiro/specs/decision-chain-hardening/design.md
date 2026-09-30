# 三条决策链加固设计

## 付款审批
复用ApprovalController GET /detail/{taskId}与PaymentApplyController源单详情；抽屉按loading/error/ready/done表达，源单及项目获取成功后才能处理付款。快照标注提交时点，不冒充当前余额；历史使用聚合端点已有approvalRecords。前端不是权限边界，仍以源单权限及Flowable任务守卫为准。请求版本防旧响应覆盖，表格快捷键仅焦点在表格时处理，勾选同步Element Plus。

## 成本变更
zw-contract所属mapper最小只读CBS归属查询，避免反向Service依赖；评估提交/批准双校验。zw-budget消费者先校验完整负载再进入postBatch；复用已有source幂等键，不增加新事件或账本。金额拒绝实际超过分精度、不静默舍入，重复账户不可因幂等键碰撞丢失。

## 风险复发
复用现有riskCode、handleStatus与handleNote，不新增表/列；再次命中RESOLVED重开，追加原记录与复发事实。保持IGNORED静音策略、升级重开、成功规则集合关闭机制。

## 风险与回滚
改变前先读取并保护既有未提交工作。回滚只撤销本次相关差异；无迁移与生产数据变更。旧的不一致变更在批准/消费时将被拒绝，需业务修正明细后重试，不能以消费者继续记账规避。

## 验证边界
单元测试可隔离外部依赖，但不能作为真实UI验收。禁止运行默认生产写入E2E；界面需本地代码与授权只读真实接口，凭证不可打印。环境受阻按项目台账记录与用户决策。