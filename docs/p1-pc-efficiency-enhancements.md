# P1: PC Web 效率增强 - 键盘导航 + 批量操作 + 上下文菜单

## 📋 概述

本次 P1 优先级修复针对 PC Web 端进行效率增强，添加了三大核心功能：
1. **键盘快捷键** - 全页面导航支持
2. **行选择 + 批量操作** - 数据表格高效管理
3. **右键上下文菜单** - 快速访问常用操作

## ✨ 已实现功能

### 1️⃣ 审批工作流 (approval/index.vue)

#### 键盘快捷键
- **Ctrl+Enter**: 提交审批/驳回表单（自动聚焦第一个输入框）
- **↑/↓ 方向键**: 表格行导航
- **空格键**: 选择/取消当前行
- **Enter**: 快速通过（选中时批量通过，未选时通过当前行）
- **Ctrl+A**: 全选当前页所有任务

#### 视觉反馈
- 高亮显示当前选中的行（浅蓝色背景 #ecf5ff）
- 焦点可见的边框样式（2px 主色调边框）
- 自动滚动到选中行视图内

### 2️⃣ 付款申请 (finance/payment-apply.vue)

#### 表单快捷键
- **Ctrl+Enter**: 快速提交付款申请表单
- **Tab**: Element Plus 原生字段切换

#### 表格导航
- **↑/↓ 方向键**: 在列表行间导航
- **Ctrl+A**: 全选（提示功能开发中）

#### 用户提示
- 表单底部显示快捷键提示条
- 按钮文字标注 "确定 (Ctrl+Enter)"

### 3️⃣ 个人报销 (finance/personal-reimbursement.vue)

#### 表单快捷键
- **Ctrl+Enter**: 快速提交报销表单
- **Tab**: 字段间自然切换

#### 用户体验
- 添加黄色提示栏显示快捷键信息
- 保持与原设计的一致性

### 4️⃣ 材料入库 (material/inbound.vue)

#### 批量操作
- ✅ **多选框**: 表格首列启用复选框
- ✅ **批量删除按钮**: 顶部工具栏显示"批量删除 (已选 X)"
- ✅ **键盘支持**: 
  - Ctrl+Enter 新建记录
  - Escape 关闭弹窗并清空选择

#### 右键上下文菜单
- 右击任意行弹出快捷菜单：
  - 📝 编辑
  - ✅ 提交（仅草稿状态）
  - 📄 复制
  - 🗑️ 删除（红色分隔）

#### 视觉增强
- 选中行高亮
- 工具栏间隙统一（gap: var(--zw-space-sm-md)）

---

## 🎯 设计规范

### 颜色方案
| 元素 | 颜色值 | 用途 |
|------|--------|------|
| 键盘聚焦行 | `#ecf5ff` | 浅蓝色背景高亮 |
| 焦点边框 | `var(--el-color-primary)` | 主色调轮廓 |
| 警告提示栏 | `var(--el-color-warning)` | 黄色边框左线 |

### 交互规则
1. **输入保护**: 所有快捷键均忽略正在输入的状态
2. **状态清理**: 切换 Tab 或刷新数据时重置行索引和选择
3. **防冲突**: 表单打开时禁用表格导航，防止误操作
4. **可访问性**: 使用 Focus Visible 模式确保键盘导航可见

---

## 🔧 技术实现细节

### 全局键盘事件处理模式
```typescript
function handleGlobalKeydown(event: KeyboardEvent) {
  // 1. 检查是否处于输入状态
  if (event.target instanceof HTMLInputElement || 
      event.target instanceof HTMLTextAreaElement || 
      event.target instanceof HTMLSelectElement) {
    return; // 忽略输入中的按键
  }

  // 2. 拦截组合键
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault();
    submitAction();
  }

  // 3. 导航逻辑
  if (event.key === 'ArrowDown') {
    navigateRow(1);
  } else if (event.key === 'ArrowUp') {
    navigateRow(-1);
  }
}
```

### 行高亮机制
```typescript
function highlightRow(index: number) {
  nextTick(() => {
    const rows = document.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row');
    rows.forEach((row, i) => {
      row.classList.toggle('keyboard-focused', i === index);
      if (i === index) {
        row.scrollIntoView({ block: 'nearest' }); // 滚动到可见区域
      }
    });
  });
}
```

