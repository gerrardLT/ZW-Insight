# Requirements Document

## Introduction

基于 ZW-Insight 工程项目管理系统功能表对比差异分析，当前系统 205 个功能点中整体完成率 63%。本需求文档覆盖 P0 优先级的 7 个核心缺失功能，这些功能直接影响核心业务流程完整性：工程量清单上传、目标成本变更、项目最终结算、验证码登录、质保金预警定时任务、预算控制配置页面、检查方案关联。

## Glossary

- **BOQ_Service**：工程量清单服务，负责清单文件的上传、解析、存储与查询
- **Budget_Change_Service**：目标成本变更服务，处理预算调整申请的创建、审批与生效
- **Settlement_Service**：项目最终结算服务，汇总项目全部合同收支并生成结算报告
- **Captcha_Service**：验证码服务，负责图形验证码和短信验证码的生成与校验
- **Retention_Warning_Task**：质保金预警定时任务，定期检查质保金到期时间并发送预警通知
- **Budget_Control_Config_Service**：预算控制配置服务，管理预算超支时的控制规则
- **Inspection_Scheme_Service**：检查方案关联服务，将质量/安全检查与预定义方案进行绑定
- **PC_Frontend**：Vue 3 + TypeScript 构建的 PC 端管理界面
- **Mobile_App**：基于 uni-app 构建的移动端应用
- **Workflow_Engine**：基于 Flowable 7.0 的审批流程引擎
- **File_Storage**：基于 MinIO 的对象存储服务
- **Message_Service**：消息通知服务，支持站内信、短信、企业微信等推送渠道

---

## Requirements

### Requirement 1: 工程量清单上传

**User Story:** 作为项目经理，我需要上传工程量清单（BOQ）文件并关联到施工合同，以便后续产值上报时按清单条目进行计量。

#### Acceptance Criteria

1. WHEN 用户在施工合同详情页面选择上传工程量清单文件时，IF 该施工合同处于"生效"或"变更中"状态，THEN THE BOQ_Service SHALL 接收 Excel 格式（.xlsx）文件（单文件不超过 20MB）并存储至 File_Storage
2. WHEN BOQ_Service 接收到上传文件后，THE BOQ_Service SHALL 在 60 秒内完成 Excel 内容解析，提取最多 5000 条清单条目（序号、项目编码、项目名称、单位、工程数量、综合单价、合价），超出 5000 条时返回校验失败信息
3. IF 上传文件格式不符合预定义模板结构，THEN THE BOQ_Service SHALL 返回包含具体错误行号和原因的校验失败信息（最多返回前 100 条错误）
4. WHEN 清单文件解析成功后，THE BOQ_Service SHALL 根据项目编码的层级编码规则（如 1、1.1、1.1.1 表示父子从属关系）将清单条目以父子层级结构（最多 4 级）保存并关联至对应施工合同
5. IF 该合同的清单条目已被产值上报引用（存在已完成工程量记录），THEN THE BOQ_Service SHALL 拒绝覆盖更新操作并返回提示信息，指明存在关联的产值上报记录
6. IF 该合同的清单条目未被产值上报引用，THEN THE BOQ_Service SHALL 允许覆盖更新操作，新上传的清单替换旧数据
7. WHEN 产值上报模块请求工程量清单数据时，THE BOQ_Service SHALL 返回该合同关联的全部清单条目及其已完成工程量
8. WHEN 清单条目保存成功后，THE BOQ_Service SHALL 自动计算清单合计金额（所有顶层条目合价之和，精确到小数点后 2 位）并回写至施工合同的合同总金额字段
9. IF 施工合同不处于"生效"或"变更中"状态，THEN THE BOQ_Service SHALL 拒绝上传操作并返回提示信息，指明当前合同状态不允许上传清单

#### 数据模型要点

