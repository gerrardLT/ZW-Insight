# Implementation Plan: P1 绯荤粺瀹屾暣搴﹀寮?

## Overview

鏈疄鐜拌鍒掕鐩?ZW-Insight 宸ョ▼椤圭洰绠＄悊绯荤粺 P1 浼樺厛绾х殑 8 涓郴缁熷畬鏁村害澧炲己鍔熻兘鐨勫叏閮ㄥ紑鍙戜换鍔°€備换鍔℃寜渚濊禆鍏崇郴鍒嗕负 5 娉㈡鎵ц锛屼粠鏁版嵁搴?Schema 鍒板悗绔湇鍔″埌鍓嶇椤甸潰鍐嶅埌娴嬭瘯楠岃瘉銆?

## Task Dependency Graph

```json
{
  "waves": [
    {
      "name": "Wave 1: 鍩虹璁炬柦",
      "tasks": [1]
    },
    {
      "name": "Wave 2: 鏍稿績鍚庣鏈嶅姟",
      "tasks": [2, 3, 5, 6, 7, 8, 9, 10]
    },
    {
      "name": "Wave 3: API 灞?,
      "tasks": [4]
    },
    {
      "name": "Wave 4: 鍓嶇椤甸潰",
      "tasks": [11, 12, 13, 14, 15, 16, 17, 18]
    },
    {
      "name": "Wave 5: 娴嬭瘯楠岃瘉",
      "tasks": [19, 20]
    }
  ]
}
```

## Tasks

- [x] 1. 鏁版嵁搴?Schema 杩佺Щ
  - Requirements: R1, R3, R4, R5, R6, R7, R8
  - Dependencies: None
  - Description: 鍒涘缓鍏ㄩ儴鏂板鏁版嵁琛ㄥ拰瀛楁鍙樻洿鐨?SQL 杩佺Щ鑴氭湰
  - Sub-tasks:
    1. [x] 鍒涘缓杩佺Щ鑴氭湰 `V2026_07__p1_system_integrity.sql`
    2. [x] ALTER `sys_role` 琛ㄦ柊澧?`data_scope` 瀛楁锛堥粯璁?SELF锛?
    3. [x] CREATE TABLE `sys_user_project`锛堝惈鍞竴绱㈠紩 uk_user_project锛?
    4. [x] CREATE TABLE `biz_project_member`锛堝惈鍞竴绱㈠紩 uk_project_user锛?
    5. [x] CREATE TABLE `sys_config`锛堝惈鍞竴绱㈠紩 uk_config_key锛?
    6. [x] CREATE TABLE `sys_config_change_log`
    7. [x] CREATE TABLE `biz_machine_work_settlement`锛堝惈鍞竴绱㈠紩 uk_settlement_code锛?
    8. [x] CREATE TABLE `biz_machine_work_settlement_detail`
    9. [x] CREATE TABLE `biz_approval_snapshot`
    10. [x] CREATE TABLE `biz_approval_rollback_log`
    11. [x] ALTER `sys_tenant` 琛ㄦ柊澧?user_type, start_date, end_date, max_users, modules 瀛楁
    12. [x] INSERT 鍒濆鍖栫郴缁熼厤缃暟鎹紙瀹夊叏/瀹℃壒/鏂囦欢/閫氱煡璁剧疆榛樿鍙傛暟锛?
    13. [x] 鍦ㄥ紑鍙戠幆澧冩墽琛岃縼绉昏剼鏈苟楠岃瘉琛ㄧ粨鏋勬纭?

- [x] 2. 鏁版嵁鏉冮檺鎷︽埅鍣?
  - Requirements: R1 (AC 1-10)
  - Dependencies: Task 1, Task 3
  - Description: 瀹炵幇 MyBatis-Plus DataPermissionInnerInterceptor锛屾牴鎹鑹叉暟鎹寖鍥磋嚜鍔ㄨ拷鍔?SQL 杩囨护鏉′欢
  - Sub-tasks:
    1. [x] 鍒涘缓 `DataScopeEnum` 鏋氫妇锛圓LL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF锛夊惈浼樺厛绾у睘鎬?
    2. [x] 鍦?`zw-common` 妯″潡鍒涘缓 `@DataPermission` + `@DataColumn` 娉ㄨВ
    3. [x] 瀹炵幇 `ZwDataPermissionHandler`锛圡yBatis-Plus 鏁版嵁鏉冮檺澶勭悊鎺ュ彛锛?
    4. [x] 瀹炵幇 `getEffectiveScope()`锛氬瑙掕壊鍙栨渶澶ф暟鎹寖鍥?
    5. [x] 瀹炵幇 `buildSelfCondition()`锛歐HERE created_by = #{userId}
    6. [x] 瀹炵幇 `buildProjectCondition()`锛歐HERE project_id IN (鐢ㄦ埛鍙備笌椤圭洰ID)
    7. [x] 瀹炵幇 `buildDeptCondition()`锛歐HERE dept_id = #{userDeptId}
    8. [x] 瀹炵幇 `buildDeptAndChildrenCondition()`锛歐HERE dept_id IN (閮ㄩ棬鍙婂瓙閮ㄩ棬)
    9. [x] 鍦?`MybatisPlusConfig` 涓敞鍐屾嫤鎴櫒锛圱enantLine 涔嬪悗銆丳agination 涔嬪墠锛?
    10. [x] 鍦ㄥ叧閿?Mapper 鏂规硶涓婃坊鍔?`@DataPermission` 娉ㄨВ
    11. [x] 楠岃瘉鏁版嵁鏉冮檺閰嶇疆鍙樻洿鍚庡疄鏃剁敓鏁堬紙鏃犵紦瀛橈級

