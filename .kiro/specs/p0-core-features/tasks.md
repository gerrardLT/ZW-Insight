# Implementation Plan: P0 鏍稿績鍔熻兘

## Overview

鍩轰簬 ZW-Insight 宸ョ▼椤圭洰绠＄悊绯荤粺 P0 浼樺厛绾?7 涓牳蹇冪己澶卞姛鑳界殑瀹炵幇璁″垝銆傚疄鐜伴噰鐢?Spring Boot 3.2 鍚庣 + Vue 3 鍓嶇 + uni-app 绉诲姩绔殑鎶€鏈爤锛屾寜鍔熻兘妯″潡鍒嗙粍锛屾瘡涓ā鍧楀唴鎸?鏁版嵁灞?鈫?鏈嶅姟灞?鈫?鎺у埗灞?鈫?鍓嶇"鐨勯『搴忛€掕繘瀹炵幇銆?

## Tasks

- [x] 1. 鏁版嵁搴撹縼绉昏剼鏈笌鍩虹璁炬柦鍑嗗
  - [x] 1.1 鍒涘缓鍏ㄩ儴鏂板琛ㄧ殑鏁版嵁搴撹縼绉昏剼鏈?
    - 鍦?`zw-insight-server/sql/` 鐩綍涓嬪垱寤鸿縼绉?SQL 鏂囦欢
    - 鍖呭惈琛細`biz_boq_item`銆乣biz_budget_change`銆乣biz_budget_change_detail`銆乣biz_project_settlement`銆乣biz_settlement_contract_detail`銆乣sys_budget_control_config`銆乣biz_retention_warning_log`銆乣biz_inspection_detail`
    - 鍖呭惈 `biz_inspection` 琛ㄧ殑 ALTER 璇彞锛堝鍔?`scheme_snapshot` 瀛楁锛?
    - 鍖呭惈 `sys_budget_control_config` 绯荤粺榛樿璁板綍鐨?INSERT锛圔LOCK, 80%锛?
    - _Requirements: 1, 2, 3, 5, 6, 7_

  - [x] 1.2 鍒涘缓鍚勬ā鍧楃殑 MyBatis-Plus Entity 鍜?Mapper 鎺ュ彛
    - `zw-contract` 妯″潡锛歚BoqItem` entity + `BoqItemMapper`
    - `zw-budget` 妯″潡锛歚BudgetChange`銆乣BudgetChangeDetail` entity + Mapper锛沗BudgetControlConfig` entity + Mapper
    - `zw-finance` 妯″潡锛歚ProjectSettlement`銆乣SettlementContractDetail` entity + Mapper锛沗RetentionWarningLog` entity + Mapper
    - `zw-site` 妯″潡锛歚InspectionDetail` entity + Mapper
    - 鎵€鏈?Entity 椤诲寘鍚?`@TableLogic` 閫昏緫鍒犻櫎瀛楁鍜?`tenant_id`
    - _Requirements: 1, 2, 3, 5, 6, 7_

- [x] 2. 宸ョ▼閲忔竻鍗曚笂浼狅紙BOQ锛夆€?鍚庣 zw-contract
  - [x] 2.1 瀹炵幇 BoqService 鏍稿績閫昏緫
    - 鍒涘缓 `BoqService` 绫伙紝瀹炵幇 `uploadBoq(Long contractId, MultipartFile file)` 鏂规硶
    - 瀹炵幇鍚堝悓鐘舵€佹牎楠岋紙浠?EFFECTIVE/CHANGING 鍏佽锛?
    - 瀹炵幇浜у€间笂鎶ュ紩鐢ㄦ鏌ワ紙鏈夊紩鐢ㄥ垯鎷掔粷瑕嗙洊锛?
    - 瀹炵幇鏂囦欢澶у皬鏍￠獙锛堚墹20MB锛?
    - 璋冪敤 MinIO FileStorageService 瀛樺偍鍘熷鏂囦欢
    - _Requirements: 1.1, 1.5, 1.6, 1.9_

  - [x] 2.2 瀹炵幇 EasyExcel 瑙ｆ瀽涓庡眰绾ф瀯寤?
    - 鍒涘缓 `BoqExcelRow` DTO锛圗asyExcel @ExcelProperty 娉ㄨВ鏄犲皠鍒楋級
    - 鍒涘缓 `BoqReadListener` 瀹炵幇琛屾牎楠岋紙蹇呭～瀛楁銆佹潯鐩笂闄?5000銆侀敊璇渶澶?100 鏉★級
    - 瀹炵幇 `buildHierarchy` 鏂规硶锛屾寜椤圭洰缂栫爜鐨?"." 鍒嗛殧瑙勫垯鏋勫缓鐖跺瓙灞傜骇锛堟渶澶?4 绾э級
    - 瀹炵幇鎵归噺鎻掑叆锛堝垹鏃?鎻掓柊锛夊拰鍚堣閲戦璁＄畻鍥炲啓鍚堝悓
    - _Requirements: 1.2, 1.3, 1.4, 1.8_

  - [x]* 2.3 缂栧啓灞炴€ф祴璇曪細BOQ 灞傜骇涓€鑷存€э紙Property P1锛?
    - **Property P1: BOQ 灞傜骇涓€鑷存€?*
    - 鐢熸垚闅忔満鍚堟硶缂栫爜鍒楄〃锛岄獙璇?buildHierarchy 杈撳嚭涓墍鏈?level > 1 鐨勬潯鐩叾 parent_id 鎸囧悜瀛樺湪涓?parent.level == current.level - 1
    - **Validates: Requirements 1.4**

  - [x] 2.4 瀹炵幇 BoqController REST 鎺ュ彛
    - POST `/api/v1/contracts/{contractId}/boq/upload` 鈥?涓婁紶骞惰В鏋?BOQ
    - GET `/api/v1/contracts/{contractId}/boq` 鈥?鏌ヨ娓呭崟鏍戝舰缁撴瀯
    - GET `/api/v1/contracts/{contractId}/boq/flat` 鈥?鏌ヨ娓呭崟骞抽摵鍒楄〃锛堜緵浜у€间笂鎶ヤ娇鐢級
    - DELETE `/api/v1/contracts/{contractId}/boq` 鈥?娓呴櫎娓呭崟鏁版嵁
    - _Requirements: 1.1, 1.7_

  - [x]* 2.5 缂栧啓 BoqService 鍗曞厓娴嬭瘯
    - 娴嬭瘯鐘舵€佹牎楠屾嫆缁濋€昏緫
    - 娴嬭瘯寮曠敤妫€鏌ユ嫆缁濊鐩?
    - 娴嬭瘯瑙ｆ瀽閿欒杩斿洖琛屽彿
    - 娴嬭瘯鍚堣閲戦璁＄畻绮惧害
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.8, 1.9_

- [x] 3. 鐩爣鎴愭湰鍙樻洿 鈥?鍚庣 zw-budget
  - [x] 3.1 瀹炵幇 BudgetChangeService 鏍稿績閫昏緫
    - 鍒涘缓 `BudgetChangeService`锛屽疄鐜?CRUD 鎿嶄綔
    - 瀹炵幇 `validateBeforeSubmit` 棰勭畻浣欓鏍￠獙锛堣皟鍑忔椂锛氳皟鏁村悗閲戦 鈮?宸插崰鐢ㄩ绠楋級
    - 瀹炵幇 `calculateOccupiedBudget`锛堝凡绛惧悎鍚岄噾棰?+ 宸蹭粯鏃犲悎鍚岃垂鐢級
    - 鍒涘缓 `BudgetChangeDTO` 鍜?`BudgetChangeDetailDTO` 璇锋眰瀵硅薄
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.2 瀹炵幇瀹℃壒娴佺▼闆嗘垚涓庡洖璋?
    - 瀹炵幇 `submit(Long changeId)` 鎻愪氦瀹℃壒锛岃皟鐢?Flowable ApprovalService 鍚姩娴佺▼
    - 瀹炵幇 `onApproved(Long changeId)` 瀹℃壒閫氳繃鍥炶皟锛氶€愮鐩洖鍐欓绠楁槑缁?+ 鏇存柊椤圭洰棰勭畻鎬婚
    - 瀹炵幇 `onRejected(Long changeId)` 瀹℃壒椹冲洖鍥炶皟锛氭洿鏂扮姸鎬佷负 REJECTED
    - 瀹炵幇 `withdraw(Long changeId)` 鎾ゅ洖鎿嶄綔锛氭洿鏂扮姸鎬佷负 WITHDRAWN
    - 娉ㄥ唽 Flowable 瀹℃壒鍥炶皟鐩戝惉鍣?
    - _Requirements: 2.4, 2.5, 2.6, 2.7, 2.8_

  - [x]* 3.3 缂栧啓灞炴€ф祴璇曪細棰勭畻鍙樻洿閲戦瀹堟亽锛圥roperty P2锛?
    - **Property P2: 棰勭畻鍙樻洿閲戦瀹堟亽**
    - 鐢熸垚闅忔満鍙樻洿鏄庣粏鍒楄〃锛岄獙璇?SUM(details.adjustAmount) == change.totalAdjustAmount 涓斿鎵归€氳繃鍚庡悇鏄庣粏鍥炲啓绱姞鎬诲拰绛変簬鍙樻洿鍗曟€昏皟鏁撮
    - **Validates: Requirements 2.2, 2.5**

  - [x] 3.4 瀹炵幇 BudgetChangeController REST 鎺ュ彛
    - 瀹屾暣 CRUD + submit + withdraw 鎺ュ彛
    - 鍙樻洿杞ㄨ抗鏌ヨ鎺ュ彛锛堟寜椤圭洰鏌ヨ鍏ㄩ儴鍙樻洿璁板綍鍙婂鎵圭粨鏋滐級
    - _Requirements: 2.1, 2.9_

  - [x]* 3.5 缂栧啓 BudgetChangeService 鍗曞厓娴嬭瘯
    - 娴嬭瘯璋冨噺鏃朵綑棰濅笉瓒虫嫆缁?
    - 娴嬭瘯瀹℃壒閫氳繃鍚庨噾棰濆洖鍐欐纭€?
    - 娴嬭瘯鎾ゅ洖/椹冲洖涓嶄慨鏀瑰師棰勭畻
    - _Requirements: 2.3, 2.5, 2.6, 2.7, 2.8_

- [x] 4. Checkpoint 鈥?纭繚鍚堝悓涓庨绠楁ā鍧楃紪璇戦€氳繃
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. 椤圭洰鏈€缁堢粨绠?鈥?鍚庣 zw-finance
  - [x] 5.1 瀹炵幇 ProjectSettlementService 鏁版嵁姹囨€婚€昏緫
    - 鍒涘缓 `ProjectSettlementService`
    - 瀹炵幇椤圭洰鐘舵€佹牎楠岋紙浠?COMPLETED 鍏佽锛夊拰閲嶅缁撶畻鍗曟牎楠?
    - 瀹炵幇鏀跺叆姹囨€伙細鏂藉伐鍚堝悓鎬婚銆佺疮璁′骇鍊笺€佺疮璁℃敹娆俱€佺疮璁″紑绁?
    - 瀹炵幇鏀嚭姹囨€伙細鍒嗗寘/鍔冲姟/鏉愭枡/鏈烘缁撶畻鎬婚 + 绱浠樻
    - 瀹炵幇鍒╂鼎璁＄畻锛堢簿纭埌鍒嗭級鍜屽埄娑︾巼璁＄畻锛堢簿纭埌灏忔暟鐐瑰悗 2 浣嶏級
    - 鐢熸垚鍏宠仈鍚堝悓鏄庣粏骞舵爣娉ㄦ湭缁撴竻鍚堝悓
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.7_

  - [x] 5.2 瀹炵幇缁撶畻瀹℃壒娴佺▼涓庨」鐩姸鎬佹祦杞?
    - 瀹炵幇 `submit` 鎻愪氦瀹℃壒锛屽惎鍔?Flowable 缁撶畻瀹℃壒娴佺▼
    - 瀹炵幇 `onApproved` 鍥炶皟锛氭洿鏂扮粨绠楀崟鐘舵€?+ 椤圭洰鐘舵€佹洿鏂颁负 CLOSED
    - 瀹炵幇 `onRejected` 鍥炶皟锛氱粨绠楀崟鐘舵€佹洿鏂颁负 REJECTED锛岄」鐩姸鎬佷笉鍙?
    - _Requirements: 3.5, 3.6_

  - [x]* 5.3 缂栧啓灞炴€ф祴璇曪細缁撶畻鍒╂鼎璁＄畻姝ｇ‘鎬э紙Property P3锛?
    - **Property P3: 缁撶畻鍒╂鼎璁＄畻姝ｇ‘鎬?*
    - 鐢熸垚闅忔満鏀舵敮鏁版嵁锛岄獙璇?profit == totalIncome - totalExpenditure 涓?profitRate == profit / totalIncome * 100锛坱otalIncome > 0 鏃讹級
    - **Validates: Requirements 3.4**

  - [x] 5.4 瀹炵幇缁撶畻鎶ュ憡 Excel 瀵煎嚭
    - 浣跨敤 EasyExcel 瀵煎嚭鏀舵敮姹囨€昏〃 + 鍚勫悎鍚岀粨绠楁槑缁?
    - 瀹炵幇 ExcelWriter 澶?Sheet 鍐欏叆锛堟敹鏀眹鎬?Sheet + 鍚堝悓鏄庣粏 Sheet锛?
    - _Requirements: 3.8_

  - [x] 5.5 瀹炵幇 ProjectSettlementController REST 鎺ュ彛
    - POST 鍒涘缓缁撶畻鍗曘€丟ET 璇︽儏銆丳UT 缂栬緫銆丳OST 鎻愪氦瀹℃壒銆丳OST 瀵煎嚭 Excel
    - GET 鏈粨娓呭悎鍚屽垪琛?
    - _Requirements: 3.1, 3.5, 3.7, 3.8_

  - [x]* 5.6 缂栧啓 ProjectSettlementService 鍗曞厓娴嬭瘯
    - 娴嬭瘯闈炵宸ラ」鐩嫆缁濆垱寤?
    - 娴嬭瘯閲嶅缁撶畻鍗曟嫆缁?
    - 娴嬭瘯瀹℃壒閫氳繃鍚庨」鐩姸鎬佸彉涓?CLOSED
    - _Requirements: 3.1, 3.2, 3.5, 3.6_

- [x] 6. 楠岃瘉鐮佺櫥褰?鈥?鍚庣 zw-security
  - [x] 6.1 瀹炵幇 CaptchaService 鍥惧舰楠岃瘉鐮?
    - 浣跨敤 Hutool CaptchaUtil 鐢熸垚 4 浣嶅瓧姣嶆暟瀛楁贩鍚堝浘褰㈤獙璇佺爜
    - Redis 瀛樺偍锛歬ey=`captcha:{uuid}`锛寁alue=code锛孴TL=300s
    - 瀹炵幇 `generateImageCaptcha()` 杩斿洖 Base64 鍥剧墖 + UUID
    - 瀹炵幇 `verifyImageCaptcha(uuid, inputCode)` 澶у皬鍐欎笉鏁忔劅姣斿 + 鏍￠獙鍚庣珛鍗冲垹闄?
    - _Requirements: 4.1, 4.2, 4.4, 4.5_

  - [x] 6.2 瀹炵幇 CaptchaService 鐭俊楠岃瘉鐮佷笌棰戠巼闄愬埗
    - 鎵嬫満鍙锋牸寮忔牎楠岋紙`^1[3-9]\\d{9}$`锛?
    - Redis 棰戠巼闄愬埗锛歚sms:freq:{phone}` TTL=60s锛?0 绉掑唴浠?1 娆★級
    - Redis 鏃ラ檺棰濓細`sms:daily:{phone}` INCR + 褰撳ぉ鍓╀綑绉掓暟 EXPIRE锛堟瘡鏃モ墹10 娆★級
    - 瀵规帴闃块噷浜戠煭淇?SDK 2.0.24 鍙戦€?6 浣嶆暟瀛楅獙璇佺爜
    - _Requirements: 4.6, 4.7, 4.9, 4.10_

  - [x] 6.3 瀹炵幇 IP 閿佸畾鏈哄埗
    - Redis 璁℃暟锛歚login:ip:fail:{ip}` INCR + EXPIRE 300s锛? 鍒嗛挓绐楀彛锛?
    - 杩炵画 5 娆″け璐ュ悗璁剧疆閿佸畾锛歚login:ip:lock:{ip}` TTL=900s锛?5 鍒嗛挓锛?
    - 瀹炵幇 `checkIpLock(clientIp)` 鍜?`recordIpFailure(clientIp)`
    - _Requirements: 4.8_

  - [x]* 6.4 缂栧啓灞炴€ф祴璇曪細楠岃瘉鐮佷竴娆℃€т娇鐢紙Property P4锛?
    - **Property P4: 楠岃瘉鐮佷竴娆℃€т娇鐢?*
    - 鐢熸垚闅忔満楠岃瘉鐮佸苟瀛樺叆 Redis锛岄娆℃牎楠屾垚鍔熷悗锛屼娇鐢ㄧ浉鍚?uuid+code 鍐嶆鏍￠獙蹇呭畾杩斿洖澶辫触
    - **Validates: Requirements 4.5**

  - [x] 6.5 瀹炵幇 CaptchaController 鍜岀櫥褰曟祦绋嬮泦鎴?
    - GET `/api/v1/captcha/image` 鈥?鐢熸垚鍥惧舰楠岃瘉鐮?
    - POST `/api/v1/captcha/sms` 鈥?鍙戦€佺煭淇￠獙璇佺爜
    - 淇敼鐜版湁 `AuthController` 鐧诲綍鎺ュ彛锛屽鍔犻獙璇佺爜鏍￠獙閫昏緫鍜?IP 閿佸畾妫€鏌?
    - 鎵╁睍 `LoginDTO` 澧炲姞 `captchaCode`銆乣captchaUuid`銆乣phone`銆乣smsCode`銆乣loginType` 瀛楁
    - _Requirements: 4.1, 4.2, 4.3, 4.6_

  - [x]* 6.6 缂栧啓 CaptchaService 鍗曞厓娴嬭瘯
    - 娴嬭瘯楠岃瘉鐮佽繃鏈熷満鏅?
    - 娴嬭瘯鐭俊棰戠巼闄愬埗鎷掔粷
    - 娴嬭瘯 IP 閿佸畾瑙﹀彂涓庤В闄?
    - _Requirements: 4.4, 4.7, 4.8, 4.10_

