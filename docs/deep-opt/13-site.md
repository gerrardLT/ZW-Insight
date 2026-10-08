# 13-现场 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 仅蓝图，创建前未发现同模块独立蓝图。已读 AGENTS.md、总控结构及07-machine.md。当前工作树静态取证，不保证已部署；不改M7及他人文件，不连接生产、不运行环境测试。路径相对 `D:/tem_projects/ZW-Insight/`，行号从1起。

## 1. 现状真相表

| 能力 | 真相/判定 | path:line |
|---|---|---|
| 施工日志 | 已实现CRUD及项目名补充；保存/编辑/删除路径没有项目施工状态守卫，无法仅凭页面证明已结项禁写 | `zw-insight-server/zw-site/src/main/java/com/zwinsight/site/service/ConstructionLogService.java:29-65` |
| 检查与明细 | 半实现：检查主表、详情、明细更新、结果提交和指派整改均有；主表update直接updateById，状态白名单与项目状态联动不足 | `zw-insight-server/zw-site/src/main/java/com/zwinsight/site/service/InspectionService.java:56-165` |
| 整改 | 已有PENDING→SUBMITTED→APPROVED及检查表同事务更新；不是BPMN审批，提交曾移除不存在流程；当前Service只有通过，无驳回重做方法 | `zw-insight-server/zw-site/src/main/java/com/zwinsight/site/service/RectificationService.java:48-110` |
| 进度反馈 | 错误/待决策：save草稿即syncPlan；submit直接APPROVED并再次同步。草稿已改变正式计划，“确认后生效”未成立 | `zw-insight-server/zw-site/src/main/java/com/zwinsight/site/service/ScheduleFeedbackService.java:43-95` |
| 竣工验收 | 已有审批后回调；但先置验收APPROVED，complete抛BusinessException被吞，项目状态可能仍未完成 | `zw-insight-server/zw-site/src/main/java/com/zwinsight/site/service/CompletionAcceptanceService.java:60-116` |
| 项目施工状态 | 项目已有CONSTRUCTION/PAUSED/COMPLETED流转，不应另写status绕过状态机 | `zw-insight-server/zw-project/src/main/java/com/zwinsight/project/service/ProjectService.java:505-511` |
| PC接线 | site.ts有计划树、反馈提交、日志CRUD、检查指派、整改提交/通过及验收API；getSchedulePlanTree(projectId)与getScheduleDetail(id)使用同一plan/{id}形状，需契约核对语义，不直接认定均正确 | `zw-insight-web/src/api/site.ts:8-18,34-137`；`zw-insight-web/src/views/site/inspection/detail.vue:1` |
| App接线 | 日志、反馈、质量/安全检查、整改和照片上传API真实存在；不能据此认定移动端有完整审批/验收入口 | `zw-insight-app/src/api/common.ts:194-240`；`zw-insight-app/src/pages/site/inspection-detail.vue:1` |
| 删除级联 | Listener删除七类含project_id记录，注明inspection_detail不纳入；主表删除后子表孤儿风险需按实际SQL验证 | `zw-insight-server/zw-site/src/main/java/com/zwinsight/site/listener/SiteProjectCascadeCleanupListener.java:35-36,58-70` |
| 测试 | Inspection、Rectification、ScheduleFeedback、CompletionAcceptance等Service测试及提醒属性测试存在；PC/site-matrix、App/site-pages为组件接线测试，不替代真实Redis/流程/存储链 | `zw-insight-server/zw-site/src/test/java/com/zwinsight/site/service/RectificationServiceTest.java:1`；`zw-insight-web/src/__tests__/site-matrix.component.test.ts:1`；`zw-insight-app/tests/pages/site-pages.test.ts:1` |

## 2. 数据考古发现

生产**未核实**：日志日覆盖、超期整改、被暂停/关闭项目继续写记录、进度越界、验收与项目不一致、催办真实收件人均无本轮数字。种子和模拟流程不算实绩。

待执行只读SQL；先逐主机核对IP/容器/DB/企业，`:tenant`为绑定参数：

```sql
SELECT DATABASE(),@@hostname;
SELECT table_name,column_name FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN
('biz_inspection','biz_inspection_detail','biz_rectification','biz_schedule_feedback','biz_completion_acceptance');
SELECT rectification_status,COUNT(*) n FROM biz_inspection
WHERE tenant_id=:tenant AND deleted=0 GROUP BY rectification_status;
SELECT f.status,COUNT(*) n FROM biz_schedule_feedback f
WHERE f.tenant_id=:tenant AND f.deleted=0 GROUP BY f.status;
SELECT a.id,a.project_id,a.status,p.status project_status
FROM biz_completion_acceptance a LEFT JOIN biz_project p
ON p.id=a.project_id AND p.tenant_id=a.tenant_id AND p.deleted=0
WHERE a.tenant_id=:tenant AND a.deleted=0 AND a.status='APPROVED'
AND (p.id IS NULL OR p.status NOT IN ('COMPLETED','CLOSING','CLOSED'));
SELECT d.inspection_id,COUNT(*) n FROM biz_inspection_detail d
LEFT JOIN biz_inspection i ON i.id=d.inspection_id AND i.tenant_id=d.tenant_id AND i.deleted=0
WHERE d.tenant_id=:tenant AND d.deleted=0 AND i.id IS NULL GROUP BY d.inspection_id;
```

核列失败即停止，不用空结果冒充PASS。补查日志按项目/日期分布、整改负责人/期限空值率、反馈progress范围及project_id与plan_id项目相等、通知投递日志FAILED与收件人。脱敏聚合，区分种子ID/测试标记/9999，不公布人员或定位明细。

## 3. 成熟度基线

