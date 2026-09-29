# Tasks：统一导航与信息架构

- [x] 1. 建立 baseline
  - [x] 运行现有 command palette、router guard 测试
  - [x] 记录前端 typecheck/build 状态

- [x] 2. 实现统一授权导航模型
  - [x] 定义菜单 DTO、NavItem、NavDomain
  - [x] 规范化相对/绝对路径
  - [x] 实现明确 MENU 授权、DIR 不扩权、hidden/status/BUTTON 过滤
  - [x] 实现 8 个业务域和任务分组映射
  - [x] 编写纯函数单元测试

- [x] 3. 实现导航状态与个性化
  - [x] loading/error/retry
  - [x] 常用入口及用户隔离持久化
  - [x] 最近访问及授权过滤

- [x] 4. 重构桌面布局
  - [x] 业务域 rail
  - [x] 固定二级 panel
  - [x] 当前域/页面自动激活
  - [x] 折叠状态与已有品牌视觉融合

- [x] 5. 增强 Ctrl+K
  - [x] 复用统一授权集合
  - [x] 增加域、分组、路径、别名检索
  - [x] focus trap、焦点恢复、活动项滚动可见

- [x] 6. 移动端与无障碍
  - [x] ≤992px drawer + backdrop
  - [x] Esc/route/backdrop 关闭
  - [x] button、aria-current、focus-visible

- [ ] 7. 验证
  - [x] unit tests、变更文件 typecheck、stylelint、build
  - [x] 一致性审计无新增 Critical
  - [x] Impeccable detector
  - [x] 桌面/移动真实登录结构与溢出检查（1440/768/375；截图已生成后按临时文件规范清理）
  - [x] 更新菜单处置表、影响范围、回滚说明