- [x] 7. Checkpoint 鈥?纭繚瀹夊叏妯″潡涓庤储鍔℃ā鍧楃紪璇戦€氳繃
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. 璐ㄤ繚閲戦璀﹀畾鏃朵换鍔?鈥?鍚庣 zw-finance
  - [x] 8.1 瀹炵幇 RetentionWarningTask 瀹氭椂浠诲姟鏍稿績閫昏緫
    - 鍒涘缓 `RetentionWarningTask`锛屼娇鐢?`@Scheduled(cron = "0 0 8 * * ?")` 姣忔棩 08:00 鎵ц
    - 鏌ヨ status='UNRETURNED' 涓斿埌鏈熸棩鍦ㄦ湭鏉?30 澶╁唴鎴栧凡杩囨湡鐨勮川淇濋噾璁板綍
    - 瀹炵幇鍒嗙骇閫昏緫锛?0~8 澶?UPCOMING锛?~1 澶?URGENT锛屽凡杩囨湡=OVERDUE
    - 閫炬湡瓒?180 澶╂爣璁?LONG_OVERDUE 骞跺仠姝㈠偓鍔?
    - _Requirements: 5.1, 5.2, 5.4, 5.5_

  - [x] 8.2 瀹炵幇閫氱煡鍘婚噸涓庡偓鍔炴満鍒?
    - Redis Set 鍘婚噸锛歬ey=`retention:warned:{retentionId}:{level}`锛堥潪閫炬湡鍚岀骇鍒彧鍙戜竴娆★級
    - 閫炬湡鍌姙棰戠巼鎺у埗锛歬ey=`retention:overdue:last:{retentionId}`锛堟瘡 3 澶╀竴娆★級
    - 璋冪敤 MessageService 鍙戦€佺珯鍐呬俊锛堥」鐩礋璐ｄ汉 + 璐㈠姟浜哄憳锛?
    - 閫氱煡鍐呭鍚細椤圭洰鍚嶇О銆佸悎鍚屽悕绉般€佽川淇濋噾閲戦銆佸埌鏈熸棩鏈熴€侀璀︾骇鍒?
    - _Requirements: 5.3, 5.5, 5.6_

  - [x] 8.3 瀹炵幇澶辫触閲嶈瘯涓庣姸鎬佹竻闄?
    - 璁板綍 `biz_retention_warning_log` 閫氱煡鏃ュ織
    - 澶辫触璁板綍鍦ㄤ笅娆′换鍔℃墽琛屾椂閲嶈瘯锛堟渶澶?3 娆★級锛? 娆′粛澶辫触鏍囪 PERMANENTLY_FAILED
    - 瀹炵幇 `onRetentionReturned(Long retentionId)` 璐ㄤ繚閲戦€€杩樻椂娓呴櫎鍘婚噸璁板綍
    - _Requirements: 5.7, 5.8_

  - [x]* 8.4 缂栧啓灞炴€ф祴璇曪細璐ㄤ繚閲戦€氱煡鍘婚噸锛圥roperty P7锛?
    - **Property P7: 璐ㄤ繚閲戦€氱煡鍘婚噸**
    - 妯℃嫙鍚屼竴璐ㄤ繚閲戣褰曡繛缁袱娆℃墽琛岄璀︿换鍔★紙绾у埆涓嶅彉锛夛紝楠岃瘉绗簩娆′笉浜х敓鏂扮殑閫氱煡鍙戦€佽皟鐢?
    - **Validates: Requirements 5.6**

  - [x]* 8.5 缂栧啓 RetentionWarningTask 鍗曞厓娴嬭瘯
    - 娴嬭瘯鍒嗙骇閫昏緫姝ｇ‘鎬э紙30澶?7澶?閫炬湡 杈圭晫锛?
    - 娴嬭瘯閫炬湡瓒?180 澶╁仠姝㈠偓鍔?
    - 娴嬭瘯閲嶈瘯娆℃暟涓婇檺
    - _Requirements: 5.1, 5.4, 5.5, 5.8_

