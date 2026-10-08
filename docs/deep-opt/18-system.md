# 18-系统管理 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 仅蓝图；此前无同模块蓝图。未接生产、未运行测试，不改业务/迁移/总控/GOAL及M7补丁。行号均1-based。

## 1. 现状真相表

|能力|判定|path:line|
|---|---|---|
|机构/岗位/角色接口权限|已分读写：类system:view、方法org/post/role增改权限。仍须逐端点验证注解组合与目录可授予性|`zw-insight-server/zw-system/src/main/java/com/zwinsight/system/controller/SysOrgController.java:18-43`；`SysPostController.java:19-46`；`SysRoleController.java:22-50`|
|角色读取租户|page/getById显式tenant；tenant空时条件不启用，内部调用不能泛化为默认安全|`zw-insight-server/zw-system/src/main/java/com/zwinsight/system/service/SysRoleService.java:36-57`|
|角色写入与授权|update查ID后直接更新；delete删角色和菜单关联，未在该方法清理/阻断用户关联；assignMenus先删再插。须核对目标tenant、授权上限和关联真实DDL|同文件 `:79-114`|
|数据范围|新角色默认SELF；updateDataScope枚举校验及ADMIN/SUPER_ADMIN检查存在，但查询目标角色未显式tenant|同文件 `:66-72,135-152,175-198`|
|机构树|新增校验父机构，删除阻断子机构/人员；update父级后仅更新当前ancestors，未见后代批量修复与环路校验|`zw-insight-server/zw-system/src/main/java/com/zwinsight/system/service/SysOrgService.java:47-104`|
|机构/岗位隔离|机构查询/按ID方法没有显式tenant；sys表隔离须结合MybatisPlusConfig全链路，不以业务表拦截器代替检查|同文件 `:29-41`；`zw-insight-server/zw-system/src/main/java/com/zwinsight/system/service/SysPostService.java:47-90`|
|岗位删除|单删/批删直接mapper删除，当前Service未见人员引用阻断；状态接口需限制合法值|`SysPostService.java:75-90`|
|逻辑删除唯一|初始DDL有deleted/version，org/post/role未建业务编码唯一；后续迁移与生产索引未全量证实，不直接说线上必冲突或没有约束|`deploy/db-init/00_schema.sql:46-98,125-143`|
|错误语义|公共处理器已兜底结构错误400与完整性409；Service仍须将已知冲突译成具体提示；R.code不自动等于HTTP状态|`zw-insight-server/zw-common/src/main/java/com/zwinsight/common/exception/GlobalExceptionHandler.java:145-162`|
|PC/App与测试|PC机构真实getOrgTree、用户/岗位/角色/菜单/字典等页面存在；本次未发现App独立system目录，不等同确认全部无API。SysOrg/Post/Role/User等ServiceTest存在，未执行|`zw-insight-web/src/views/system/org/index.vue:115,151`；`zw-insight-server/zw-system/src/test/java/com/zwinsight/system/service/SysRoleServiceTest.java`|

## 2. 数据考古发现

无真实生产取证，机构规模、岗位空置率、角色实际使用、越权可利用性和索引冲突量均未知。初始schema不代表生产最终schema；演示管理员/角色不作真实组织实绩。

只读SQL未执行。两机分别确认IP、容器、DB、企业；用户资料只取计数，不输出密码/手机号。

```sql
SELECT DATABASE(),@@hostname;
SELECT table_name,column_name,column_type FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name IN
 ('sys_org','sys_post','sys_role','sys_user','sys_user_role','sys_role_menu');
SELECT table_name,index_name,non_unique,column_name FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name IN
 ('sys_org','sys_post','sys_role','sys_user_role','sys_role_menu');
SELECT tenant_id,status,deleted,COUNT(*) n FROM sys_org GROUP BY tenant_id,status,deleted;
SELECT tenant_id,status,deleted,COUNT(*) n FROM sys_post GROUP BY tenant_id,status,deleted;
SELECT tenant_id,role_code,deleted,COUNT(*) n FROM sys_role
 GROUP BY tenant_id,role_code,deleted HAVING COUNT(*)>1;
SELECT o.tenant_id,COUNT(*) invalid_parent FROM sys_org o
 LEFT JOIN sys_org p ON p.id=o.parent_id
 WHERE o.deleted=0 AND o.parent_id<>0
 AND (p.id IS NULL OR p.deleted<>0 OR p.tenant_id<>o.tenant_id) GROUP BY o.tenant_id;
SELECT COUNT(*) orphan_or_cross_tenant FROM sys_user_role ur
 LEFT JOIN sys_user u ON u.id=ur.user_id LEFT JOIN sys_role r ON r.id=ur.role_id
 WHERE u.id IS NULL OR r.id IS NULL OR u.tenant_id<>r.tenant_id OR r.deleted<>0;
SELECT role_id,menu_id,COUNT(*) n FROM sys_role_menu
 GROUP BY role_id,menu_id HAVING COUNT(*)>1;
```

data_scope、用户post关联、唯一guard等列先探针确认再写查询；不猜列名。需按授权目录与路由权限对照“有按钮无权限码/有权限码无可授予菜单”；环路、ancestors漂移应只读遍历并列出ID/影响数，禁止自动修树。

## 3. 成熟度基线

