# Implementation Plan: P2 Advanced Features

## Overview

基于 P2 高级功能需求和设计文档，将实现分为四大功能模块：打印模板管理、离线缓存与拍照水印、用户安全增强、数据运维。每个模块遵循"数据层 → 后端服务 → 前端页面"的顺序，属性测试紧跟对应实现。

## Tasks

- [x] 1. 打印模板管理 — 后端实现
  - [x] 1.1 创建数据库表结构和实体类
    - 执行 `ALTER TABLE sys_template` 新增 `engine_type`、`business_type`、`data_query_config` 字段
    - 在 `zw-file` 模块创建/更新 `SysTemplate` 实体类，新增上述字段映射
    - 创建 `PrintTemplateMapper.java` 和 `PrintTemplateMapper.xml`（MyBatis-Plus）
    - 输入：设计文档 Data Models - sys_print_template
    - 输出：`zw-file/src/main/resources/db/migration/V2__print_template_fields.sql`、`SysTemplate.java`、`PrintTemplateMapper.java`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 1.2 实现 ThymeleafRenderService 模板渲染服务
    - 在 `zw-file` 模块创建 `ThymeleafRenderService.java`
    - 使用 Spring Boot 自带 `TemplateEngine` 实现 `render(templateContent, variables)` 方法
    - 支持 `th:text`、`th:each`、`th:if` 等语法的字符串模板渲染
    - 捕获 Thymeleaf 解析异常并返回包含行号和描述的错误信息
    - 输入：Thymeleaf 官方 API
    - 输出：`zw-file/src/main/java/.../service/ThymeleafRenderService.java`
    - _Requirements: 2.1, 2.4, 2.6_

  - [x] 1.3 实现 PdfConvertService PDF 转换服务
    - 在 `zw-file` 模块创建 `PdfConvertService.java`
    - 使用 `ProcessBuilder` 调用 wkhtmltopdf，HTML 写入临时文件 → 转换 → 读取 PDF 字节数组
    - 处理进程超时和异常退出码
    - 配置项：`print.wkhtmltopdf.path`（默认 `/usr/local/bin/wkhtmltopdf`）
    - 输出：`zw-file/src/main/java/.../service/PdfConvertService.java`
    - _Requirements: 2.2_

  - [x] 1.4 实现 PrintTemplateController REST 接口
    - 创建 `PrintTemplateController.java`，映射 `/api/print-template`
    - 实现接口：POST `/`、PUT `/{id}`、DELETE `/{id}`、GET `/list`、GET `/{id}`、POST `/render`、POST `/export-pdf`
    - 复用 `PrintTemplateService`（CRUD） + `ThymeleafRenderService`（渲染） + `PdfConvertService`（PDF）
    - 实现模板名称 + 业务类型唯一性校验
    - 输出：`zw-file/src/main/java/.../controller/PrintTemplateController.java`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.1, 2.2, 2.4, 2.5_

  - [x]* 1.5 编写打印模板后端属性测试（jqwik）
    - **Property 1: 打印模板 CRUD 往返一致性** — 随机 name/type/content 创建后查询详情字段一致
    - **Property 2: 模板业务类型过滤正确性** — 随机 templates + filter 只返回匹配的未删除模板
    - **Property 3: 模板名称唯一性约束** — 同 name+type 创建第二次应报 409
    - **Property 4: Thymeleaf 模板渲染变量替换** — 随机变量 Map + 模板，输出不含未解析 th:表达式
    - **Property 5: 无效模板语法返回错误详情** — 随机破损 HTML 渲染应返回包含 error 信息的响应
    - **Validates: Requirements 1.1, 1.3, 1.4, 1.5, 1.6, 2.1, 2.4, 2.6**
    - 输出：`zw-file/src/test/java/.../PrintTemplatePropertyTest.java`

