# Requirements Document

## Introduction

本文档定义 ZW-Insight 工程项目管理平台 P2 第三批高级功能的需求规格，涵盖四大功能模块：打印模板管理、离线缓存与拍照水印、用户安全增强、数据运维。这些功能旨在提升系统的体验完整度、移动端可用性、安全防护能力和运维可管理性。

## Glossary

- **Print_Template_Service**: 后端打印模板管理服务，位于 zw-file 模块，负责模板 CRUD 和文档渲染
- **Template_Engine**: 模板渲染引擎（Thymeleaf 或 FreeMarker），将模板与业务数据合并生成 HTML
- **PDF_Converter**: PDF 转换组件（iText 或 wkhtmltopdf），将 HTML 文档转换为 PDF 格式
- **Offline_Cache_Manager**: 移动端离线缓存管理模块，负责数据缓存、过期检测和同步调度
- **Sync_Engine**: 联网后数据同步引擎，负责检测变更、处理冲突、上传离线操作
- **Watermark_Compositor**: 移动端拍照水印合成模块，使用 Canvas API 叠加水印信息
- **Password_Reset_Service**: 忘记密码服务，通过短信验证码完成密码重置
- **Device_Manager**: 登录设备管理服务，记录、查询和注销登录设备
- **Login_Location_Detector**: 异地登录检测服务，通过 IP 归属地判断登录位置变化
- **Secondary_Confirm_Interceptor**: 操作二次确认拦截器，拦截标注 @SecondaryConfirm 的高风险操作
- **Backup_Service**: 数据库备份服务，执行 mysqldump 并将备份文件存储到 MinIO
- **Version_Manager**: 系统版本管理服务，记录版本号和更新日志
- **Health_Monitor**: 系统健康监控服务，基于 Spring Boot Actuator + Micrometer 采集运行指标
- **Mobile_App**: ZW-Insight uni-app 移动端应用
- **PC_Frontend**: ZW-Insight Vue 3 + Element Plus PC 端前端应用

## Requirements

---

### Requirement 1: 打印模板 CRUD 管理

**User Story:** 作为系统管理员，我想管理各业务模块的打印模板（创建、编辑、删除、查询），以便为不同业务场景配置专属的打印格式。

#### Acceptance Criteria

1. WHEN 管理员提交创建请求（包含模板名称、关联业务类型、模板内容），THE Print_Template_Service SHALL 创建打印模板记录并返回模板 ID
2. WHEN 管理员提交编辑请求（包含模板 ID 和更新字段），THE Print_Template_Service SHALL 更新对应模板记录
3. WHEN 管理员提交删除请求（包含模板 ID），THE Print_Template_Service SHALL 逻辑删除对应模板记录
4. WHEN 管理员查询模板列表（可按业务类型筛选），THE Print_Template_Service SHALL 返回分页模板列表（包含模板名称、业务类型、创建时间、更新时间）
5. WHEN 管理员查询模板详情（包含模板 ID），THE Print_Template_Service SHALL 返回模板完整信息（包含模板内容）
6. IF 模板名称在同一业务类型下已存在，THEN THE Print_Template_Service SHALL 拒绝创建并返回重复名称错误
7. IF 请求的模板 ID 不存在，THEN THE Print_Template_Service SHALL 返回 404 资源未找到错误

---

### Requirement 2: 打印文档渲染

**User Story:** 作为业务用户，我想基于打印模板和业务数据生成打印文档，以便进行打印预览或导出 PDF。

#### Acceptance Criteria

1. WHEN 用户请求渲染文档（包含模板 ID 和业务数据 ID），THE Print_Template_Service SHALL 从对应业务模块获取数据，使用 Template_Engine 渲染为 HTML 文档
2. WHEN 用户请求 PDF 导出（包含模板 ID 和业务数据 ID），THE PDF_Converter SHALL 将渲染后的 HTML 转换为 PDF 文件并返回下载流
3. WHEN 用户请求打印预览，THE PC_Frontend SHALL 在新窗口中展示渲染后的 HTML 文档
4. IF 模板内容语法错误导致渲染失败，THEN THE Print_Template_Service SHALL 返回渲染错误详情（包含错误行号和描述）
5. IF 业务数据 ID 对应的记录不存在，THEN THE Print_Template_Service SHALL 返回业务数据未找到错误
6. THE Template_Engine SHALL 支持模板变量替换、条件判断和循环遍历语法