人工暂评L2：基础组织/RBAC CRUD可用，租户闭合、授权边界、树结构与删除生命周期未获完整证据。未运行feature-ledger。八维人工检查：功能（授权预览）、流程（机构移动/角色退役）、数据（唯一与孤儿）、规则（授权不超自身）、权限（sys显式隔离）、体验（冲突具体提示）、集成（M17角色/M19范围）、质量（跨租户真实契约）。不是工具自动评分。

## 4. 缺口矩阵（vs 标杆/规范/实绩）

|能力|我们|规范/标杆|实绩|
|---|---|---|---|
|租户权限闭合|读写实现不均匀|项目AGENTS与现有Service注释为已读本地约束；产品标杆未逐项核实|未取证|
|组织树无环/路径一致|仅父存在检查不足|领域不变量，不伪称国家标准条款|未取证|
|删除后可重建|需逐表确认|AGENTS明确禁止布尔deleted加入唯一键，关联优先restore/upsert|未取证|
|错误协议|已有公共400/409代码|项目AGENTS约束；HTTP传输状态须真实契约检查|未取证|

外部产品/法规未核实，本节不声称达到等保、审计或任一商业产品标准。后续可核对OWASP授权指导与MySQL唯一索引官方文档，但本轮未读原文，不作为现行技术选型证据。

## 5. 目标状态机与业务不变量

组织/岗位/角色：`ENABLED ↔ DISABLED → DELETED`，恢复/重建规则按对象分别定义。关联：有效授权→撤销→restore/upsert，不让撤销历史变成第二次插入唯一冲突。

1. 系统对象所有读写校验同租户；共享菜单/平台对象须显式列白名单，不把sys前缀当平台共享权限。缺租户默认拒绝，内部系统任务仅通过受控入口。
2. 机构parent同租户、非自己/后代；移动子树同步重算后代ancestors，事务失败整体回滚。
3. 被人员/审批候选/组织引用的岗位或角色不得无声删除；角色停用立即失去办理资格，M17候选人须有替代规则。
4. 分配角色、菜单、dataScope不得超出操作者授权上限；保护最后有效管理员，平台管理员与租户管理员分域。
5. 可重建实体采用可无限保留删除历史的唯一guard；关联优先restore/upsert；禁止UNIQUE(code,deleted)布尔方案；账本/幂等流水不删。不能先建唯一索引再掩盖存量重复。
6. JSON结构错400、约束冲突409，已知重复提示对象/编码；校验状态/枚举、白名单防止body改tenant/deleted/权限范围；更新行数为0须明确失败或幂等语义。
7. 授权变化有审计与缓存失效，在线JWT/权限缓存失效窗口明确；M17候选角色与M19数据范围消费一致。

## 6. 分档实现计划（范围/代价/依赖）

粗估人日S=1-2/M=3-5/L=6-10，不含审批等待。

|档/项|范围/代价|依赖|
|---|---|---|
|A1 系统隔离|L：用户/机构/岗位/角色/关联全CRUD目标tenant、受控平台对象、body白名单|security/common与既有平台共享设计|
|A2 授权闭合|L：授权上限、最后管理员、缓存失效、角色停用及审批候选联动|M17与权限目录，不临时给所有人SUPER_ADMIN|
|A3 组织岗位生命周期|M：环路、后代路径、引用阻断、批删事务|人事/项目成员关系先查真实表|
|A4 唯一与错误|M-L：逐表restore/upsert或guard，冲突提示、HTTP契约|最新DDL/存量重复只读证据；迁移另批双轨|
|B 运维体验|M：授权差异预览、变更审计、组织停用影响提示|A闭环后再做，不重造权限引擎|
|C 高级治理|L以上：SSO/SCIM/审批式授权|真实企业需求与身份源确认后做|

较省方案：复用当前RBAC与DB约束，先堵边界；无需求不添新权限框架。

## 7. 生产数据语义与决策

不重置机构、岗位、角色或授权，不以tenant_id=1识别同企业。待决：哪些菜单/字典跨租户共享、管理员授权上限、停用人员/角色对在途流程的政策、删除vs恢复身份语义。重复编码先列冲突清单与引用，不自动合并；角色合并会改M17候选权限与历史审计，须业务签字。树修复须只读预览、目标表备份、幂等增量方案和回滚清单；本轮不执行。变更原因是边界和生命周期闭合，影响M17/M19/人事及security缓存；回滚保留授权审计，不回填宽权限“临时救火”。

## 8. 验证方案

仅计划，不执行本地Java/mvn，不写测试。授权后CI单测+真实DB契约：租户A枚举/修改B对象均拒绝；平台共享菜单按白名单；机构自环/后代环拒绝、合法移动后所有后代路径一致；并发引用与删除不留孤儿；角色停用/权限撤销即时或在确认窗口内生效；最后管理员不能删除；二次删除重建第三次仍成功，关联重复授权幂等；JSON错误400、完整性409、已知编码具体提示，分别检查HTTP状态与R.code。租户9999测试数据清理不得误删真实sys对象。PC机构/岗位/角色/菜单权限及M17普通财务、M19范围联验；App不新增管理入口，先查路由和需求。无覆盖率测量与线上成功声明。

## 9. 门A待确认与确认记录

- 待确认A1-A4、平台共享边界、授权上限/管理员保护、角色停用在途策略。
- 待核实生产DDL/索引、数据范围拦截全链路、HTTP真实状态、sys对象共享设计、测试覆盖实测。
- 门A未确认；门B未进入。任何存量组织/权限变更均需另批，蓝图不是执行授权。
