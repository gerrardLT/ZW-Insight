# Implementation Plan: Consistency Audit Engine

## Overview

鍩轰簬 Node.js CLI 宸ュ叿鏋舵瀯锛屼娇鐢?TypeScript + Commander.js + Vitest + fast-check 鎶€鏈爤锛屽疄鐜颁腑缁存櫤钀ヤ笁绔唬鐮佸簱鐨?API 璺緞鍖归厤銆佹暟鎹粨鏋勫榻愩€佸姛鑳借鐩栧畬鏁存€у鏍稿伐鍏枫€傛寜妯″潡鍖栨柟寮忛€愭鏋勫缓鎵弿鍣ㄣ€佹瘮瀵瑰櫒銆佸鏍稿櫒鍜屾姤鍛婄敓鎴愬櫒銆?

## Tasks

- [x] 1. 椤圭洰鍒濆鍖栦笌鏍稿績鎺ュ彛瀹氫箟
  - [x] 1.1 鍒濆鍖栭」鐩粨鏋勪笌渚濊禆閰嶇疆
    - 鍒涘缓 `tools/consistency-audit/` 鐩綍缁撴瀯
    - 鍒濆鍖?`package.json`锛岄厤缃?TypeScript 5.x銆丆ommander.js 12.x銆乂itest 2.x銆乫ast-check 3.x 渚濊禆
    - 鍒涘缓 `tsconfig.json` 閰嶇疆涓ユ牸妯″紡缂栬瘧
    - 鍒涘缓 `vitest.config.ts` 閰嶇疆娴嬭瘯妗嗘灦
    - _Requirements: 鍏ㄩ儴_

  - [x] 1.2 瀹氫箟鏍稿績绫诲瀷涓庢帴鍙?
    - 鍒涘缓 `src/types.ts`锛屽畾涔?`BackendApiEntry`銆乣FrontendApiEntry`銆乣HttpMethod`銆乣InconsistencyType`銆乣Severity`銆乣InconsistencyItem`銆乣AuditStats`銆乣AuditReport`銆乣ModuleReport`銆乣AuditConfig` 绛夋帴鍙?
    - 鍒涘缓 `src/interfaces.ts`锛屽畾涔?`IScanner<T>`銆乣IComparator`銆乣IReportGenerator` 鎺ュ彛
    - _Requirements: 1.2, 2.3, 3.3, 4.3, 4.4, 4.5, 7.1, 7.3_

- [x] 2. 鍚庣鎵弿鍣ㄥ疄鐜?
  - [x] 2.1 瀹炵幇 BackendScanner 绫?
    - 鍒涘缓 `src/scanners/backend-scanner.ts`
    - 瀹炵幇绫荤骇 `@RequestMapping` 鍓嶇紑鎻愬彇姝ｅ垯
    - 瀹炵幇鏂规硶绾?`@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@RequestMapping` 娉ㄨВ瑙ｆ瀽
    - 瀹炵幇绫荤骇璺緞涓庢柟娉曠骇璺緞鎷兼帴閫昏緫锛堝鐞嗗弻鏂滄潬闂锛?
    - 閬嶅巻 zw-insight-server 鍚勬ā鍧?controller 鐩綍锛屾彁鍙?Java 鏂囦欢涓殑 API 澹版槑
    - 浜у嚭 `BackendApiEntry[]`锛岃鐩栧叏閮?20 涓笟鍔℃ā鍧?
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x]* 2.2 缂栧啓 Property Test锛氬悗绔敞瑙ｈВ鏋愭纭€?
    - **Property 1: 鍚庣娉ㄨВ瑙ｆ瀽姝ｇ‘鎬?*
    - 浣跨敤 fast-check 鐢熸垚鍚堟硶 Java Controller 婧愮爜鐗囨锛岄獙璇?httpMethod 涓庢敞瑙ｇ被鍨嬩竴鑷翠笖 fullPath 涓烘纭嫾鎺?
    - **Validates: Requirements 1.1, 1.3**

  - [x]* 2.3 缂栧啓 Property Test锛氳矾寰勬嫾鎺ヤ竴鑷存€?
    - **Property 2: 璺緞鎷兼帴涓€鑷存€?*
    - 浣跨敤 fast-check 鐢熸垚 classPrefix 鍜?methodPath 缁勫悎锛岄獙璇佹嫾鎺ョ粨鏋滀互 classPrefix 寮€澶淬€佷笉鍚弻鏂滄潬
    - **Validates: Requirements 1.3**

  - [x]* 2.4 缂栧啓鍗曞厓娴嬭瘯锛欱ackendScanner
    - 鍦?`tests/unit/backend-scanner.test.ts` 涓娇鐢?fixtures 涓殑 Java 婧愮爜鐗囨楠岃瘉瑙ｆ瀽缁撴灉
    - 楠岃瘉鍏ㄩ儴 20 涓ā鍧楀彲琚纭瘑鍒?
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 3. PC 鍓嶇鎵弿鍣ㄥ疄鐜?
  - [x] 3.1 瀹炵幇 PcWebScanner 绫?
    - 鍒涘缓 `src/scanners/pc-web-scanner.ts`
    - 浣跨敤 TypeScript Compiler API 瑙ｆ瀽 AST锛岃瘑鍒?`request.get/post/put/delete` 璋冪敤
    - 瀹炵幇妯℃澘瀛楃涓茶鑼冨寲锛坄${id}` 鈫?`{id}`锛?
    - 鎻愬彇鏂囦欢鍚嶃€佸嚱鏁板悕銆丠TTP 鏂规硶銆佽姹傝矾寰勩€佽矾寰勫弬鏁般€佹墍灞炴ā鍧?
    - 鎵弿 `zw-insight-web/src/api/` 涓嬪叏閮?16 涓?TypeScript 鏂囦欢
    - _Requirements: 2.1, 2.2, 2.3_

  - [x]* 3.2 缂栧啓 Property Test锛氭ā鏉垮瓧绗︿覆瑙勮寖鍖?
    - **Property 3: 妯℃澘瀛楃涓茶鑼冨寲**
    - 浣跨敤 fast-check 鐢熸垚鍚ā鏉垮彉閲忕殑璺緞瀛楃涓诧紝楠岃瘉 `${xxx}` 鍏ㄦ浛鎹负 `{xxx}`锛屾棤 `$` 娈嬬暀锛屽弬鏁板垪琛ㄩ暱搴︽纭?
    - **Validates: Requirements 2.2, 3.2**

  - [x]* 3.3 缂栧啓 Property Test锛氳В鏋愬櫒杈撳嚭瀹屾暣鎬?
    - **Property 4: 瑙ｆ瀽鍣ㄨ緭鍑哄畬鏁存€?*
    - 楠岃瘉鎵弿鍣ㄤ骇鍑虹殑姣忎釜鏉＄洰鍧囧寘鍚潪绌?module銆乭ttpMethod銆乫ilePath 鍜屾湁鏁堣矾寰勫瓧娈?
    - **Validates: Requirements 1.2, 2.3, 3.3**

  - [x]* 3.4 缂栧啓鍗曞厓娴嬭瘯锛歅cWebScanner
    - 鍦?`tests/unit/pc-web-scanner.test.ts` 涓娇鐢?TypeScript fixtures 楠岃瘉瑙ｆ瀽缁撴灉
    - 楠岃瘉妯℃澘瀛楃涓层€佹櫘閫氬瓧绗︿覆璺緞鍧囧彲姝ｇ‘鎻愬彇
    - _Requirements: 2.1, 2.2, 2.3_