---

### Requirement 3: 打印模板前端管理页面

**User Story:** 作为系统管理员，我想在 PC 端管理打印模板，以便可视化配置和测试模板效果。

#### Acceptance Criteria

1. THE PC_Frontend SHALL 提供打印模板管理页面（列表展示、新增、编辑、删除操作）
2. WHEN 管理员编辑模板内容时，THE PC_Frontend SHALL 提供代码编辑器（支持 HTML 语法高亮）
3. WHEN 管理员点击"预览"按钮时，THE PC_Frontend SHALL 调用渲染接口并在新窗口展示预览效果
4. THE PC_Frontend SHALL 在各业务模块的列表/详情页增加"打印"按钮，点击后调用渲染并触发浏览器打印

---

### Requirement 4: 移动端离线数据缓存

**User Story:** 作为移动端用户，我想在无网络环境下仍能浏览关键业务数据，以便在施工现场等网络不稳定场景下正常工作。

#### Acceptance Criteria

1. WHEN Mobile_App 首次登录成功或用户手动触发同步时，THE Offline_Cache_Manager SHALL 将材料字典（最多 10000 条）、项目列表（最多 500 条）、当前用户信息缓存到本地存储，单次同步数据总量不超过 50MB
2. WHILE Mobile_App 处于离线状态，THE Offline_Cache_Manager SHALL 从本地缓存读取数据并在对应页面正常展示给用户，数据读取响应时间不超过 500 毫秒
3. WHEN Mobile_App 从离线状态恢复为在线状态，THE Sync_Engine SHALL 在 30 秒内比对本地缓存版本号与服务端最新版本号，对版本不一致的数据执行全量覆盖更新（以服务端数据为准）
4. THE Offline_Cache_Manager SHALL 为每条缓存数据记录版本号和缓存时间戳
5. WHEN 缓存数据超过配置的有效期（默认 7 天），THE Offline_Cache_Manager SHALL 标记数据为过期状态（expired = true），下次联网时在常规数据同步之前先刷新已过期数据
6. IF 本地存储已用空间超过配置的缓存上限（默认 100MB），THEN THE Offline_Cache_Manager SHALL 按照最近最少使用（LRU）策略逐条清除已过期缓存直至已用空间降至上限的 80% 以下
7. WHILE Mobile_App 处于离线状态，THE Mobile_App SHALL 在页面顶部展示"离线模式"提示条
8. IF 离线状态下本地缓存数据不存在或读取失败，THEN THE Offline_Cache_Manager SHALL 在对应页面展示空状态提示信息，指明当前无可用离线数据并建议用户联网后同步
9. IF Sync_Engine 同步请求失败（网络中断或服务端返回错误），THEN THE Sync_Engine SHALL 保留本地现有缓存数据不变，并在 5 分钟后自动重试，最多重试 3 次，3 次均失败后停止自动重试并通知用户同步失败

---

### Requirement 5: 离线操作同步与冲突处理

**User Story:** 作为移动端用户，我想在离线时执行的操作能在联网后自动同步到服务端，以便确保数据不丢失。

#### Acceptance Criteria

1. WHILE Mobile_App 处于离线状态，THE Offline_Cache_Manager SHALL 将用户的写操作（新增、编辑）记录到本地操作队列
2. WHEN Mobile_App 恢复在线状态，THE Sync_Engine SHALL 按操作时间顺序逐条提交本地操作队列中的请求
3. IF 同步过程中服务端返回数据冲突错误（版本号不匹配），THEN THE Sync_Engine SHALL 将该操作标记为冲突并通知用户手动处理
4. WHEN 同步完成后，THE Sync_Engine SHALL 清除已成功同步的本地操作记录
5. IF 同步过程中网络再次中断，THEN THE Sync_Engine SHALL 暂停同步并保留未提交的操作队列，待网络恢复后继续

---