- 清单条目表：`biz_boq_item`（id, contract_id, parent_id, item_code, item_name, unit, quantity, unit_price, total_price, completed_quantity, sort_order）
- 支持树形层级（分部分项工程 → 子目，最多 4 级）

---

### Requirement 2: 目标成本变更

**User Story:** 作为预算管理员，我需要对已审批通过的目标成本进行变更调整，以便在项目执行过程中应对工程变更导致的预算追加或调减。

#### Acceptance Criteria

1. WHEN 用户针对已审批通过的目标成本提交变更申请时，THE Budget_Change_Service SHALL 创建变更记录并关联原预算编制单据，变更申请须包含变更原因和至少一条变更科目明细
2. THE Budget_Change_Service SHALL 记录每个变更科目的原金额、调整金额（正值表示追加，负值表示调减）、调整后金额，其中调整后金额 = 原金额 + 调整金额
3. IF 变更科目的调整后金额小于该科目已占用预算金额（已签合同金额 + 已付无合同费用），THEN THE Budget_Change_Service SHALL 阻止提交并提示该科目预算余额不足以支撑调减
4. WHEN 变更申请提交后，THE Workflow_Engine SHALL 启动目标成本变更审批流程
5. WHEN 变更审批通过后，THE Budget_Change_Service SHALL 将调整金额累加至原预算对应科目的预算金额
6. WHEN 变更审批通过后，THE Budget_Change_Service SHALL 更新项目总预算金额为各科目变更后金额之和，并回写至项目信息的项目预算字段
7. IF 目标成本变更审批被驳回，THEN THE Budget_Change_Service SHALL 将变更单状态更新为"已驳回"且不修改原预算数据
8. IF 用户在审批流程中撤回变更申请，THEN THE Budget_Change_Service SHALL 将变更单状态更新为"已撤回"且不修改原预算数据
9. THE Budget_Change_Service SHALL 保留每条变更记录的变更原因、变更时间、变更人、各科目调整明细及审批结果，支持按项目查询全部变更轨迹

#### 数据模型要点

- 变更主表：`biz_budget_change`（id, project_id, budget_id, change_reason, total_adjust_amount, status, workflow_instance_id）
- 变更明细表：`biz_budget_change_detail`（id, change_id, subject_id, original_amount, adjust_amount, adjusted_amount）

---

### Requirement 3: 项目最终结算

**User Story:** 作为财务人员，我需要在项目竣工后进行最终结算，汇总项目全部合同收支数据并生成结算报告，以便完成项目财务关闭。

#### Acceptance Criteria

1. WHILE 项目状态为"已竣工"（COMPLETED）时，THE Settlement_Service SHALL 允许创建项目最终结算单，且同一项目仅允许存在一份状态为"草稿"或"审批中"的结算单
2. IF 项目状态非"已竣工"，THEN THE Settlement_Service SHALL 拒绝创建结算单并提示"项目未竣工，无法进行最终结算"
3. WHEN 创建结算单时，THE Settlement_Service SHALL 自动汇总以下数据：施工合同总额、累计产值、累计收款、累计开票、各分包/劳务/材料/机械合同结算总额、累计付款
4. THE Settlement_Service SHALL 计算项目最终利润（累计收入 - 累计支出）和利润率（利润 ÷ 累计收入 × 100%），金额精确到分（小数点后 2 位），利润率精确到小数点后 2 位
5. WHEN 结算单提交审批通过后，THE Settlement_Service SHALL 将项目状态从"已竣工"更新为"已关闭"（CLOSED）
6. IF 结算单审批被驳回，THEN THE Settlement_Service SHALL 将结算单状态更新为"已驳回"，项目状态保持"已竣工"不变，且允许财务人员修改后重新提交
7. IF 项目存在未结算完成（合同结算状态非"已完结"或已结算金额小于合同金额）的分包/劳务/材料/机械合同，THEN THE Settlement_Service SHALL 在结算单中标注未结清合同列表（包含合同编号、合同名称、未结金额）作为提醒
8. THE Settlement_Service SHALL 支持结算报告导出为 Excel 格式（.xlsx），导出内容包含收支汇总表和各合同结算明细