- [x] 2. 打印模板管理 — PC 前端实现
  - [x] 2.1 实现打印模板管理页面
    - 在 `zw-insight-web/src/views/system/` 下创建 `print-template/index.vue`
    - 使用 Element Plus Table 实现列表展示（模板名称、业务类型、创建时间、操作列）
    - 实现新增/编辑对话框（集成 CodeMirror 6 做 HTML 语法高亮编辑器）
    - 实现逻辑删除确认
    - 输入：`/api/print-template/*` 接口
    - 输出：`zw-insight-web/src/views/system/print-template/index.vue`
    - _Requirements: 3.1, 3.2_

  - [x] 2.2 实现打印预览和业务模块打印按钮
    - 在模板管理页添加"预览"按钮，调用 `/api/print-template/render` 后新窗口展示 HTML
    - 在各业务列表/详情页（合同、预算、材料等）添加"打印"按钮组件
    - 打印按钮调用渲染接口 + `window.print()`
    - 输出：`zw-insight-web/src/components/PrintButton.vue`、各业务页面修改
    - _Requirements: 3.3, 3.4, 2.3_

  - [x] 2.3 添加打印模板相关路由和菜单配置
    - 在路由配置中添加 `/system/print-template` 路由
    - 在 `data-menu.sql` 中添加对应菜单和权限配置
    - 输出：路由文件、菜单 SQL
    - _Requirements: 3.1_

- [x] 3. Checkpoint — 打印模板功能完成验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. 离线缓存与拍照水印 — 移动端实现
  - [x] 4.1 实现 OfflineCacheManager 离线缓存管理器
    - 在 `zw-insight-app/src/utils/` 下创建 `offlineCache.ts`
    - 实现 `CacheEntry` 接口和 `CacheMeta` 结构
    - 实现 `sync()`（首次登录/手动同步）、`get()`、`set()`、`getUsedSize()`
    - 实现 `markExpired()`（7天过期标记）和 `evictLRU()`（淘汰至80%）
    - 使用 `uni.setStorageSync` / `uni.getStorageSync` 操作本地存储
    - 输出：`zw-insight-app/src/utils/offlineCache.ts`
    - _Requirements: 4.1, 4.2, 4.4, 4.5, 4.6_

  - [x] 4.2 实现 SyncEngine 同步引擎
    - 在 `zw-insight-app/src/utils/` 下创建 `syncEngine.ts`
    - 实现 `OfflineOperation` 接口和操作队列管理
    - 实现 `enqueue()`、`syncAll()`（按时间顺序提交）、`handleConflict()`
    - 实现 `compareVersions()`（版本号比对 + 全量覆盖）
    - 实现重试逻辑（5分钟间隔，最多3次）
    - 输出：`zw-insight-app/src/utils/syncEngine.ts`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 4.3, 4.9_

  - [x] 4.3 实现 WatermarkCompositor 水印合成器
    - 在 `zw-insight-app/src/utils/` 下创建 `watermarkCompositor.ts`
    - 使用 uni-app Canvas API 实现水印合成
    - 实现半透明背景条（60%+ 不透明度）+ 四行文字信息
    - 字号计算：`clamp(width * 0.025, 12, 36)`
    - GPS 失败时显示"定位未获取"；未选择项目时阻止拍照
    - 输出质量 ≥ 90%，保持原始分辨率
    - 输出：`zw-insight-app/src/utils/watermarkCompositor.ts`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8_

  - [x] 4.4 集成离线缓存到移动端页面
    - 在 App.vue 中初始化 OfflineCacheManager，监听网络状态变化
    - 离线时页面顶部显示"离线模式"提示条
    - 在材料、项目列表页面优先读取离线缓存，联网后自动同步
    - 在拍照功能入口集成 WatermarkCompositor
    - 输出：修改 `App.vue`、相关业务页面
    - _Requirements: 4.2, 4.7, 4.8, 6.1_

  - [x]* 4.5 编写离线缓存和水印前端属性测试（fast-check）
    - **Property 6: 离线缓存同步完整性** — 同步后本地应包含 material/project/user 且有有效版本号
    - **Property 7: 缓存过期标记** — cachedAt 超过 7 天的 entry expired 标记为 true
    - **Property 8: LRU 缓存淘汰** — 超过 100MB 后淘汰至 80%，淘汰最久未访问的过期项
    - **Property 9: 离线操作队列有序提交** — 按 timestamp 升序提交
    - **Property 10: 同步冲突标记** — 冲突响应后 status 变为 CONFLICT 且保留队列
    - **Property 11: 同步成功后队列清理** — 2xx 响应后操作从队列移除
    - **Property 12: 水印合成完整性** — 输出尺寸不变 + 字号计算正确
    - **Validates: Requirements 4.1, 4.4, 4.5, 4.6, 5.1, 5.2, 5.3, 5.4, 6.1, 6.2, 6.7, 6.8**
    - 输出：`zw-insight-app/tests/offlineCache.property.test.ts`、`zw-insight-app/tests/watermark.property.test.ts`

