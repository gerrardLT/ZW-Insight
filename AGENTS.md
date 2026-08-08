# AGENTS.md 鈥?AI 浠ｇ悊宸ヤ綔绾﹀畾

鏈」鐩娇鐢?**Kiro**锛圓I 寮€鍙戜唬鐞嗭級杩涜 Spec 椹卞姩寮€鍙戯紝瑕嗙洊闇€姹傚垎鏋愩€佽璁°€佷换鍔℃媶瑙ｃ€佷唬鐮佸疄鐜颁笌鑱旇皟楠岃瘉鍏ㄦ祦绋嬨€?
## Spec 椹卞姩寮€鍙?
椤圭洰閲囩敤 Kiro 鐨?Spec 宸ヤ綔娴侊細**Requirements 鈫?Design 鈫?Tasks**锛屾瘡涓?feature 瀵瑰簲涓€涓嫭绔嬬洰褰曪紝鍖呭惈 `requirements.md`銆乣design.md`銆乣tasks.md` 涓変釜鏂囦欢銆?
### Spec 鏂囦欢浣嶇疆

```
.kiro/specs/
```

### 宸插畬鎴愮殑 Spec 鍒楄〃

| Spec 鍚嶇О | 璇存槑 |
|-----------|------|
| `zw-insight-platform` | 骞冲彴鏁翠綋鏋舵瀯涓庢ā鍧楀垝鍒?|
| `p0-core-features` | P0 鏍稿績鍔熻兘锛圕RUD銆佸鎵广€佹祦绋嬶級 |
| `p0-data-permission-overdue` | P0 鏁版嵁鏉冮檺 & 閫炬湡鎻愰啋 |
| `p1-system-integrity` | P1 绯荤粺瀹屾暣鎬э紙琛ュ叏缂哄け鍔熻兘锛?|
| `p1-business-completion` | P1 涓氬姟琛ュ叏锛堝姵鍔?鍒嗗寘/鏈烘绛夛級 |
| `p2-quick-wins` | P2 蹇€熶紭鍖栭」 |
| `p2-experience-enhancement` | P2 浣撻獙澧炲己 |
| `p2-business-enhance` | P2 涓氬姟澧炲己 |
| `p2-advanced` | P2 楂樼骇鍔熻兘 |
| `frontend-backend-integration` | 鍓嶅悗绔仈璋冨榻愶紙63 椤规牳蹇冮敊浣嶏級 |
| `consistency-audit` | 涓€鑷存€у璁″伐鍏峰紑鍙?|

## 鑱旇皟楠岃瘉鍩哄骇

鐢ㄤ簬楠岃瘉杩滅▼鑱旇皟鏈嶅姟鍣ㄤ笂鐨勭湡瀹炴帴鍙ｆ槸鍚︽甯稿伐浣溿€?
```powershell
# Windows锛圥owerShell锛?.\keys\verify.ps1
```

```bash
# Linux / SSH
bash keys/verify-base.sh
```

