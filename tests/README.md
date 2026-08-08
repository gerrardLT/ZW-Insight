# ZW-Insight 娴嬭瘯浣撶郴

## 鏋舵瀯姒傝锛? 灞傛祴璇曢噾瀛楀

```
鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹? L5  鍓嶇 E2E (Playwright 鍙屾ā寮?       鈹? 鈫?鏈€鎱? 瑕嗙洊鐢ㄦ埛鎿嶄綔鍦烘櫙
鈹溾攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹? L4  绔埌绔笟鍔℃祦 (lifecycle-sim-v2.sh) 鈹? 鈫?璺ㄦā鍧楀叏閾捐矾楠岃瘉
鈹溾攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹? L3  API 鎺ュ彛娴嬭瘯 (Shell 鑴氭湰)          鈹? 鈫?楠岃瘉 REST 濂戠害
鈹溾攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹? L2  闆嗘垚娴嬭瘯 (@SpringBootTest)         鈹? 鈫?鐩磋繛鏈嶅姟鍣ㄦ暟鎹簱
鈹溾攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹? L1  鍗曞厓娴嬭瘯 (JUnit 5 + Mockito)       鈹? 鈫?鏈€蹇? 绾€昏緫楠岃瘉
鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

| 灞傜骇 | 妗嗘灦 | 鐩爣 | 棰勬湡鑰楁椂 |
|------|------|------|---------|
| L1 | JUnit 5 + Mockito + AssertJ + jqwik | 22 妯″潡 Service 灞傞€昏緫锛屽綋鍓嶉棬妲?60%锛坧om jacoco check锛夛紝80% 涓洪樁娈典笁鐩爣 | < 60s |
| L2 | @SpringBootTest + Testcontainers锛圡ySQL 8 + Redis锛夛紝澶囬€夌洿杩炴湇鍔″櫒 | CRUD 寰€杩斻€佸鎵规祦銆丗lowable 娴佺▼ | 3-5min |
| L3 | Shell 鑴氭湰 + verify-base.sh | 姣忔ā鍧?REST 绔偣 CRUD + 瀹℃壒 + 鍒嗛〉 | 2-3min |
| L4 | lifecycle-sim-v2.sh | 19 闃舵涓氬姟鐢熷懡鍛ㄦ湡锛堢珛椤光啋鎶曟爣鈫掑悎鍚屸啋棰勭畻鈫掓敹鏀啋绔ｅ伐鈫掑叧闂級 | 5-8min |
| L5 | Playwright 1.61 (鐪熷疄妯″紡/Mock 妯″紡) | 鍓嶇鐧诲綍銆侀」鐩搷浣溿€佸鎵规祦 UI | 3-5min |

---

## 鍚勫眰绾ф墽琛屾柟寮?
### L1 鍗曞厓娴嬭瘯

```bash
# 鍦ㄥ悗绔牴鐩綍鎵ц
cd zw-insight-server
mvn test                              # 杩愯鎵€鏈夊崟鍏冩祴璇?mvn test -pl zw-project               # 浠呰繍琛?project 妯″潡
mvn test -Dtest=ProjectServiceTest    # 杩愯鍗曚釜娴嬭瘯绫?```

骞惰閰嶇疆锛歋urefire `parallel=classes, threadCount=4, forkCount=1C`

### L2 闆嗘垚娴嬭瘯

```bash
cd zw-insight-server
mvn verify -Pintegration-test         # 浣跨敤鐙珛 profile 鎵ц
```

涓ょ杩愯妯″紡锛?
1. **Testcontainers 鏈湴妯″紡锛堥閫夛紝hermetic锛?*锛歚zw-app` 鐨?`BaseIntegrationTest` 浣撶郴鑷姩鍚姩 MySQL 8 + Redis 瀹瑰櫒锛屾棤 Docker 鏃?`@EnabledIfDockerAvailable` 鑷姩璺宠繃銆傛墽琛岋細`mvn test -Dtest="com.zwinsight.integration.*"`
2. **鐩磋繛鏈嶅姟鍣ㄦā寮?*锛氶厤缃枃浠?`src/test/resources/application-integration-test.yml`

鐩磋繛妯″紡鍓嶇疆鏉′欢锛?- 鏈嶅姟鍣?MySQL锛?306锛夊拰 Redis锛?379锛夊彲杈?
> 鍙楅樆鎻愰啋锛欴ocker/鏈嶅姟鍣ㄤ笉鍙揪瀵艰嚧 L2 鏃犳硶瀹炶窇鏃讹紝鎸夈€婃祴璇曞彈闃绘眹鎶ヨ鍒欍€嬬櫥璁帮紙瑙?AGENTS.md锛夛紝绂佹闈欓粯璺宠繃銆?
### L3 API 鎺ュ彛娴嬭瘯

```bash
# 鍗曚釜妯″潡
bash keys/test-api-project.sh
bash keys/test-api-contract.sh
bash keys/test-api-finance.sh
bash keys/test-api-purchase.sh
bash keys/test-api-material.sh
bash keys/test-api-machine.sh
bash keys/test-api-labor.sh
bash keys/test-api-subcontract.sh