- [x] 3. 椤圭洰鎴愬憳绠＄悊鍚庣
  - Requirements: R3 (AC 1-9)
  - Dependencies: Task 1
  - Description: 瀹炵幇椤圭洰鎴愬憳 CRUD銆佽鑹插垎閰嶃€佸敮涓€鎬ф牎楠屽拰绂昏亴鑱斿姩閫昏緫
  - Sub-tasks:
    1. [x] 鍒涘缓 `ProjectRoleEnum` 鏋氫妇锛?绉嶉」鐩鑹诧級
    2. [x] 鍒涘缓 `BizProjectMember` 瀹炰綋绫伙紙缁ф壙 BaseEntity锛?
    3. [x] 鍒涘缓 `BizProjectMemberMapper` 鎺ュ彛 + XML
    4. [x] 瀹炵幇 `addMember()`锛氬惈鍞竴鎬ф牎楠岋紙鍚岄」鐩悓鐢ㄦ埛涓嶅彲閲嶅锛?
    5. [x] 瀹炵幇 `removeMember()`锛氬惈鍞竴椤圭洰缁忕悊淇濇姢鏍￠獙
    6. [x] 瀹炵幇 `updateRoles()`锛氭洿鏂伴」鐩鑹插垪琛?
    7. [x] 瀹炵幇 `listMembers()`锛氬垎椤垫煡璇?+ 鎸夎鑹茬瓫閫?
    8. [x] 瀹炵幇 `getUserProjectIds()`锛氫緵鏁版嵁鏉冮檺妯″潡浣跨敤
    9. [x] 瀹炵幇 `deactivateByUserId()`锛氱敤鎴峰仠鐢ㄦ椂鏍囪鎵€鏈夋垚鍛樹负宸插け鏁?
    10. [x] 鍒涘缓 `ProjectMemberController`锛圧EST API锛?
    11. [x] 鍦ㄩ」鐩垱寤烘祦绋嬩腑鑷姩灏嗗垱寤轰汉娣诲姞涓洪」鐩粡鐞?
    12. [x] 鍚屾缁存姢 `sys_user_project` 琛ㄦ暟鎹?

- [x] 4. 鏁版嵁鏉冮檺閰嶇疆 API
  - Requirements: R1 (AC 2)
  - Dependencies: Task 2
  - Description: 鎻愪緵瑙掕壊鏁版嵁鑼冨洿閰嶇疆 REST API锛屽厑璁哥鐞嗗憳涓鸿鑹茶缃暟鎹潈闄愮骇鍒?
  - Sub-tasks:
    1. [x] 鍦?`SysRole` 瀹炰綋绫绘柊澧?`dataScope` 瀛楁
    2. [x] 鍒涘缓 `DataScopeUpdateRequest` DTO
    3. [x] 鍦?`SysRoleService` 鏂板 `updateDataScope()` 鏂规硶
    4. [x] 鍦?`SysRoleController` 鏂板 `PUT /api/v1/system/role/{id}/data-scope`
    5. [x] 娣诲姞鏉冮檺鏍￠獙锛氫粎绯荤粺绠＄悊鍛樺彲閰嶇疆
    6. [x] 鍗曞厓娴嬭瘯锛氫笉鍚堟硶 dataScope 鍊艰鎷掔粷