- [x] 9. 棰勭畻鎺у埗閰嶇疆椤甸潰 鈥?鍚庣 zw-budget
  - [x] 9.1 瀹炵幇 BudgetControlConfigService CRUD
    - 鍒涘缓 `BudgetControlConfigService`锛屽疄鐜伴厤缃殑澧炲垹鏀规煡
    - 瀹炵幇 `getEffectiveConfig(Long projectId)` 浼樺厛椤圭洰绾ч厤缃?鈫?鍥炶惤绯荤粺榛樿 鈫?寮傚父鏃剁‖缂栫爜 BLOCK
    - 鍒涘缓 `BudgetControlConfigDTO` 鍚?projectId銆乧ontrolMode锛圵ARN_ONLY/BLOCK/EXEMPT锛夈€亀arningThreshold锛?0-99锛?
    - 鍒犻櫎椤圭洰绾ч厤缃悗鍥炶惤涓洪粯璁よ鍒?
    - _Requirements: 6.1, 6.4, 6.7, 6.8, 6.9_

  - [x] 9.2 瀹炵幇棰勭畻鎵ц鐜囪绠椾笌鎷︽埅閫昏緫
    - 瀹炵幇 `checkBudget(Long projectId, String costCategory, BigDecimal newAmount)` 鏂规硶
    - 璁＄畻鎵ц鐜?= 宸插彂鐢熼 / 棰勭畻棰?* 100%
    - BLOCK 妯″紡瓒?100% 鎶涘紓甯搁樆姝㈡彁浜わ紱WARN_ONLY 瓒?100% 杩斿洖璀﹀憡鏍囪瘑锛汦XEMPT 鐩存帴鏀捐
    - 杈惧埌棰勮闃堝€兼椂閫氳繃 MessageService 鍙戦€佺珯鍐呬俊
    - _Requirements: 6.3, 6.5, 6.6_

  - [x] 9.3 鏀归€?BudgetControlAspect 鍒囬潰
    - 鏇挎崲鐜版湁纭紪鐮?绂佹鎻愪氦"閫昏緫涓洪厤缃┍鍔?
    - 鍒涘缓 `@BudgetCheck` 娉ㄨВ鍜?`BudgetControlAspect` AOP 鍒囬潰
    - 瀹炵幇 `BudgetCheckResult`锛圥ASS/WARN/BLOCK锛夊拰 `BudgetWarningContext` 绾跨▼鍙橀噺
    - 鍦ㄤ笟鍔″崟鎹彁浜ゆ柟娉曚笂娣诲姞 `@BudgetCheck` 娉ㄨВ锛堥噰璐悎鍚?鍔冲姟鍚堝悓/鏈烘鍚堝悓/鍏朵粬浠樻锛?
    - _Requirements: 6.5, 6.7_

  - [x]* 9.4 缂栧啓灞炴€ф祴璇曪細棰勭畻鎺у埗閰嶇疆鍗曡皟鎬э紙Property P5锛?
    - **Property P5: 棰勭畻鎺у埗閰嶇疆鍗曡皟鎬?*
    - 鍒涘缓椤圭洰绾ч厤缃悗鍒犻櫎锛岄獙璇?`getEffectiveConfig` 杩斿洖鍊煎洖钀戒负绯荤粺榛樿锛圔LOCK, 80%锛?
    - **Validates: Requirements 6.4, 6.9**

  - [x] 9.5 瀹炵幇 BudgetControlConfigController REST 鎺ュ彛
    - 瀹屾暣 CRUD 鎺ュ彛 + 鎸夐」鐩幏鍙栫敓鏁堥厤缃帴鍙?
    - 鍒楄〃鏀寔鎸夐」鐩悕绉扮瓫閫?
    - _Requirements: 6.1, 6.2_

  - [x]* 9.6 缂栧啓 BudgetControlConfigService 鍗曞厓娴嬭瘯
    - 娴嬭瘯涓夌妯″紡鏍￠獙琛屼负
    - 娴嬭瘯閰嶇疆寮傚父鍥炶惤榛樿鍊?
    - 娴嬭瘯棰勮闃堝€奸€氱煡瑙﹀彂
    - _Requirements: 6.3, 6.5, 6.6, 6.8_