# 鎵€鏈?L3 鑴氭湰
for script in keys/test-api-*.sh; do bash "$script"; done
```

### L4 绔埌绔笟鍔℃祦

```bash
bash keys/lifecycle-sim-v2.sh
```

杈撳嚭鎶ュ憡浣嶄簬锛歚tests/reports/lifecycle-sim-report.json`

### L5 鍓嶇 E2E

```bash
cd zw-insight-web

# 鐪熷疄妯″紡锛堟墦鏈嶅姟鍣級
npx playwright test --project=e2e-real

# Mock 妯″紡锛堟湰鍦?UI 鍥炲綊锛?npx playwright test --project=e2e
```

### 缁熶竴缂栨帓

```bash
bash tests/run-all-tests.sh                    # 鎵ц鍏ㄩ儴 5 灞?bash tests/run-all-tests.sh --layers=L1,L3     # 浠呮墽琛屾寚瀹氬眰绾?bash tests/run-all-tests.sh --fail-fast        # 棣栧眰澶辫触鍗冲仠姝?```

---

## 濡備綍娣诲姞鏂版祴璇?
### 娣诲姞 L1 鍗曞厓娴嬭瘯

1. 鍦ㄥ搴旀ā鍧?`src/test/java/` 涓嬪垱寤?`{Module}ServiceTest.java`
2. 浣跨敤 `@ExtendWith(MockitoExtension.class)` 娉ㄨВ
3. Mock 鎵€鏈?Mapper 鍜屽閮ㄤ緷璧栵紙`@Mock`锛?4. 閫氳繃 `@InjectMocks` 娉ㄥ叆琚祴 Service
5. 姣忎釜 public 鏂规硶鑷冲皯鍐?1 涓甯歌矾寰?+ 1 涓紓甯歌矾寰勬祴璇?
```java
@ExtendWith(MockitoExtension.class)
class YourServiceTest {
    @Mock private YourMapper yourMapper;
    @InjectMocks private YourServiceImpl yourService;

    @Test
    @DisplayName("姝ｅ父璺緞 - 鎻忚堪")
    void method_happyPath() {
        // Given - 璁剧疆 mock 琛屼负
        when(yourMapper.selectById(1L)).thenReturn(new YourEntity());
        // When - 璋冪敤琚祴鏂规硶
        var result = yourService.getById(1L);
        // Then - 鏂█
        assertThat(result).isNotNull();
    }
}
```

### 娣诲姞 L2 闆嗘垚娴嬭瘯

1. 鍦?`src/test/java/.../integration/` 涓嬪垱寤?`{Module}IntegrationTest.java`
2. 缁ф壙 `IntegrationTestBase`
3. 浣跨敤 `@ActiveProfiles("integration-test")` + `@SpringBootTest`
4. 鎵€鏈夋暟鎹娇鐢?`tenant_id=9999`
5. `@AfterAll` 涓皟鐢?`TestDataCleaner.cleanByTenantId(9999L)`

### 娣诲姞 L3 API 娴嬭瘯

1. 鍦?`keys/` 涓嬪垱寤?`test-api-{module}.sh`
2. 鏂囦欢寮€澶?`source "$(dirname "$0")/verify-base.sh"`
3. 浣跨敤 `call` + `assert_http` 妯″紡
4. 娴嬭瘯缁撴潫鍓?DELETE 宸插垱寤鸿祫婧?
### 娣诲姞灞炴€ф祴璇?(jqwik)

1. 鍦ㄥ搴旀ā鍧楀垱寤?`{Module}PropertyTest.java`
2. 浣跨敤 `@Property` + `@ForAll` 娉ㄨВ
3. 瀹氫箟鐢熸垚鍣ㄧ害鏉熻緭鍏ョ┖闂?4. 楠岃瘉涓氬姟涓嶅彉閲?鎭掔瓑寮?
---