- [x] 5. 寮曠敤鏍￠獙娉ㄨВ + AOP
  - Requirements: R6 (AC 1-9)
  - Dependencies: Task 1
  - Description: 瀹炵幇 @ReferenceCheck 娉ㄨВ鍜?AOP 鍒囬潰锛屽湪鍒犻櫎鎿嶄綔鍓嶈嚜鍔ㄦ牎楠屽紩鐢ㄥ叧绯?
  - Sub-tasks:
    1. [x] 鍦?`zw-common` 鍒涘缓 `@ReferenceCheck` 鍜?`@ReferenceRelation` 娉ㄨВ
    2. [x] 鍒涘缓 `ReferenceInfoVO` 鍜?`ReferenceExistsException`
    3. [x] 瀹炵幇 `ReferenceCheckAspect` AOP 鍒囬潰锛園Before 鎷︽埅锛?
    4. [x] 瀹炵幇 `extractEntityId()` 浠庢柟娉曞弬鏁版彁鍙栧疄浣?ID
    5. [x] 瀹炵幇寮曠敤璁℃暟鏌ヨ锛堝弬鏁板寲 SQL 闃叉敞鍏ワ級
    6. [x] 瀹炵幇寮曠敤璇︽儏鏌ヨ锛堟渶澶氬墠 10 鏉★級
    7. [x] 寮傚父澶勭悊锛欴B 鏌ヨ寮傚父鏃堕樆姝㈠垹闄ゅ苟璁板綍 ERROR 鏃ュ織
    8. [x] 浜哄憳璇佷欢鍒犻櫎娣诲姞 @ReferenceCheck锛堟姇鏍囨姤鍚嶃€佹姇鏍囦换鍔★級
    9. [x] 鐝粍鍒犻櫎娣诲姞 @ReferenceCheck锛堣姳鍚嶅唽銆佺敤宸ュ崟銆佸伐璧勫崟锛?
    10. [x] 鏈烘鍙拌处鍒犻櫎娣诲姞 @ReferenceCheck锛堣繘鍑哄満銆佸伐浣滈噺銆佸悎鍚岋級
    11. [x] 鍏徃璇佷欢鍒犻櫎娣诲姞 @ReferenceCheck锛堟姇鏍囨姤鍚嶏級
    12. [x] 渚涘簲鍟嗗垹闄ゆ坊鍔?@ReferenceCheck锛堥噰璐悎鍚屻€佸叆搴撳崟銆佽浠凤級
    13. [x] 鏉愭枡瀛楀吀鍒犻櫎娣诲姞 @ReferenceCheck锛堝悎鍚屾槑缁嗐€佸叆搴撴槑缁嗐€佸簱瀛橈級
    14. [x] 鍏ㄥ眬寮傚父澶勭悊鍣ㄦ敞鍐?ReferenceExistsException 鍝嶅簲

- [x] 6. 绯荤粺璁剧疆鍚庣
  - Requirements: R5 (AC 1-10)
  - Dependencies: Task 1
  - Description: 瀹炵幇绯荤粺鍙傛暟 CRUD銆佸€艰寖鍥存牎楠屻€丷edis 缂撳瓨鍜屽彉鏇存棩蹇?
  - Sub-tasks:
    1. [x] 鍒涘缓 `SysConfig` 瀹炰綋绫?+ Mapper
    2. [x] 鍒涘缓 `SysConfigChangeLog` 瀹炰綋绫?+ Mapper
    3. [x] 瀹炵幇 `listByGroup()`锛氭寜鍒嗙粍鏌ヨ閰嶇疆
    4. [x] 瀹炵幇 `updateConfig()`锛氬惈鍊艰寖鍥存牎楠岄€昏緫
    5. [x] 瀹炵幇鍙傛暟鍊艰寖鍥存牎楠屽櫒锛氳В鏋?value_range 鏍煎紡骞舵寜 value_type 鏍￠獙
    6. [x] 瀹炵幇 `batchUpdate()`锛氭壒閲忔洿鏂?
    7. [x] 瀹炵幇 `resetToDefault()`锛氭仮澶嶉粯璁ゅ€?
    8. [x] 瀹炵幇 `getConfigValue()`锛歊edis 缂撳瓨璇诲彇锛坘ey: sys:config:{key}锛岃繃鏈?1h锛?
    9. [x] 瀹炵幇缂撳瓨娓呴櫎锛氭洿鏂板悗鍒犻櫎瀵瑰簲 Redis key
    10. [x] 瀹炵幇鍙樻洿鏃ュ織璁板綍锛氳嚜鍔ㄥ啓鍏?sys_config_change_log
    11. [x] 鍒涘缓 `SystemConfigController`锛圧EST API锛?
    12. [x] 鏉冮檺鏍￠獙锛氫粎绯荤粺绠＄悊鍛樺彲淇敼