- [x] 4. 绉诲姩绔壂鎻忓櫒瀹炵幇
  - [x] 4.1 瀹炵幇 MobileScanner 绫?
    - 鍒涘缓 `src/scanners/mobile-scanner.ts`
    - 浣跨敤 TypeScript Compiler API 瑙ｆ瀽 `request({ url, method })` 璋冪敤妯″紡
    - 澶勭悊 method 瀛楁缂虹渷鏃堕粯璁や负 GET 鐨勯€昏緫
    - 鎵弿 `zw-insight-app/src/api/` 涓嬬殑 TypeScript 鏂囦欢
    - _Requirements: 3.1, 3.2, 3.3_

  - [x]* 4.2 缂栧啓鍗曞厓娴嬭瘯锛歁obileScanner
    - 鍦?`tests/unit/mobile-scanner.test.ts` 涓娇鐢?fixtures 楠岃瘉瑙ｆ瀽缁撴灉
    - 楠岃瘉 method 榛樿鍊笺€佹ā鏉垮瓧绗︿覆澶勭悊閫昏緫
    - _Requirements: 3.1, 3.2, 3.3_

- [x] 5. Checkpoint - 纭繚鎵弿鍣ㄦ祴璇曢€氳繃
  - 纭繚鎵€鏈夋壂鎻忓櫒鍗曞厓娴嬭瘯鍜?Property 娴嬭瘯閫氳繃锛屽鏈夐棶棰樿璇㈤棶鐢ㄦ埛銆?

