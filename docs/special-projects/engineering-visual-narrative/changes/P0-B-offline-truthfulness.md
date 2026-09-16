# P0-B 离线真实性改造记录

- 实施日期：2026-09-14
- 涉及文件：
  - `zw-insight-app/src/types/offline.ts`
  - `zw-insight-app/src/utils/offlineData.ts`
  - `zw-insight-app/tests/offlineData.test.ts`

## 1. 真实性与新鲜度元数据规范
1. **数据来源真实化**：
   - 在线拉取返回 `source: 'NETWORK'`
   - 离线回退或离线直读返回 `source: 'CACHE'`
2. **拒绝静默网络失败**：
   - 之前代码在在线接口报错时，`catch` 内部直接调用 `readFromCache` 返回相同数据形态，UI 层和用户无法得知刚才的在线网络请求其实失败了。
   - 修复后：增加 `fallbackReason: 'NETWORK_ERROR'` 与明确提示 `message: '在线请求失败，当前展示本地缓存'`，向业务层与用户诚实呈现真实状态。
3. **新鲜度 (Freshness)**：
   - 依据缓存时间戳计算年龄 `ageMs`：7 天以内为 `FRESH`，超过 7 天为 `STALE`，未记录为 `UNKNOWN`。
4. **单测覆盖**：
   - 11/11 项测试通过，精确覆盖在线/离线/接口失败回退等全链路元数据断言。
