# Requirements Document

> 需求文档：前后端联调（frontend-backend-integration）

## Introduction

> 简介

本需求文档由已批准的设计文档（`design.md`）反向派生，目标是为 ZW-Insight 工程项目管理系统的"全流程前后端联调"建立可验证的、与设计完全可追溯的需求基线。

当前状态：后端已稳定运行于服务器 Docker（容器 `zwi-backend`，对外 `18080`→容器 `8080`，路由前缀 `/api/v1/...`），本地前端 Vite 通过代理把 `/api` 转发到 `129.204.3.200:18080`；登录、动态侧边栏（18 模块）、首页、系统管理基础功能已联调通过。

核心待解决问题是前后端 API 路径与 HTTP 方法在应用级范围内大面积错位。一致性审计工具扫出 757 项不一致，其中真正需要本次联调对齐的核心项为 **63 项**：`HTTP_METHOD_MISMATCH` 38 项 + `FRONTEND_EXTRA_API` 25 项，分布在 10 个模块。本需求覆盖：统一 REST 约定、逐项判定准则、按依赖排序的模块对齐、真实接口验证（禁止假数据兜底）、遗留问题修复（DB 中文乱码、未提交本地改动）、以及一致性审计复跑作为回归门禁。

## Glossary

- **联调系统（Integration_System）**：本次前后端联调所涉及的整体系统，含本地前端、Vite 代理、远程后端 Docker 服务及其数据存储。
- **前端API层（Frontend_API_Layer）**：前端 `src/api/*.ts` 中定义请求方法与路径的代码层。
- **后端控制器（Backend_Controller）**：后端 Spring `@RestController` 中以 `@RequestMapping("/api/v1/...")` 暴露真实路由的代码，是路径与方法的事实来源（source of truth）。
- **核心错位项（Core_Mismatch_Item）**：审计分类为 `HTTP_METHOD_MISMATCH` 或 `FRONTEND_EXTRA_API` 的不一致项，构成 63 项门禁基线。
- **噪音项（Noise_Item）**：审计分类为 `BACKEND_ORPHAN_API`、`FEATURE_EXTRA`、`FEATURE_MISSING` 的不一致项，由 `/page` 与根路径归一化或功能表覆盖差异产生，默认不计入门禁。
- **判定准则（Classification_Rule）**：将每个核心错位项归类为 A（改前端）、B（改后端）、C（噪音/废弃）的流程。
- **一致性审计工具（Consistency_Audit）**：`tools/consistency-audit` CLI，扫描前后端契约并产出 `audit-reports/audit-report-<ts>.md/json` 报告。
- **对齐台账（Alignment_Ledger）**：逐项记录"项 → 归类 A/B/C → 处理结果 → 验证证据"的台账，用于保留改造上下文。
- **回归门禁（Regression_Gate）**：以核心两类计数之和为指标的门禁机制，最终要求核心 63 项归零。
- **基线审计报告（Baseline_Audit_Report）**：锁定 63 项核心错位清单的那一次审计产物，即 `audit-reports/audit-report-2026-06-30T01-33-13.md/json`；该报告中的核心两类项构成不可变的 63 项基线。
- **已对齐（Item_Aligned）**：某核心错位项的代码改动已完成——归类 A 项的前端 `api/*.ts` 方法与路径已改为与后端真实路由一致；归类 B 项的后端控制器路由已补充/修正并已按 Requirement 10 完成部署；归类 C 项已在对齐台账登记。仅完成代码改动而归类 B 项尚未部署的，不算"已对齐"。
- **已通过验证（Module_Verified）**：某模块登记的全部核心错位项均为"已对齐"，且该模块按 Requirement 5 的真实接口验证全部成功（真实接口、真实数据、状态码 2xx、后端日志无 404/405 与异常）。
- **统一REST约定（Unified_REST_Convention）**：列表=根 GET、详情=GET `/{id}`、新增=POST 根、更新=PUT `/{id}`、删除=DELETE `/{id}`、批量删除=DELETE `/batch` 的目标契约。
- **真实验证码（Real_Captcha）**：后端生成并落地 Redis（key=`captcha:<uuid>`）的图形验证码值。

## Requirements

### Requirement 1: 统一 REST 路径约定基准

**User Story:** 作为联调负责人，我希望确立前后端统一的 REST 路径与 HTTP 方法约定，以便所有模块的对齐都有唯一、明确的目标契约。

#### Acceptance Criteria

