# Tasks

- [x] 阅读审计报告与 AGENTS，核对初始工作区并建立 fix/dual-server-permission-audit 分支。
- [x] Requirements → Design → Tasks 记录授权、安全范围、测试及回滚。
- [ ] 重查角色共享架构、权限SQL、普通详情、业务详情及审批操作。
- [ ] 真实双机身份、逐行关联/业务引用、运行实例与节点候选清单。
- [ ] 实施最小代码修复及正常/拒绝路径单测；检查前端竞态与claim意见。
- [ ] 执行前端测试及接口一致性审计；后端 CI/安全非生产验证，受阻登记。
- [ ] 证据明确的生产修复：明细、备份验证、幂等守卫、改后复核；模糊项登记用户决策。
- [ ] 向主会话提交全部文件、diff、测试及生产证据，等待独立只读审阅结果。
- [ ] 无 P0/P1 后提交本scope，CI通过后按既有方式发布main并监控双机部署。
- [ ] 双机只读/API/必要tenant9999验收与完整R7对比；持久最终验收报告及临时文件清理。

## 当前证据
初始 HEAD：087cff956a0355ff2b1f264659b3b484d04d56eb。用户审计报告 untracked 不夹带。SysUserMapper 角色/权限SQL确实缺少启用、删除及关联租户条件；ApprovalService.getTaskDetail 仅调用 assertSameTenant，相关人判定仅在 BusinessDetailService 私有方法，普通详情未保护。其余报告发现仍待独立实际重查。

## 2026-10-09 接续实现与审阅门范围
- 已实现：角色SQL启用/删除/同租户或NULL共享角色过滤；任务普通与业务详情统一相关人授权；complete白名单源单存在守卫（PAYMENT_APPLY补映射），batch复用同事务；trace同租户相关人授权；withdraw缺失发起人拒绝；转办目标status/tenant NULL拒绝；防自审变量读取失败传播、受保护类型缺发起人拒绝；业务ID溢出403及旧依赖清理。
- 测试：审批前端15/15 PASS；正确root consistency 905后端/788PC/93移动/20模块，Critical=0 Major=0 Minor=335；git diff --check通过。后端仅补Mockito测试与fixtures，未执行，待独立review后测试分支CI。
- 双机只读复核：43企业安徽徽颍建设，跨租户user_role9999001/9999003仍2条；129企业中正建设集团，role1 tenant NULL合法。异常业务43逐条22、129逐条6均project不存在，无法唯一推定正确归属，不写。43最新33/33含candidateUsers（旧报告14已变化，需其他执行者变更证据）；129最新34/0。运行任务43=3/过7天0/自指派0；129=166/160/163，未终止实例。
- 本执行者生产写0、commit/push/deploy0，暂无SHA/run/备份，不得填造。最终闭环仍待review、CI、目标表备份后有界修复与双机验收。

## 首次独立review整改（待复审）
P1 source状态/实例及公共启动禁403；PC batch真实详情逐单核验展示；P2 assignee响应契约、持久审计输出及历史快照更正。新增纯测试CI permission-audit-test.yml仅编译单测和artifact。项目立项/终止回写已有实例字段，无迁移。22项PC测试PASS；Java未执行。状态机历史提交即APPROVED六类仅同实例兼容，DRAFT退回需业务重新提交，详报告。未提交/推送/生产写。

## 第二次复审整改（全部三项已实现，待复审/CI）
- HR5严格当前实例协议双轨幂等V2026_88；Service保存实例且update==1，缺/歧义历史拒绝，未自动回填。
- FINAL_SETTLEMENT同实例+REJECT_TO_START/REJECT证据允许DRAFT重审，初始草稿/旧实例单批拒绝。
- 项目立项/终止update0失败回滚与测试；全量串行纯测试CI覆盖全部下游。
- PC22/22复验通过；Java未执行；43历史HR SUBMITTED0、1297条用印唯一候选待备份复核写入。生产写0、提交推送0。

第三review整改：rejectPrevious/rejectStart/terminate/withdraw独立当前binding守卫，不按状态/删除拦安全回收；HR listener事件实例二次验证，陈旧/已删不dispatch。补HR5current/stale、旧A三个动作及withdraw拒绝测试；Final DRAFT fixture initiator300及具体源单错误/selectCount六次断言。用户仅批准修复分支纯测试CI，严禁main/部署。
