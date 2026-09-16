# M2 工程视觉底座实施记录

## 实施范围
- 双端业务视觉编码（代码 + 颜色 + 语义）：`zw-insight-web/src/constants/business-visual.ts` 与 `zw-insight-app/src/constants/businessVisual.ts`
- 双端工业蓝图空状态组件：`zw-insight-web/src/components/visual/ZwBlueprintEmpty.vue` 与 `zw-insight-app/src/components/visual/ZwBlueprintEmpty.vue`
- PC 端核心工程与凭证 SVG 图标：`CostFiveStateIcon.vue`、`EvidenceIcon.vue`、`WbsIcon.vue`，导出至 `zw-insight-web/src/components/icons/zw/index.ts`
- 移动端规范 81x81 PNG 双态导航图标：`home.png`, `home-active.png`, `workbench.png`, `workbench-active.png`, `approval.png`, `approval-active.png`, `mine.png`, `mine-active.png`，并配置入 `zw-insight-app/src/pages.json`

## 验证结果
- 移动端单元测试全通：25 个测试套件，177 个用例全部通过。
- PC 端工作流与平台组件测试全通：19 个测试用例全部通过。
- 修复了 `workflow/approval/index.vue` 遗留的重复闭合 `</script>` 标签语法错误。