### Requirement 6: 拍照水印合成

**User Story:** 作为现场人员，我想拍照时自动叠加水印信息（时间、位置、人员、项目），以便照片可作为施工日志、质量检查、安全检查的可信现场记录。

#### Acceptance Criteria

1. WHEN 用户在 Mobile_App 中使用拍照功能时，THE Watermark_Compositor SHALL 在照片底部区域（高度占照片总高度 10%~15%）合成水印信息
2. THE Watermark_Compositor SHALL 在水印中包含以下信息：拍照时间（yyyy-MM-dd HH:mm:ss 格式）、GPS 坐标（经度和纬度，保留 6 位小数）、当前登录用户姓名、当前选择的项目名称
3. THE Watermark_Compositor SHALL 使用半透明背景条（不低于 60% 不透明度）确保水印文字可读
4. WHEN 拍照完成后，THE Watermark_Compositor SHALL 使用 Canvas API 合成水印，合成耗时不超过 2 秒
5. IF GPS 定位失败，THEN THE Watermark_Compositor SHALL 在水印 GPS 位置显示"定位未获取"替代文字
6. IF 用户未选择项目，THEN THE Watermark_Compositor SHALL 阻止拍照操作并提示用户先选择项目
7. WHEN 水印合成完成后，THE Watermark_Compositor SHALL 返回带水印的图片数据（Base64 或文件路径），原始照片不保留
8. THE Watermark_Compositor SHALL 保持原始照片分辨率不变，输出图片质量不低于 90%，水印文字字号按照片宽度的 2.5% 计算（最小 12px，最大 36px）

---

### Requirement 7: 忘记密码与密码重置

**User Story:** 作为用户，我想通过手机号和短信验证码重置密码，以便在忘记密码时恢复账户访问。

#### Acceptance Criteria

1. WHEN 用户提交密码重置请求（包含手机号），THE Password_Reset_Service SHALL 调用 AliyunSmsService 发送 6 位数字验证码到该手机号
2. WHEN 用户提交验证码校验请求（包含手机号和验证码），THE Password_Reset_Service SHALL 调用 CaptchaService 校验验证码有效性
3. WHEN 验证码校验通过且用户提交新密码时，THE Password_Reset_Service SHALL 校验新密码满足长度 8-20 个字符且至少包含字母和数字两种字符类型，使用 BCrypt 加密新密码并更新用户密码
4. IF 手机号未绑定任何用户账户，THEN THE Password_Reset_Service SHALL 返回"手机号未注册"错误
5. IF 验证码已过期（超过 5 分钟）或校验失败，THEN THE Password_Reset_Service SHALL 返回"验证码无效或已过期"错误
6. IF 同一手机号在 60 秒内重复请求发送验证码，THEN THE Password_Reset_Service SHALL 拒绝发送并返回"请稍后再试"提示
7. WHEN 密码重置成功后，THE Password_Reset_Service SHALL 使该用户所有已登录 Token 失效
8. IF 同一手机号的验证码连续校验失败达到 5 次，THEN THE Password_Reset_Service SHALL 锁定该手机号的验证码校验功能 30 分钟，并返回错误信息指示校验次数超限
9. IF 用户提交的新密码不满足长度 8-20 个字符或未包含字母和数字两种字符类型，THEN THE Password_Reset_Service SHALL 拒绝重置并返回错误信息指示密码复杂度不符合要求
10. IF 同一手机号在 24 小时内验证码发送次数达到 10 次，THEN THE Password_Reset_Service SHALL 拒绝发送并返回错误信息指示当日发送次数已达上限

---

### Requirement 8: 登录设备管理

**User Story:** 作为用户，我想查看和管理自己的登录设备，以便发现异常登录时能远程注销可疑设备。

#### Acceptance Criteria

