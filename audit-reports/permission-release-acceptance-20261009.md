# 双机权限修复发布验收（2026-10-09）

## 发布与结论

发布代码 SHA `81782cd1dbd7790ee33fa567dfc141906de21b8d`，正常 push main，无 force、无 fast_deploy。唯一追加测试文件 `zw-insight-web/src/__tests__/workflow-pages.component.test.ts`，4增3删，原断言未改；独立审阅已由用户提供确认。

CI/CD Deploy to BaoTa Server run [37890289140](https://github.com/gerrardLT/ZW-Insight/actions/runs/37890289140) SUCCESS。Backend Build 113689454963、PC 113689455131、app 113689455090、supplier 113689455256、runner frontend dist 113689455128、server1 Deploy 113691033513、server2 Deploy 113691033473 均success。两机Health check与生产API docs关闭断言success。L2/Integration/k6按push策略skip，不记通过。未本地启动Java/Maven，未生产执行Node/Vite构建。

部署及有界数据修复完成；整体验收有保留项，不能称全功能通过。跨租户详情failclosed无数据，但错误语义code500；L4未执行。

## 实际远端身份及备份

实际SSH核对：129.204.3.200 hostname VM-0-15-opencloudos；43.142.44.145 hostname VM-0-2-opencloudos。两机容器zwi-backend/zwi-mysql，backend datasource数据库zw_insight、用户root，由inspect读取配置但不输出密码。129 sys_org根机构90021/tenant1/中正建设集团有限公司；43根机构2105101928955174914/tenant1/安徽徽颍建设。两库独立，不凭tenant1混同。

备份目录 `/root/zwi-deploy/backups/permission-audit-20261009-pre88`，本轮重新读取文件并计算SHA256，与发布前记录完全一致：

| 主机 | 文件 | 字节 | SHA256 |
|---|---|---:|---|
|129|tables.sql|14972|de2a835c42ef4abc73bbc788bd607dad6171abd3a43605b7436825c380cf6304|
|129|targets.sql|4278|e5cca37eff7b8e9210b4bff211fe9abf24bfa5294078716f279d1939435b6a98|
|43|tables.sql|12471|8dc3e86562d74f6f8ec31c8d8d5f518e47c38ffebdaee59679bdfbe81f910147|
|43|targets.sql|2278|9fa16d32062992600a27064edae504a3531714a773bad6713a1e8a858e1c7c35|

首次find -exec命令终止符传递失败；已改Python完成哈希核验。首次sys_tenant查询误用name列报1054，已SHOW COLUMNS后使用tenant_name复核；SQL错误未当零违规。

## Flyway与角色

两机flyway_schema_history version2026.88 success1。biz_regular_apply/biz_resign_apply/biz_seal_apply/biz_transfer_apply/biz_vehicle_apply均实际存在workflow_instance_id varchar(64)。

43此前两条跨租户关联9999001/9999003→role1 tenant1已由前执行者修复；本轮只读确认cross_tenant=0。9999001、9999003各仅一条SUPER_ADMIN关联role9999901/tenant9999；9999002仅T9999_LIMITED role9999900/tenant9999，无SUPER_ADMIN。129cross_tenant=0，9999001与9999003关联受控NULL共享role1，9999002仅LIMITED。本轮没有写角色或扩大权限，未重跑init-test-tenant。

## 129七条用印来源绑定

写前逐条重新查询：tenant1、SUBMITTED、deleted0、workflow_instance_id NULL；businessKey为SEAL_APPLY:<id>，全库唯一runtime root、runtime与history同tenant1、history END_TIME NULL、同实例task1。备份哈希再次assert通过。

单一MySQL事务：临时CHECK(n=7)守卫先验证合格数7；UPDATE仅明确七ID且再次附所有来源/状态/tenant/唯一根/历史未结束/task1条件；ROW_COUNT再插入CHECK守卫；COMMIT后读取。任一不满足MySQL非force中止连接，未提交事务回滚。实际CHANGED=7、SQL_EXIT=0。只写biz_seal_apply.workflow_instance_id，未写ACT_*，未改状态、删除标记或流程定义。

| source id | before | after instance |
|---|---|---|
|2090607321489141762|NULL|094c4bec-9cfd-11f1-b952-0242ac130006|
|2090618775554551810|NULL|64efdd72-9d03-11f1-8394-0242ac130006|
|2090628258695540738|NULL|a891c304-9d08-11f1-b9b5-0242ac130006|
|2090649868991262721|NULL|a7ae97ed-9d14-11f1-a13e-0242ac130006|
|2091518529624928258|NULL|dbc8338c-9ef6-11f1-98da-0242ac130006|
|2091533727224098818|NULL|4ba17e95-9eff-11f1-b2bd-0242ac130006|
|2091552359626895361|NULL|a3497bc8-9f09-11f1-9665-0242ac130006|

回滚仅可在再次核对来源和实例未变后，将以上明确ID且绑定仍等于本表实例的字段恢复NULL；不得覆盖后续业务合法变化。未执行回滚。

## strict R7

复用旧代理远端副本 `/root/zwi-deploy/_permission-r7-20261009/_audit.sh`。已实际审查Q：session READ ONLY、MYSQL_PWD、SQL_ERROR sentinel、持久_errors旁路；main检测旁路即FAIL。两机故意不存在列均NEGATIVE_SQL_ERROR_TRIGGERED且exit97。完整after没有SQL旁路错误。

| 主机 | before PASS/FAIL/WARN/INFO | after | exit |
|---|---|---|---|
|129|67/0/0/39|67/0/0/39|0|
|43|64/0/1/38|64/0/1/38|2（既有WARN）|

43 WARN为演示种子项目0 vs脚本期望4，合法环境差异，不造种子消警。CBS为空不等于链路已覆盖。正式证据见permission-r7-before/after-{IP}-20261009.md。after UTC06:02:01与06:02:15开始，完整Section0-7。

## 真实API边界

两机各使用正式契约t9999admin/t9999approver/t9999user及正式密码、真实captcha API和Redis对应验证码登录；未猜其他用户、未伪造JWT、未清生产登录锁、未token URL、未curl -v。token仅进程内存。

六次登录均HTTP200/code200。三个账号todo均HTTP200/code200/total0。admin与approver project/page均HTTP200/code200/total0；limited project/page均HTTP403/code403/data缺失。三个账号通用approval/start空请求均HTTP200/code403/data缺失，没有启动实例。

对SQL选出的tenant1真实运行task（129 025ba652-9ef6-11f1-98da-0242ac130006；43 66c9722c-c36f-11f1-8915-0a42b6fb39c4），三个测试账号detail与business均HTTP200/code500/data缺失。failclosed无数据泄露，但不是正确403错误语义，不标语义PASS。源码assertSameTenant按不存在抛默认BusinessException，需另行审核修复。普通tenant1非参与人HTTP未验，不能以测试租户越界替代同tenant相关人验证；不存在有效获授权凭证，不猜密码。

## 保留项与安全边界

- 28条项目引用异常（43=22/129=6）指向不存在项目，不能唯一归属，保留need user decision；未删除或造项目。
- 129长期任务160、自指派163不是垃圾证明，不批终止；2条FINANCE未来/在途无明确审批人处理授权，保留风险。
- 未重新部署43流程定义，未改129流程定义。
- L4 `keys/lifecycle-sim-v2.sh:335-343` direct DELETE ACT_*不安全；无当前run API withdraw及限定biz清理证明，按用户指示登记不跑。原init含43角色租户错误假设，未执行。19阶段L4未验，非PASS。
- 本轮无业务写测试；唯生产写为七条明确source实例绑定。登录仅产生正常登录审计/设备记录。

受阻登记已更新test-maturity-upgrade/tasks.md。正式报告可持久保存；临时strict目录在证据落本地后清理，不删生产备份与既有非本任务文件。