#### 数据模型要点

- 结算主表：`biz_project_settlement`（id, project_id, total_income, total_expenditure, profit, profit_rate, status, workflow_instance_id）
- 结算关联合同明细表：`biz_settlement_contract_detail`（id, settlement_id, contract_type, contract_id, contract_amount, settled_amount, paid_amount）

---

### Requirement 4: 验证码登录

**User Story:** 作为系统管理员，我需要在用户登录时增加验证码校验机制，以防止暴力破解和自动化攻击，保障系统登录安全。

#### Acceptance Criteria

1. WHEN 用户访问 PC 端登录页面时，THE Captcha_Service SHALL 生成包含 4 位字母数字混合字符的图形验证码图片，并返回 Base64 编码的图片内容和对应的验证码标识（UUID）
2. WHEN 用户提交 PC 端登录请求时，THE Captcha_Service SHALL 以大小写不敏感方式校验用户输入的验证码是否与生成的验证码匹配
3. IF 验证码校验失败，THEN THE Captcha_Service SHALL 返回错误提示信息指明验证码错误，同时自动生成新的图形验证码并返回新的 Base64 图片内容和新的验证码标识
4. THE Captcha_Service SHALL 将生成的验证码存储于 Redis 中，有效期为 5 分钟
5. WHEN 验证码被成功校验或超过有效期后，THE Captcha_Service SHALL 从 Redis 中删除该验证码记录，确保验证码仅可使用一次
6. WHEN 移动端用户选择短信验证码登录时，THE Captcha_Service SHALL 校验手机号为中国大陆 11 位手机号格式后，调用短信网关向该手机号发送 6 位数字验证码，短信验证码有效期为 5 分钟
7. THE Captcha_Service SHALL 对同一手机号的短信发送频率进行限制，60 秒内仅允许发送 1 次，每日累计不超过 10 次
8. IF 同一 IP 地址在 5 分钟内连续 5 次验证码校验失败，THEN THE Captcha_Service SHALL 临时锁定该 IP 地址 15 分钟内的登录请求，并返回错误提示信息指明该 IP 已被临时锁定及剩余锁定时长
9. IF 手机号格式校验不通过，THEN THE Captcha_Service SHALL 返回错误提示信息指明手机号格式无效，不发送短信
10. IF 短信发送频率超出限制（60 秒内重复请求或当日累计超过 10 次），THEN THE Captcha_Service SHALL 返回错误提示信息指明发送过于频繁，并告知用户需等待的剩余秒数或当日额度已用尽

#### 技术实现要点

- 图形验证码：使用 Hutool CaptchaUtil 生成
- 短信验证码：对接阿里云短信 SDK（AccessKey 配置化）
- Redis 存储：key 格式 `captcha:{uuid}` / `sms:{phone}`
- 频率限制：Redis INCR + EXPIRE 实现滑动窗口

---

### Requirement 5: 质保金预警定时任务

**User Story:** 作为财务人员，我需要系统在质保金即将到期时自动发送预警通知，以便及时跟进质保金退还事宜，避免逾期。

#### Acceptance Criteria