楠岃瘉鍩哄骇浼氫緷娆¤皟鐢ㄦ牳蹇?API 绔偣锛岀‘璁?HTTP 鐘舵€佺爜鍜屽搷搴旂粨鏋勬纭€傚嚟璇佹枃浠?`keys/zwinsight.pem` 宸茬撼鍏?`.gitignore`銆?
## L4 鍏ㄧ敓鍛藉懆鏈熸祴璇曪紙闅旂绉熸埛 9999锛?
`keys/lifecycle-sim-v2.sh` 鍦ㄩ殧绂绘祴璇曠鎴凤紙tenant_id=9999锛岃处鍙?t9999admin锛変笂璺戦€?19 涓樁娈电殑鍏ㄤ笟鍔￠棴鐜細鎶ュ鈫掔珛椤光啋鎶曟爣鈫掍腑鏍団啋鏂藉伐鍚堝悓鈫掗绠楋紙鍚?BLOCK 鎷︽埅璐熷悜鐢ㄤ緥锛夆啋鍥涚被鏀嚭鍚堝悓鈫掓潗鏂欏叆鍑哄簱鈫掓満姊?鍔冲姟/鍒嗗寘鎵ц涓庣粨绠椻啋浜у€尖啋寮€绁ㄦ敹娆锯啋閲囪喘缁撶畻鈫掍粯娆鹃棴鐜啋椹冲洖鍒嗘敮锛堥€€鍥為噸瀹?缁堟閲嶆彁锛夆啋绔炲伐缁撶畻鈫掔粨椤广€傛瘡闃舵鍚姸鎬?閲戦纭柇瑷€锛岄€€鍑虹爜涓ユ牸鍙嶆槧缁撴灉銆?
```bash
# 棣栨锛氬垵濮嬪寲娴嬭瘯绉熸埛锛堝箓绛夛級+ 閮ㄧ讲 BPMN 鍒扮鎴?9999
bash keys/init-test-tenant.sh
ZWI_USER=t9999admin ZWI_PASS=123456 ZWI_TENANT_ID=9999 bash keys/deploy-bpmn.sh

# 杩愯 L4 + 娓呯悊楠屾敹锛坆iz_ 琛?tenant 9999 娈嬬暀搴斾负 0锛?bash keys/lifecycle-sim-v2.sh
bash keys/verify-l4-clean.sh
```

鍏抽敭绾︽潫锛氭祴璇曠鎴风紪鍙疯鍒欑敤 `T9` 鍓嶇紑锛堜笟鍔＄紪鍙峰敮涓€閿负鍏ㄥ眬鍞竴锛岄槻涓庣鎴?1 鎾炲彿锛夛紱鍏戝簳娓呯悊浠呴檺 `biz_%` 琛紙涓ョ璇垹 sys_user/serial_number_rule 绛夊惈 tenant_id 鐨勭郴缁熻〃锛夛紱寰呭姙椹卞姩浠?ACT_RU_TASK 鍙?taskId锛堝€欓€夌粍浠诲姟涓嶅叆 assignee 寰呭姙锛孲UPER_ADMIN 鍙畬鎴愪换鎰忎换鍔★級銆?
## 婕旂ず绉嶅瓙鏁版嵁

鐢ㄤ簬蹇€熷～鍏呴粯璁ょ鎴凤紙`tenant_id=1`锛夌殑鍏ㄦā鍧楁紨绀烘暟鎹紝鐧诲綍绯荤粺鍗冲彲鐪嬪埌瀹屾暣鐨勯」鐩€佸悎鍚屻€侀绠椼€佽储鍔＄瓑涓氬姟閾捐矾銆?
### 鑴氭湰浣嶇疆

```
deploy/db-init/31_V2026_26__seed_demo_data.sql
```

### 璁捐瑕佺偣

- **绉熸埛**锛氬叏閮ㄨ褰?`tenant_id=1`锛屼负鎸佷箙鍖栨紨绀烘暟鎹?- **ID 娈?*锛氬浐瀹氫娇鐢?`90001-99999`锛岄伩鍏嶄笌涓氬姟闆姳 ID 鍙婂凡鏈夌瀛愶紙`900001-900005` 缂栧彿瑙勫垯锛夊啿绐?- **骞傜瓑**锛氬叏閮?`INSERT IGNORE`锛屽彲閲嶅鎵ц涓嶆姤閿欙紙閲嶅閿?1062 琚拷鐣ワ級
- **渚濊禆椤哄簭**锛氭寜 Layer 0-14 浠庡簳灞傚埌椤跺眰鎻掑叆锛堝熀纭€鏁版嵁鈫掗」鐩啋鎶曟爣鈫掑悎鍚屸啋棰勭畻鈫掍骇鍊尖啋鏉愭枡鈫掓満姊扳啋鍔冲姟鈫掑垎鍖呪啋鐜板満鈫掕储鍔♀啋璇环鈫掓秷鎭啋璇勪环锛夛紝瑕嗙洊 55+ 寮犱笟鍔¤〃
- **鏁版嵁闂幆**锛? 涓笉鍚岀敓鍛藉懆鏈熼」鐩€斺€擿90001 婊ㄦ睙鑺卞洯涓€鏈焋锛堟柦宸ヤ腑锛屽叏妯″潡锛夈€乣90002 鍩庡崡甯傛斂閬撹矾鏀归€燻锛堝凡绔ｅ伐锛岀粨绠?璐ㄤ繚閲戯級銆乣90003 楂樻柊鍖轰骇涓氬洯浜屾湡`锛堝凡鎶ュ锛屾姇鏍囷級锛涢噾棰濇寜銆屽悎鍚屸啋浜у€尖啋寮€绁ㄢ啋鏀舵鈫掗绠椻啋鍚勬敮鍑哄悎鍚屸啋缁撶畻鈫掍粯娆俱€嶉€昏緫鑷唇

### 瀵煎叆涓庨獙璇?
```bash
# 瀵煎叆绉嶅瓙骞剁粺璁¤鏁帮紙骞傜瓑锛?bash keys/verify-seed.sh import