1. THE 联调系统 SHALL 规定路径中的 `<module>` 与 `<entity>` 占位符仅由小写字母、数字及连字符组成，长度为 1 至 64 个字符，且 `{id}` 占位符为非空的路径段
2. THE 联调系统 SHALL 将列表查询定义为对 `/v1/<module>/<entity>` 根路径的 GET 请求
3. THE 联调系统 SHALL 将详情查询定义为对 `/v1/<module>/<entity>/{id}` 的 GET 请求
4. THE 联调系统 SHALL 将新增操作定义为对 `/v1/<module>/<entity>` 根路径的 POST 请求
5. THE 联调系统 SHALL 将更新操作定义为对 `/v1/<module>/<entity>/{id}` 的 PUT 请求，其中 id 置于路径、实体置于请求体
6. THE 联调系统 SHALL 将单条删除定义为对 `/v1/<module>/<entity>/{id}` 的 DELETE 请求
7. THE 联调系统 SHALL 将批量删除定义为对 `/v1/<module>/<entity>/batch` 的 DELETE 请求，且请求体为 id 列表
8. IF 批量删除请求体中的 id 列表为空或超过 1000 个元素，THEN THE 联调系统 SHALL 拒绝该请求并返回指示列表数量越界的错误响应，且不执行任何删除（此 1000 上限为目标契约约束，归类 B 补充或修正批量删除接口时按此实现；既有后端接口若未实现该校验，记录于对齐台账待排期，不阻塞本次联调门禁）
9. WHERE 接口为状态变更或提交等动作，THE 联调系统 SHALL 以 `/v1/<module>/<entity>/{id}/<action>` 形式并使用 POST 方法定义，且以后端实际路由为准

### Requirement 2: 核心错位项判定准则

**User Story:** 作为联调开发者，我希望每个核心错位项都按统一准则归类后再动手，以便避免仅凭审计描述误改代码。

#### Acceptance Criteria

1. WHEN 处理任一核心错位项，THE 联调开发者 SHALL 先读取后端对应控制器源码以确认真实的请求方法与请求路径，并为该项分配且仅分配 A、B、C 三类归类中的一类后，方可修改任何代码
2. IF 后端已存在符合统一REST约定的路由，THEN THE 联调开发者 SHALL 将该项归类为 A，并仅修改前端API层使其请求方法与请求路径与后端真实路由完全一致
3. IF 后端确实缺少该业务接口且该接口在系统功能表中存在对应条目，THEN THE 联调开发者 SHALL 将该项归类为 B，并按统一REST约定补充后端控制器方法
4. IF 经核对确认该项为审计归一化噪音或前端调用已废弃接口，THEN THE 联调开发者 SHALL 将该项归类为 C，并在对齐台账记录至少包含错位项标识与判定依据的记录，且不修改任何前后端代码
5. WHERE 前端与后端两侧调整均仅涉及单一文件改动且后端已符合统一REST约定，THE 联调开发者 SHALL 优先选择归类 A 以保持后端契约稳定
6. THE 联调开发者 SHALL 将后端控制器源码作为请求方法与请求路径的唯一事实来源
7. IF 审计描述与后端控制器源码冲突，THEN THE 联调开发者 SHALL 以源码为准进行归类
8. IF 后端对应控制器源码不存在或无法定位，THEN THE 联调开发者 SHALL 暂不归类该项、在对齐台账记录该情况，且不修改任何代码

### Requirement 3: 核心错位项对齐

**User Story:** 作为联调开发者，我希望消除 63 项核心错位，以便前端 API 调用与后端真实路由完全一致。

#### Acceptance Criteria

1. THE 联调系统 SHALL 将核心错位基线定义为基线审计报告（`audit-report-2026-06-30T01-33-13`）中 `HTTP_METHOD_MISMATCH` 38 项与 `FRONTEND_EXTRA_API` 25 项之和共 63 项，并作为不可变的项清单用于全过程追踪
2. WHEN 处理一个归类为 A 的项，THE 前端API层 SHALL 被修改为与后端真实路由一致，即请求方法在大小写规范化后逐字符相等、请求路径在去除尾部斜杠后逐字符相等
3. WHEN 处理一个归类为 B 的项，THE 后端控制器 SHALL 按统一REST约定补充或修正路由，使其请求方法与请求路径与该项目标契约一致
4. WHEN 一个核心错位项完成对齐，THE 联调系统 SHALL 将未对齐核心项计数减 1
5. WHEN 全部核心错位项完成对齐，THE 联调系统 SHALL 使未对齐核心项计数归零并将对应范围置为完成状态
6. THE 联调系统 SHALL 仅修改前端 API 调用层的方法与路径声明及后端控制器的路由声明，不修改业务数据模型、业务逻辑或数据库结构
7. IF 一个项无法在归类 A 与归类 B 间唯一确定（分类冲突），THEN THE 联调开发者 SHALL 不对其执行自动对齐、保留其原状态、记录冲突说明并标记为待复核
8. WHERE 审计项被分类为噪音项，THE 联调开发者 SHALL 不将其计入核心对齐范围；除非经复核确认其为真实功能缺口，此时 SHALL 将其重新归类为 A 或 B 并纳入对齐范围

