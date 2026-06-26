# Requirements Document

## Introduction

P2 优先级快速功能集合，包含 4 项体验增强功能：数据脱敏、甘特图前端接入、批量启用/停用用户、可视化编号规则管理。这些功能后端接口大多已就绪，主要工作集中在前端页面对接和新增 Jackson 序列化层的脱敏能力。

## Glossary

- **Desensitize_Service**：数据脱敏服务，基于自定义 `@Desensitize` 注解 + Jackson 序列化器，在 API 响应序列化阶段对敏感字段进行掩码处理
- **PC_Frontend**：Vue 3 + Element Plus + TypeScript 构建的 PC 端管理界面
- **User_Management_Page**：PC 端系统管理 → 人员管理页面（路由 `/system/user`）
- **Serial_Number_Page**：PC 端系统管理 → 编号规则管理页面（待新建）
- **Schedule_Page**：PC 端现场管理 → 进度计划页面（路由 `/site/schedule`，已引入 GanttChart 组件）
- **GanttChart_Component**：基于 dhtmlx-gantt 封装的甘特图 Vue 组件（`@/components/GanttChart.vue`）
- **SysUserController**：系统用户管理后端控制器，已提供 `PUT /v1/system/user/status` 批量状态更新接口
- **SerialNumberController**：编号规则后端控制器（`/api/v1/file/serial`），已提供完整 CRUD + 编号生成接口
- **SerialNumberRule**：编号规则实体，字段包含 businessType、rulePrefix、dateFormat、seqLength、resetPeriod、description

---

## Requirements

### Requirement 1: 数据脱敏

**User Story:** 作为系统管理员，我需要对 API 响应中的手机号、身份证号、银行卡号、邮箱、地址等敏感信息进行自动掩码处理，以保障数据安全合规。

#### Acceptance Criteria

1. THE Desensitize_Service SHALL 提供自定义注解 `@Desensitize`，支持标注在实体类的 String 类型字段上，注解参数 `type` 指定脱敏类型
2. THE Desensitize_Service SHALL 支持以下脱敏类型：PHONE（手机号）、ID_CARD（身份证号）、BANK_CARD（银行卡号）、EMAIL（邮箱）、ADDRESS（地址）
3. WHEN API 响应经过 Jackson 序列化时，THE Desensitize_Service SHALL 通过自定义 JsonSerializer 拦截标注了 `@Desensitize` 注解的字段，按脱敏类型执行掩码替换
4. WHEN 脱敏类型为 PHONE 时，THE Desensitize_Service SHALL 保留前 3 位和后 4 位，中间以 4 个星号替代（如 138****1234）
5. WHEN 脱敏类型为 ID_CARD 时，THE Desensitize_Service SHALL 保留前 3 位和后 4 位，中间以星号替代（如 110***********1234）
6. WHEN 脱敏类型为 BANK_CARD 时，THE Desensitize_Service SHALL 保留前 4 位和后 4 位，中间以星号替代（如 6222************1234）
7. WHEN 脱敏类型为 EMAIL 时，THE Desensitize_Service SHALL 保留邮箱用户名首字母和 @ 符号及后面的域名部分，用户名其余部分以星号替代（如 z***@example.com）
8. WHEN 脱敏类型为 ADDRESS 时，THE Desensitize_Service SHALL 保留省市区前 6 个字符，其余以星号替代（如 北京市朝阳区****）
9. IF 被脱敏字段值为 null 或空字符串，THEN THE Desensitize_Service SHALL 原样返回，不执行掩码处理
10. IF 被脱敏字段值长度不满足对应类型的最低掩码要求（如手机号少于 7 位），THEN THE Desensitize_Service SHALL 将全部字符替换为等长星号
11. THE Desensitize_Service SHALL 对所有 REST API 端点的响应统一生效，无需按端点逐一配置

---

### Requirement 2: 甘特图前端接入

**User Story:** 作为项目管理人员，我需要在进度计划页面查看和操作甘特图视图，以便直观掌握项目进度安排和任务之间的时间关系。

#### Acceptance Criteria

1. THE Schedule_Page SHALL 在进度计划页面中展示 GanttChart_Component 甘特图视图区域，且甘特图区域在页面加载时默认可见
2. WHEN 用户在进度计划页面选择项目后，THE Schedule_Page SHALL 调用后端进度计划树形数据接口（`GET /v1/site/schedule/plan?projectId={id}`）获取该项目的进度计划数据并渲染甘特图
3. THE GanttChart_Component SHALL 以横向时间轴展示每个进度计划任务的计划开始日期和计划结束日期，支持任务层级（父子关系）的折叠与展开
4. WHEN 用户在甘特图中拖拽任务条调整日期时，THE Schedule_Page SHALL 调用后端更新接口（`PUT /v1/site/schedule/plan/{id}`）保存修改后的计划开始日期和计划结束日期
5. THE PC_Frontend SHALL 确保进度计划页面（`/site/schedule`）在左侧导航菜单中已注册且路由可正常访问
6. IF 当前项目无进度计划数据，THEN THE GanttChart_Component SHALL 显示空状态提示"暂无进度计划数据"

---

### Requirement 3: 批量启用/停用用户