# 浠?DB 琛屾暟缁熻 / 浠?API 鎶芥煡 / 瀹屾暣楠岃瘉
bash keys/verify-seed.sh db
bash keys/verify-seed.sh api
bash keys/verify-seed.sh
```

楠岃瘉鑴氭湰 `keys/verify-seed.sh` 澶嶇敤 `verify-base.sh` 鐨勭湡瀹炵櫥褰曡兘鍔涳紝鎶芥煡 `project/page`銆乣contract/page`銆乣finance/payment-apply/page` 绛夊垎椤垫帴鍙ｏ紝骞剁洿杩?MySQL 鏍￠獙鍥哄畾 ID 娈佃鏁般€?
## 涓€鑷存€у璁″伐鍏?
浣嶄簬 `tools/consistency-audit/`锛屾槸涓€涓?Node.js CLI 宸ュ叿锛岃嚜鍔ㄦ壂鎻忓悗绔?Controller銆丳C 鍓嶇 api/*.ts銆佺Щ鍔ㄧ api/*.ts锛岀敓鎴愪笁绔竴鑷存€у璁℃姤鍛娿€?
### 浣跨敤鏂规硶

```bash
cd tools/consistency-audit
npm install
npm run dev          # 杩愯瀹¤锛堝紑鍙戞ā寮忥紝tsx 鐩存帴鎵ц锛?npm run build        # 缂栬瘧 TypeScript
npm test             # 杩愯灞炴€ф祴璇曪紙fast-check锛?```

### 瀹¤杈撳嚭

- 鎶ュ憡鑷姩鐢熸垚鍒?`audit-reports/` 鐩綍锛圝SON + Markdown 鍙屾牸寮忥級
- 鍖呭惈妯″潡绾у埆鐨勪笉涓€鑷撮」鍒嗙被锛歚FEATURE_MISSING`銆乣HTTP_METHOD_MISMATCH`銆乣FRONTEND_EXTRA_API`銆乣BACKEND_ORPHAN_API` 绛?- 涓ラ噸绾у埆锛欳ritical > Major > Minor

## Steering 瑙勫垯

椤圭洰绾?Steering 瑙勫垯浣嶄簬锛?
```
~/.kiro/steering/base.md        # 鍏ㄥ眬鐢ㄦ埛绾ц鍒?```

褰撳墠鏃犻」鐩骇 `.kiro/steering/` 鐩綍锛岃鍒欓€氳繃鍏ㄥ眬閰嶇疆鐢熸晥銆?
## 寮€鍙戠害瀹?
浠ヤ笅绾﹀畾鍦?AI 浠ｇ悊鍗忎綔寮€鍙戜腑蹇呴』閬靛畧锛?
### 1. 鐪熷疄鎺ュ彛锛屼笉鐢ㄥ亣鏁版嵁

鎵€鏈変笟鍔″紑鍙戝繀椤诲鎺ョ湡瀹炲悗绔帴鍙ｃ€傜姝娇鐢?mock 鏁版嵁鎴栭潤榛?fallback銆傚彲浠ユ湁澶囬€夋柟妗堬紝浣嗕笉鑳界敤瀹屽叏涓嶇湡瀹炵殑鏁版嵁鏉ュ疄鐜板姛鑳姐€?
### 2. 鍚庣 Controller 涓?Source of Truth