### Requirement 4: 按依赖排序的模块处理顺序

**User Story:** 作为联调负责人，我希望按依赖与风险排序逐个处理模块，以便被引用的基础模块先打通，避免错位累积。

#### Acceptance Criteria

1. THE 联调系统 SHALL 严格按"阶段一 system 与 basedata → 阶段二 project 与 budget → 阶段三 contract 与 subcontract → 阶段四 tender → 阶段五 site 与 machine → 阶段六 hr → 阶段七 回归门禁"的固定顺序处理模块，且在前一阶段全部模块标记为"已通过验证"之前，不得开始后一阶段任一模块的处理
2. WHEN 一个模块登记的全部核心错位项均被标记为"已对齐"，THE 联调开发者 SHALL 对该模块执行真实接口验证，且仅当验证使用真实后端接口与真实业务数据（不使用 mock、桩数据或 fallback 静默处理）、全部核心接口返回成功响应且数据与预期一致时，方可将该模块标记为"已通过验证"并进入下一个模块
3. IF 当前模块存在任一核心项未标记为"已对齐"，或其真实接口验证未全部成功，THEN THE 联调开发者 SHALL 保持当前模块为"未通过验证"状态、不进入下一模块的处理，并记录未通过的具体核心项
4. IF 模块的真实接口验证执行失败（接口报错、超时或返回数据与预期不一致），THEN THE 联调系统 SHALL 给出标识该失败原因与对应核心项的提示，保留该模块已对齐项的状态不回滚，并将该模块维持为"未通过验证"
5. WHEN 核实 hr 模块的核心项，THE 联调开发者 SHALL 对其 2 项 `HTTP_METHOD_MISMATCH` 走常规判定准则（归类 A 为主）；并对 12 项 `FRONTEND_EXTRA_API` 逐项确认后端是否真实缺失该接口：若后端真实缺失且对应前端功能需要该接口，则归类为 B；若后端真实缺失但对应前端功能不需要该接口，或后端实际已存在等价接口，则归类为 C，并记录每一项的归类依据（hr 模块核心项合计 14 项）

### Requirement 5: 真实接口联调验证

**User Story:** 作为联调开发者，我希望每个模块都用真实后端与真实数据流验证，以便确认对齐结果真实有效。

#### Acceptance Criteria

1. WHEN 执行登录验证，THE 联调开发者 SHALL 在 10 秒内通过读取 Redis `captcha:<uuid>` 获取真实验证码，并以账号 admin/123456 完成真实登录，不绕过验证码、不伪造或 mock token
2. IF 登录请求未返回真实有效的会话凭证，THEN THE 联调开发者 SHALL 视为登录失败、记录失败原因并以新的真实验证码重新登录，不得伪造 token 继续
3. WHEN 验证一个 CRUD 请求，THE 联调开发者 SHALL 核对其请求方法与路径符合统一REST约定（查询 GET、新增 POST、更新 PUT、删除 DELETE）、响应状态码在 200 至 299 之间、且响应体为数据库中的真实业务数据而非静态样例数据
4. WHEN 验证请求是否真实到达后端，THE 联调开发者 SHALL 通过后端容器日志确认该请求无路由 404/405 且无异常堆栈
5. THE 前端API层 SHALL 在请求失败时显式返回错误，不静默回退到本地假数据
6. IF 后端确实缺少某接口，THEN THE 联调开发者 SHALL 将该项归类 B 补充后端或归类 C 登记，而不使用假数据掩盖
7. WHEN 一个模块全部核心项满足请求成功、日志干净、数据正确，THE 联调开发者 SHALL 判定该模块通过逐模块门禁；IF 任一核心项不满足，THEN THE 联调开发者 SHALL 判定该模块未通过门禁

### Requirement 6: DB 种子中文乱码修复

