# 17-工作流 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 仅蓝图授权；此前无同模块蓝图。不修改M7未提交补丁，不执行Java/mvn、部署或生产写。
> 本地HEAD `01d5362541f7cc27ff92d4d75c477400625b53d8`。HEAD是已提交基线，**本轮未核对远端构建SHA，不能称其为已部署版本**；M7蓝图称门B未到。工作树与生产状态严格分开。引用按1-based行号。

## 1. 现状真相表

|能力|判定|path:line与证据|
|---|---|---|
|租户发起|已实现按tenant启动、标准businessKey、发起人finally清理|`zw-insight-server/zw-workflow/src/main/java/com/zwinsight/workflow/service/ApprovalService.java:66-97`|
|候选待办与签收|HEAD待办只查assignee，无claim；M7未提交补丁增tenant+候选用户/角色OR查询及claim。不是上线成果|同文件工作树 `:387-423,679-711`；`zw-insight-server/zw-workflow/src/main/java/com/zwinsight/workflow/controller/ApprovalController.java:43-48`；git diff 已核对|
|防自审/角色|M7补丁对列举业务类型防自审，并核对候选identityLinks；不是全类型规则。M1/M5/M7 BPMN角色改FINANCE_STAFF亦未提交|`ApprovalService.java:687-707,713-726`；`zw-insight-server/zw-machine/src/main/resources/processes/machine_settlement.bpmn20.xml:27`|
|读操作隔离|待办补丁已加tenant；详情按taskId运行/历史直查、轨迹按instanceId直查，已办只按assignee。需补完整租户与参与者范围校验；尚不宣称已验证漏洞可利用|`ApprovalService.java:437-465,537-558,595-599`|
|转办/委托/撤回|动作有事务，但转办/委托直接设置目标，需核对目标同租户、启用、角色、防自审；按business撤回查询未显式租户，startUserId缺失时校验不闭合|同文件 `:294-318,332-375`|
|完成回调|错误实现风险：publish业务事件异常被catch吞；isFailOnException=false，业务listener即使抛错也可能不能使引擎完成整体回滚|`zw-insight-server/zw-workflow/src/main/java/com/zwinsight/workflow/listener/ProcessCompleteListener.java:36-81`|
|驳回回调|存在异常catch，需区分引擎删除事件与显式reject事件、避免重复反冲|`zw-insight-server/zw-workflow/src/main/java/com/zwinsight/workflow/listener/ApprovalRejectListener.java:73`；`ApprovalService.java:174-182,230-238,271-276`|
|版本|部署duplicateFiltering与扩展表upsert已实现；只展示最新定义不代表在途实例迁移|`zw-insight-server/zw-workflow/src/main/java/com/zwinsight/workflow/service/ProcessDefinitionService.java:65-131`|
|PC/App|PC workflow API未见claim；App common未见claim，详情通过真实接口，加载失败阻断操作。后端签收端点不等于三端完成|`zw-insight-web/src/api/workflow.ts:7-39`；`zw-insight-app/src/pages/approval/detail.vue:72-120`；`zw-insight-app/src/api/common.ts:28-52`|
|测试|ApprovalServiceTest等存在，PC approval/index.test.ts与App approval-material-pages.test.ts存在；未执行、不计当前补丁通过率|`zw-insight-server/zw-workflow/src/test/java/com/zwinsight/workflow/service/ApprovalServiceTest.java:82-154`；`zw-insight-web/src/views/workflow/approval/index.test.ts`|

## 2. 数据考古发现

无本轮生产取证。M7文档 `docs/deep-opt/07-machine.md:45-49` 记129财务候选任务/FINANCE角色错位及历史949条超级管理员办结，是他轮特定实例背景，不推算当前双机分布，更非M17新实绩。

只读SQL未执行；先核对主机、容器、DB、企业和Flowable实际列名，勿直接删ACT表。

