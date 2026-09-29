# Design：统一导航与信息架构

## 1. 架构
以静态 Vue Router 路由作为页面目录，以 `/v1/system/menu/user` 作为导航可见授权来源。新增纯函数导航层，把两者规范化为 `NavigationModel`，供布局、常用、最近访问与 Command Palette 共同消费。

```text
static routes ─┐
               ├─ buildAuthorizedNavigation ─ NavigationModel
user menus ────┘                              ├─ domain rail
                                              ├─ secondary panel
                                              ├─ favorites / recent
                                              └─ Ctrl+K commands
```

## 2. 数据结构
```ts
interface AuthorizedMenuDto {
  id: string | number
  parentId?: string | number | null
  menuName?: string
  menuType?: 'DIR' | 'MENU' | 'BUTTON'
  path?: string
  hidden?: boolean | number
  status?: boolean | number
  sortOrder?: number
}
interface NavItem {
  id: string
  path: string
  title: string
  icon?: string
  domainId: string
  domainTitle: string
  section: string
  keywords: string
}
interface NavDomain {
  id: string
  title: string
  icon: string
  sections: { title: string; items: NavItem[] }[]
}
```

## 3. 业务域映射
路径前缀与少量精确路径映射到 9 个业务域。映射仅改变展示归组，不改变 route、API 或权限。

- 工作台：`/dashboard`
- 经营分析：`/cockpit/*`、`/project-dashboard`、`/project-cost-control`、月度经营分析
- 项目经营：project、tender、contract、budget 的项目主线
- 成本供应链：purchase、subcontract、供应商与成本配置
- 履约现场：site、material、labor、machine
- 财资中心：finance
- 组织协同：hr、archive、message
- 配置中心：system、workflow、basedata 中配置类页面
- 平台运维：platform、备份、版本、日志；monitor 暂隐

同一路径只归属一个域；精确规则优先于前缀规则。

## 4. 交互
- 桌面：窄 rail 选择业务域，固定二级 panel 呈现常用、最近和按任务分组的页面。
- 桌面折叠：隐藏二级 panel，保留 rail；不使用位移裁切。
- 移动：rail + panel 置于单一 drawer；导航后关闭。
- 常用：二级项目尾部星标按钮；最多 8 项。
- 最近：成功路由切换后记录；动态详情不进入 canonical recent。
- 搜索：导航命令显示 `业务域 / 分组`，附加页面路径和别名关键词。

## 5. 状态与错误
`idle | loading | ready | error`。loading 和 error 均不显示静态回退菜单；error 显示原因与重试按钮。

## 6. 权限不变量
DIR 仅容器；MENU 必须明确授权。后端 Controller 继续是最终安全边界。旧 URL 不由此次前端导航模型强制拦截，避免破坏隐藏详情路由；导航可见性与直接 URL 授权差异另立安全治理任务。

## 7. 回滚
布局可回滚到旧 `menuRoutes` 渲染；纯导航模块和 localStorage 数据无后端副作用。旧路由和业务页面未删除，故回滚不需要数据迁移。
