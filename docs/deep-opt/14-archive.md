# 14-档案 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 仅蓝图；创建前未发现同模块独立蓝图。已读AGENTS.md、总控与07-machine.md。本轮仅当前工作树静态取证，未连生产、未测环境；不改M7及其他代理文件。路径相对 `D:/tem_projects/ZW-Insight/`，行号从1起。

## 1. 现状真相表

| 能力 | 真相/判定 | path:line |
|---|---|---|
| 档案本质 | 已实现只读聚合视图，直接查多模块Mapper，不是归档库、版本快照或交付包；源数据变动会改变查询结果 | `zw-insight-server/zw-archive/src/main/java/com/zwinsight/archive/service/ArchiveService.java:71-105` |
| 项目档案 | 半实现：实际聚合项目、成员、施工合同、付款、收款、分包和机械合同及资金概览；注释提及施工过程/成本，方法未取现场检查、验收、最终结算或文件清单 | 同上 `:110-161` |
| 预算档案 | 最新预算取创建时间第一条，非明确“最新已审批”快照；不得称竣工决算归档依据 | 同上 `:198-226` |
| 人事档案 | 错误关联风险：入职按createdBy关联userId，代表申请创建者而非入职员工；转正/离职按userId，未聚合调动 | 同上 `:367-388` |
| 机械档案 | 仍注入旧BizMachineSettlementMapper；M7新旧口径治理未门B，不能自行接走或改M7 | 同上 `:37-40,86-88`；`docs/deep-opt/07-machine.md:99-108` |
| 档案权限 | 类级archive:view覆盖项目、财务、人事等端点；Service无显式人员/项目权限断言。全局拦截器是否补足对象范围需专项取证，不能认定安全或已越权 | `zw-insight-server/zw-archive/src/main/java/com/zwinsight/archive/controller/ArchiveController.java:22-98` |
| 真实文件存储 | 已实现：FileService调用MinioService上传后写file_info；100MB及扩展名黑名单校验。不是FileStorage实体即代表实际写入链 | `zw-insight-server/zw-file/src/main/java/com/zwinsight/file/service/FileService.java:29-91`；`zw-insight-server/zw-file/src/main/java/com/zwinsight/file/domain/FileInfo.java:13` |
| 文件删除/归属 | 风险：对象先删后删元数据，无归档冻结守卫；按businessType/businessId取列表但未验证宿主对象；上传projectId与businessId由客户端提供 | `zw-insight-server/zw-file/src/main/java/com/zwinsight/file/service/FileService.java:111-129` |
| 文件权限与下载 | 公共Controller明确不加RequiresPermission、注释称宿主守卫；但直接upload/delete/list路径需证明宿主守卫真实生效。MinioService有下载与预签名能力，FileController自身没有下载端点，不能称受控下载已接通 | `zw-insight-server/zw-file/src/main/java/com/zwinsight/file/controller/FileController.java:15-38`；`zw-insight-server/zw-file/src/main/java/com/zwinsight/file/service/MinioService.java:65-105` |
| PC/App | PC archive.ts对应聚合端点，archive/index.vue及三个列表页；App只有项目档案调用可证，不等于有受控归档上传/下载链 | `zw-insight-web/src/api/archive.ts:5-65`；`zw-insight-web/src/views/archive/index.vue:1`；`zw-insight-app/src/api/common.ts:320`；`zw-insight-app/src/pages/project/archive.vue:1` |
| 测试 | 有聚合、搜索过滤属性、Controller及PC档案组件测试；证明字段映射/接线，不证明真实对象存在、桶策略、保留期或越权隔离 | `zw-insight-server/zw-archive/src/test/java/com/zwinsight/archive/service/ArchiveServiceAggregateTest.java:152-388`；`zw-insight-server/zw-archive/src/test/java/com/zwinsight/archive/service/ArchiveSearchFilterPropertyTest.java:1`；`zw-insight-web/src/__tests__/archive-pages.component.test.ts:1` |

## 2. 数据考古发现

**生产未核实**：file_info条数、对象缺失率、桶是否公开、业务归属孤儿、扫描件覆盖、敏感人事被谁读取均未知。种子附件字符串不证明对象存在；不得用元数据计数替代可读取实物。

待执行只读SQL，先逐主机核DB/企业并核列，`:tenant`绑定参数：

```sql
SELECT DATABASE(),@@hostname;
SELECT table_name,column_name FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN ('file_info','biz_entry_apply','biz_project');
SELECT storage_type,business_type,COUNT(*) n,
SUM(business_id IS NULL) missing_business,SUM(project_id IS NULL) missing_project
FROM file_info WHERE tenant_id=:tenant AND deleted=0 GROUP BY storage_type,business_type;
SELECT f.id,f.business_type,f.business_id,f.project_id
FROM file_info f LEFT JOIN biz_project p
ON p.id=f.project_id AND p.tenant_id=f.tenant_id AND p.deleted=0
WHERE f.tenant_id=:tenant AND f.deleted=0 AND f.project_id IS NOT NULL AND p.id IS NULL;
SELECT status,COUNT(*) n,COUNT(DISTINCT created_by) creators
FROM biz_entry_apply WHERE tenant_id=:tenant AND deleted=0 GROUP BY status;
```

补证计划：按business_type逐宿主表检查归属及tenant；经授权只读MinIO HEAD/GET与桶策略核查，采样比对file_path、大小/哈希、权限撤销后签名有效期；不在报告输出对象签名URL、证件或凭证。SQL不能证明对象完整性；错误即停，不将空返回视为零孤儿。

## 3. 成熟度基线

