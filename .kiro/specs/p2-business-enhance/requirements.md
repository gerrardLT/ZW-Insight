# Requirements Document

## Introduction

本文档定义 ZW-Insight 工程项目管理平台 P2 第二批功能增强的需求，涵盖三个功能模块：
1. **项目看板（独立项目维度）** — 在现有公司级看板基础上，新增按单项目聚合展示的数据看板页面
2. **财务封账与税率配置** — 支持按期间锁定财务单据、管理税率字典
3. **移动端快捷功能自定义编辑** — 用户可自定义编辑移动端首页快捷入口并持久化

## Glossary

- **Project_Dashboard_Service**: 项目维度数据看板后端服务，聚合单项目的预算、进度、合同、产值数据
- **Finance_Lock_Service**: 财务封账服务，管理封账期记录并拦截封账期内的写入操作
- **Tax_Rate_Service**: 税率配置服务，管理税率字典的增删改查
- **Shortcut_Service**: 用户快捷入口服务，管理用户的个性化快捷功能配置与排序
- **Project_Dashboard_Page**: PC 端项目看板页面组件
- **Shortcut_Edit_Page**: 移动端快捷入口编辑页面组件
- **Finance_Lock_Interceptor**: 封账拦截器，在财务模块写入操作前校验封账状态
- **Available_Shortcut**: 系统预定义的可选快捷功能项
- **User_Shortcut_Config**: 用户个性化快捷入口配置（包含已选项和排序）
- **Lock_Period**: 封账期，以年-月标识的会计期间
- **Tax_Rate_Dict**: 税率字典项，包含税率名称和税率数值

## Requirements

### Requirement 1: 项目看板数据聚合

**User Story:** As a 项目经理, I want to 在一个页面查看单项目的预算执行、进度完成率、合同金额与回款、产值上报汇总, so that I can 快速掌握项目整体运营状况并做出决策。

#### Acceptance Criteria

1. WHEN 用户请求指定项目的预算执行数据, THE Project_Dashboard_Service SHALL 返回该项目的预算总额、已使用金额（已审批付款累计）、使用率（已使用金额÷预算总额，保留4位小数，预算总额为0时返回0）、各科目执行明细（按一级成本科目分组，每组包含科目名称、预算金额、已付金额、占比）
2. WHEN 用户请求指定项目的进度完成率数据, THE Project_Dashboard_Service SHALL 返回该项目的总计划任务数、已完成任务数、完成百分比（已完成÷总计划，保留4位小数，总计划为0时返回0）
3. WHEN 用户请求指定项目的合同与回款数据, THE Project_Dashboard_Service SHALL 返回该项目的施工合同总额、累计开票金额、累计回款金额、回款率（累计回款÷施工合同总额，保留4位小数，合同总额为0时返回0）
4. WHEN 用户请求指定项目的产值上报汇总, THE Project_Dashboard_Service SHALL 返回该项目的累计上报产值、本月产值、最近12个月的各月产值趋势列表（每条包含月份和当月产值金额，按时间正序排列）
5. IF 用户请求的项目 ID 在系统中不存在, THEN THE Project_Dashboard_Service SHALL 返回 404 错误码和包含项目ID的错误提示信息
6. IF 用户无该项目的访问权限, THEN THE Project_Dashboard_Service SHALL 返回 403 错误码和权限不足的错误提示信息
7. IF 指定项目的某数据维度无记录（如无预算、无进度计划、无合同、无产值上报）, THEN THE Project_Dashboard_Service SHALL 对该维度返回零值和空列表，不影响其他维度数据的正常返回
8. THE Project_Dashboard_Service SHALL 在 2000 毫秒内完成单项目全部四个维度数据的聚合响应

### Requirement 2: 项目看板 PC 端页面

**User Story:** As a 项目经理, I want to 在 PC 端通过可视化图表查看单项目数据看板, so that I can 直观理解项目各维度的数据表现。

#### Acceptance Criteria