- [x] 7. 钖祫缁熻鍚庣
  - Requirements: R2 (AC 1-9)
  - Dependencies: Task 1
  - Description: 瀹炵幇鍔冲姟钖祫鎸夌彮缁?涓汉缁村害缁熻姹囨€汇€佸悓姣旂幆姣旇绠楀拰 Excel 瀵煎嚭
  - Sub-tasks:
    1. [x] 鍒涘缓 VO 绫伙細SalaryStatsSummary, TeamSalaryVO, SalaryDetailVO, SalaryCompareVO
    2. [x] 鍒涘缓 `SalaryStatisticsQuery` 鏌ヨ DTO
    3. [x] 瀹炵幇 `getStatsByTeam()`锛氭寜鐝粍鍒嗙粍姹囨€诲凡瀹℃壒宸ヨ祫鍗?
    4. [x] 瀹炵幇 `getTeamDetail()`锛氱彮缁勫唴宸ヤ汉鏄庣粏锛堝嚭鍕ゃ€佸姞鐝€佸簲鍙戙€佹墸娆俱€佸疄鍙戯級
    5. [x] 瀹炵幇 `generateMonthlyReport()`锛氭眹鎬绘姤琛ㄦ暟鎹?
    6. [x] 瀹炵幇 `getCompareData()`锛氬悓姣?鐜瘮鍙樺寲鐜囷紙绮剧‘灏忔暟鐐瑰悗1浣嶏級
    7. [x] 瀹炵幇 `exportReport()`锛欵asyExcel 澶?Sheet 瀵煎嚭
    8. [x] 瀹炵幇鑷湁鍔冲姟鍜岄浂鏄熺敤宸ュ垎绫荤粺璁?
    9. [x] 绌烘暟鎹鐞嗭細鏃犲鎵规暟鎹椂杩斿洖鎻愮ず
    10. [x] 閲戦璁＄畻浣跨敤 BigDecimal锛坰cale=2, ROUND_HALF_UP锛?
    11. [x] 鍒涘缓 `SalaryStatisticsController`锛圧EST API锛?
    12. [x] 浠庡凡瀹℃壒鐢ㄥ伐鍗曚腑鍏宠仈鍑哄嫟鍜屽姞鐝暟鎹?

- [x] 8. 鏈烘宸ヤ綔閲忕粨绠楀悗绔?
  - Requirements: R4 (AC 1-9)
  - Dependencies: Task 1
  - Description: 瀹炵幇鏈烘缁撶畻鍗曞垱寤恒€佽垂鐢ㄨ绠椼€佸鎵规祦绋嬮泦鎴愬拰 Excel 瀵煎嚭
  - Sub-tasks:
    1. [x] 鍒涘缓 `BizMachineWorkSettlement` 瀹炰綋绫伙紙缁ф壙 BaseEntity锛?
    2. [x] 鍒涘缓 `BizMachineWorkSettlementDetail` 瀹炰綋绫?
    3. [x] 鍒涘缓 Mapper 鎺ュ彛 + XML
    4. [x] 鍒涘缓 VO/DTO锛欳reateRequest, SettlementVO, SummaryVO
    5. [x] 瀹炵幇 `createSettlement()`锛氬懆鏈熼噸鍙犳牎楠?+ 鏃犲伐浣滈噺鏍￠獙
    6. [x] 瀹炵幇鍛ㄦ湡閲嶅彔妫€娴嬶細start1 <= end2 AND start2 <= end1
    7. [x] 瀹炵幇璐圭敤璁＄畻锛氬彴鐝浠?vs 宸ヤ綔閲忚浠凤紙BigDecimal scale=2锛?
    8. [x] 瀹炵幇缁撶畻鍗曠紪鍙疯嚜鍔ㄧ敓鎴?
    9. [x] 瀹炵幇 `submitForApproval()`锛氬惎鍔?Flowable 瀹℃壒
    10. [x] 瀹炵幇 `onApproved()`锛氬鎵归€氳繃鍥炶皟锛岀疮鍔犲悎鍚屽凡缁撶畻閲戦
    11. [x] 瀹炵幇 `getProjectSummary()`锛氶」鐩垂鐢ㄦ€昏
    12. [x] 瀹炵幇 `exportSettlement()`锛欵asyExcel 瀵煎嚭
    13. [x] 鍒涘缓 `MachineSettlementController`锛圧EST API锛?
    14. [x] 娉ㄥ唽 Flowable 鏈烘缁撶畻瀹℃壒娴佺▼瀹氫箟