鍓嶇 API 瀹氫箟蹇呴』涓庡悗绔?Controller 娉ㄨВ涓ユ牸涓€鑷达細
- HTTP 鏂规硶锛圙ET/POST/PUT/DELETE锛?- 璺緞锛坄@RequestMapping` 鍊硷級
- 璇锋眰/鍝嶅簲瀛楁鍚?
褰撳嚭鐜板垎姝ф椂锛屼互鍚庣 Controller 涓哄噯淇敼鍓嶇銆?
### 3. RESTful 绾﹀畾

- 璺緞鏍煎紡锛歚/api/v1/{module}/{resource}`
- 鍒嗛〉鏌ヨ锛欸ET + Query Params锛坄page`銆乣size`锛?- 鍒涘缓锛歅OST
- 鏇存柊锛歅UT `/{id}`
- 鍒犻櫎锛欴ELETE `/{id}`
- 璇︽儏锛欸ET `/{id}`
- 鎵归噺鎿嶄綔锛歅OST `/{resource}/batch`

璇︾粏瑙勮寖瑙?`audit-reports/rest-convention.md`銆?
### 4. 鍓嶅悗绔竴鑷存€ф鏌?
姣忔娑夊強鎺ュ彛鍙樻洿鐨勫紑鍙戝畬鎴愬悗锛岃繍琛屼竴鑷存€у璁★細

```bash
cd tools/consistency-audit && npm run dev
```

纭繚鏂板/淇敼鐨勬帴鍙ｄ笉寮曞叆 Critical 绾у埆鐨勪笉涓€鑷撮」銆?
### 5. 鏁版嵁搴撳彉鏇?
- 澧為噺杩佺Щ鑴氭湰鏀惧叆 `deploy/db-init/`锛屾寜搴忓彿鍛藉悕
- 瀛楁浣跨敤瑙勮寖锛歚BigDecimal`/`DECIMAL(18,2)`锛堥噾棰濓級銆乣deleted`锛堥€昏緫鍒犻櫎锛夈€乣version`锛堜箰瑙傞攣锛夈€乣tenant_id`锛堢鎴烽殧绂伙級

### 6. 鎶€鏈柟妗堣皟鐮?
杩涜鎶€鏈柟妗堥€夊瀷鏃讹紝浼樺厛鏌ユ壘瀹樻柟鏂囨。浜嗚В鏈€鏂扮敤娉曪紝瀵绘壘绋冲畾鍙潬鐨勫紑婧愰」鐩繘琛屽姣旈€夋嫨銆?
### 7. 鏀归€犺褰?
杩涜椤圭洰浼樺寲鏀归€犳椂锛岄渶瀹屾暣璁板綍鏀归€犵殑璇︾粏淇℃伅锛堝彉鏇村師鍥犮€佸奖鍝嶈寖鍥淬€佸洖婊氭柟妗堬級锛岀‘淇濆悗缁兘浠庝笂涓嬫枃鎭㈠銆?
### 8. 娴嬭瘯寮€鍙戣鍒?
浠ヤ笅瑙勫垯鍦ㄥ紑鍙戞柊鍔熻兘鎴栦慨澶?Bug 鏃跺繀椤婚伒瀹堬細

#### 鏂版ā鍧楀繀椤诲寘鍚崟鍏冩祴璇?
- 姣忎釜鏂板缓 Service 绫荤殑 public 鏂规硶鑷冲皯缂栧啓 1 涓甯歌矾寰?+ 1 涓紓甯歌矾寰勬祴璇?- 浣跨敤 `@ExtendWith(MockitoExtension.class)` + Mockito Mock 鎵€鏈夊閮ㄤ緷璧?- 瑕嗙洊鐜囬棬妲涳細褰撳墠 pom jacoco check 涓鸿瑕嗙洊鐜?鈮?0%锛坴erify 闃舵锛孊UNDLE锛夛紝涓?`tests/coverage-baseline.json` 鍙崌涓嶉檷锛圕I 姣斿锛夛紱鏍稿績 8 妯″潡 鈮?0% 涓洪樁娈典笁鐩爣锛堣 `.kiro/specs/test-maturity-upgrade/tasks.md` 3.3锛夈€傚悇妯″潡瀹炴祴鍩虹嚎瑙?`tests/TESTING-MATURITY.md` 闄勫綍 A

