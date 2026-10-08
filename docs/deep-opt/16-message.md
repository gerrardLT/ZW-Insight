# 16-消息 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 本轮仅蓝图，不授权实现、迁移、测试执行、生产写入或部署。此前无同模块蓝图。
> 证据为本地工作树；未访问两台生产服务器，未取得投递成功率、失败量或使用率。种子不是实绩。下列 path:line 均按文件实际 1-based 行号。

## 1. 现状真相表

|能力|判定与证据|位置|
|---|---|---|
|本人消息分页/未读数/已读|已实现；更新已读带 userId 条件；更新行数未检查，零更新仍可呈现成功|`zw-insight-server/zw-message/src/main/java/com/zwinsight/message/service/MessageService.java:26-95`|
|真实站内落库|已实现 insert，但 sendMessage 返回 void，未包含通知事件唯一身份；不可把每次 insert 当可靠投递协议|同文件 `:76-85`|
|催办异步租户|已实现从事件设 tenantId/systemTask、finally 清理；缺租户拒发；异常仅日志|`zw-insight-server/zw-message/src/main/java/com/zwinsight/message/listener/UrgeNotifyEventListener.java:31-67`|
|渠道成功语义|半实现：站内消息后依次 WebSocket、企微；企微 boolean 未检查；catch 后不向生产者反馈。拼接 JSON 未转义标题/内容|同文件 `:43-65`|
|去重与重试|风险：应收任务 publishEvent 后 return true、再写 Redis 标记，不能证明异步落库成功；本模块未发现持久化通知重试协议|`zw-insight-server/zw-finance/src/main/java/com/zwinsight/finance/task/ReceivableOverdueTask.java:181-194`|
|outbox|已有公共能力，不应另造队列；本次检索 message/workflow 未发现对其直接接入|`zw-insight-server/zw-common/src/main/java/com/zwinsight/common/event/outbox/OutboxEventRecorder.java:24,63-113`；`OutboxEventHandler.java:13-47`|
|WebSocket会话|单实例内存 userId 单会话；离线不发、IOException 仅日志；不能等同已读/可靠送达。握手鉴权与多租户身份组合尚待专项证实|`zw-insight-server/zw-message/src/main/java/com/zwinsight/message/websocket/MessageWebSocketHandler.java:18-55`|
|PC/App|真实接口，PC中心/公告/通知/推送配置；App消息中心。App若干空 catch 须核对错误是否已由 request 层展示|`zw-insight-web/src/api/message.ts:7-23`；`zw-insight-web/src/views/message/center/index.vue:53,84,113`；`zw-insight-app/src/pages/message-center/index.vue:111,131,140`|
|测试|有 MessageServiceTest、WeChatWorkServiceTest、UrgeNotifyEventListenerTest；后者覆盖缺租户/设置清理/落库失败清理，不证明真实线程池、事务提交、重启重放或渠道回执|`zw-insight-server/zw-message/src/test/java/com/zwinsight/message/listener/UrgeNotifyEventListenerTest.java:49-75`|

## 2. 数据考古发现

无本轮真实生产取证；历史 AGENTS 与 M7 记录仅背景，不外推到两个独立实例。本地初始 schema `deploy/db-init/00_schema.sql:371-390` 有 user/read 和 tenant 索引，没有通知事件唯一键；不能据此断言生产最新索引。

以下只读 SQL **未执行**。执行前分别核对主机 IP、容器、数据库及企业；先确认最新列与索引，勿输出消息正文/用户隐私。

```sql
SELECT DATABASE(), @@hostname;
SELECT table_name,column_name,column_type FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name IN ('msg_message','sys_outbox_event');
SELECT table_name,index_name,non_unique,column_name FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name IN ('msg_message','sys_outbox_event');
SELECT tenant_id,message_type,is_read,deleted,COUNT(*) n FROM msg_message
 GROUP BY tenant_id,message_type,is_read,deleted;
SELECT m.tenant_id,COUNT(*) orphan_or_cross_tenant FROM msg_message m
 LEFT JOIN sys_user u ON u.id=m.user_id
 WHERE m.deleted=0 AND (u.id IS NULL OR u.tenant_id<>m.tenant_id)
 GROUP BY m.tenant_id;
SELECT tenant_id,event_type,status,COUNT(*) n FROM sys_outbox_event
 GROUP BY tenant_id,event_type,status;
SELECT tenant_id,COUNT(*) missing_business_ref FROM msg_message
 WHERE deleted=0 AND business_type='WORKFLOW' AND business_id IS NULL GROUP BY tenant_id;
```

需补证：消息与源事件关联率、排队年龄、DEAD原因、无项目经理导致失败数、Redis去重标记但无站内消息的缺口。现有 business_id=NULL 无法精确反查 task，不能凭标题相似就认定重复或漏发。演示ID与测试前缀另列，未知来源保留，不报真实通知成功率。

## 3. 成熟度基线