- [x] 9. SaaS 澶氱鎴风鐞嗗寮?
  - Requirements: R7 (AC 1-10)
  - Dependencies: Task 1
  - Description: 澧炲己绉熸埛绠＄悊锛屽疄鐜扮敤鎴风被鍨嬨€佺画鏈熴€佸仠鐢ㄣ€佸姛鑳芥ā鍧楁潈闄愬拰鍒版湡鑷姩妫€鏌?
  - Sub-tasks:
    1. [x] 鎵╁睍 `SysTenant` 瀹炰綋绫绘柊澧炲瓧娈?
    2. [x] 鍒涘缓 `TenantUserTypeEnum` 鍜?`TenantStatusEnum`
    3. [x] 瀹炵幇 `createTenant()`锛氳嚜鍔ㄧ敓鎴愮紪鐮?+ 鍒濆鍖栫鐞嗗憳 + 璁剧疆榛樿鏈夋晥鏈?
    4. [x] 瀹炵幇 `disableTenant()`锛氭洿鏂扮姸鎬?+ 娓呴櫎 Redis Token
    5. [x] 瀹炵幇 `enableTenant()`锛氭仮澶嶆甯哥姸鎬?
    6. [x] 瀹炵幇 `renewTenant()`锛氱画鏈熷ぉ鏁版牎楠岋紙1-1095锛? 鏈夋晥鏈熺疮鍔?
    7. [x] 瀹炵幇 `updateModules()`锛氶厤缃姛鑳芥ā鍧楁潈闄?
    8. [x] 瀹炵幇 `checkUserLimit()`锛氭椿璺冪敤鎴锋暟涓婇檺鏍￠獙
    9. [x] 瀹炵幇妯″潡鏉冮檺鎷︽埅鍣細鏈巿鏉冩ā鍧?API 杩斿洖 403
    10. [x] 瀹炵幇瀹氭椂浠诲姟 `checkExpiredTenants()`锛氭瘡澶╁噷鏅ㄦ鏌ュ埌鏈?
    11. [x] 瀹炵幇瀹氭椂浠诲姟 `sendRenewalReminders()`锛氬埌鏈熷墠 15/7 澶╂彁閱?
    12. [x] 鐧诲綍鎷︽埅鍣ㄥ鍔犵鎴风姸鎬佹牎楠?
    13. [x] 澧炲己 `TenantController` 鏂板 API 鎺ュ彛

- [x] 10. 瀹℃壒鏁版嵁鍥炴粴
  - Requirements: R8 (AC 1-10)
  - Dependencies: Task 1
  - Description: 瀹炵幇瀹℃壒椹冲洖/鎾ゅ洖鏃惰嚜鍔ㄦ暟鎹洖婊氾紝鍚揩鐓ц褰曘€佺瓥鐣ユā寮忓拰涔愯閿侀噸璇?
  - Sub-tasks:
    1. [x] 鍒涘缓 `BizApprovalSnapshot` 瀹炰綋绫?+ Mapper
    2. [x] 鍒涘缓 `BizApprovalRollbackLog` 瀹炰綋绫?+ Mapper
    3. [x] 瀹氫箟 `RollbackStrategy` 鎺ュ彛
    4. [x] 瀹炵幇 `RollbackStrategyRegistry` 鑷姩娉ㄥ唽
    5. [x] 瀹炵幇 `saveSnapshot()`锛氬鎵规彁浜ゆ椂璁板綍蹇収
    6. [x] 鍚勪笟鍔″鎵规彁浜ゅ叆鍙ｈ皟鐢?saveSnapshot
    7. [x] 瀹炵幇 `executeRollback()`锛氱瓥鐣ユ煡鎵?鈫?鍐茬獊妫€娴?鈫?浜嬪姟鍥炴粴 鈫?鏃ュ織
    8. [x] 瀹炵幇鍐茬獊妫€娴嬶細蹇収鍊?vs 褰撳墠鍊间笉涓€鑷存爣璁板啿绐?
    9. [x] 瀹炵幇涔愯閿侀噸璇曪紙鏈€澶?3 娆★級
    10. [x] 瀹炵幇 6 绉嶄笟鍔″洖婊氱瓥鐣?
    11. [x] 闆嗘垚 Flowable TaskListener锛氶┏鍥?鎾ゅ洖 鈫?Spring Event 鈫?鍥炴粴
    12. [x] 瀹炵幇瓒呮椂妫€娴嬶紙>5s 鏍囪澶辫触 + 閫氱煡绠＄悊鍛橈級
    13. [x] 瀹炵幇鍥炴粴璁板綍鏌ヨ鎺ュ彛
    14. [x] 瀹炵幇鍐茬獊纭鎺ュ彛
    15. [x] 鍒涘缓 `ApprovalRollbackController`锛圧EST API锛?

- [x] 11. 椤圭洰鎴愬憳绠＄悊鍓嶇
  - Requirements: R3 (AC 3, 8)
  - Dependencies: Task 3
  - Description: 瀹炵幇椤圭洰璇︽儏椤?椤圭洰鍥㈤槦"鏍囩椤碉紝鍚垚鍛樺垪琛ㄥ拰瑙掕壊绠＄悊鎿嶄綔
  - Sub-tasks:
    1. [x] 鍒涘缓 `src/views/project/components/ProjectMember.vue`
    2. [x] 瀹炵幇鎴愬憳鍒楄〃琛ㄦ牸锛堝鍚嶃€侀儴闂ㄣ€佽鑹叉爣绛俱€佸姞鍏ユ椂闂淬€佹搷浣滃垪锛?
    3. [x] 瀹炵幇鎸夎鑹茬瓫閫変笅鎷夋
    4. [x] 瀹炵幇"娣诲姞鎴愬憳"寮圭獥锛氱敤鎴疯繙绋嬫悳绱?+ 瑙掕壊澶氶€?
    5. [x] 瀹炵幇"绉婚櫎鎴愬憳"纭寮圭獥 + 閿欒鎻愮ず
    6. [x] 瀹炵幇"鍙樻洿瑙掕壊"寮圭獥
    7. [x] 鍦ㄩ」鐩鎯呴〉娣诲姞"椤圭洰鍥㈤槦"鏍囩椤?
    8. [x] API 灞傚皝瑁咃紙src/api/project.ts锛?
    9. [x] 閿欒鍝嶅簲 Toast 鎻愮ず澶勭悊