- [x] 6. API 璺緞涓€鑷存€ф瘮瀵瑰紩鎿庡疄鐜?
  - [x] 6.1 瀹炵幇 ConsistencyComparator 绫?
    - 鍒涘缓 `src/comparators/consistency-comparator.ts`
    - 瀹炵幇 `normalizePath` 鏂规硶锛氱Щ闄?`/api` 鍓嶇紑宸紓锛岀粺涓€璺緞鍙傛暟鏍煎紡
    - 瀹炵幇 `pathsMatch` 鏂规硶锛氭敮鎸佽矾寰勫弬鏁伴€氶厤鍖归厤
    - 瀹炵幇 `compare` 鏂规硶锛氶€愪竴妫€娴嬪墠绔浣欐帴鍙ｃ€佸悗绔绔嬫帴鍙ｃ€丠TTP 鏂规硶涓嶅尮閰?
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [x]* 6.2 缂栧啓 Property Test锛氳矾寰勮鑼冨寲骞傜瓑鎬?
    - **Property 5: 璺緞瑙勮寖鍖栧箓绛夋€?*
    - 楠岃瘉 `normalizePath("/api" + P) === normalizePath(P)` 鍜?`normalizePath(normalizePath(P)) === normalizePath(P)`
    - **Validates: Requirements 4.6**

  - [x]* 6.3 缂栧啓 Property Test锛氭瘮瀵瑰紩鎿庡垎绫绘纭€?
    - **Property 6: 姣斿寮曟搸鍒嗙被姝ｇ‘鎬?*
    - 浣跨敤 fast-check 鐢熸垚鍓嶅悗绔?API 鏉＄洰闆嗗悎锛岄獙璇佸垎绫婚€昏緫姝ｇ‘涓斾笉浼氬嚭鐜板悓涓€閰嶅琚爣璁颁负澶氱鍐茬獊绫诲瀷
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

  - [x]* 6.4 缂栧啓鍗曞厓娴嬭瘯锛欳onsistencyComparator
    - 鍦?`tests/unit/comparator.test.ts` 涓獙璇佽矾寰勮鑼冨寲銆佽矾寰勫尮閰嶃€佸悇绫讳笉涓€鑷撮」妫€娴?
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 7. 鍔熻兘瑕嗙洊鐜囧鏍稿櫒瀹炵幇
  - [x] 7.1 瀹炵幇 CoverageAuditor 绫?
    - 鍒涘缓 `src/auditors/coverage-auditor.ts`
    - 瀹氫箟 `FeatureMapping` 閰嶇疆缁撴瀯锛屽皢 REQ-031 鍔熻兘琛ㄦ槧灏勪负閰嶇疆鏁版嵁
    - 瀹炵幇鍔熻兘瑕嗙洊妫€娴嬶細楠岃瘉 PC 绔?浜屻€佷笟鍔＄鐞?鍜?鍥涖€佸钩鍙扮鐞嗗姛鑳?瑕嗙洊鎯呭喌
    - 瀹炵幇鍔熻兘瑕嗙洊妫€娴嬶細楠岃瘉绉诲姩绔?涓夈€佹墜鏈虹鐞嗗姛鑳?瑕嗙洊鎯呭喌
    - 妫€娴嬪姛鑳界己澶卞拰瓒呰寖鍥村疄鐜?
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x] 7.2 瀹炵幇绉诲姩绔笌 PC 绔姛鑳藉樊寮傛爣娉ㄩ€昏緫
    - 鍦?CoverageAuditor 涓疄鐜板钩鍙板樊寮傚垎鏋?
    - 鍖哄垎 PC 绔嫭鏈夈€佺Щ鍔ㄧ鐙湁鍔熻兘鍒楄〃
    - 鏍规嵁 REQ-031 鍒ゆ柇宸紓鏄惁鍚堢悊锛堝骞冲彴绠＄悊浠呴渶 PC 绔€佸畾浣嶇鍒颁粎闇€绉诲姩绔級
    - 妫€娴嬩笉鍚堢悊宸紓骞惰褰曚负 `MOBILE_FEATURE_MISSING` 鎴?`PC_FEATURE_MISSING`
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [x]* 7.3 缂栧啓 Property Test锛氬姛鑳借鐩栨娴嬪畬澶囨€?
    - **Property 7: 鍔熻兘瑕嗙洊妫€娴嬪畬澶囨€?*
    - 楠岃瘉 pcRequired=true 涓旀湭鍖归厤鏃跺繀椤讳骇鍑?FEATURE_MISSING锛屽凡鍖归厤鏃朵笉浜у嚭缂哄け椤?
    - **Validates: Requirements 5.2, 5.3, 5.4**

  - [x]* 7.4 缂栧啓鍗曞厓娴嬭瘯锛欳overageAuditor
    - 楠岃瘉鍔熻兘缂哄け妫€娴嬨€佽秴鑼冨洿瀹炵幇妫€娴嬨€佸钩鍙板樊寮傛爣娉?
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 9.1, 9.2, 9.3, 9.4_