- [x] 10. 妫€鏌ユ柟妗堝叧鑱?鈥?鍚庣 zw-site
  - [x] 10.1 瀹炵幇 InspectionSchemeService 鏂规鍏宠仈閫昏緫
    - 鍒涘缓 `InspectionSchemeService`
    - 瀹炵幇 `listSchemes(String inspectionType, int page, int size)` 鎸夌被鍨嬬瓫閫夊凡鍚敤鏂规锛堟瘡椤碘墹50锛?
    - 瀹炵幇 `applyScheme(Long inspectionId, Long schemeId)` 鍏宠仈鏂规骞剁敓鎴愬揩鐓?
    - 蹇収涓?JSON 鏍煎紡鍚?schemeId銆乻chemeName銆乮tems锛坕temName + checkStandard + checkMethod锛?
    - 娓呴櫎鏃ф鏌ユ槑缁?鈫?濉厖鏂版柟妗堟鏌ラ」 鈫?鏇存柊 scheme_id 鍜?scheme_snapshot
    - _Requirements: 7.1, 7.2, 7.4, 7.7_

  - [x] 10.2 瀹炵幇妫€鏌ユ槑缁嗙紪杈戜笌鎵嬪姩濉啓
    - 鍏佽缂栬緫宸插～鍏呯殑妫€鏌ラ」锛堜慨鏀规鏌ユ爣鍑?鍒犻櫎涓嶉€傜敤椤癸紝涓嶅彲鏂板鏂规澶栨鏌ラ」锛?
    - 鏈€夋嫨鏂规鏃跺厑璁告墜鍔ㄥ～鍐欐鏌ラ」锛堚墹100 鏉★紝椤圭洰鍚嶇О鈮?00 瀛楃锛屾鏌ユ爣鍑嗏墹500 瀛楃锛?
    - _Requirements: 7.3, 7.8_

  - [x]* 10.3 缂栧啓灞炴€ф祴璇曪細鏂规蹇収涓嶅彲鍙樻€э紙Property P6锛?
    - **Property P6: 鏂规蹇収涓嶅彲鍙樻€?*
    - 鍒涘缓妫€鏌ヨ褰曞苟鍏宠仈鏂规锛屼慨鏀瑰師鏂规婧愭暟鎹悗閲嶆柊璇诲彇妫€鏌ヨ褰曪紝楠岃瘉 scheme_snapshot JSON 鍐呭涓庡垱寤烘椂涓€鑷?
    - **Validates: Requirements 7.7**

  - [x] 10.4 瀹炵幇 InspectionSchemeController REST 鎺ュ彛
    - GET `/api/v1/inspection-schemes` 鈥?鏂规鍒楄〃锛堟寜 inspectionType 绛涢€夛級
    - GET `/api/v1/inspection-schemes/{id}/items` 鈥?鏂规妫€鏌ラ」鍒楄〃
    - POST `/api/v1/inspections/{id}/apply-scheme` 鈥?鍏宠仈鏂规鍒版鏌ヨ褰?
    - _Requirements: 7.1, 7.2_

  - [x]* 10.5 缂栧啓 InspectionSchemeService 鍗曞厓娴嬭瘯
    - 娴嬭瘯鏂规鍏宠仈鍚庢鏌ユ槑缁嗘纭～鍏?
    - 娴嬭瘯閲嶆柊閫夋嫨鏂规娓呴櫎鏃ф暟鎹?
    - 娴嬭瘯鎵嬪姩濉啓闄愬埗鏍￠獙
    - _Requirements: 7.2, 7.3, 7.4, 7.8_