## 甯歌闂鎺掓煡

### Q: L2 闆嗘垚娴嬭瘯鎶?"Connection refused"

鏈嶅姟鍣?MySQL/Redis 涓嶅彲杈俱€傛鏌ワ細
1. 鏈嶅姟鍣?IP 鍜岀鍙ｆ槸鍚︽纭紙榛樿 129.204.3.200:3306 / 6379锛?2. Docker 瀹瑰櫒 `zwi-mysql` / `zwi-redis` 鏄惁杩愯涓?3. 闃茬伀澧欐槸鍚︽斁琛岀鍙?4. 娴嬭瘯灏嗚嚜鍔ㄦ爣璁颁负 `@Disabled("Server unreachable")`

### Q: L3/L4 鑴氭湰鎶?"鐧诲綍澶辫触"

1. 纭 `verify-base.sh` 涓殑鐢ㄦ埛鍚?瀵嗙爜姝ｇ‘
2. 纭鍚庣瀹瑰櫒 `zwi-backend` 杩愯涓?3. 妫€鏌?Redis 楠岃瘉鐮佹槸鍚﹀彲璇诲彇锛坄docker exec zwi-redis redis-cli keys "captcha:*"`锛?
### Q: JaCoCo 瑕嗙洊鐜囦笉杈炬爣瀵艰嚧鏋勫缓澶辫触

1. 鏌ョ湅 `target/site/jacoco/index.html` 浜嗚В鍝簺鏂规硶鏈鐩?2. 閲嶇偣琛ュ厖澶嶆潅鍒嗘敮閫昏緫鐨勬祴璇?3. 褰撳墠闂ㄦ锛氳瑕嗙洊鐜?鈮?0%锛坧om jacoco check锛寁erify 闃舵锛夛紱80% 涓洪樁娈典笁鐩爣锛堣 `.kiro/specs/test-maturity-upgrade/tasks.md` 3.3锛?4. CI 涓?JaCoCo 鎶ュ憡浼氫笂浼犱负 artifact锛屽彲鐩存帴涓嬭浇鏌ョ湅
5. **Windows 涓枃璺緞娉ㄦ剰**锛氭湰鏈洪噰闆嗚鐩栫巼闇€鍔?`-Djacoco.destFile=<ASCII璺緞>`锛堜腑鏂囪矾寰勫鑷?agent 鏃犳硶鍐?exec 鏂囦欢锛?
### Q: 娴嬭瘯鏁版嵁娈嬬暀鎬庝箞娓呯悊

```bash
# 鎵嬪姩娓呯悊鑴氭湰
bash keys/cleanup-test-data.sh
```

鎴栫洿鎺ヨ繛鎺ユ暟鎹簱鎵ц锛?```sql
-- 娓呯悊 tenant_id=9999 鐨勬墍鏈夋祴璇曟暟鎹?DELETE FROM {table} WHERE tenant_id = 9999;
```

### Q: lifecycle-sim-v2 涓€斿け璐ュ悗鏁版嵁娌℃竻鐞?
鑴氭湰浣跨敤 `trap EXIT` 鏈哄埗锛屾甯告儏鍐典笅浼氳嚜鍔ㄦ竻鐞嗐€傚鏋滃鍣ㄥ穿婧冪瓑鏋佺鎯呭喌锛?1. 杩愯 `bash keys/cleanup-test-data.sh` 鎵嬪姩娓呯悊
2. 妫€鏌?`tests/reports/lifecycle-sim-report.json` 浜嗚В澶辫触闃舵

---

## 鏁版嵁闅旂璇存槑

### 绉熸埛闅旂鏈哄埗

鎵€鏈夋祴璇曟暟鎹娇鐢?**tenant_id=9999**锛堣嚜鍔ㄥ寲娴嬭瘯绉熸埛锛夛紝涓庣敓浜ф暟鎹畬鍏ㄩ殧绂伙細

```
鐢熶骇鏁版嵁: tenant_id 鈭?{1, 2, 3, ...}  鈫?鐪熷疄绉熸埛
娴嬭瘯鏁版嵁: tenant_id = 9999             鈫?浠呰嚜鍔ㄥ寲娴嬭瘯浣跨敤
```

### 鍏抽敭甯搁噺

| 甯搁噺 | 鍊?| 璇存槑 |
|------|---|------|
| TEST_TENANT_ID | 9999 | 娴嬭瘯绉熸埛 ID |
| TEST_TENANT_NAME | "鑷姩鍖栨祴璇曠鎴? | 绉熸埛鍚嶇О |
| REDIS_TEST_PREFIX | "test:t9999:" | Redis 閿墠缂€ |

### 瀹夊叏鎶ゆ爮

- `TestDataCleaner.cleanByTenantId()` 寮哄埗鏍￠獙 tenantId==9999锛岄潪 9999 鐩存帴鎶涘紓甯?- 鎷撴墤閫嗗簭鍒犻櫎锛岄伩鍏嶅閿害鏉熷啿绐?- `trap EXIT` 纭繚寮傚父閫€鍑烘椂浠嶆墽琛屾竻鐞?- 鍏滃簳 SQL锛歚DELETE WHERE tenant_id=9999` 娓呯悊娈嬬暀