人工暂评 L2：CRUD与异步实际存在，可靠闭环未证实；未运行 feature-ledger，非自动评分。八维缺口：功能（回执/重试）、流程（发布不等于落库）、数据（事件身份不足）、规则（渠道分别成功）、权限（会话身份待验）、体验（离线/失败提示）、集成（公共outbox未接入）、质量（重启与并发证据不足）。八维为本蓝图人工检查维度，非冒充账本工具原字段。

## 4. 缺口矩阵（vs 标杆/规范/实绩）

|能力|我们|规范/参考及核实程度|实绩|
|---|---|---|---|
|提交后通知|普通 @EventListener + @Async|Spring官方事务事件文档已查：AFTER_COMMIT为默认阶段；无事务默认不调用。但它不提供持久化和可靠重试|未取证|
|可靠投递|公共outbox可复用，通知未接入|本仓库 OutboxEventHandler 明示至少一次，处理器必须幂等；不是恰好一次承诺|未取证|
|多渠道回执与告警|日志级|产品标杆未逐产品核实；不将企业微信发送成功当用户阅读|未取证|

真实来源（2026-10-08读取）：https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html 。无已核实法规强制短信/时限条款。

## 5. 目标状态机与业务不变量

命令：记录通知意图、投递、确认落库、渠道重试、人工重放。事件：业务提交成功/通知已持久化/渠道失败。聚合：通知意图及逐收件人逐渠道投递记录。

`PENDING → PROCESSING → SENT`；失败进入可重试状态，达到上限 `DEAD`；人工重放留痕。具体状态名应适配现有公共outbox，不先造第二套调度器。READ是站内阅读状态，不是外部投递状态。

1. 通知意图与业务状态同事务；回滚不发，崩溃后可重放。AFTER_COMMIT单独使用不能填补崩溃窗口。
2. 幂等身份至少含 tenant、源事件/版本、收件人、渠道；同一次重试不得重复站内消息，合法重复催办须有新轮次。
3. 收件人必须同租户且在授权范围；异步与outbox均设置、清理上下文。Reminder/Login监听器也需验证，不能把 Urge 修复泛化。
4. 去重标记以所选成功门槛写入；队列受理、站内落库、企微受理、已读分别统计。
5. 外部渠道失败不撤销已成功站内消息；重试只补失败渠道。幂等账本不可逻辑删除后重新计数。
6. 跨模块契约置 zw-common；M9预警、M17催办复用协议，禁止生产者仅 publish 后谎报送达。

## 6. 分档实现计划（范围/代价/依赖）

估算为开发人日，不含等待业务确认与双机验证；S=1-2，M=3-5，L=6-10。

|档/项|范围与代价|依赖与验收边界|
|---|---|---|
|A1 可靠通知|L：复用公共outbox、逐收件人幂等、重试/DEAD、投递指标|M9/M17生产者、公共dispatcher租户与抢占恢复审查；新增表/索引须另批双轨迁移|
|A2 异步安全|M：全监听器上下文、JSON序列化、握手鉴权与归属校验|security/common；跨租户与线程复用负向必过|
|A3 成功语义|M：站内落库确认后去重、无收件人失败、分渠道记录|M9/M17，不能只改监听器日志|
|B 体验运维|M：业务定位、失败入口、阅读时间、分页索引与积压看板|A完成后再做；历史空引用不伪补|
|C 外部渠道扩展|L：多终端、集群推送、短信/邮件|业务量与费用确认后做，当前不新增依赖|

较省方案：先以站内持久化为成功门槛，WebSocket仅刷新提示、企微作为独立尽力渠道；可靠outbox仍不可省。

## 7. 生产数据语义与决策

禁止将历史“已发布”改写成“已送达”；历史缺事件身份记 UNKNOWN，不按标题自动补发。需用户确认：成功门槛、重试次数/退避/保留期、DEAD告警责任人、合法催办周期、外部渠道预算与消息敏感级别。旧Redis去重键迁移必须有清单和灰度切换，不能全清后群发历史消息。回滚只停新处理器并保留outbox/幂等记录，不丢意图；历史补发另需收件人名单与业务授权。

## 8. 验证方案

仅规划，未执行；不是受环境阻断而跳过测试。后续授权后用租户9999、Redis `test:t9999:`：业务回滚不发；提交后进程崩溃恢复；同事件重复/并发只一条站内；企微失败只重试企微；无收件人不写成功标记；缺租户/跨租户拒绝；线程复用无串租户；离线重连从DB读取；恶意引号内容仍合法JSON。验证 dispatcher 多实例抢占、超时PROCESSING恢复、DEAD重放。现有单测加真实事务/线程池测试，CI执行Java；两台服务器仅授权后的只读取证及测试租户验收。L3消息契约与PC/App错误态抽验，SQL报错须FAIL，不把空结果当零违规。

## 9. 门A待确认与确认记录

- 待确认A1-A3范围、成功门槛及是否允许公共outbox扩展；B/C默认不实施。
- 待确认生产只读取证、历史补发政策、渠道隔离和运维责任。
- 门A：未确认。门B：未进入。本轮没有实现、迁移、生产变更与测试通过结论。