- [x] 11. Checkpoint 鈥?纭繚鍏ㄩ儴鍚庣妯″潡缂栬瘧閫氳繃銆佸崟鍏冩祴璇曢€氳繃
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. PC 鍓嶇 鈥?宸ョ▼閲忔竻鍗曚笂浼犻〉闈?
  - [x] 12.1 瀹炵幇 BOQ 涓婁紶鍓嶇缁勪欢
    - 鍒涘缓 `src/api/boq.ts` API 鏂囦欢锛坲ploadBoq銆乬etBoqTree銆乨eleteBoq锛?
    - 鍒涘缓 `views/contract/boq-upload.vue` 椤甸潰缁勪欢
    - 瀹炵幇 Excel 鏂囦欢鎷栨嫿/鐐瑰嚮涓婁紶锛圗lement Plus Upload 缁勪欢锛岄檺鍒?.xlsx銆?0MB锛?
    - 涓婁紶鎴愬姛鍚庡睍绀烘竻鍗曟爲褰㈣〃鏍硷紙ElTable + 鏍戝舰灞曞紑锛?
    - 灞曠ず鍚堣閲戦銆佹潯鐩暟銆佸眰绾ф暟
    - _Requirements: 1.1, 1.2, 1.7_

- [x] 13. PC 鍓嶇 鈥?鐩爣鎴愭湰鍙樻洿椤甸潰
  - [x] 13.1 瀹炵幇鐩爣鎴愭湰鍙樻洿鍒楄〃涓庤〃鍗曢〉闈?
    - 鍒涘缓 `src/api/budget-change.ts` API 鏂囦欢
    - 鍒涘缓 `views/budget/change/index.vue` 鍙樻洿鍗曞垪琛ㄩ〉锛堝垎椤?鐘舵€佺瓫閫夛級
    - 鍒涘缓 `views/budget/change/form.vue` 鍙樻洿鍗曟柊寤?缂栬緫琛ㄥ崟
    - 琛ㄥ崟鍚細鍙樻洿鍘熷洜锛堝繀濉級銆佸彉鏇存槑缁嗚〃鏍硷紙鍔ㄦ€佸琛岋細绉戠洰鍚嶇О銆佸師閲戦銆佽皟鏁撮噾棰濄€佽皟鏁村悗閲戦鑷姩璁＄畻锛?
    - 鎻愪氦/鎾ゅ洖鎿嶄綔鎸夐挳锛堟牴鎹姸鎬佹樉闅愶級
    - _Requirements: 2.1, 2.2, 2.9_