人工静态定位（未运行账本）：日志L1，计划反馈L2，检查整改L2/L3，验收L2/L3，移动采集L2；无线上闭环证据，不认L4。八维：efficiency甘特/移动采集已有；query项目过滤已有但范围待核；state草稿生效/整改无退回/验收吞错；audit检查明细与证据版本欠闭环；notify有提醒测试但真实投递未证；permission读写及复验职责待核；error更新与项目状态冲突恢复不足；value进度可信性取决于正式反馈口径。覆盖率未知。

## 4. 缺口矩阵（标杆/规范/实绩）

| 能力 | 当前 | 对照依据 | 实绩 |
|---|---|---|---|
| 发现→责任→整改→复验退回→关闭 | 缺复验不通过分支 | 内部目标；外部产品功能未核实 | 未核实 |
| 草稿与正式进度分离 | 草稿写正式计划 | 内部不变量，不宣称法规要求 | 未核实 |
| 停工/复工/竣工状态联动 | Service守卫不足 | 项目现有状态机为代码依据 | 未核实 |
| 安全检查与竣工质量材料 | 有记录无完整证据门禁 | 《建设工程安全生产管理条例》《建设工程质量管理条例》名称候选；现行文本、条款与官方URL未核实 | 未核实 |

不写未经核验法定周期、留存年限或监管接入要求；补证后由业务选入范围。

## 5. 目标状态机与业务不变量

- 整改PENDING→SUBMITTED→APPROVED；复验不通过回PENDING并保留每轮整改及复验意见，不覆盖历史。此复验是业务验收，不强制引入通用BPMN。
- 反馈DRAFT→CONFIRMED（或沿用APPROVED文案，待决策），只有正式反馈影响计划；若允许最新反馈覆盖，须明确取数日期/序列及回撤重算规则。
- 验收DRAFT→SUBMITTED→APPROVED / DRAFT；验收状态与项目COMPLETE同事务，任何状态拒绝均回滚，禁止吞错。
- SI-1：所有日志/反馈/检查/整改/附件对象同租户、同项目；plan_id须属于project_id，客户端传值不是权限证明。
- SI-2：CONSTRUCTION可正常采集；PAUSED仅允许停工安全巡检等白名单；COMPLETED/CLOSING/CLOSED只读或授权补录，不擅自一刀切禁止维保整改。
- SI-3：progress在0–100，日期顺序合法，主子节点聚合规则确定；草稿不污染正式进度，并发反馈有版本/条件更新。
- SI-4：复验人与整改人职责分离；复验结论、照片、时间及退回原因可追溯，删除不能破坏闭环。
- SI-5：竣工前重大未闭环问题是否阻断由用户定；预检与回调双时点验证。与M12交接经project现有服务/zw-common事件，不反向注入site进project。
- SI-6：提醒只有真实投递成功才写去重成功；无收件人FAILED可重试；Redis清理失败与数据库状态分别记录，不伪造发送。
- SI-7：ProjectDeletedEvent清理主子记录同事务，模块前缀Listener且不吞错；已归档文件与账本按保留策略处理，不把项目删除等同对象物理删除。

## 6. A/B/C分档计划、推荐与代价

| 档 | 内容 | 相对代价/范围 |
|---|---|---|
| A 推荐必须闭环 | 反馈正式生效口径、项目状态守卫、验收失败回滚、整改退回复验、对象权限及明细级联 | 7–11人日；site/project/common、PC/App、测试，可能涉及双轨迁移 |
| B 细节增强 | 证据版本、超期升级真实触达、检查模板变更差异、离线重试去重、进度来源下钻 | 5–8人日；site/file/message；单独确认 |
| C 锦上添花 | BIM/IoT、自动识别风险、监管平台对接 | 10–20人日及外部成本，延后 |

更省方案：保留现有整改端点与计划树，不建第二套流程引擎；优先补业务守卫与退回链。

## 7. 生产语义与用户决策

Q1推荐草稿不改正式计划；旧反馈是否重算未知，先只读比较，严禁全量重写progress。Q2确认暂停及竣工后允许的采集类型/补录权限。Q3确认重大问题阻断验收的分类、豁免人、复验职责。Q4推荐验收流转失败整体回滚；历史APPROVED但项目未竣工不自动改项目，逐单复核。Q5定位/照片保留期限及人员可见范围需业务与合规确认。

未来存量处理须双机分别备份、只读核对、异常单清单审批；回滚保留旧反馈与证据，不删除历史复验。新表唯一键采用可保留无限逻辑删除历史的guard，配置优先restore/upsert。本轮无生产写授权。

## 8. 验证方案（仅计划，未执行）

1. L1受权CI：反馈草稿零副作用，越界/跨项目拒绝；暂停/关闭守卫；整改退回再提交、并发复验；验收状态机失败全事务回滚、重复回调幂等。
2. tenant9999真实生命周期：施工日志→检查指派→照片上传→整改→退回→复验通过→验收→M12结案；普通角色办理、越权及无收件人失败重试。
3. R7隔离负向：检查孤儿明细、验收/项目状态矛盾、反馈项目错配及越界；构造异常证明FAIL真实触发。
4. PC/App契约对照Controller；分别验证计划树和详情语义、移动草稿与提交；真实MinIO/消息/Redis失败不得mock为通过。
5. 本轮未运行任何测试，因授权范围排除，不登记为环境受阻或通过；现有测试文件仅静态证据。

## 9. 门A待确认

- 2026-10-08：门A未确认；门B未启动，不构成实现/迁移/部署授权。
- 待确认A范围/代价、Q1–Q5、M12/M14/M16依赖、双机只读探针及官方规范补证。
- 不足：生产项目写入分布、整改真实职责分离、定位签名可信性、真实催办链、测试运行结果均未核实；不据种子认定业务在用。不改总控、GOAL或Spec。