```sql
SELECT DATABASE(), @@hostname;
SELECT table_name,column_name FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name IN
 ('ACT_RU_TASK','ACT_RU_IDENTITYLINK','ACT_RE_PROCDEF','ACT_RU_EXECUTION','sys_role','wf_approval_record');
SELECT TENANT_ID_,PROC_DEF_ID_,ASSIGNEE_,COUNT(*) n FROM ACT_RU_TASK
 GROUP BY TENANT_ID_,PROC_DEF_ID_,ASSIGNEE_;
SELECT t.TENANT_ID_,i.GROUP_ID_,COUNT(*) n FROM ACT_RU_TASK t
 JOIN ACT_RU_IDENTITYLINK i ON i.TASK_ID_=t.ID_ AND i.TYPE_='candidate'
 GROUP BY t.TENANT_ID_,i.GROUP_ID_;
SELECT tenant_id,role_code,status,deleted,COUNT(*) n FROM sys_role
 GROUP BY tenant_id,role_code,status,deleted;
SELECT TENANT_ID_,KEY_,VERSION_,COUNT(*) n FROM ACT_RE_PROCDEF
 GROUP BY TENANT_ID_,KEY_,VERSION_;
SELECT TENANT_ID_,PROC_DEF_ID_,COUNT(*) n FROM ACT_RU_EXECUTION
 WHERE PARENT_ID_ IS NULL GROUP BY TENANT_ID_,PROC_DEF_ID_;
```

需再只读关联运行任务、实例、businessKey和各业务单据审批状态，统计孤儿/双实例/自审/超期；源业务表分别确认，不能用一个泛型SQL猜状态。按角色join时须保留tenant，空candidate/assignee不自动判孤儿（可能合法系统任务）。存量原始证据须区分种子、9999测试与业务流程。

## 3. 成熟度基线

人工暂评L2/L3：发起与审批/撤回/轨迹完整度较高，职责分离、候选可达与回调原子性未闭合，不能L4。未运行feature-ledger。八维人工检查：功能（候选入口）、流程（委托resolve/并行退回）、数据（源单据与实例对齐）、规则（统一防自审）、权限（全部读写tenant）、体验（签收冲突）、集成（回调事务）、质量（真实引擎故障注入）。不是工具自动字段/评分。

## 4. 缺口矩阵（vs 标杆/规范/实绩）

|能力|现状|可核实来源|实绩|
|---|---|---|---|
|候选角色与直接办理人|HEAD不足，补丁部分修|Flowable官方已查：candidateGroups可多组，assignee单一用户；Flowable不保证所填用户存在|本轮未取证|
|签收竞争|补丁直接TaskService.claim|本次官方所读页面未给claim细节；须补TaskService API与实际版本测试，不伪称已核实|未取证|
|在途版本迁移|没有取到迁移证据|所读官方页面仅述新版启动订阅替换，未证明在途自动更新；运行实例策略另须核实|未取证|
|职责分离与回调一致性|有明确缺口|AGENTS业务纪律为项目约束；不冒充行业法规条款|未取证|

来源：https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs/ （2026-10-08读取）。其他产品功能/合规时限未核实，不列为强制要求。

## 5. 目标状态机与业务不变量

命令：发起/签收/完成/退回/转办/委托/resolve/撤回；事件：节点完成、源业务审批成功或失败。聚合：流程实例、任务、业务单据及回调幂等记录。

候选 `UNCLAIMED → CLAIMED → COMPLETED`；竞争失败409；委托 `PENDING → RESOLVED` 后由owner完成，不能直接把委托当转办。源单据 `DRAFT → SUBMITTED → APPROVED/REJECTED`，退回重审与终止重提分别定义，不凭最近历史任务支持任意并行网关回退。