1. THE Retention_Warning_Task SHALL 每日固定时间（08:00）执行一次质保金到期检查任务
2. WHEN 定时任务执行时，THE Retention_Warning_Task SHALL 扫描所有状态为"未退还"的质保金记录，筛选出到期日在未来 30 天内的记录以及已超过到期日的记录
3. WHEN 检测到即将到期的质保金记录时，THE Retention_Warning_Task SHALL 通过 Message_Service 向项目负责人和财务人员发送站内信预警通知，通知内容包含：项目名称、合同名称、质保金金额、到期日期、预警级别
4. WHEN 定时任务检测到质保金距到期日剩余 30 天（含）至 8 天时，THE Retention_Warning_Task SHALL 发送"即将到期"级别通知；WHEN 检测到剩余 7 天（含）至 1 天时，THE Retention_Warning_Task SHALL 发送"紧急到期"级别通知
5. IF 质保金已超过到期日仍未退还，THEN THE Retention_Warning_Task SHALL 每 3 天发送一次"逾期未退还"催办通知，逾期超过 180 天后停止自动催办并标记为"长期逾期"状态
6. THE Retention_Warning_Task SHALL 以质保金ID与预警级别的组合作为去重标识，同一质保金在同一预警级别阶段内仅发送一次通知；当预警级别发生变更（如从"即将到期"升级为"紧急到期"）时，重新发送对应级别的通知
7. WHEN 质保金状态变更为"已退还"后，THE Retention_Warning_Task SHALL 停止对该笔质保金的预警通知并清除该笔质保金的通知去重记录
8. IF Message_Service 发送通知失败，THEN THE Retention_Warning_Task SHALL 记录发送失败日志并在下次任务执行时对失败记录进行重试，单条记录最多重试 3 次，3 次仍失败则标记为"通知发送失败"并跳过

#### 技术实现要点

- Spring `@Scheduled(cron = "0 0 8 * * ?")` 定时执行
- 通知去重：Redis Set 记录已发送的 `{retentionId}:{warningLevel}` 组合
- 通知渠道：站内信（必选）+ 短信/企微（按推送配置）

---

### Requirement 6: 预算控制配置页面

**User Story:** 作为系统管理员，我需要通过配置页面设定预算超支时的控制策略，以便不同项目可以根据实际情况灵活控制预算执行，替代当前硬编码的"禁止提交"逻辑。

#### Acceptance Criteria

1. THE Budget_Control_Config_Service SHALL 提供预算控制规则的 CRUD 管理接口，创建规则时须指定项目、控制模式和预警阈值，其中控制模式为必填项
2. THE PC_Frontend SHALL 提供预算控制配置页面，允许管理员查看、创建、编辑和删除控制规则，页面以列表形式展示全部已配置规则并支持按项目名称筛选
3. THE Budget_Control_Config_Service SHALL 支持以下三种控制模式：仅提醒（WARN_ONLY：允许提交但弹窗展示超预算警告信息，用户确认后方可继续提交）、禁止提交（BLOCK：阻止超预算单据提交并展示错误提示）、免控（EXEMPT：不做预算校验，直接放行）
4. THE Budget_Control_Config_Service SHALL 支持按项目维度配置不同的控制规则，未单独配置的项目使用系统默认规则（系统初始默认规则为 BLOCK 模式，预警阈值为 80%）
5. WHEN 业务单据（采购合同/劳务合同/机械合同/其他付款）提交时，THE Budget_Control_Config_Service SHALL 读取当前项目的控制规则，计算预算执行率（预算执行率 = 该预算科目已发生金额合计 ÷ 该科目预算金额 × 100%），并根据控制模式执行校验：BLOCK 模式下若执行率超过 100% 则阻止提交，WARN_ONLY 模式下若执行率超过 100% 则弹窗警告但允许确认提交，EXEMPT 模式下跳过校验
6. WHEN 预算执行率达到配置的预警阈值（取值范围 50% 至 99%，精度为整数百分比）时，THE Budget_Control_Config_Service SHALL 通过 Message_Service 向项目负责人发送站内信预警通知，提示当前科目预算执行率已达到预警线
7. WHEN 控制规则变更保存成功后，THE Budget_Control_Config_Service SHALL 对后续提交的业务单据按新规则校验（每次单据提交时实时查询当前生效的配置，不使用缓存）
8. IF 业务单据提交时 Budget_Control_Config_Service 查询配置失败或配置数据异常，THEN THE Budget_Control_Config_Service SHALL 按系统默认规则（BLOCK 模式）执行校验，并记录异常日志
9. IF 管理员尝试删除某项目的控制规则配置，THEN THE Budget_Control_Config_Service SHALL 删除该项目级配置，该项目后续按系统默认规则执行预算控制