- [x] 12. 鏁版嵁鏉冮檺閰嶇疆鍓嶇
  - Requirements: R1 (AC 2)
  - Dependencies: Task 4
  - Description: 瑙掕壊绠＄悊椤甸潰澧炲姞鏁版嵁鏉冮檺閰嶇疆鐣岄潰
  - Sub-tasks:
    1. [x] 瑙掕壊缂栬緫椤靛鍔?鏁版嵁鏉冮檺"閰嶇疆鍖哄煙
    2. [x] 瀹炵幇鏁版嵁鑼冨洿涓嬫媺閫夋嫨鍣?
    3. [x] 璋冪敤 PUT /api/v1/system/role/{id}/data-scope
    4. [x] 瑙掕壊鍒楄〃灞曠ず鏁版嵁鑼冨洿鏍囩
    5. [x] API 灞傚皝瑁?

- [x] 13. 绯荤粺璁剧疆鍓嶇
  - Requirements: R5 (AC 2)
  - Dependencies: Task 6
  - Description: 瀹炵幇绯荤粺璁剧疆椤甸潰锛屾寜鍒嗙粍鏍囩椤靛睍绀哄苟鍔ㄦ€佹覆鏌撹緭鍏ユ帶浠?
  - Sub-tasks:
    1. [x] 鍒涘缓 `src/views/system/config/index.vue`
    2. [x] 瀹炵幇鏍囩椤靛垏鎹紙瀹夊叏/瀹℃壒/鏂囦欢/閫氱煡璁剧疆锛?
    3. [x] 鍔ㄦ€佽〃鍗曟覆鏌擄細鎸?value_type 娓叉煋鎺т欢
    4. [x] 瀹炵幇鍊艰寖鍥存彁绀?
    5. [x] 瀹炵幇淇濆瓨 + 鏍￠獙澶辫触鎻愮ず
    6. [x] 瀹炵幇"鎭㈠榛樿鍊?鎸夐挳
    7. [x] API 灞傚皝瑁咃紙src/api/system.ts锛?
    8. [x] 娉ㄥ唽璺敱 + 鑿滃崟椤?

- [x] 14. 钖祫缁熻鍓嶇
  - Requirements: R2 (AC 1-9)
  - Dependencies: Task 7
  - Description: 瀹炵幇钖祫缁熻椤甸潰锛屽惈绛涢€夈€佺彮缁勬眹鎬汇€佸悓姣旂幆姣斿拰 Excel 瀵煎嚭
  - Sub-tasks:
    1. [x] 鍒涘缓 `src/views/labor/salary/stats.vue`
    2. [x] 瀹炵幇绛涢€夋爮锛氶」鐩紙蹇呴€夛級+ 鏈堜唤锛堝繀閫夛級+ 鐝粍 + 宸ヤ汉濮撳悕
    3. [x] 瀹炵幇鐝粍姹囨€昏〃鏍?
    4. [x] 瀹炵幇鐝粍鏄庣粏灞曞紑
    5. [x] 瀹炵幇鍚屾瘮鐜瘮鏁版嵁鍗＄墖
    6. [x] 瀹炵幇鑷湁鍔冲姟/闆舵槦鐢ㄥ伐鍒嗙被 Tab
    7. [x] 瀹炵幇 Excel 瀵煎嚭鎸夐挳
    8. [x] 绌烘暟鎹姸鎬佸睍绀?
    9. [x] API 灞傚皝瑁咃紙src/api/labor.ts锛?
    10. [x] 娉ㄥ唽璺敱 + 鑿滃崟椤?

- [x] 15. 鏈烘缁撶畻鍓嶇
  - Requirements: R4 (AC 1-9)
  - Dependencies: Task 8
  - Description: 瀹炵幇鏈烘缁撶畻绠＄悊椤甸潰锛屽惈鍒涘缓銆佸鎵规彁浜ゃ€佽垂鐢ㄦ€昏鍜屽鍑?
  - Sub-tasks:
    1. [x] 鍒涘缓 `src/views/machine/settlement/index.vue` 鍒楄〃椤?
    2. [x] 瀹炵幇缁撶畻鍗曞垪琛ㄨ〃鏍?
    3. [x] 鍒涘缓 `src/views/machine/settlement/create.vue` 鍒涘缓椤?
    4. [x] 瀹炵幇鍒涘缓琛ㄥ崟锛氶」鐩?+ 鍛ㄦ湡閫夋嫨
    5. [x] 瀹炵幇鏈烘鏄庣粏棰勮
    6. [x] 瀹炵幇鎻愪氦瀹℃壒 + 鐘舵€佸睍绀?
    7. [x] 鍒涘缓璇︽儏椤甸潰
    8. [x] 瀹炵幇椤圭洰璐圭敤鎬昏鍗＄墖
    9. [x] 瀹炵幇 Excel 瀵煎嚭
    10. [x] API 灞傚皝瑁咃紙src/api/machine.ts锛?
    11. [x] 娉ㄥ唽璺敱 + 鑿滃崟椤?

