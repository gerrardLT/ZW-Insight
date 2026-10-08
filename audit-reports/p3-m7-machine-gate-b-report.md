# P3-M7「机械管理」深度优化 门 B 验收报告

> 日期：2026-10-08
> 标的：`zw-machine`、`zw-labor`（BPMN 候选组修正）、`keys/` 测试基座
> 依据：[docs/deep-opt/07-machine.md](../docs/deep-opt/07-machine.md)（v1，门 A 已确认）

## 1. 结论

主环境 129.204.3.200 全部验收通过；徽颖 43.142.44.145 代码已随 CI 部署，但**未跑 L3/L4，且劳务/机械 BPMN 仍是旧版**，门 B 不能整体关闭，见第 4 节。

## 2. 验收证据（129 主环境）

| 项 | 结果 |
|---|---|
| CI（commit `0aa688e`） | Backend / 三端前端单测 / 双机部署全绿 |
| L3 `test-api-machine.sh` | 37 通过 / 0 失败 |
| L4 `lifecycle-sim-v2.sh`（租户 9999） | 26 个阶段 0 个失败，含合同审批→日志确认→结算审批→累计硬断言 |
| 零残留 `verify-l4-clean.sh` | biz_ 残留 0、Flowable 待办 0、测试基建保留 |
| R7 数据审计 | PASS=67 FAIL=0 WARN=0 INFO=39，基线未劣化 |

## 3. 本轮验证中发现并修复的问题

| # | 问题 | 根因 | 修复 |
|---|---|---|---|
| 1 | 劳务五类审批在 L4 报 403 | 5 个劳务 BPMN 的 userTask 用 `assignee=${initiator}`，而 `ApprovalService.assertTaskAssignee` 的防自审在超管放行之前执行，发起人永远无法办结，**真实用户同样会卡死** | 改为 `candidateGroups`（PROJECT_MANAGER / FINANCE_STAFF），与采购、机械流程一致 |
| 2 | 机械结算审批通过后累计回写 | 原 `selectById + updateById` 非原子 | 改用 `BizMachineContractMapper.addSettlement`，带租户、项目、生效、`0 ≤ 累计 ≤ 合同额` 条件，更新行数为 0 即抛异常回滚 |
| 3 | 结算按合同分组 | `TreeMap` + 合同 ID 为空时分组键冲突 | 改 `LinkedHashMap`，合同无 ID 时回退机械 ID |
| 4 | 项目删除级联清理 | 只认线程租户上下文，缺失即抛异常 | 优先取事件自带租户，缺失才回退上下文 |
| 5 | L4 与 L3 脚本落后于新校验 | 合同要求 `unitPrice`、合法 `rentalType`，且提交后须审批 | 补字段；L4 机械合同加 `approve`；新增独立审批人 `t9999approver`；部署清单补 `machine_contract_approval` |
| 6 | L4 预清理未生效 | `docker exec` 读 heredoc 缺 `-i` | 两处补 `-i`，僵尸待办不再串扰 |

## 4. 未关闭项

1. **徽颖服务器**：只读探查显示 `labor_*` 五个流程在租户 1 仍是 v1（`assignee=${initiator}`），`ACT_RU_TASK` 为 0。该服务器的劳务审批一旦发起就会卡死，需用徽颖应用账号补部署 5 个 BPMN，并跑 L3/L4。我没有该环境的应用登录凭证，也未对其数据库做任何写入。
2. 129 租户 1 的 5 个劳务流程已补部署到 v2；机械合同流程 v1 已是候选组，无需改。
3. 蓝图 D5（45 号 E2E 台账）仍按门 A 记录延后。

## 5. 回滚

代码：`git revert a01598c 81ad1ee 0aa688e e60e24d` 后重新部署。流程定义：Flowable 保留 v1，重新部署旧 BPMN 即生成新版本，无需删库。