#### 数据模型要点

- 配置表：`sys_budget_control_config`（id, project_id, control_mode, warning_threshold, is_default, created_by, created_at）
- control_mode 枚举：WARN_ONLY / BLOCK / EXEMPT

---

### Requirement 7: 检查方案关联

**User Story:** 作为现场管理人员，我需要在创建质量/安全检查记录时关联预定义的检查方案，以便检查时自动带出方案中的检查项和标准要求，提高检查规范性。

#### Acceptance Criteria

1. WHEN 用户创建质量检查或安全检查记录时，THE Inspection_Scheme_Service SHALL 提供检查方案选择功能，按检查类型（质量/安全）筛选状态为"已启用"的方案列表，每页显示不超过 50 条记录
2. WHEN 用户选择检查方案后，THE Inspection_Scheme_Service SHALL 自动将方案中的检查项目列表填充到检查记录的检查明细中，每个检查项包含：项目名称、检查标准、检查方法，并将方案内容以 JSON 格式保存为快照（scheme_snapshot 字段）
3. WHEN 用户选择检查方案后，THE Inspection_Scheme_Service SHALL 允许用户对已填充的检查项进行编辑（修改检查标准或删除不适用项），但不可新增方案中不存在的检查项
4. IF 用户在已关联方案的检查记录中重新选择其他方案，THEN THE Inspection_Scheme_Service SHALL 清除当前检查明细并重新填充新方案的检查项，同时更新方案快照
5. THE PC_Frontend SHALL 在检查详情页面展示关联方案的名称，并从方案快照中读取和显示完整检查项内容及每项的检查结果
6. THE Mobile_App SHALL 在移动端检查页面以逐项列表形式展示方案快照中的检查项，每个检查项支持标记检查结果（合格/不合格/未检查）
7. WHEN 检查方案内容在 basedata 模块中更新后，THE Inspection_Scheme_Service SHALL 确保已创建的检查记录保持创建时的方案内容快照不变，仅新创建的检查记录使用更新后的方案内容
8. IF 用户未选择检查方案，THEN THE Inspection_Scheme_Service SHALL 允许用户手动填写检查项（不超过 100 条），每条检查项至少包含项目名称（不超过 200 字符）和检查标准（不超过 500 字符）

#### 数据模型要点

- 检查记录增加字段：`scheme_id`（关联 basedata 检查方案）、`scheme_snapshot`（JSON 格式方案快照）
- 检查方案已存在于 basedata 模块：`biz_inspection_scheme`（含 scheme_items 子表）

---

## 依赖关系说明

| 功能 | 上游依赖 | 下游影响 |
|------|---------|---------|
| 工程量清单上传 | 施工合同模块、File_Storage | 产值上报模块 |
| 目标成本变更 | 预算编制模块、Workflow_Engine | 预算控制校验 |
| 项目最终结算 | 全部合同/财务模块 | 项目状态流转（→CLOSED）|
| 验证码登录 | Redis、短信网关 | 全部需登录功能 |
| 质保金预警 | 质保金模块、Message_Service | 站内信/短信通知 |
| 预算控制配置 | 无 | 采购/劳务/机械/付款提交校验 |
| 检查方案关联 | 基础数据-检查方案 | 质量检查/安全检查模块 |

## 非功能性约束

1. THE BOQ_Service SHALL 支持单次上传不超过 20MB 的 Excel 文件，清单条目不超过 5000 行，解析在 60 秒内完成
2. THE Captcha_Service SHALL 在 200ms 内完成验证码生成和返回
3. THE Retention_Warning_Task SHALL 在 5 分钟内完成全部质保金记录的扫描和通知发送
4. THE Settlement_Service SHALL 在 10 秒内完成项目收支数据的汇总计算