- [x] 16. 绉熸埛绠＄悊鍓嶇澧炲己
  - Requirements: R7 (AC 9)
  - Dependencies: Task 9
  - Description: 澧炲己绉熸埛绠＄悊椤甸潰锛屾柊澧炲仠鐢?鍚敤/缁湡鍜屽姛鑳芥ā鍧楅厤缃?
  - Sub-tasks:
    1. [x] 澧炲己鍒楄〃琛ㄦ牸锛氱敤鎴风被鍨嬨€佹湁鏁堟湡銆佷娇鐢ㄩ噺鍒?
    2. [x] 瀹炵幇鐘舵€佸拰绫诲瀷绛涢€?
    3. [x] 瀹炵幇"鍋滅敤"/"鍚敤"鎿嶄綔鎸夐挳
    4. [x] 瀹炵幇"缁湡"寮圭獥锛堝ぉ鏁拌緭鍏?1-1095锛?
    5. [x] 瀹炵幇"鍔熻兘妯″潡閰嶇疆"寮圭獥锛堟ā鍧楀閫夛級
    6. [x] 澧炲己鍒涘缓琛ㄥ崟锛氱敤鎴风被鍨?+ 鏈夋晥鏈?+ 涓婇檺
    7. [x] API 灞傚皝瑁?

- [x] 17. 瀹℃壒鍥炴粴鍓嶇
  - Requirements: R8 (AC 9, 10)
  - Dependencies: Task 10
  - Description: 瀹炵幇鍥炴粴璁板綍鏌ヨ椤甸潰鍜屽鎵硅鎯呴〉鍥炴粴鐘舵€佸睍绀?
  - Sub-tasks:
    1. [x] 鍒涘缓 `src/views/workflow/rollback/index.vue`
    2. [x] 瀹炵幇鍥炴粴璁板綍琛ㄦ牸
    3. [x] 瀹炵幇绛涢€夋爮锛氶」鐩?+ 涓氬姟绫诲瀷 + 鏃堕棿鑼冨洿 + 鐘舵€?
    4. [x] 瀹炵幇鍐茬獊纭鎿嶄綔寮圭獥
    5. [x] 瀹℃壒璇︽儏椤靛鍔犲洖婊氫俊鎭尯鍩?
    6. [x] 鍐茬獊澶勭悊鎸囧紩鏂囨
    7. [x] API 灞傚皝瑁咃紙src/api/workflow.ts锛?
    8. [x] 娉ㄥ唽璺敱 + 鑿滃崟椤?

- [x] 18. 寮曠敤鏍￠獙鍓嶇闆嗘垚
  - Requirements: R6 (AC 8)
  - Dependencies: Task 5
  - Description: 鍦ㄥ垹闄ょ‘璁ゅ脊绐椾腑闆嗘垚寮曠敤鏍￠獙缁撴灉锛屾湁寮曠敤鏃剁鐢ㄧ‘璁ゆ寜閽?
  - Sub-tasks:
    1. [x] 鍒涘缓閫氱敤缁勪欢 `src/components/ReferenceCheckDialog.vue`
    2. [x] 瀹炵幇寮曠敤鏍￠獙缁撴灉灞曠ず鍒楄〃
    3. [x] 鏈夊紩鐢ㄦ椂绂佺敤"纭鍒犻櫎"鎸夐挳
    4. [x] 鏃犲紩鐢ㄦ椂姝ｅ父鍒犻櫎娴佺▼
    5. [x] 鍦ㄨ瘉浠?鐝粍/鍙拌处/渚涘簲鍟?鏉愭枡瀛楀吀鍒犻櫎涓泦鎴?
    6. [x] 缁熶竴瑙ｆ瀽 ReferenceExistsException 鍝嶅簲