**User Story:** 作为联调开发者，我希望修复数据库种子的中文乱码，以便菜单与字典等基础数据正确显示。

#### Acceptance Criteria

1. THE 联调系统 SHALL 确保 MySQL 容器、目标库及其全部相关表的字符集为 `utf8mb4`、排序规则为 `utf8mb4_general_ci`
2. WHEN 重新导入种子脚本，THE 联调开发者 SHALL 显式指定客户端字符集为 `utf8mb4`
3. IF 存在字段值与种子源文件 UTF-8 原文不一致的数据行，THEN THE 联调开发者 SHALL 清空相关表后用 utf8mb4 重灌种子，而非逐行修补
4. WHEN 种子重灌完成，THE 联调开发者 SHALL 通过真实登录确认动态侧边栏 18 个模块名称与种子源文件 UTF-8 原文逐字一致
5. IF 重灌后校验仍存在与源文件不一致的数据，THEN THE 联调开发者 SHALL 判定本次重灌失败、返回指明不一致模块的提示，并保留现有数据待排查
6. WHEN 执行种子重灌，THE 联调开发者 SHALL 在写入前完成确认目标库与备份两项前置条件，否则中止重灌并提示，以避免误灌生产数据

### Requirement 7: 遗留本地改动纳入版本管理

**User Story:** 作为联调开发者，我希望将联调相关的遗留本地改动提交入库，以便保留可追溯的联调上下文。

#### Acceptance Criteria

1. WHEN 将联调遗留改动纳入版本管理，THE 联调开发者 SHALL 把 Vite 代理（`zw-insight-web/vite.config.ts`）、动态侧边栏改动、后端 WebMvcConfig 验证码白名单改动提交入库，使这三类改动在最终提交记录中均可检索到
2. WHEN 提交改动，THE 联调开发者 SHALL 逐文件 `git add` 仅暂存联调相关文件，且 SHALL NOT 使用 `git add .`
3. WHEN 提交前检查，THE 联调开发者 SHALL 核对暂存区文件清单，确认 `zwinsight.pem`、`keys/` 目录等敏感文件不在暂存区
4. IF 发现敏感文件已进入暂存区，THEN THE 联调开发者 SHALL 将其从暂存区移除、终止本次提交并保持工作区内容不变
5. THE 联调开发者 SHALL 使用包含"联调遗留改动纳入"字样且标明所涉模块的提交信息，并在改造记录中登记提交标识、文件清单与提交时间
6. WHEN 提交按模块产生的 `api/*.ts` 对齐改动，THE 联调开发者 SHALL 按模块分批提交，每次提交仅含单一模块的 `api/*.ts` 改动以便回溯

### Requirement 8: 一致性审计回归门禁

**User Story:** 作为联调负责人，我希望以一致性审计复跑作为回归门禁，以便确认核心错位项最终归零且无新增回归。

#### Acceptance Criteria

1. THE 回归门禁 SHALL 以 `HTTP_METHOD_MISMATCH` 与 `FRONTEND_EXTRA_API` 两类问题项计数之和作为门禁指标
2. THE 联调开发者 SHALL 将噪音类问题项独立记录登记于对齐台账，且噪音类不计入门禁指标
3. WHEN 复跑一致性审计，THE 一致性审计工具 SHALL 产出新的 `audit-reports/audit-report-<ts>.md` 与 `audit-report-<ts>.json` 报告，并刷新核心项工作清单 `api-alignment-worklist.md`
4. IF 审计复跑失败或报告未完整生成，THEN THE 回归门禁 SHALL 判定为不通过，且保留上一次成功复跑的报告不被覆盖
5. WHEN 完成一个模块对齐，THE 联调开发者 SHALL 复跑一次审计并确认该模块核心项计数归零且未引入新错位
6. WHEN 每次模块对齐完成并自检后复跑审计，THE 回归门禁 SHALL 满足核心两类计数之和不大于上一次成功复跑的计数之和；单调收敛以"模块全部核心项标记为已对齐后"的复跑为准，归类 B 补后端过程中前端尚未跟改的中间态不计入该比较
7. WHEN 全部模块处理完成，THE 回归门禁 SHALL 要求核心两类计数之和等于 0
8. IF 仍有核心残留项，THEN THE 联调开发者 SHALL 确认其为经核实并书面登记的归类 B 功能缺口待排期或归类 C 噪音，否则门禁判定为不通过
9. THE 联调开发者 SHALL 维护逐项对齐台账，每项记录的"项标识、归类 A/B/C、处理结果、验证证据"四个字段均不得为空

