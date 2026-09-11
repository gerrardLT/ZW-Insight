# 移动端机械台班现场上报功能设计文档 (Mobile Machine Work Log Design)

## 1. 背景与目标
目前移动端在机械设备域覆盖率为 0，现场工长在基坑/作业面无法实时上报机械（挖机、塔吊、泵车等）日常运转台班数、工作量和耗油量，全靠纸质台账在月末后补，容易造成结算争议与机械成本黑洞。
本功能旨在提供轻量、快捷、支持离线缓存的现场机械台班上报与历史查阅能力。

## 2. 架构设计与页面规划
### 2.1 页面路由
- `pages/machine/work-log/index`：机械台班记录列表（支持按项目、机械名称、日期筛选，支持下拉刷新与触底分页，右下角提供 FAB 新增按钮）
- `pages/machine/work-log/create`：新增台班填报表单（项目选择、机械设备选择、工作日期、台班数、完成工作量、耗油量、备注、离线入队支持）

### 2.2 接口契约 (对齐后端 MachineWorkLogController & MachineLedgerController)
- 设备台账分页：`GET /api/v1/machine/ledger/page`（参数：`page`, `size`, `machineName`, `machineType`）
- 台班日志分页：`GET /api/v1/machine/work-log/page`（参数：`page`, `size`, `projectId`, `machineId`, `machineName`, `workDate`）
- 保存台班日志：`POST /api/v1/machine/work-log`
  - 载荷字段：`projectId: number`, `machineId: number`, `workDate: string`, `shiftCount: number`, `workQuantity: number`, `oilConsumption: number`, `remark: string`

## 3. 异常态与边界处理 (product-design:edge)
- **空状态**：项目下暂无台班记录展示空态文案及快捷新增引导。
- **离线能力**：施工现场网络不佳时，接入 `submitOrQueue` 自动进入离线队列，联网后自动同步后端。
- **校验边界**：台班数必须大于 0 且不超过 3 个台班/天；耗油量与工程量必须为合法非负数。

## 4. 测试与验证策略
- 编写 `tests/pages/machine-work-log.test.ts`，涵盖页面初始化、字段校验、接口调用载荷、离线入队与空状态渲染。
- 执行 `npm run test` 确保 100% 绿灯。