### 娴嬭瘯绔偣瀹夊叏

- `/api/v1/test/*` 鎺ュ彛浠呭湪 `spring.profiles.active=test` 鏃舵縺娲?- 鐢熶骇鐜閮ㄧ讲鏃惰 profile 涓嶅惎鐢紝绔偣鑷姩绂佺敤
- Token 缂撳瓨鏂囦欢鏉冮檺 600锛屾祴璇曠粨鏉熷悗鍒犻櫎

---

## 瑕嗙洊鐜囨姤鍛?
### 鏈湴鐢熸垚

```bash
cd zw-insight-server
mvn test                    # 杩愯娴嬭瘯锛圝aCoCo agent 鑷姩鏀堕泦锛?mvn jacoco:report           # 鐢熸垚 HTML 鎶ュ憡
```

鎶ュ憡璺緞锛歚{module}/target/site/jacoco/index.html`

### CI 鎶ュ憡

CI 鏋勫缓瀹屾垚鍚庯紝JaCoCo HTML 鎶ュ憡浼氫笂浼犱负 GitHub Actions artifact锛?1. 杩涘叆 GitHub Actions 鈫?瀵瑰簲 workflow run
2. 涓嬭浇 `jacoco-coverage-report` artifact
3. 瑙ｅ帇鍚庢墦寮€ `index.html` 鏌ョ湅璇︾粏瑕嗙洊鐜?
### 瑕嗙洊鐜囬棬妲?
| 闃舵 | 琛岃鐩栫巼瑕佹眰 | 寮哄埗鏂瑰紡 |
|------|------------|---------|
| 褰撳墠锛堥樁娈典竴锛?| 鈮?0%锛坧om jacoco check锛孊UNDLE LINE锛? `tests/coverage-baseline.json` 涓嶅洖閫€瀹堟姢 | verify 闃舵闂ㄦ + CI 鍩虹嚎姣斿 |
| 闃舵涓夌洰鏍?| 鏍稿績 8 妯″潡 鈮?0% | 杈炬爣鍚?pom check 璋冭嚦 0.80锛孋I 鍒?verify |

鍚勬ā鍧楀疄娴嬪熀绾胯 `tests/TESTING-MATURITY.md` 闄勫綍 A锛涙祴璇曞彈闃诲鐞嗚鍒欒 AGENTS.md銆?
---

## 鐩稿叧鏂囨。

- 娴嬭瘯鎴愮啛搴﹁瘎浼帮細`tests/TESTING-MATURITY.md`
- 鍗囩骇涓夐樁娈典换鍔★細`.kiro/specs/test-maturity-upgrade/tasks.md`锛堝惈鍙楅樆椤圭櫥璁板彴璐︼級



---

## Performance Testing (k6)

### Overview
Performance baseline testing using Grafana k6 for key business flows.

### Scripts Location
	ests/performance/:
- login.js - Login API performance test
- page-query.js - Page query performance test  
- payment-submit.js - Payment submission performance test

### Execution
**NOTE**: Due to production environment constraints, k6 is configured to run on the remote server during off-peak hours.

#### Server-side Execution (Recommended):
`ash
# Connect to production server
ssh user@server

# Run k6 performance tests
cd /root/zw-insight/tests/performance
bash run-k6.sh
`

#### Constraints:
- Concurrent users: ≤ 20
- Duration: ≤ 5 minutes per test
- Target tenant_id: 9999 (test tenant only)
- Schedule: Nightly low-peak window (23:00 UTC recommended)
- **Status**: Script and Docker image are ready on server, cron job disabled until manual execution needed

### Captcha Bridge
Captcha bridge script (keys/captcha-bridge.py) ensures real captcha validation during testing.