### Requirement 9: 联调错误处理

**User Story:** 作为联调开发者，我希望对联调过程中的异常场景有明确处理路径，以便不以假数据掩盖问题。

#### Acceptance Criteria

1. IF 前端请求方法或路径仍与后端不符导致 404/405，THEN THE 联调系统 SHALL 视为联调失败、记录失败项，并由联调开发者回到判定准则重新核对后端源码后方可继续后续联调步骤
2. IF Redis 中 `captcha:<uuid>` 在 120 秒过期时间内失效或读取到错误 key，THEN THE 联调系统 SHALL 视该验证码为无效
3. WHEN 验证码无效，THE 联调开发者 SHALL 重新获取验证码 uuid 并以新验证码完成真实登录
4. IF 出现中文乱码，THEN THE 联调系统 SHALL 由联调开发者定位到 DB 层并通过清表加 utf8mb4 重灌种子修复，不得在前端通过转码、字符替换或样式掩盖
5. IF 改后端重新部署引入异常，THEN THE 联调开发者 SHALL 拉取后端容器日志定位并修复；若 3 次内仍无法修复，则回滚到稳定 jar
6. THE 联调系统 SHALL 不允许任何 fallback 静默回退、以假数据替代真实响应、或对失败请求吞异常不报
7. WHEN 请求失败，THE 联调系统 SHALL 返回包含失败原因的明确错误响应并记录该失败

### Requirement 10: 归类 B 后端改动的部署流程

**User Story:** 作为联调开发者，我希望对触发后端改动的归类 B 项有受控的部署流程，以便降低对服务器真实服务的风险。

#### Acceptance Criteria

1. WHERE 一个项被归类为 B，THE 联调开发者 SHALL 严格按"本地重打 jar → 上传至服务器 → 重建并重启后端容器 → 拉取容器日志验证"四个顺序步骤执行部署，且仅在前一步骤成功后方可进入下一步骤
2. WHEN 执行重新部署前，THE 联调开发者 SHALL 完成以下两项前置确认：记录本次改动涉及的模块清单与影响范围，并将当前正在运行的上一版 jar 备份保留至少 1 份用于回滚
3. WHEN 部署完成后，THE 联调开发者 SHALL 在重启后 120 秒内通过后端容器日志确认服务满足全部以下条件：容器进程处于运行状态、日志中出现服务启动完成标识、且无 ERROR 或异常堆栈记录
4. IF 部署后 120 秒内容器未进入运行状态、日志未出现启动完成标识、或出现 ERROR 或异常堆栈，THEN THE 联调开发者 SHALL 停止后续验证，使用步骤 2 保留的上一版 jar 回滚并重启容器，并保留失败日志用于排查
5. WHERE 一个项未被归类为 B 或归类 B 尚未确认，THE 联调开发者 SHALL 不对该项执行任何后端重新部署或后端代码改动

### Requirement 11: 联调安全约束

**User Story:** 作为联调负责人，我希望联调过程遵守安全约束，以便不泄露凭证、不误操作生产数据。

#### Acceptance Criteria

1. THE 联调开发者 SHALL 在每次联调开始前确认 PEM 私钥（`zwinsight.pem`）已被 `.gitignore` 规则覆盖，且通过版本库追踪状态检查确认其未被纳入暂存区或提交历史；IF 检测到该私钥已被纳入版本追踪，THEN THE 联调开发者 SHALL 立即将其从追踪中移除并停止后续提交操作
2. WHEN 读取后端日志或 Redis 数据，THE 联调开发者 SHALL 对敏感凭证值（包括私钥内容、登录令牌、验证码、数据库连接口令）进行脱敏处理，确保任何输出或回显中此类值不以明文完整出现，仅可保留键名引用
3. WHEN 执行后端重新部署或种子数据重灌操作，THE 联调开发者 SHALL 在操作执行前校验目标环境标识为联调环境（非生产环境）；IF 目标环境标识无法确认为联调环境或被识别为生产环境，THEN THE 联调开发者 SHALL 中止该操作并保持目标数据不被修改
4. THE 后端控制器 SHALL 将验证码免鉴权白名单的放行范围严格限制为验证码相关路径，对该范围之外的全部业务接口请求执行鉴权校验
5. IF 收到对非验证码路径的请求且其缺失有效鉴权凭证，THEN THE 后端控制器 SHALL 拒绝该请求并返回指示鉴权失败的错误响应，同时不执行任何业务数据操作