- [x] 8. 瀛楁涓€鑷存€у鏍稿櫒瀹炵幇
  - [x] 8.1 瀹炵幇 FieldAuditor 绫?
    - 鍒涘缓 `src/auditors/field-auditor.ts`
    - 瀹炵幇 Java 瀹炰綋/DTO 瀛楁鎻愬彇锛堟鍒欏尮閰?private 瀛楁鍜?@NotNull/@NotBlank 娉ㄨВ锛?
    - 瀹炵幇 Vue 缁勪欢 v-model 缁戝畾瀛楁鎻愬彇
    - 瀹炵幇椹煎嘲/涓嬪垝绾垮懡鍚嶈浆鎹㈠嚱鏁帮紙`normalizeFieldName`锛?
    - 瀹炵幇瀛楁姣斿閫昏緫锛氬瓧娈靛悕涓嶅尮閰嶃€佸墠绔浣欏瓧娈点€佸繀濉瓧娈靛墠绔己澶?
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x]* 8.2 缂栧啓 Property Test锛氬瓧娈靛悕澶у皬鍐欒浆鎹?Round-Trip
    - **Property 8: 瀛楁鍚嶅ぇ灏忓啓杞崲 Round-Trip**
    - 楠岃瘉 `toCamelCase(toSnakeCase(fieldName)) === fieldName`锛堝鍚堟硶椹煎嘲鍛藉悕锛?
    - **Validates: Requirements 6.3**

  - [x]* 8.3 缂栧啓 Property Test锛氬瓧娈靛鏍稿垎绫绘纭€?
    - **Property 9: 瀛楁瀹℃牳鍒嗙被姝ｇ‘鎬?*
    - 楠岃瘉涓嶄竴鑷撮」鏁伴噺绛変簬 |F\B| + |{b鈭圔 : b.isRequired 鈭?b鈭塅}|
    - **Validates: Requirements 6.4, 6.5**

  - [x]* 8.4 缂栧啓鍗曞厓娴嬭瘯锛欶ieldAuditor
    - 鍦?`tests/unit/field-auditor.test.ts` 涓獙璇佸瓧娈垫彁鍙栧拰姣斿閫昏緫
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 9. 璺緞瑙勮寖瀹℃牳鍣ㄥ疄鐜?
  - [x] 9.1 瀹炵幇 PathAuditor 绫?
    - 鍒涘缓 `src/auditors/path-auditor.ts`
    - 瀹炵幇璺緞鍓嶇紑瑙勮寖楠岃瘉锛歚/api/v1/{module}/` 鏍煎紡鏍￠獙
    - 瀹炵幇 RESTful 鍛藉悕椋庢牸鏍￠獙锛歬ebab-case 楠岃瘉
    - 瀹炵幇鍓嶇璺緞妯″潡鍚嶄笌鍚庣妯″潡鍚嶄竴鑷存€ф牎楠?
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [x]* 9.2 缂栧啓 Property Test锛氳矾寰勫墠缂€瑙勮寖楠岃瘉
    - **Property 12: 璺緞鍓嶇紑瑙勮寖楠岃瘉**
    - 楠岃瘉绗﹀悎瑙勮寖鐨勮矾寰勪笉浜у嚭涓嶄竴鑷撮」锛屼笉绗﹀悎瑙勮寖鐨勮矾寰勪骇鍑?PATH_NAMING_VIOLATION
    - **Validates: Requirements 10.1, 10.2**

  - [x]* 9.3 缂栧啓 Property Test锛歊ESTful 鍛藉悕椋庢牸楠岃瘉
    - **Property 13: RESTful 鍛藉悕椋庢牸楠岃瘉**
    - 楠岃瘉 kebab-case 璺緞涓嶄骇鍑轰笉涓€鑷撮」锛屽惈澶у啓/涓嬪垝绾跨殑璺緞浜у嚭 RESTFUL_NAMING_VIOLATION
    - **Validates: Requirements 10.4**

  - [x]* 9.4 缂栧啓鍗曞厓娴嬭瘯锛歅athAuditor
    - 鍦?`tests/unit/path-auditor.test.ts` 涓獙璇佽矾寰勫墠缂€鍜?RESTful 鍛藉悕鏍￠獙閫昏緫
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [x] 10. Checkpoint - 纭繚瀹℃牳鍣ㄦ祴璇曢€氳繃
  - 纭繚鎵€鏈夊鏍稿櫒鍜屾瘮瀵瑰紩鎿庣殑鍗曞厓娴嬭瘯鍜?Property 娴嬭瘯閫氳繃锛屽鏈夐棶棰樿璇㈤棶鐢ㄦ埛銆?

- [x] 11. 鎶ュ憡鐢熸垚鍣ㄥ疄鐜?
  - [x] 11.1 瀹炵幇 ReportGenerator 绫?
    - 鍒涘缓 `src/reporters/report-generator.ts`
    - 瀹炵幇鎸夋ā鍧楀垎缁勯€昏緫锛坄groupByModule`锛?
    - 瀹炵幇涓ラ噸绋嬪害鍒嗙被瑙勫垯锛坄classifySeverity`锛夛細Critical锛堣矾寰勪笉瀛樺湪锛夈€丮ajor锛圚TTP鏂规硶閿欒/蹇呭～瀛楁缂哄け锛夈€丮inor锛堝懡鍚嶉鏍煎樊寮?瓒呰寖鍥村疄鐜帮級
    - 瀹炵幇 JSON 鏍煎紡鎶ュ憡杈撳嚭
    - 瀹炵幇 Markdown 鏍煎紡鎶ュ憡杈撳嚭锛堝惈鍒嗙被姹囨€汇€佹ā鍧楀垎缁勩€佽鐩栫巼缁熻锛?
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x]* 11.2 缂栧啓 Property Test锛氭姤鍛婄粺璁′笉鍙橀噺
    - **Property 10: 鎶ュ憡缁熻涓嶅彉閲?*
    - 楠岃瘉 totalInconsistencies 绛変簬杈撳叆闀垮害锛宐ySeverity 鍜?byType 鍚勫€间箣鍜岀瓑浜?totalInconsistencies
    - **Validates: Requirements 7.1, 7.4, 7.5**

  - [x]* 11.3 缂栧啓 Property Test锛氫弗閲嶇▼搴﹀垎绫荤‘瀹氭€?
    - **Property 11: 涓ラ噸绋嬪害鍒嗙被纭畾鎬?*
    - 楠岃瘉鐩稿悓 type 鐨勪笉涓€鑷撮」濮嬬粓杩斿洖鐩稿悓涓ラ噸绋嬪害
    - **Validates: Requirements 7.3**

  - [x]* 11.4 缂栧啓鍗曞厓娴嬭瘯锛歊eportGenerator
    - 楠岃瘉鍒嗙粍閫昏緫銆佷弗閲嶇▼搴﹀垎绫汇€丣SON/Markdown 杈撳嚭鏍煎紡
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 12. CLI 鍏ュ彛涓庡鏍稿紩鎿庢暣鍚?
  - [x] 12.1 瀹炵幇 AuditEngine 涓诲紩鎿庣被
    - 鍒涘缓 `src/engine.ts`
    - 瀹炵幇閰嶇疆鍔犺浇閫昏緫
    - 缂栨帓骞惰鎵弿锛圔ackendScanner銆丳cWebScanner銆丮obileScanner锛?
    - 缂栨帓椤哄簭鎵ц姣斿鍜屽鏍革紙ConsistencyComparator銆丆overageAuditor銆丗ieldAuditor銆丳athAuditor锛?
    - 姹囨€讳笉涓€鑷撮」骞惰皟鐢?ReportGenerator 鐢熸垚鎶ュ憡
    - 瀹炵幇妯″潡娓呭崟鏍￠獙锛堥獙璇佽鐩栧叏閮?20 涓ā鍧楋級鍜?寰呭墠绔鎺?鐘舵€佹爣娉?
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 12.2 瀹炵幇 CLI 鍏ュ彛
    - 鍒涘缓 `src/cli.ts`
    - 浣跨敤 Commander.js 瀹氫箟鍛戒护琛屽弬鏁帮細`--root`锛堥」鐩牴鐩綍锛夈€乣--output`锛堣緭鍑虹洰褰曪級銆乣--format`锛堣緭鍑烘牸寮忥級銆乣--modules`锛堟寚瀹氭ā鍧楋級
    - 瀹炵幇 CLI 閫€鍑虹爜閫昏緫锛?=鏃?Critical锛?=鏈?Critical锛?=閰嶇疆閿欒锛?=杩愯鏃堕敊璇級
    - 閰嶇疆 `package.json` 鐨?`bin` 瀛楁锛屾敮鎸?`npx zw-audit` 璋冪敤
    - _Requirements: 鍏ㄩ儴_

  - [x]* 12.3 缂栧啓闆嗘垚娴嬭瘯
    - 鍦?`tests/integration/full-audit.test.ts` 涓娇鐢?fixtures 妯℃嫙瀹屾暣瀹℃牳娴佺▼
    - 楠岃瘉 CLI 鍙傛暟瑙ｆ瀽銆佹壂鎻忊啋姣斿鈫掑鏍糕啋鎶ュ憡鍏ㄩ摼璺?
    - 楠岃瘉閫€鍑虹爜鍜屾姤鍛婃枃浠剁敓鎴?
    - _Requirements: 鍏ㄩ儴_