- [x] 14. PC 鍓嶇 鈥?椤圭洰鏈€缁堢粨绠楅〉闈?
  - [x] 14.1 瀹炵幇椤圭洰鏈€缁堢粨绠楀垪琛ㄤ笌璇︽儏椤甸潰
    - 鍒涘缓 `src/api/settlement.ts` API 鏂囦欢
    - 鍒涘缓 `views/finance/settlement/index.vue` 缁撶畻鍗曞垪琛ㄩ〉
    - 鍒涘缓 `views/finance/settlement/detail.vue` 缁撶畻鍗曡鎯呴〉
    - 璇︽儏椤靛睍绀猴細鏀舵敮姹囨€诲崱鐗囥€佸埄娑?鍒╂鼎鐜囥€佸悎鍚屾槑缁嗚〃鏍笺€佹湭缁撴竻鍚堝悓鏍囨敞
    - 瀹炵幇 Excel 瀵煎嚭鎸夐挳锛圔lob 涓嬭浇锛?
    - _Requirements: 3.3, 3.4, 3.7, 3.8_

- [x] 15. PC 鍓嶇 鈥?楠岃瘉鐮佺櫥褰曟敼閫?
  - [x] 15.1 鏀归€犵櫥褰曢〉澧炲姞楠岃瘉鐮?
    - 鍒涘缓 `src/api/captcha.ts` API 鏂囦欢
    - 淇敼 `views/login/index.vue`锛屽鍔犲浘褰㈤獙璇佺爜杈撳叆琛岋紙杈撳叆妗?+ 楠岃瘉鐮佸浘鐗囷紝鐐瑰嚮鍒锋柊锛?
    - 鐧诲綍琛ㄥ崟澧炲姞 captchaCode 鍜?captchaUuid 瀛楁
    - 楠岃瘉鐮佹牎楠屽け璐ユ椂鑷姩鍒锋柊鍥剧墖
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 16. PC 鍓嶇 鈥?棰勭畻鎺у埗閰嶇疆椤甸潰
  - [x] 16.1 瀹炵幇棰勭畻鎺у埗閰嶇疆 CRUD 椤甸潰
    - 鍒涘缓 `src/api/budget-control-config.ts` API 鏂囦欢
    - 鍒涘缓 `views/budget/control-config/index.vue` 閰嶇疆鍒楄〃椤碉紙琛ㄦ牸+绛涢€?寮圭獥琛ㄥ崟锛?
    - 琛ㄥ崟瀛楁锛氶」鐩€夋嫨鍣ㄣ€佹帶鍒舵ā寮忎笅鎷夛紙浠呮彁閱?绂佹鎻愪氦/鍏嶆帶锛夈€侀璀﹂槇鍊兼粦鍧楋紙50-99%锛?
    - 鍒楄〃灞曠ず锛氶」鐩悕绉般€佹帶鍒舵ā寮忋€侀璀﹂槇鍊笺€佹搷浣滄寜閽?
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 17. PC 鍓嶇 鈥?妫€鏌ユ柟妗堝叧鑱?
  - [x] 17.1 鏀归€犳鏌ヨ〃鍗曞鍔犳柟妗堥€夋嫨鍔熻兘
    - 淇敼 `views/site/inspection/form.vue` 妫€鏌ヨ〃鍗曢〉闈?
    - 澧炲姞鏂规閫夋嫨涓嬫媺/寮圭獥缁勪欢锛堟寜妫€鏌ョ被鍨嬬瓫閫夊凡鍚敤鏂规锛?
    - 閫夋嫨鏂规鍚庤嚜鍔ㄥ～鍏呮鏌ユ槑缁嗚〃鏍?
    - 鏀寔缂栬緫妫€鏌ラ」锛堜慨鏀规爣鍑?鍒犻櫎锛屼笉鍙柊澧炴柟妗堝椤癸級
    - 妫€鏌ヨ鎯呴〉浠?scheme_snapshot 灞曠ず鏂规鍐呭
    - _Requirements: 7.1, 7.2, 7.3, 7.5_