1. WHEN 用户导航到项目看板页面并通过 ProjectSelector 选择一个项目, THE Project_Dashboard_Page SHALL 显示加载状态，调用后端接口获取该项目的预算执行、项目进度、合同回款、月度产值四个维度的看板数据，并在数据返回后渲染对应图表
2. THE Project_Dashboard_Page SHALL 使用 ECharts 展示预算执行情况的饼图或柱状图，包含已执行预算金额与剩余预算金额的占比，金额精度为小数点后两位
3. THE Project_Dashboard_Page SHALL 使用进度条或环形图展示项目进度完成率，数值范围为 0%–100%，精度为整数百分比
4. THE Project_Dashboard_Page SHALL 使用柱状图展示合同金额与累计回款金额的对比数据，双柱并列显示，Y 轴单位为万元
5. THE Project_Dashboard_Page SHALL 使用折线图展示近 12 个月的月度产值上报趋势，X 轴为月份，Y 轴为产值金额（万元）
6. IF 后端接口返回错误（网络异常或非 2xx 响应）, THEN THE Project_Dashboard_Page SHALL 在对应图表区域展示错误提示信息，保留其他已成功加载的图表不受影响
7. IF 后端接口返回成功但某维度数据为空, THEN THE Project_Dashboard_Page SHALL 在对应图表区域展示"暂无数据"的空状态提示，而非渲染空白图表
8. WHEN 用户切换所选项目, THE Project_Dashboard_Page SHALL 清除当前图表数据，重新显示加载状态，并请求新项目的看板数据进行渲染
9. WHILE 页面处于展示状态, THE Project_Dashboard_Page SHALL 监听浏览器窗口尺寸变化并在 300ms 内完成图表自适应重绘

### Requirement 3: 财务封账期管理

**User Story:** As a 财务管理员, I want to 按月度或季度设定封账期, so that I can 防止已结账期间的财务数据被篡改。

#### Acceptance Criteria

1. WHEN 财务管理员创建一条封账记录（指定年月和封账类型：月度或季度）, THE Finance_Lock_Service SHALL 校验年月不晚于当前会计期间，将该期间标记为已封账状态，并记录操作人和操作时间；若封账类型为季度，则将该季度所含3个自然月全部标记为已封账
2. WHEN 封账记录创建成功, THE Finance_Lock_Service SHALL 返回封账记录的完整信息包括 ID、期间、封账类型、状态、操作人、操作时间
3. WHEN 财务管理员查询封账记录列表, THE Finance_Lock_Service SHALL 返回按期间倒序排列的封账记录分页数据，默认每页 20 条，单页最大 100 条
4. IF 同一期间已存在状态为"已封账"的记录, THEN THE Finance_Lock_Service SHALL 拒绝重复创建并返回错误信息指示该期间已封账
5. WHEN 管理员对已封账期间执行解封操作, THE Finance_Lock_Service SHALL 将该期间状态变更为已解封并记录解封操作人和时间；已解封的期间允许再次执行封账操作
6. IF 非财务管理员角色用户尝试执行封账创建或解封操作, THEN THE Finance_Lock_Service SHALL 拒绝操作并返回 403 权限不足错误
7. IF 创建封账时指定的年月晚于当前会计期间, THEN THE Finance_Lock_Service SHALL 拒绝操作并返回错误信息指示不可对未来期间封账

### Requirement 4: 封账拦截校验

**User Story:** As a 财务管理员, I want to 封账后自动阻止该期间的财务单据写入操作, so that I can 确保已封账期间数据的完整性和不可变性。

#### Acceptance Criteria