**User Story:** 作为系统管理员，我需要在人员管理列表中一次性选择多个用户并批量修改启用/停用状态，以提高用户状态管理效率。

#### Acceptance Criteria

1. THE User_Management_Page SHALL 在用户列表表格首列增加多选框（Checkbox），支持全选和单行选择
2. WHEN 用户选中一个或多个用户记录后，THE User_Management_Page SHALL 在表格上方工具栏显示"批量启用"和"批量停用"操作按钮
3. WHEN 用户未选中任何记录时，THE User_Management_Page SHALL 禁用（disabled 状态）"批量启用"和"批量停用"按钮
4. WHEN 用户点击"批量启用"按钮时，THE User_Management_Page SHALL 弹出确认对话框，显示"确认启用选中的 N 个用户？"（N 为选中数量）
5. WHEN 用户点击"批量停用"按钮时，THE User_Management_Page SHALL 弹出确认对话框，显示"确认停用选中的 N 个用户？"（N 为选中数量）
6. WHEN 用户确认批量操作后，THE User_Management_Page SHALL 调用后端接口 `PUT /v1/system/user/status`，请求体包含 `ids`（选中用户 ID 数组）和 `status`（1 表示启用，0 表示停用）
7. WHEN 批量操作接口返回成功后，THE User_Management_Page SHALL 刷新用户列表数据并显示操作成功提示消息
8. IF 批量操作接口返回失败，THEN THE User_Management_Page SHALL 显示错误提示消息，列表数据保持不变
9. THE User_Management_Page SHALL 在批量操作中排除当前登录用户自身，防止管理员将自己停用

---

### Requirement 4: 可视化编号规则管理

**User Story:** 作为系统管理员，我需要通过可视化页面管理自动编号规则（查看、新增、编辑、删除），以替代当前硬编码方式，支持灵活配置各业务模块的编号格式。

#### Acceptance Criteria

1. THE PC_Frontend SHALL 在系统管理菜单下新增"编号规则管理"菜单项，对应路由为 `/system/serial-number`
2. THE Serial_Number_Page SHALL 以表格形式展示所有编号规则记录，列包含：业务类型、规则前缀、日期格式、序号长度、重置周期、描述
3. THE Serial_Number_Page SHALL 在表格上方提供"新增"按钮，点击后弹出表单对话框
4. WHEN 用户点击"新增"或表格行的"编辑"按钮时，THE Serial_Number_Page SHALL 弹出表单对话框，包含以下字段：业务类型（必填，文本输入）、规则前缀（必填，文本输入）、日期格式（必填，下拉选择：yyyyMMdd / yyyyMM / yyyy）、序号长度（必填，数字输入，范围 1-10）、重置周期（必填，下拉选择：MONTH / YEAR）、描述（选填，文本输入）
5. WHEN 用户提交新增表单时，THE Serial_Number_Page SHALL 调用后端接口 `POST /api/v1/file/serial`，请求体为 SerialNumberRule 对象
6. WHEN 用户提交编辑表单时，THE Serial_Number_Page SHALL 调用后端接口 `PUT /api/v1/file/serial/{id}`，请求体为修改后的 SerialNumberRule 对象
7. WHEN 用户点击表格行的"删除"按钮时，THE Serial_Number_Page SHALL 弹出确认对话框，用户确认后调用后端接口 `DELETE /api/v1/file/serial/{id}`
8. WHEN 新增、编辑或删除操作成功后，THE Serial_Number_Page SHALL 刷新表格数据并显示操作成功提示消息
9. IF 后端接口返回失败，THEN THE Serial_Number_Page SHALL 显示错误提示消息，表单对话框保持打开状态不关闭
10. THE Serial_Number_Page SHALL 在表格中提供"预览"按钮，点击后调用 `POST /api/v1/file/serial/generate/{businessType}` 并以 toast 形式展示生成的编号示例
11. THE Serial_Number_Page SHALL 对表单进行前端校验：业务类型仅允许字母、数字和下划线且长度不超过 50 字符；规则前缀长度不超过 20 字符；序号长度为 1-10 的整数

---

## 依赖关系说明

| 功能 | 上游依赖 | 下游影响 |
|------|---------|---------|
| 数据脱敏 | Jackson 序列化框架 | 所有 REST API 响应中包含敏感字段的实体 |
| 甘特图前端接入 | GanttChart.vue 组件（已实现）、后端进度计划接口（已实现） | 进度计划页面交互完善 |
| 批量启用/停用用户 | SysUserController `PUT /status` 接口（已实现） | 人员管理页面交互增强 |
| 可视化编号规则管理 | SerialNumberController CRUD 接口（已实现） | 各业务模块自动编号配置化 |

## 非功能性约束

1. THE Desensitize_Service SHALL 在序列化阶段完成掩码处理，不影响原始数据库数据，且对单次 API 响应增加的处理耗时不超过 5ms
2. THE GanttChart_Component SHALL 支持同时渲染不超过 500 条进度计划任务，渲染完成时间不超过 3 秒
3. THE User_Management_Page SHALL 单次批量操作支持不超过 100 个用户 ID
4. THE Serial_Number_Page SHALL 在页面加载后 1 秒内完成编号规则列表渲染