- [x] 18. 绉诲姩绔?鈥?楠岃瘉鐮佺櫥褰曚笌妫€鏌ユ柟妗?
  - [x] 18.1 瀹炵幇绉诲姩绔煭淇￠獙璇佺爜鐧诲綍
    - 淇敼 `zw-insight-app/src/pages/login/index.vue` 澧炲姞"鐭俊楠岃瘉鐮佺櫥褰?Tab
    - 瀹炵幇鎵嬫満鍙疯緭鍏?+ 鍙戦€侀獙璇佺爜鎸夐挳锛?0s 鍊掕鏃讹級+ 楠岃瘉鐮佽緭鍏?
    - 璋冪敤 `/api/v1/captcha/sms` 鍜岀櫥褰曟帴鍙ｏ紙loginType=SMS锛?
    - _Requirements: 4.6, 4.7_

  - [x] 18.2 瀹炵幇绉诲姩绔鏌ユ柟妗堝睍绀轰笌缁撴灉鏍囪
    - 淇敼绉诲姩绔鏌ラ〉闈紝灞曠ず鏂规蹇収涓殑妫€鏌ラ」閫愰」鍒楄〃
    - 姣忛」鏀寔鏍囪妫€鏌ョ粨鏋滐紙鍚堟牸/涓嶅悎鏍?鏈鏌ワ級鍗曢€?
    - _Requirements: 7.6_