1. WHEN 用户尝试新增一条财务单据, IF 该单据业务日期所属年月与某条状态为"已封账"的 Lock_Period 的年月一致, THEN THE Finance_Lock_Interceptor SHALL 拒绝操作并返回错误信息指明该期间已封账且禁止新增，同时不对数据库产生任何写入
2. WHEN 用户尝试编辑一条财务单据, IF 该单据业务日期所属年月与某条状态为"已封账"的 Lock_Period 的年月一致, THEN THE Finance_Lock_Interceptor SHALL 拒绝操作并返回错误信息指明该期间已封账且禁止编辑，同时不对数据库产生任何写入
3. WHEN 用户尝试删除一条财务单据, IF 该单据业务日期所属年月与某条状态为"已封账"的 Lock_Period 的年月一致, THEN THE Finance_Lock_Interceptor SHALL 拒绝操作并返回错误信息指明该期间已封账且禁止删除，同时不对数据库产生任何写入
4. THE Finance_Lock_Interceptor SHALL 对以下财务模块的写入操作（新增、编辑、删除）执行封账校验：开票申请、回款登记、收票登记、付款申请、其他费用付款、项目报销、个人报销
5. WHEN 单据业务日期所属年月不匹配任何状态为"已封账"的 Lock_Period, THE Finance_Lock_Interceptor SHALL 允许操作正常执行且不增加额外延迟超过 200ms
6. WHEN 封账期间状态已变更为"已解封", THE Finance_Lock_Interceptor SHALL 允许该期间内的单据写入操作正常执行
7. IF 单据的业务日期字段为空, THEN THE Finance_Lock_Interceptor SHALL 拒绝操作并返回错误信息指明业务日期不可为空
8. IF 封账状态查询失败（缓存与数据库均不可用）, THEN THE Finance_Lock_Interceptor SHALL 拒绝当前写入操作并返回错误信息指明系统暂时无法校验封账状态，请稍后重试

### Requirement 5: 税率字典管理

**User Story:** As a 财务管理员, I want to 管理系统的税率字典, so that I can 统一税率配置供各业务表单引用。

#### Acceptance Criteria

1. WHEN 管理员新增税率配置（包含税率名称和税率数值）, THE Tax_Rate_Service SHALL 保存税率记录并返回该记录的完整信息（包含ID、税率名称、税率数值、启用状态、创建时间）
2. WHEN 管理员查询税率列表, THE Tax_Rate_Service SHALL 返回所有启用状态的税率记录列表，按创建时间升序排列
3. WHEN 管理员修改已有税率记录的名称或数值, THE Tax_Rate_Service SHALL 更新该记录并返回更新后的完整信息
4. WHEN 管理员删除税率记录, THE Tax_Rate_Service SHALL 将该税率标记为停用状态（逻辑删除），不物理删除数据
5. IF 新增或修改时税率名称与系统中已有记录（含停用状态）的名称重复, THEN THE Tax_Rate_Service SHALL 拒绝操作并返回错误信息指明税率名称已存在
6. IF 税率数值不在 0.01 至 99.99（含）范围内或超过 2 位小数, THEN THE Tax_Rate_Service SHALL 拒绝操作并返回错误信息指明税率数值不合法
7. IF 税率名称为空或长度超过 30 个字符, THEN THE Tax_Rate_Service SHALL 拒绝操作并返回错误信息指明税率名称不合法

### Requirement 6: 税率表单引用

**User Story:** As a 业务操作人员, I want to 在开票申请和收票登记表单中从预定义税率中选择, so that I can 减少手动输入错误并确保税率一致。

#### Acceptance Criteria

1. WHEN 用户打开开票申请表单, THE 表单 SHALL 提供税率下拉选择器（el-select filterable），数据来源为 Tax_Rate_Service 返回的启用状态税率列表，每个选项显示格式为"税率名称（数值%）"
2. WHEN 用户打开收票登记表单, THE 表单 SHALL 提供税率下拉选择器（el-select filterable），数据来源为 Tax_Rate_Service 返回的启用状态税率列表，每个选项显示格式为"税率名称（数值%）"
3. WHEN 用户选择一个预定义税率, THE 表单 SHALL 自动将对应税率数值（精度为2位小数）填入税率字段，替换该字段当前值
4. THE 表单 SHALL 允许用户手动输入自定义税率数值，取值范围为 0.00 至 100.00（精度2位小数），以支持预定义列表未覆盖的场景
5. IF Tax_Rate_Service 请求失败或返回空列表, THEN THE 表单 SHALL 显示提示信息告知用户税率列表不可用，并仍允许用户通过手动输入方式填写税率
6. WHEN 用户在已选择预定义税率后手动修改税率数值, THE 表单 SHALL 清除下拉选择器的选中状态，以税率字段中的手动输入值为准

### Requirement 7: 移动端快捷入口配置管理

**User Story:** As a 移动端用户, I want to 自定义首页的快捷功能入口, so that I can 将常用功能放在首页快速访问。

#### Acceptance Criteria