- [x] 5. Checkpoint — 离线缓存和水印功能完成验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. 用户安全增强 — 后端实现
  - [x] 6.1 创建设备管理数据库表和实体
    - 执行建表 SQL：`sys_login_device`
    - 创建 `SysLoginDevice` 实体类 + `SysLoginDeviceMapper`
    - 创建 `DeviceInfo` VO 和 `LoginDeviceVO` 响应类
    - 输出：`V3__login_device_table.sql`、`SysLoginDevice.java`、`SysLoginDeviceMapper.java`
    - _Requirements: 8.1_

  - [x] 6.2 实现 PasswordResetService 密码重置服务
    - 在 `zw-security` 模块创建 `PasswordResetService.java`
    - 复用已有 `AliyunSmsService` 发送验证码 + `CaptchaService` 校验验证码
    - 实现发送频率限制（60秒/次、10次/天）
    - 实现验证码校验（5次失败锁定30分钟）
    - 实现密码复杂度校验（8-20字符，字母+数字）+ BCrypt 加密更新
    - 密码重置成功后：使该用户所有 Token 加入 Redis 黑名单
    - Redis keys：`pwd_reset:verify_fail:{phone}`、`pwd_reset:lock:{phone}`
    - 输出：`zw-security/src/main/java/.../service/PasswordResetService.java`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.10_

  - [x] 6.3 实现 PasswordResetController REST 接口
    - 创建 `PasswordResetController.java`，映射 `/api/auth/password-reset`
    - 实现：POST `/send-code`、POST `/verify-code`、POST `/reset`
    - 输出：`zw-security/src/main/java/.../controller/PasswordResetController.java`
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 6.4 实现 DeviceManagerService 设备管理服务
    - 在 `zw-security` 模块创建 `DeviceManagerService.java`
    - 实现 `recordLogin()`（记录设备信息）、`listDevices()`、`revokeDevice()`
    - 实现 `autoEvictOldest()`（超过5台自动注销最早设备）
    - 实现 `addToBlacklist()`（Token 加入 Redis 黑名单，TTL = 剩余有效期）
    - 禁止注销当前设备校验
    - 输出：`zw-security/src/main/java/.../service/DeviceManagerService.java`
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [x] 6.5 实现 LoginLocationService 异地登录检测
    - 在 `zw-security` 模块创建 `LoginLocationService.java`
    - 集成 ip2region 本地库（`ip2region.xdb` 文件）
    - 实现 `resolveLocation(ip)` → "省份|城市"
    - 实现 `detectAndNotify(userId, ip, deviceInfo)` — 对比上次登录地，不一致则发送站内消息
    - IP 解析失败时 log.warn 但不阻断登录
    - 输出：`zw-security/src/main/java/.../service/LoginLocationService.java`
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [x] 6.6 实现 @SecondaryConfirm 注解和 AOP 拦截器
    - 创建 `@SecondaryConfirm` 注解（Target METHOD，属性 message）
    - 创建 `SecondaryConfirmInterceptor`（@Aspect）
    - 检查 `X-Confirm-Password` 请求头：空/缺失 → 449
    - BCrypt 校验密码：错误 → 403 + 失败计数；5次 → 锁定15分钟（423）
    - 密码正确 → 重置计数 + 放行
    - Redis keys：`secondary_confirm:fail:{userId}`、`secondary_confirm:lock:{userId}`
    - 输出：`zw-security/src/main/java/.../annotation/SecondaryConfirm.java`、`SecondaryConfirmInterceptor.java`
    - _Requirements: 10.1, 10.2, 10.5, 10.6, 10.7_

  - [x] 6.7 实现设备管理和异地登录 Controller
    - 创建 `DeviceController.java`，映射 `/api/user/devices`
    - 实现：GET `/list`、DELETE `/{deviceId}`
    - 在现有登录流程中集成 `DeviceManagerService.recordLogin()` + `LoginLocationService.detectAndNotify()`
    - 输出：`zw-security/src/main/java/.../controller/DeviceController.java`
    - _Requirements: 8.2, 8.3, 8.4, 9.1, 9.2_

  - [x]* 6.8 编写用户安全后端属性测试（jqwik）
    - **Property 13: 短信验证码校验往返** — 发送后立即验证应成功
    - **Property 14: 密码复杂度校验** — 不满足规则的密码应被拒绝
    - **Property 15: 密码重置后 Token 全部失效** — N 个 Token 全部进入黑名单
    - **Property 16: 验证码连续失败锁定** — 5次失败后第6次无论正确均被锁
    - **Property 17: 设备登录记录与注销** — 登录记录设备；注销后 Token 进黑名单
    - **Property 18: 最大设备数自动淘汰** — 超过5台自动注销最早设备
    - **Property 19: 异地登录检测与通知** — IP 归属地不同则产生通知消息
    - **Property 20: 二次确认缺失密码返回 449** — 空白/缺失 header → 449
    - **Property 21: 二次确认密码正确放行并重置计数** — 正确密码 → 放行 + 计数归零
    - **Property 22: 二次确认连续失败锁定** — 5次错误 → 锁定15分钟
    - **Validates: Requirements 7.2, 7.7, 7.8, 7.9, 8.1, 8.3, 8.5, 8.6, 9.2, 9.3, 10.1, 10.2, 10.5, 10.6, 10.7**
    - 输出：`zw-security/src/test/java/.../SecurityPropertyTest.java`