人工静态定位：业务聚合视图L2/L3，归档冻结L0，文件管理L2，受控跨域档案L1/L2；未运行账本及覆盖率。八维：efficiency多域聚合有但无交付清单；query分类与搜索有；state无独立归档/撤销状态；audit缺归档版本/读取审计；notify缺移交签收链；permission统一view不足以证明敏感边界；error文件与DB跨资源失败恢复欠缺；value“聚合实时视图”与“历史归档快照”价值应分开表达。

## 4. 缺口矩阵（标杆/规范/实绩）

| 能力 | 当前缺口 | 依据边界 | 实绩 |
|---|---|---|---|
| 业务索引与文件实物一致 | 档案未汇总file_info，对象未核 | 代码与存储链为内部证据 | 未核实 |
| 归档版本、清单、签收、完整性 | 实时聚合无冻结 | 标杆产品具体功能未核实 | 未核实 |
| 人事与项目权限分域 | archive:view统一 | 内部最小权限目标；全局守卫待证 | 未核实 |
| 工程归档/电子档案保留 | 无版本及保留规则 | 《建设工程文件归档规范》《中华人民共和国档案法》《电子档案管理办法》仅补证候选；版本、适用范围、官方条款/URL未核实 | 未核实 |

不虚构保存年限、必须使用WORM、签章法律效力或外部产品URL。

## 5. 目标状态机与业务不变量

- 保留“实时业务档案”名称；新增最小归档包DRAFT→SUBMITTED→ARCHIVED，退回DRAFT；撤销/修订生成新版本并保留旧版本，不覆盖源审批单。
- AR-1：业务对象授权成功后才查关联文件、预览/签名/下载；每次校验租户+对象范围+敏感领域权限，不相信客户端projectId或businessType。
- AR-2：archive:view不能授予人事/财务全部明细；人员隐私字段脱敏，下载另权、读取留痕，项目成员权限不自动扩展至人事档案。
- AR-3：归档清单由真实业务键、文件ID/对象键、版本、哈希、大小和审批依据构成；缺实物明确失败，不用静默空数组/零金额冒充完整。
- AR-4：ARCHIVED冻结清单/对象引用，允许业务新版本追加；已引用对象禁止直接删。DB与对象存储非单事务，失败需补偿/重试或待清理标记，不能宣称@Transactional能回滚MinIO。
- AR-5：受控短期预签名/代理下载由既有MinioService复用，桶私有策略需真实验证；过期、撤权、租户隔离、响应头和危险文件内容边界纳入验证。
- AR-6：M12结案请求归档检查经zw-common契约或现有只读预检，不令project反向依赖archive；结项不调用删除事件物理清理文件。保留期及legal hold由业务定。
- AR-7：人事按明确员工userId/账号创建关联，不以createdBy作雇员身份；历史无可靠映射的不自动匹配姓名。
- AR-8：机械档案口径等M7门B；只读聚合可继续Mapper依赖，写入档案包由本域负责，不新增通用插件架构。

## 6. A/B/C分档计划、推荐与代价

| 档 | 内容/推荐 | 相对代价/涉及面 |
|---|---|---|
| A推荐必须闭环 | 文件宿主对象权限、敏感分域、正确人事关联、缺实物失败语义、删除保护；区分实时视图与归档 | 6–10人日；archive/file/hr/security、PC；历史关联可能需双轨迁移 |
| B细节增强 | 最小版本清单、完整性校验、移交签收、M12预检交接、下载审计 | 6–9人日；archive/common/project；独立确认 |
| C锦上添花 | OCR全文索引、电子签章、WORM/异地归档、外部城建档案馆对接 | 10–20人日及存储/合规成本，延后 |

更省方案：先保留实时聚合，补权限与对象真实性；不将整套电子档案平台作为A前提。

## 7. 生产语义与用户决策

Q1确认“实时视图”是否继续叫档案，以及B归档包本轮是否纳入。Q2推荐人事单独权限，下载与预览分权、脱敏审计；用户定合法角色。Q3确认文件保留/删除/法律保留与结项清单，不能从项目deleted推导文件应删。Q4推荐历史人事关联人工复核，不按姓名自动回填。Q5桶策略、签名时长、对象校验负载与已公开URL兼容由用户定，不能直接收紧导致生产附件失效。

未来改动先双机分别只读盘点与目标元数据/对象备份，保留旧映射和旧归档版本；回滚代码不等于恢复已删对象，因此默认不物理删对象。本轮不操作桶策略、数据或迁移。

## 8. 验证方案（仅计划，未执行）

1. CI L1：跨租户/无宿主/非成员/仅archive:view读人事拒绝；员工与申请创建者不同；缺实物、DB写失败补偿、删除冻结对象拒绝。
2. 受权tenant9999真实MinIO：上传→业务绑定→签名预览→归档→冻结删除失败→新版本；过期/撤权/桶不可公开读取断言；不暴露真实人员证件。
3. PC/App复核真实API与对象授权，404/权限错误清晰，不fallback假档案；聚合接口不得绕过原财务/人事权限。
4. R7检查文件宿主孤儿、归档清单实物缺失、归档版本唯一与哈希；隔离造缺对象证明FAIL。跨资源恢复须真实故障验证而非仅Mockito。
5. 本轮未启动测试，非环境受阻，不写受阻台账、不报PASS；既有聚合及组件测试未运行。

## 9. 门A待确认

- 2026-10-08：门A未确认，门B未启动；仅授权此蓝图，不授权代码/迁移/生产写/部署。
- 待确认A/B边界、Q1–Q5、代价、M7/M12/M15协同及官方规范补证。
- 不足：全局对象权限/数据权限拦截器完整执行链、生产桶策略与真实文件、法定保留期、运行测试结果未核实；不存在“已生产验证”结论。不改总控/GOAL/Spec。