1. 所有运行/历史任务、定义、变量、审批轨迹读写均校验tenant和参与范围；超级管理员不豁免跨租户。
2. 候选角色从同租户启用角色解析；配置岗位不等于授予审批角色；每节点有可办理人员，发起人排除，自审策略覆盖全部指定审批类型，转办/委托不能绕过。
3. 完成与业务回写/金额更新同事务，失败传播使引擎任务仍可重试；业务listener、Flowable listener failOnException及事务manager共同验证。禁止仅在业务service加@Transactional便宣称原子。
4. 回调校验源单据状态、instanceId与租户，CAS、幂等、更新行数、金额上下限；重复回调不重复累加/反冲。
5. 新版定义仅影响新启动的目标策略；在途按原definitionId保留，确需迁移用引擎支持能力逐实例校验，禁改ACT表。角色改名亦不能靠重新部署自动修旧任务。
6. M7已有补丁由其负责人收敛，不重复改同文件；M17补全读权限、回调、全局规则与三端入口，跨M1/M5/M6/M9/M12列回归矩阵。

## 6. 分档实现计划（范围/代价/依赖）

人日粗估S=1-2/M=3-5/L=6-10，不含用户确认与环境等待。

|档/项|范围与代价|依赖|
|---|---|---|
|A1 候选可达|M：接续M7补丁，PC/App签收/冲突提示、真实角色校验|M7门B与M18角色；不得重复承诺补丁已上线|
|A2 隔离与职责|L：读写/历史/撤回tenant、目标用户、防自审、变量白名单|security/M18；complete请求不得覆盖initiator/businessId等协议字段|
|A3 原子回调|L：监听器传播、同事务核对、业务回调幂等/行数守卫、失败记录|全部审批业务模块；M7回调测试不足以替代公共监听器验证|
|A4 版本治理|M：定义/角色校验、旧实例清单、发布前演练、旧版办理策略|M7与各BPMN拥有者，存量处置另批|
|B 流程细节|L：委托resolve、并行网关退回约束、审计与批量部分成功协议|真实引擎测试后开放|
|C 高级编排|L以上：会签/加签/SLA自动升级|仅有业务需求后做，不引入第二引擎|

较省方案：保留旧实例与旧定义，先补角色/签收及原子回调；不做无需求的通用流程迁移平台。

## 7. 生产数据语义与决策

不得将历史自审改为他人审批，不把完成实例重开伪造审计。待定：SUPER_ADMIN紧急代理是否需理由/双人确认；防自审类型全集；发起人角色兼任时由谁代审；无角色成员是提交拒绝还是挂起告警。旧FINANCE任务须清单核验后决定引擎级identityLink调整/实例迁移/终止重提，三者不是同义；M7已获授权事项不可扩大至任意历史实例。回调错误的已完成流程与未回写单据需人工复核，不批量补金额。回滚停止新启动并保留旧定义/实例/幂等记录，不删除流程部署关联历史。

## 8. 验证方案

本轮只规划，未运行Java/mvn、L3/L4或生产探针。后续CI真实Flowable+DB事务测试：两候选竞争签收；非候选拒绝；同角色异租户拒绝；任务/历史/trace枚举拒绝；自审/转办绕过拒绝；恶意变量不可改业务协议；委托resolve；回调注入异常后任务与业务/累计值均回滚；重复事件一次回写；驳回与撤回反冲不重入；并行退回拒绝非法路径。新旧定义并存的在途实例演练，角色变更单独测试。租户9999，Redis test:t9999，清理仅测试数据并通过引擎终止，不直接删ACT。PC/App合同、机械结算、采购结算、项目结案闭环，普通财务账号不靠超级管理员。测试现有文件存在不等于覆盖当前补丁，需CI结果与两机部署SHA核对后方可门B。

## 9. 门A待确认与确认记录

- 待确认A1-A4范围、M7接续边界、全局防自审与管理员例外、旧流程办理/迁移政策。
- 待补核实：远端SHA、真实候选角色与成员、Flowable版本/claim与迁移API、事务manager与业务监听器全链路。
- 门A未确认，门B未进入；仅四模块蓝图授权不覆盖M7实现或上线授权。