- [x] 13. 娴嬭瘯 Fixtures 涓庡姛鑳芥槧灏勯厤缃?
  - [x] 13.1 鍒涘缓娴嬭瘯 Fixtures
    - 鍒涘缓 `tests/fixtures/java/` 鐩綍锛岀紪鍐欑ず渚?Java Controller 婧愮爜鐗囨
    - 鍒涘缓 `tests/fixtures/typescript/` 鐩綍锛岀紪鍐欑ず渚?TypeScript API 璋冪敤鏂囦欢
    - 鍒涘缓 `tests/fixtures/vue/` 鐩綍锛岀紪鍐欑ず渚?Vue 缁勪欢锛堝惈 v-model 缁戝畾锛?
    - _Requirements: 鍏ㄩ儴_

  - [x] 13.2 鍒涘缓鍔熻兘鏄犲皠閰嶇疆鏂囦欢
    - 鍒涘缓 `src/config/feature-mappings.ts`
    - 鏍规嵁 REQ-031 鍔熻兘琛ㄥ畾涔夋墍鏈夊姛鑳界偣涓庡悗绔ā鍧椼€佸墠绔枃浠剁殑鏄犲皠鍏崇郴
    - 鍖哄垎 PC 绔繀椤汇€佺Щ鍔ㄧ蹇呴』銆佸弻绔叡鏈夌殑鍔熻兘鏍囪
    - _Requirements: 5.1, 9.4_