- [x] 7. 用户安全增强 — 前端实现
  - [x] 7.1 实现忘记密码页面（PC + 移动端）
    - PC 端：在 `zw-insight-web/src/views/login/` 下创建 `forgot-password.vue`（三步流程）
    - 移动端：在 `zw-insight-app/src/pages/login/` 下创建 `forgot-password.vue`
    - 三步流程：手机号输入 → 验证码校验 → 新密码设置
    - 输出：两端 `forgot-password.vue`
    - _Requirements: 14.1, 14.2_

  - [x] 7.2 实现设备管理页面和异地登录通知展示
    - PC 端：在个人中心页面新增"登录设备"标签页
    - 展示设备列表（设备名称、OS、IP、位置、登录时间、状态）+ 远程注销按钮
    - 当前设备标识 + 禁止注销
    - 异地登录通知在消息中心高亮展示 + "查看设备"链接
    - 输出：`zw-insight-web/src/views/user/devices.vue`
    - _Requirements: 14.3, 14.4, 8.4_

  - [x] 7.3 实现二次确认对话框组件
    - 创建全局二次确认对话框组件 `ConfirmPasswordDialog.vue`
    - 拦截 axios 响应 449 状态码 → 弹出密码输入框 → 重新发起请求（Header: X-Confirm-Password）
    - 用户取消则终止操作
    - 输出：`zw-insight-web/src/components/ConfirmPasswordDialog.vue`、axios 拦截器修改
    - _Requirements: 10.3, 10.4_

- [x] 8. Checkpoint — 用户安全功能完成验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. 数据运维 — 后端实现
  - [x] 9.1 创建备份和版本数据库表及实体
    - 执行建表 SQL：`sys_backup_record`、`sys_backup_restore_log`、`sys_version`
    - 创建实体类：`SysBackupRecord`、`SysBackupRestoreLog`、`SysVersion`
    - 创建对应 Mapper 接口和 XML
    - 输出：`V4__backup_version_tables.sql`、实体类、Mapper 文件
    - _Requirements: 11.3, 12.1_

  - [x] 9.2 实现 BackupService 数据库备份服务
    - 在 `zw-system` 模块创建 `BackupService.java`
    - 实现 `executeBackup()`：`ProcessBuilder` 执行 mysqldump → GZIP 压缩 → MinIO 上传
    - 使用 `AtomicBoolean` 防止并发备份
    - 实现超时控制（默认3600秒）+ 失败时清理临时文件
    - 实现 `restore()`：MinIO 下载 → 解压 → mysql 命令恢复 → 记录恢复日志
    - 实现定时备份（@Scheduled + cron 配置）
    - 输出：`zw-system/src/main/java/.../service/BackupService.java`
    - _Requirements: 11.1, 11.2, 11.3, 11.7, 11.8, 11.9, 11.10_

  - [x] 9.3 实现 BackupController REST 接口
    - 创建 `BackupController.java`，映射 `/api/system/backup`
    - 实现：POST `/execute`、GET `/list`、GET `/download/{id}`、DELETE `/{id}`、POST `/restore/{id}`
    - 删除时同时删除 MinIO 文件 + 数据库记录；MinIO 删除失败则保留记录返回错误
    - 输出：`zw-system/src/main/java/.../controller/BackupController.java`
    - _Requirements: 11.1, 11.4, 11.5, 11.6, 11.11_

  - [x] 9.4 实现 VersionManagerService 和 VersionController
    - 在 `zw-system` 模块创建 `VersionManagerService.java`
    - 实现版本号语义化校验（正则 `^\d+\.\d+\.\d+$`）
    - 实现版本号唯一性校验
    - 创建 `VersionController.java`，映射 `/api/system/version`
    - 实现：POST `/`、GET `/list`、GET `/current`
    - 输出：`VersionManagerService.java`、`VersionController.java`
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

  - [x] 9.5 实现 HealthMonitorConfig 健康监控
    - 在 `zw-system` 模块创建 `HealthMonitorConfig.java`
    - 注册 Micrometer 自定义指标：在线用户数（Gauge）、请求吞吐量（Counter）
    - 配置 Actuator 端点暴露（health、metrics、info）
    - 实现阈值告警日志：CPU > 90%、内存 > 85%、磁盘 > 90% 时记录 WARN 日志
    - 在 `application.yml` 中添加 Actuator 和阈值配置
    - 输出：`HealthMonitorConfig.java`、`application.yml` 修改
    - _Requirements: 13.1, 13.2, 13.4, 13.5_

  - [x]* 9.6 编写数据运维后端属性测试（jqwik）
    - **Property 23: 版本号语义化校验** — 不匹配 `^\d+\.\d+\.\d+$` 的字符串应被拒绝
    - **Property 24: 版本列表按发布日期降序** — 随机日期版本列表查询结果降序排列
    - **Property 25: 健康指标阈值告警** — 超阈值指标应产生 WARN 日志
    - **Validates: Requirements 12.2, 12.3, 12.4, 13.5**
    - 输出：`zw-system/src/test/java/.../OpsPropertyTest.java`