1. WHEN 用户成功登录时，THE Device_Manager SHALL 记录登录设备信息（设备 ID、设备名称、操作系统、IP 地址、登录时间）
2. WHEN 用户查询已登录设备列表时，THE Device_Manager SHALL 返回该用户所有活跃会话的设备信息列表
3. WHEN 用户请求注销指定设备（通过设备会话 ID）时，THE Device_Manager SHALL 使该设备对应的 Token 失效
4. THE Device_Manager SHALL 在设备列表中标识当前设备，禁止用户注销当前正在使用的设备
5. IF 用户的活跃设备数超过配置的最大值（默认 5 台），THEN THE Device_Manager SHALL 自动注销最早登录的设备
6. WHEN Token 被注销后，THE Device_Manager SHALL 将对应 Token 加入 Redis 黑名单，剩余有效期内的请求均被拒绝

---

### Requirement 9: 异地登录检测与提醒

**User Story:** 作为用户，我想在账户出现异地登录时收到提醒，以便及时发现账户安全风险。

#### Acceptance Criteria

1. WHEN 用户成功登录时，THE Login_Location_Detector SHALL 通过登录 IP 地址解析归属地（省份+城市）
2. WHEN 本次登录归属地与用户最近一次登录归属地不一致时，THE Login_Location_Detector SHALL 发送站内消息通知用户
3. THE Login_Location_Detector SHALL 在通知消息中包含：登录时间、登录 IP、归属地、设备信息
4. IF IP 归属地解析失败，THEN THE Login_Location_Detector SHALL 记录解析失败日志但不阻断登录流程
5. THE Login_Location_Detector SHALL 使用本地 IP 地址库（如 ip2region）进行归属地解析，避免依赖外部 API

---

### Requirement 10: 操作二次确认

**User Story:** 作为系统管理员，我想对高风险操作（删除、审批终止等）增加密码二次确认，以便防止误操作造成数据损失。

#### Acceptance Criteria

1. WHEN 请求到达标注了 @SecondaryConfirm 注解的 Controller 方法时，THE Secondary_Confirm_Interceptor SHALL 检查请求头 X-Confirm-Password 中是否包含非空的确认密码（空字符串及纯空白字符串视为未包含）
2. IF 请求头 X-Confirm-Password 缺失或其值为空/纯空白，THEN THE Secondary_Confirm_Interceptor SHALL 返回 HTTP 449（需要二次确认）状态码，不执行目标操作
3. WHEN 前端收到 449 状态码时，THE PC_Frontend SHALL 弹出密码输入对话框；IF 用户点击取消或关闭对话框，THEN THE PC_Frontend SHALL 终止该操作请求且不重新发起请求
4. WHEN 用户在密码输入对话框中输入密码并确认提交时，THE PC_Frontend SHALL 将密码置于请求头 X-Confirm-Password 中重新发起原请求
5. WHEN Secondary_Confirm_Interceptor 收到包含确认密码的请求时，THE Secondary_Confirm_Interceptor SHALL 校验密码是否与当前登录用户的密码匹配
6. IF 确认密码校验失败，THEN THE Secondary_Confirm_Interceptor SHALL 返回密码错误响应，不执行目标操作，并记录失败次数；IF 同一用户在 15 分钟内连续校验失败达到 5 次，THEN THE Secondary_Confirm_Interceptor SHALL 临时锁定该用户的二次确认能力 15 分钟，期间所有二次确认请求返回错误响应指示账户临时锁定
7. IF 确认密码校验通过，THEN THE Secondary_Confirm_Interceptor SHALL 重置该用户的连续失败次数并放行请求，执行目标方法

---

### Requirement 11: 数据库备份与恢复

**User Story:** 作为系统管理员，我想执行数据库备份和恢复操作，以便保障数据安全和灾难恢复能力。

#### Acceptance Criteria