- [x] 19. Property-Based Tests
  - Requirements: R1-R8锛堟纭€у睘鎬?1-20锛?
  - Dependencies: Task 2, Task 3, Task 5, Task 6, Task 7, Task 8, Task 9, Task 10
  - Description: 浣跨敤 jqwik 妗嗘灦缂栧啓灞炴€ф祴璇曪紝楠岃瘉鏍稿績涓氬姟閫昏緫鐨勬纭€у睘鎬?
  - Sub-tasks:
    1. [x] 娣诲姞 jqwik 渚濊禆鍒?pom.xml锛坣et.jqwik:jqwik:1.8.x锛?
    2. [x] Property 1锛欴ataPermissionHandler SQL 杩囨护鏉′欢姝ｇ‘鎬?
    3. [x] Property 2锛氬瑙掕壊鏁版嵁鑼冨洿浼樺厛绾ц绠?
    4. [x] Property 3锛氳柂璧勬眹鎬昏仛鍚堢簿搴?
    5. [x] Property 5锛氬悓姣旂幆姣斿彉鍖栫巼鍏紡
    6. [x] Property 9锛氭満姊拌垂鐢ㄨ绠楀叕寮忔纭€?
    7. [x] Property 10锛氱粨绠楀懆鏈熼噸鍙犳娴?
    8. [x] Property 12锛氱郴缁熷弬鏁板€艰寖鍥存牎楠?
    9. [x] Property 14锛氬紩鐢ㄦ牎楠屽喅绛栭€昏緫
    10. [x] Property 15锛氱鎴风画鏈熸棩鏈熻绠?
    11. [x] Property 18锛氬揩鐓?鍥炴粴 Round Trip
    12. [x] Property 19锛氬洖婊氬啿绐佹娴?
    13. [x] Property 20锛氫箰瑙傞攣閲嶈瘯鏈哄埗
    14. [x] 纭繚姣忎釜灞炴€ф祴璇曡嚦灏?100 娆¤凯浠?+ @Tag 鏍囨敞

- [x] 20. 闆嗘垚娴嬭瘯
  - Requirements: R1-R8
  - Dependencies: Task 19
  - Description: Spring Boot Test 闆嗘垚娴嬭瘯锛岄獙璇佹湇鍔￠棿鍗忎綔鍜岀鍒扮涓氬姟娴佺▼
  - Sub-tasks:
    1. [x] 鎼缓闆嗘垚娴嬭瘯鍩虹璁炬柦锛園SpringBootTest + Testcontainers锛?
    2. [x] 鏁版嵁鏉冮檺鎷︽埅鍣?SQL 鎷兼帴 + 鏌ヨ缁撴灉楠岃瘉
    3. [x] 椤圭洰鎴愬憳娣诲姞 鈫?鏁版嵁鏉冮檺 PROJECT 鑼冨洿鑱斿姩
    4. [x] 绯荤粺閰嶇疆鏇存柊 鈫?Redis 缂撳瓨娓呴櫎 鈫?璇诲彇鏈€鏂板€?
    5. [x] 鏈烘缁撶畻 鈫?Flowable 瀹℃壒 鈫?鍥炶皟 鈫?閲戦绱姞
    6. [x] 瀹℃壒蹇収 鈫?椹冲洖浜嬩欢 鈫?鍥炴粴 鈫?鏁版嵁鎭㈠
    7. [x] 鍥炴粴涔愯閿佸啿绐?鈫?閲嶈瘯鏈哄埗
    8. [x] 绉熸埛鍋滅敤 鈫?Token 娓呴櫎 鈫?鐧诲綍鎷掔粷
    9. [x] 绉熸埛鍒版湡 鈫?鐘舵€佹洿鏂?鈫?鐧诲綍鎷掔粷
    10. [x] 寮曠敤鏍￠獙闃绘鍒犻櫎 + 鏃犲紩鐢ㄦ甯稿垹闄?
    11. [x] 钖祫缁熻姹囨€?+ Excel 瀵煎嚭楠岃瘉
    12. [x] 杩愯鍏ㄩ儴娴嬭瘯骞剁‘淇濋€氳繃

## Notes

- Wave 2 涓殑 Task 2 渚濊禆 Task 3锛堥」鐩垚鍛樼鐞嗘彁渚?getUserProjectIds 缁欐暟鎹潈闄愮殑 PROJECT 鑼冨洿锛夛紝鍏朵綑 Wave 2 浠诲姟浠呬緷璧?Task 1
- Wave 3 鐨?Task 4 渚濊禆 Task 2锛堟暟鎹潈闄愭嫤鎴櫒灏辩华鍚庡啀鎻愪緵閰嶇疆 API锛?
- 鍓嶇浠诲姟锛圵ave 4锛夊悇鑷嫭绔嬶紝浠呬緷璧栧搴旂殑鍚庣浠诲姟
- 娴嬭瘯浠诲姟锛圵ave 5锛変緷璧栧叏閮ㄥ悗绔换鍔″畬鎴?
- 鎵€鏈夐噾棰濆瓧娈典娇鐢?BigDecimal锛岀姝?float/double
- Redis key 缁熶竴鍓嶇紑锛歚sys:config:`, `token:tenant:`