- [x] 10. 数据运维 — PC 前端实现
  - [x] 10.1 实现备份管理页面
    - 在 `zw-insight-web/src/views/system/` 下创建 `backup/index.vue`
    - 展示备份记录列表（文件名、大小、耗时、类型、状态、时间）
    - 操作按钮：手动备份、下载、删除、恢复（恢复需二次确认 @SecondaryConfirm）
    - 输出：`zw-insight-web/src/views/system/backup/index.vue`
    - _Requirements: 11.1, 11.4, 11.5, 11.6, 11.9_

  - [x] 10.2 实现版本管理页面
    - 在 `zw-insight-web/src/views/system/` 下创建 `version/index.vue`
    - 展示版本列表（版本号、发布日期、更新日志摘要）
    - 创建版本对话框（版本号输入 + Markdown 更新日志编辑）
    - 输出：`zw-insight-web/src/views/system/version/index.vue`
    - _Requirements: 12.1, 12.2, 12.3_

  - [x] 10.3 实现监控仪表盘页面
    - 在 `zw-insight-web/src/views/system/` 下创建 `monitor/index.vue`
    - 使用 ECharts 5.5 绘制系统概览卡片（CPU、内存、磁盘、连接池）
    - 使用 ECharts 绘制历史趋势折线图
    - 定时轮询 Actuator `/metrics` 端点
    - 输出：`zw-insight-web/src/views/system/monitor/index.vue`
    - _Requirements: 13.3, 13.6_

  - [x] 10.4 添加数据运维相关路由和菜单配置
    - 在路由配置中添加 `/system/backup`、`/system/version`、`/system/monitor` 路由
    - 在 `data-menu.sql` 中添加对应菜单和权限
    - 输出：路由文件、菜单 SQL
    - _Requirements: 11.4, 12.2, 13.3_

- [x] 11. Final Checkpoint — 全部功能完成验证
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- 后端使用 Java 17 + Spring Boot 3.2.6 + MyBatis-Plus 3.5.5
- 前端 PC 使用 Vue 3.4 + Element Plus 2.6 + TypeScript + Vite 5.2
- 移动端使用 uni-app + TypeScript
- 属性测试后端使用 jqwik 1.8.2，前端使用 fast-check 3.15.0
- 所有新增表需通过 SQL 迁移文件管理
- Redis key 命名遵循设计文档约定的格式
- wkhtmltopdf 和 mysqldump 路径通过配置文件注入，支持不同环境
- ip2region.xdb 文件需放置在 resources 目录下

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "6.1", "9.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "6.2", "4.1", "9.2"] },
    { "id": 2, "tasks": ["1.4", "6.3", "6.4", "6.5", "4.2", "4.3", "9.3", "9.4", "9.5"] },
    { "id": 3, "tasks": ["1.5", "6.6", "6.7", "4.4", "9.6"] },
    { "id": 4, "tasks": ["2.1", "2.3", "6.8", "4.5", "7.1"] },
    { "id": 5, "tasks": ["2.2", "7.2", "7.3", "10.1", "10.2", "10.3", "10.4"] }
  ]
}
```