- [x] 14. Final Checkpoint - 纭繚鍏ㄩ儴娴嬭瘯閫氳繃
  - 纭繚鎵€鏈夊崟鍏冩祴璇曘€丳roperty 娴嬭瘯銆侀泦鎴愭祴璇曢€氳繃锛屽鏈夐棶棰樿璇㈤棶鐢ㄦ埛銆?

## Notes

- 鏍囪 `*` 鐨勫瓙浠诲姟涓哄彲閫変换鍔★紝鍙烦杩囦互鍔犻€?MVP 浜や粯
- 姣忎釜浠诲姟寮曠敤浜嗗叿浣撻渶姹傛潯鐩紝纭繚鍙拷婧€?
- Checkpoint 浠诲姟纭繚澧為噺楠岃瘉
- Property 娴嬭瘯楠岃瘉閫氱敤姝ｇ‘鎬у睘鎬э紙鍏?13 涓?Property锛?
- 鍗曞厓娴嬭瘯楠岃瘉鍏蜂綋绀轰緥鍜岃竟鐣屾儏鍐?
- 瀹炵幇璇█锛歍ypeScript锛堜笌 design.md 涓€鑷达級
- 娴嬭瘯妗嗘灦锛歏itest + fast-check
- CLI 妗嗘灦锛欳ommander.js

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "13.1"] },
    { "id": 2, "tasks": ["2.1", "3.1", "4.1", "13.2"] },
    { "id": 3, "tasks": ["2.2", "2.3", "2.4", "3.2", "3.3", "3.4", "4.2"] },
    { "id": 4, "tasks": ["6.1", "7.1", "8.1", "9.1"] },
    { "id": 5, "tasks": ["6.2", "6.3", "6.4", "7.2", "7.3", "7.4", "8.2", "8.3", "8.4", "9.2", "9.3", "9.4"] },
    { "id": 6, "tasks": ["11.1"] },
    { "id": 7, "tasks": ["11.2", "11.3", "11.4"] },
    { "id": 8, "tasks": ["12.1"] },
    { "id": 9, "tasks": ["12.2"] },
    { "id": 10, "tasks": ["12.3"] }
  ]
}
```