1. WHEN 管理员触发手动备份时，IF 当前无备份任务正在执行，THEN THE Backup_Service SHALL 执行 mysqldump 全量备份并将备份文件（.sql.gz）上传到 MinIO；IF 已有备份任务正在执行，THEN THE Backup_Service SHALL 拒绝请求并返回提示信息说明有备份任务进行中
2. THE Backup_Service SHALL 支持定时自动备份，备份频率通过系统配置（cron 表达式）指定
3. WHEN 备份完成后，THE Backup_Service SHALL 记录备份元数据（备份 ID、文件名、文件大小、耗时、创建时间、存储路径）
4. WHEN 管理员查询备份记录列表时，THE Backup_Service SHALL 返回分页的备份记录列表，默认每页 20 条，每页最大 100 条，按创建时间降序排列
5. WHEN 管理员请求下载备份文件时，THE Backup_Service SHALL 从 MinIO 获取文件并返回下载流
6. WHEN 管理员请求删除备份记录时，THE Backup_Service SHALL 同时删除 MinIO 中的备份文件和数据库中的元数据记录
7. IF mysqldump 执行失败（进程返回非零退出码）或执行时间超过 3600 秒未完成，THEN THE Backup_Service SHALL 终止备份进程、清除本地临时文件、记录错误日志并返回备份失败错误信息
8. IF 备份过程中 MinIO 上传失败，THEN THE Backup_Service SHALL 清除本地临时文件并返回存储失败错误信息
9. WHEN 管理员选择一条备份记录并触发恢复操作时，THE Backup_Service SHALL 从 MinIO 下载对应备份文件，解压后执行 SQL 恢复到目标数据库，恢复完成后记录恢复操作日志（操作人、恢复时间、源备份 ID、恢复结果）
10. IF 恢复过程中 SQL 执行失败，THEN THE Backup_Service SHALL 终止恢复操作、记录错误日志并返回恢复失败错误信息（包含失败原因说明）
11. IF 删除操作中 MinIO 文件删除失败，THEN THE Backup_Service SHALL 保留元数据记录不变，返回错误信息指示存储文件删除失败

---

### Requirement 12: 系统版本管理

**User Story:** 作为系统管理员，我想管理系统版本记录和更新日志，以便用户了解系统迭代历史和新功能。

#### Acceptance Criteria

1. WHEN 管理员创建版本记录时（包含版本号、发布日期、更新日志），THE Version_Manager SHALL 保存版本记录
2. WHEN 用户查询版本列表时，THE Version_Manager SHALL 返回按发布日期降序排列的版本记录列表
3. WHEN 用户查询当前版本时，THE Version_Manager SHALL 返回最新发布的版本信息
4. IF 版本号格式不符合语义化版本规范（x.y.z），THEN THE Version_Manager SHALL 拒绝创建并返回格式错误
5. IF 版本号已存在，THEN THE Version_Manager SHALL 拒绝创建并返回版本号重复错误

---

### Requirement 13: 系统健康监控

**User Story:** 作为系统管理员，我想实时监控系统运行状态（CPU、内存、磁盘、数据库连接池），以便及时发现和处理系统性能问题。

#### Acceptance Criteria

1. THE Health_Monitor SHALL 通过 Spring Boot Actuator 暴露系统健康指标端点
2. THE Health_Monitor SHALL 采集以下指标：CPU 使用率、JVM 内存使用量与最大值、磁盘已用空间与总空间、数据库连接池活跃数与最大值、Redis 连接状态
3. WHEN PC_Frontend 访问监控仪表盘页面时，THE PC_Frontend SHALL 以图表形式展示各项指标的实时值和趋势
4. THE Health_Monitor SHALL 通过 Micrometer 注册自定义指标（在线用户数、请求吞吐量）
5. WHEN 任一健康指标超过预设阈值（如 CPU > 90%、内存 > 85%、磁盘 > 90%）时，THE Health_Monitor SHALL 记录告警日志
6. THE PC_Frontend SHALL 提供监控仪表盘页面，包含系统概览卡片和历史趋势折线图

---

### Requirement 14: 用户安全前端交互

**User Story:** 作为用户，我想在 PC 端和移动端完成密码重置、设备管理等安全操作，以便自主管理账户安全。

#### Acceptance Criteria

1. THE PC_Frontend SHALL 在登录页提供"忘记密码"链接，点击后展示手机号输入 + 验证码校验 + 新密码设置三步流程
2. THE Mobile_App SHALL 在登录页提供"忘记密码"入口，交互流程与 PC 端一致
3. THE PC_Frontend SHALL 在"个人中心"页面提供"登录设备"标签页，展示设备列表和远程注销按钮
4. WHEN 用户收到异地登录通知时，THE PC_Frontend SHALL 在消息中心高亮展示，并提供"查看设备"快捷链接