- [x] 19. 璺敱涓庤彍鍗曢厤缃?
  - [x] 19.1 閰嶇疆鍓嶇璺敱鍜屽悗绔彍鍗曟暟鎹?
    - PC 绔?`router/index.ts` 澧炲姞鏂伴〉闈㈣矾鐢憋紙鐩爣鎴愭湰鍙樻洿銆侀」鐩渶缁堢粨绠椼€侀绠楁帶鍒堕厤缃級
    - 淇敼 `data-menu.sql` 鎻掑叆瀵瑰簲鑿滃崟璁板綍鍜屾潈闄愭爣璇?
    - _Requirements: 2.1, 3.1, 6.2_

- [x] 20. Final Checkpoint 鈥?鍏ㄩ儴鍔熻兘闆嗘垚楠岃瘉
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from design document Section 10
- Unit tests validate specific examples and edge cases
- 鍚庣瀹炵幇鎸夋ā鍧楀垎缁勶細zw-contract 鈫?zw-budget 鈫?zw-finance 鈫?zw-security 鈫?zw-site
- 鍓嶇瀹炵幇鍦ㄥ叏閮ㄥ悗绔帴鍙ｅ畬鎴愬悗杩涜锛岀‘淇濇帴鍙ｅ彲鑱旇皟
- Flowable 瀹℃壒娴佺▼瀹氫箟锛圔PMN XML锛夌敱鐜版湁瀹℃壒妗嗘灦鑷姩鐢熸垚锛屾棤闇€鍗曠嫭浠诲姟

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "3.1", "6.1", "9.1"] },
    { "id": 2, "tasks": ["2.2", "3.2", "6.2", "6.3", "9.2"] },
    { "id": 3, "tasks": ["2.3", "2.4", "3.3", "3.4", "6.4", "6.5", "9.3"] },
    { "id": 4, "tasks": ["2.5", "3.5", "5.1", "6.6", "9.4", "9.5"] },
    { "id": 5, "tasks": ["5.2", "5.4", "8.1", "9.6", "10.1"] },
    { "id": 6, "tasks": ["5.3", "5.5", "5.6", "8.2", "8.3", "10.2", "10.4"] },
    { "id": 7, "tasks": ["8.4", "8.5", "10.3", "10.5"] },
    { "id": 8, "tasks": ["12.1", "13.1", "14.1", "15.1", "16.1", "17.1"] },
    { "id": 9, "tasks": ["18.1", "18.2", "19.1"] }
  ]
}
```
