# ZW-Insight 数据库数据一致性审计报告 - 第 6 轮（system tables + flyway 收官）

**审计日期**: 2026-08-17  
**审计范围**: Flyway 迁移一致性 + sys_* 系统表（配置/字典/组织/岗位/模板/日志/备份/设备/编号规则/用户项目映射）  
**审计口径**: 租户 1 + 9999，仅只读查询  
**总体结论**: 🔴 **发现 2 个真实机制缺陷：数据库备份功能 100% 失效（P1，✅ 已修复闭环）+ 审计日志链路从未接通（P2）**

---

## 一、A 部分：Flyway 迁移一致性

| 检查项 | 结果 |
|--------|------|
| flyway_schema_history 记录数 | 37（含 baseline） |
| 失败迁移（success=0） | ✅ 0 |
| 版本与本地文件对齐 | ✅ 全部对齐（V5_1/V5_2 位于 zw-purchase 模块多模块迁移轨道，非漂移） |
| 最新版本 | 2026.39 app material return shortcut（2026-08-16 21:52） |

**Flyway 迁移历史健康**。

## 二、B 部分：系统表审计

### 🔴 R6-01（P1 真实缺陷）：数据库备份功能 100% 失效
- sys_backup_record 48 条记录**全部 FAILED**（每日 02:00 定时任务持续执行持续失败）
- 统一错误：`Cannot run program "/usr/bin/mysqldump": Exec failed, error: 2 (No such file or directory)`
- 根因：后端为 Java 容器，镜像内无 mysqldump 二进制；备份代码直调本机路径
- **影响：联调环境自上线以来无任何有效数据库备份**
- 处置建议：改走 `docker exec zwi-mysql mysqldump`（host 侧）或容器内安装 mysql-client，或改用 MySQL 容器内的备份方案；修复后需验证一次真实备份+恢复

### 🔴 R6-02（P2 功能缺口）：审计/操作日志链路从未接通
- sys_audit_log = **0 行**、sys_login_log = **0 行**、sys_oper_log = **0 行**
- 代码层核验：`AuditLogService.save()` **全仓零生产调用方**（仅 AuditLogServiceTest 引用）；Controller 查询端点存在但无数据来源
- 定性：实体/服务/查询端点齐备，但写入侧从未接线（无 AOP 切面/事件监听触发 save）
- 处置建议：待产品决策审计范围后接线（实体变更 AOP 或关键业务显式调用）

### ⚠️ R6-03（P3 测试残留）：sys_user_project 孤儿映射 1287 条（占 79%）
- 1632 条映射中：指向已删除项目 1077 + 指向物理不存在项目 210
- 租户分布：租户 1 = 1072（07-16~08-16）、租户 9999 = 215
- 项目名特征全部为 E2E_TEST_*/乱码中文测试名 → 测试项目删除未级联清理成员映射
- 处置建议：随 R5-01 清理批次一并处置；后端 ProjectService.delete 应补级联删除 sys_user_project

### ⚠️ R6-04（P3 表膨胀）：sys_login_device 17260 行
- 安全测试（多设备踢出 max-devices=5）遗留，峰值 4399 行/天（08-10）
- 无过期清理机制；处置建议：观察或批量清理测试期记录

### ✅ 通过项
| 检查项 | 结果 |
|--------|------|
| serial_number_rule 租户内 business_type 重复 | ✅ 0（租户 1=46 / 9999=5） |
| sys_dict_item → sys_dict 孤儿 | ✅ 0 |
| sys_org 父节点孤儿 | ✅ 0 |
| sys_tenant_menu → sys_menu 孤儿 | ✅ 0 |
| sys_user_project → sys_user 孤儿 | ✅ 0（用户侧完整） |
| sys_config | 14 条全局配置（无 tenant_id 列，全局语义正确） |
| sys_template | 87 条（租户 1=82 / 全局=5）正常 |

## 三、缺陷清单

| 编号 | 缺陷 | 等级 | 定性 | 处置建议 |
|------|------|------|------|---------|
| **R6-01** | 每日数据库备份 48 连败（mysqldump 不存在于 Java 容器） | **P1** | 真实机制缺陷 | ✅ **已修复闭环（2026-08-17）**：Dockerfile 补装 mariadb 5.5.68 客户端（commit 85968e3，CI run 31992570263），真实备份 SUCCESS（24.5MB/213 张表）+ scratch 库恢复机制验证全绿，详见受阻台账 |
| **R6-02** | 审计/登录/操作日志三表全空，AuditLogService.save 零生产调用 | P2 | 功能缺口（写入链路未接线） | 待产品决策审计范围后接线 |
| **R6-03** | sys_user_project 孤儿映射 1287 条（79%） | P3 | 测试残留 + 项目删除无级联 | 随 R5-01 批次清理 + 后端补级联 |
| **R6-04** | sys_login_device 17260 行无清理机制 | P3 | 表膨胀观察项 | 观察或批量清理 |

---

## 四、六轮审计总收官结论

| 轮次 | 范围 | 核心结论 |
|------|------|---------|
| 1 | finance/contract/project | P0-01 资金回写断裂→已修复闭环（Flowable act_* 表缺失）；2 僵尸字段 |
| 2 | labor/machine/subcontract | 勾稽全部 MATCH，直批回写链路正确 |
| 3 | purchase/material | 100+ 条回写勾稽 MATCH；种子 2 处叙事不自洽（R3-01/02）；库存不变量 40 条全 MATCH |
| 4 | budget/hr | 勾稽全 MATCH；R4-01 种子超预算 100万；R4-02 预算变更未回写（功能缺口） |
| 5 | workflow/security | R5-01 租户 1 积压 312 测试残留审批任务（用户决策保留观察）；权限配置全通过 |
| 6 | system/flyway | R6-01 备份 100% 失效（P1）；R6-02 审计日志未接线（P2）；Flyway 健康 |

**总体判断**：真实业务回写机制经六轮勾稽验证**全部正确**（付款/结算/库存/预算占用），所有 MISMATCH 均定性为种子数据叙事设计或测试残留；真正的系统性风险集中在**运维保障层**（备份失效、审计未接线）与**测试残留治理**（租户 1 的 312 任务 + 1287 孤儿映射 + 设备记录膨胀）。

---

**报告生成时间**: 2026-08-17  
**审计人员**: Qoder + Human Collaboration  
**状态**: 六轮审计全部完成