1. WHEN 用户请求可选快捷功能列表, THE Shortcut_Service SHALL 返回系统预定义的所有 Available_Shortcut 项（包含功能名称、图标、路由路径），列表上限不超过 50 项
2. WHEN 用户请求当前已选配置, THE Shortcut_Service SHALL 返回该用户的 User_Shortcut_Config（已选功能项及排序序号），按 sort_order 升序排列
3. WHEN 用户保存新的快捷入口配置（包含已选功能 ID 列表及排序）, THE Shortcut_Service SHALL 以整体替换方式持久化该用户的配置，已选功能数量须在 1 至 8 个之间（含边界值）
4. IF 用户未保存过个性化配置, THEN THE Shortcut_Service SHALL 返回系统默认的快捷入口列表（不超过 4 项）
5. THE Shortcut_Service SHALL 确保每个用户的快捷入口配置独立存储，互不影响
6. IF 保存的功能 ID 列表中包含无效的功能 ID, THEN THE Shortcut_Service SHALL 过滤无效项并只保存有效配置，且在响应中返回被过滤的无效功能 ID 列表
7. IF 保存的功能 ID 列表过滤无效项后为空, THEN THE Shortcut_Service SHALL 拒绝保存并返回错误信息指明无有效功能项可保存
8. IF 保存的已选功能数量超过 8 个, THEN THE Shortcut_Service SHALL 拒绝保存并返回错误信息指明超出数量上限

### Requirement 8: 移动端快捷入口编辑页面

**User Story:** As a 移动端用户, I want to 在编辑页面通过拖拽排序来安排快捷入口顺序, so that I can 按自己的使用习惯排列功能入口。

#### Acceptance Criteria

1. WHEN 用户进入快捷入口编辑页面, THE Shortcut_Edit_Page SHALL 展示两个区域：已选功能区和可选功能区
2. THE Shortcut_Edit_Page SHALL 在已选功能区展示用户当前已选的快捷入口（按排序顺序），已选数量最少 1 个、最多 8 个
3. THE Shortcut_Edit_Page SHALL 在可选功能区展示所有未被选中的可用功能
4. WHEN 用户在已选功能区长按并拖拽功能项, THE Shortcut_Edit_Page SHALL 在 300ms 内提供视觉反馈并更新排序顺序，拖拽释放后列表按新顺序排列
5. WHEN 用户点击可选功能区的功能项, IF 已选功能区数量未达到上限（8 个）, THEN THE Shortcut_Edit_Page SHALL 将该项添加到已选功能区末尾
6. IF 用户点击可选功能区的功能项且已选功能区已达到上限（8 个）, THEN THE Shortcut_Edit_Page SHALL 展示提示信息指明已达到最大可选数量，不执行添加操作
7. WHEN 用户点击已选功能区的功能项移除按钮, IF 移除后已选数量不低于下限（1 个）, THEN THE Shortcut_Edit_Page SHALL 将该项从已选区移至可选区
8. IF 用户点击已选功能区的功能项移除按钮且当前已选数量为 1 个, THEN THE Shortcut_Edit_Page SHALL 展示提示信息指明至少保留 1 个快捷入口，不执行移除操作
9. WHEN 用户点击保存按钮, THE Shortcut_Edit_Page SHALL 调用 Shortcut_Service 保存当前配置并返回首页
10. WHEN 保存接口返回成功, THE Shortcut_Edit_Page SHALL 展示保存成功提示并在 1.5 秒后自动返回首页
11. IF 保存接口返回失败或请求超时（超过 10 秒未响应）, THEN THE Shortcut_Edit_Page SHALL 展示保存失败提示信息，保留当前编辑页面状态不变，允许用户重新点击保存

### Requirement 9: 项目看板数据聚合的正确性保证

**User Story:** As a 开发人员, I want to 确保项目看板数据聚合逻辑的正确性, so that I can 信赖看板展示的数据准确无误。

#### Acceptance Criteria

