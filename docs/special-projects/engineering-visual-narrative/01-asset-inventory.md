# 资产盘点与命名规范清单 (M0)

## 1. 资产命名约定
`zw-{domain}-{concept}-{variant}.{ext}`

业务域 (Domain)：
- `project`: 项目/WBS/里程碑
- `contract`: 施工合同/支出合同/变更
- `cost`: 成本五态/科目/下钻
- `workflow`: 审批流/驳回/委托
- `site`: 施工现场/机械/日志/检查
- `finance`: 发票/回款/付款/报销
- `evidence`: 水印照片/定位/存证状态
- `empty`: 蓝图风格空状态

## 2. 首批工程业务图标需求清单 (SVG 24x24 / 32x32)
1. `zw-project-wbs-node`
2. `zw-project-milestone`
3. `zw-cost-baseline`
4. `zw-cost-commitment`
5. `zw-cost-actual`
6. `zw-cost-forecast`
7. `zw-cost-variance`
8. `zw-contract-master`
9. `zw-contract-change`
10. `zw-workflow-approval-seal`
11. `zw-site-tower-crane`
12. `zw-site-safety-helmet`
13. `zw-evidence-local-watermarked`
14. `zw-evidence-server-verified`
15. `zw-evidence-hash-mismatch`

## 3. 蓝图空状态清单 (SVG 320x200)
1. `zw-empty-no-project`: 无项目数据
2. `zw-empty-no-budget`: 预算尚未编制
3. `zw-empty-no-cost-trend`: 暂无月度历史趋势能力
4. `zw-empty-offline-no-cache`: 离线且无本地缓存
5. `zw-empty-no-evidence`: 暂无现场影像凭证
6. `zw-empty-pending-sync`: 离线操作队列同步中/等待

## 4. 移动端 TabBar 资产清单 (PNG 81x81)
1. 首页：`home-normal.png` / `home-active.png`
2. 工作台：`workbench-normal.png` / `workbench-active.png`
3. 审批：`approval-normal.png` / `approval-active.png`
4. 我的：`mine-normal.png` / `mine-active.png`