#### 闆嗘垚娴嬭瘯浣跨敤 tenant_id=9999

- 鎵€鏈夐泦鎴愭祴璇曟暟鎹繀椤讳娇鐢?`tenant_id=9999`锛堣嚜鍔ㄥ寲娴嬭瘯绉熸埛锛?- 涓ョ鍦ㄦ祴璇曚腑浣跨敤鐪熷疄绉熸埛 ID 鎴栨搷浣滅敓浜ф暟鎹?- `@AfterAll` 蹇呴』璋冪敤 `TestDataCleaner.cleanByTenantId(9999L)` 娓呯悊娴嬭瘯鏁版嵁
- Redis 娴嬭瘯閿娇鐢?`test:t9999:` 鍓嶇紑锛屾祴璇曞悗娓呴櫎

#### PR 鍓嶈繍琛?L1 鍗曞厓娴嬭瘯

- 鎻愪氦 PR 鍓嶅繀椤诲湪鏈湴杩愯 `mvn test` 纭鍗曞厓娴嬭瘯閫氳繃
- CI backend job 鎵ц `mvn -B clean package`锛堣繍琛屽叏閲忓崟鍏冩祴璇?+ JaCoCo 鎶ュ憡锛夛紝骞舵瘮瀵?`tests/coverage-baseline.json`锛氫换涓€妯″潡瑕嗙洊鐜囧洖閫€鍗虫瀯寤哄け璐?- 瑕嗙洊鐜囬棬妲涳紙jacoco check 0.60锛夌粦瀹?verify 闃舵锛涢樁娈典笁鐩爣杈炬垚鍚?CI 鍒?verify 寮哄埗锛堣 spec 3.3锛?
#### 娴嬭瘯鍙楅樆姹囨姤瑙勫垯锛堝己鍒讹紝AI 浠ｇ悊涓庝汉鍧囬€傜敤锛?
娴嬭瘯鍥犵幆澧冩垨鍏朵粬鍘熷洜鏃犳硶鎵ц鏃讹紙Docker 鏈惎銆佺綉缁滀笉鍙揪銆佸嚟璇佸け鏁堛€佸伐鍏疯涓嶄笂銆佽鐩栫巼閲囬泦澶辫触绛夛級锛?
1. **绂佹闈欓粯璺宠繃銆佺姝㈡爣璁颁负閫氳繃銆佺姝㈢敤 mock/鍋囨暟鎹檷绾ф浛浠ｇ湡瀹為獙璇?*锛堜笌鏈」鐩€滅湡瀹炴帴鍙ｇ湡瀹炴祦绋嬧€濆師鍒欎竴鑷达級
2. 蹇呴』绔嬪嵆锛氣憼 鍋滄璇ラ」鎵ц 鈶?鍦?`.kiro/specs/test-maturity-upgrade/tasks.md` 鏈熬鈥滃彈闃婚」鐧昏鍙拌处鈥濊拷鍔犱竴琛岋紙鏃ユ湡/灞傜骇/娴嬭瘯椤?鍒嗙被 ENV|DEP|NET|CRED|DATA|OTHER/鍘熷洜/褰卞搷鑼冨洿/澶勭疆鍐崇瓥/鍐崇瓥浜?鐘舵€侊級 鈶?鍚戠敤鎴锋眹鎶ワ紙鍙楅樆鍘熷洜 + 褰卞搷鑼冨洿 + 涓夐€夐」锛氫慨澶嶇幆澧?寤舵湡/缂╁噺鑼冨洿锛?3. 鐢ㄦ埛鍐崇瓥鍚庡洖濉彴璐︹€滃缃喅绛?鍐崇瓥浜衡€濆垪锛涚姝?AI 鑷鍐冲畾闄嶇骇鏂规
4. 姹囨姤妯℃澘涓庡巻鍙叉渚嬭 `tests/TESTING-MATURITY.md` 闄勫綍 B锛涙湰鏈?JaCoCo 涓枃璺緞鍧戦渶鍔?`-Djacoco.destFile=<ASCII璺緞>`