1. FOR ALL 有效的项目 ID，THE Project_Dashboard_Service 返回的预算使用率 SHALL 等于已使用金额除以预算总额，采用 BigDecimal 运算并以 RoundingMode.HALF_UP 保留两位小数；IF 预算总额为 0，THEN THE Project_Dashboard_Service SHALL 返回预算使用率为 0
2. FOR ALL 有效的项目 ID，THE Project_Dashboard_Service 返回的回款率 SHALL 等于累计回款金额除以施工合同总额，采用 BigDecimal 运算并以 RoundingMode.HALF_UP 保留两位小数；IF 施工合同总额为 0，THEN THE Project_Dashboard_Service SHALL 返回回款率为 0（与需求 1.3 口径一致：回款率以施工合同总额为分母）
3. FOR ALL 有效的项目 ID，THE Project_Dashboard_Service 返回的完成百分比 SHALL 等于已完成任务数除以总计划任务数，采用 BigDecimal 运算并以 RoundingMode.HALF_UP 保留两位小数；IF 总计划任务数为 0，THEN THE Project_Dashboard_Service SHALL 返回完成百分比为 0
4. FOR ALL 有效的项目 ID，THE Project_Dashboard_Service 返回的月度产值趋势列表 SHALL 按月份升序排列；IF 该项目无任何产值上报记录，THEN THE Project_Dashboard_Service SHALL 返回空列表
5. FOR ALL 有效的项目 ID，THE Project_Dashboard_Service 返回的预算使用率、回款率、完成百分比 SHALL 为大于等于 0 的数值（不设上限，允许超过 100% 以反映超预算或超额回款的真实情况），且结果数据类型为 BigDecimal

### Requirement 10: 封账拦截的正确性保证

**User Story:** As a 开发人员, I want to 确保封账拦截逻辑的正确性, so that I can 保证封账机制可靠运行。

#### Acceptance Criteria

1. WHEN Finance_Lock_Interceptor 校验一张财务单据, IF 该单据业务日期的年月部分与任一已封账期间（格式 YYYY-MM）相匹配, THEN THE Finance_Lock_Interceptor SHALL 返回校验失败标识并阻止该单据的创建或修改操作继续执行
2. WHEN Finance_Lock_Interceptor 校验一张财务单据, IF 该单据业务日期的年月部分不与任何已封账期间相匹配, THEN THE Finance_Lock_Interceptor SHALL 返回校验通过标识并允许该单据操作继续执行
3. WHEN 某期间已被解封后 Finance_Lock_Interceptor 校验业务日期落在该期间的财务单据, THE Finance_Lock_Interceptor SHALL 返回校验通过标识，与该期间从未封账时的校验结果一致
4. WHEN Finance_Lock_Service 对同一期间（相同 YYYY-MM）执行多次封账操作, THE Finance_Lock_Service SHALL 仅保留一条该期间的封账状态记录，不产生重复记录
5. IF Finance_Lock_Interceptor 接收到业务日期为空或格式不符合日期规范的财务单据, THEN THE Finance_Lock_Interceptor SHALL 返回校验失败标识并给出错误提示信息，指明业务日期无效

### Requirement 11: 快捷入口配置的正确性保证

**User Story:** As a 开发人员, I want to 确保快捷入口配置的持久化逻辑正确, so that I can 保证用户配置不会丢失或错乱。

#### Acceptance Criteria

1. FOR ALL 有效的快捷入口配置（功能 ID 列表长度为 1 至 20 个、各 ID 互不重复且均存在于 Available_Shortcut 集合中），WHEN 调用保存接口后再次查询, THE Shortcut_Service SHALL 返回与保存时相同的功能 ID 列表（顺序一致）及对应的 sortOrder 值序列
2. FOR ALL 用户 A 的配置保存操作，THE Shortcut_Service SHALL 保证用户 B 在操作前后通过查询接口获得的功能 ID 列表和排序完全不变
3. FOR ALL 包含 N 个有效功能 ID（N 范围为 1 至 20）的保存请求，WHEN 保存完成后查询, THE Shortcut_Service 返回的功能项数量 SHALL 等于 N
4. IF 保存请求的功能 ID 列表为空（N=0），THEN THE Shortcut_Service SHALL 清空该用户的快捷入口配置，后续查询返回空列表
5. IF 保存请求的功能 ID 列表中包含重复 ID，THEN THE Shortcut_Service SHALL 对重复项去重后仅保留首次出现的顺序位置，保存后查询结果的功能项数量等于去重后的数量