### 上下文菜单位置计算
```typescript
function showContextMenu(event: MouseEvent, row: any) {
  event.preventDefault();
  contextMenuTarget.value = row;
  contextMenuPosition.value = { 
    x: event.clientX, 
    y: event.clientY 
  };
  showContextMenu.value = true;
}
```

---

## 📝 测试计划

### Manual Testing Checklist

#### 审批页面 (approval/index.vue)
- [ ] 待办 Tab 下按 ↑/↓ 能否正确导航行
- [ ] 空格键能否切换选中状态
- [ ] Ctrl+Enter 能否提交审批表单
- [ ] Enter 键能否在未选中时通过当前行
- [ ] 切换 Tab 后是否重置行索引和选择
- [ ] 对话框打开后焦点是否在第一个输入框

#### 付款申请 (payment-apply.vue)
- [ ] 表单中 Ctrl+Enter 能否提交
- [ ] Tab 键能否在字段间切换
- [ ] 表格导航 ↑/↓ 是否能正常工作
- [ ] 提示条是否正确显示快捷键说明

#### 个人报销 (personal-reimbursement.vue)
- [ ] Ctrl+Enter 能否提交表单
- [ ] Tab 切换是否正常

#### 材料入库 (inbound.vue)
- [ ] 复选框能否勾选
- [ ] 批量删除按钮是否显示选中数量
- [ ] 右键菜单能否正常弹出
- [ ] 右键菜单各项功能是否正常工作
- [ ] Ctrl+Enter 能否创建新记录
- [ ] Escape 能否关闭弹窗

### Automated Testing (Planned)

使用 Playwright/Puppeteer 编写以下测试：

```typescript
// example: approval navigation test
test('should navigate rows with arrow keys', async ({ page }) => {
  await page.goto('/approval/todo');
  
  // Navigate down
  await page.keyboard.press('ArrowDown');
  expect(await isRowFocused(0)).toBe(true);
  
  await page.keyboard.press('ArrowDown');
  expect(await isRowFocused(1)).toBe(true);
});

test('should select rows with space bar', async ({ page }) => {
  await page.goto('/approval/todo');
  
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press(' ');
  
  expect(await getSelectedCount()).toBe(1);
});
```

---

## 🚀 后续优化建议

1. **Cmd/Ctrl+K 命令面板** - 全局快捷键触发器
2. **批量操作扩展** - 批量提交、批量编辑等更多操作
3. **自定义快捷键配置** - 允许用户定义自己的快捷键映射
4. **触摸设备支持** - 添加长按上下文菜单等手势操作
5. **无障碍增强** - ARIA 标签完善，屏幕阅读器支持

---

## 📊 影响范围评估

| 文件 | 修改类型 | 新增功能 | 兼容性 |
|------|----------|----------|--------|
| workflow/approval/index.vue | Enhancement | 完整键盘导航系统 | ✅ 无破坏性变更 |
| finance/payment-apply.vue | Enhancement | 表单快捷键 + 表格导航 | ✅ 无破坏性变更 |
| finance/personal-reimbursement.vue | Enhancement | 表单快捷键 | ✅ 无破坏性变更 |
| material/inbound.vue | Enhancement | 批量操作 + 右键菜单 | ✅ 无破坏性变更 |

---

## 🔐 安全与隐私考量

- ✅ 所有快捷键均在客户端执行，不涉及敏感操作
- ✅ 批量操作均有二次确认提示
- ✅ 右键菜单权限受控（基于后端状态判断可用操作）
- ✅ 不影响原有的权限控制系统

---

## 📅 发布历史

- **2026-XX-XX**: V1.0 初始版本上线
  - 审批页面完整键盘支持
  - 付款申请表单快捷键
  - 材料入库批量操作 + 右键菜单

---

## 👥 贡献指南

如需改进此功能，请遵循以下规范：

1. **代码风格**: 保持现有命名约定（P1 Keyboard XXXXXX 注释）
2. **测试要求**: 必须包含手动测试验证
3. **文档更新**: 同步更新本文件和相应组件注释
4. **审查重点**: 确保无破坏性变更，保持向后兼容

---

**版本**: v1.0  
**作者**: AI Agent (Spec-driven Development)  
**审核**: Pending  
**最后更新**: 2026-XX-XX