#### 娴嬭瘯浣撶郴鏂囨。

- 璇︾粏鐨勬祴璇曟灦鏋勩€佹墽琛屾柟寮忋€佹坊鍔犳柊娴嬭瘯鎸囧崡瑙?`tests/README.md`
- 娴嬭瘯甯搁噺瀹氫箟瑙?`zw-common/src/test/java/com/zwinsight/common/base/TestConstants.java`
- 缁熶竴缂栨帓鑴氭湰锛歚bash tests/run-all-tests.sh`

### 9. 涓存椂鏂囦欢涓庢枃妗ｇ鐞?
AI 浠ｇ悊鍦ㄥ紑鍙戙€佽皟璇曘€佽瘎瀹¤繃绋嬩腑浜х敓鐨勪复鏃朵骇鐗╁繀椤婚伒寰互涓嬭鍒欙細

#### 鍛藉悕绾﹀畾

- 鎵€鏈変复鏃舵枃浠跺繀椤讳互 `_` 鍓嶇紑鍛藉悕锛堝 `_review_dashboard.png`銆乣_test4.log`锛?- `.gitignore` 宸查厤缃?`keys/_*`銆乣*.log`銆乣test-results/`銆乣**/eng.traineddata` 绛夋帓闄よ鍒?- 鏂板涓存椂鏂囦欢绫诲瀷鏃讹紝鍚屾鏇存柊 `.gitignore`

#### 鐢熷懡鍛ㄦ湡

- **浠诲姟缁撴潫鍗虫竻鐞?*锛氭瘡娆′换鍔★紙寮€鍙?璋冭瘯/璇勫/楠岃瘉锛夊畬鎴愬悗锛屽繀椤诲垹闄ゆ湰娆′骇鐢熺殑鎵€鏈変复鏃舵枃浠?- **涓嶅緱璺ㄤ細璇濇畫鐣?*锛氭埅鍥俱€佹棩蹇椼€佽瘖鏂緭鍑恒€侀獙璇佺爜鍥剧墖绛変笉寰楅仐鐣欏湪宸ヤ綔鍖?- **鏋勫缓浜х墿鎸夐渶淇濈暀**锛歚dist/`銆乣deploy/zw-insight-app.jar` 绛夊彲鍐嶇敓浜х墿鍙繚鐣欙紝浣嗕笉搴斾富鍔ㄥ垱寤哄啑浣欏壇鏈?
#### 绂佹浜嬮」

- 绂佹鍦ㄩ」鐩牴鐩綍鎴?`src/` 涓嬪垱寤轰换浣曢潪婧愮爜涓存椂鏂囦欢
- 绂佹灏嗚皟璇曟埅鍥俱€丱CR 璁粌鏁版嵁銆丳laywright trace 绛夊ぇ浣撶Н浜岃繘鍒舵枃浠剁暀鍦ㄥ伐浣滃尯
- 绂佹鍒涘缓鐢ㄤ簬鈥滆窡韪繘搴︹€濈殑 `.md` 鏂囨。锛堝 `TODO.md`銆乣PROGRESS.md`锛?
#### 娓呯悊妫€鏌ユ竻鍗?
浠诲姟瀹屾垚鍚庤嚜鏌ワ細

```
鉁?鏍圭洰褰曟棤 _*.png / _*.log 娈嬬暀
鉁?keys/ 鏃犳柊澧?_* 璇婃柇浜х墿
鉁?zw-insight-web/ 鏃?test-results/ 鎴?eng.traineddata
鉁?鏃犳柊澧炴湭绾冲叆 .gitignore 鐨勪复鏃舵枃浠?```
