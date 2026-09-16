# M4 后端契约实施记录

## 实施范围
1. **P3-A 现场可信证据链**：
   - 后端新增领域契约：
     - `EvidenceSubmitDTO.java`
     - `EvidenceVO.java`
     - `SiteEvidenceController.java` (`POST /api/v1/site/evidence`, `GET /api/v1/site/evidence/verify`)
   - 包含文件存储、客户端 SHA-256 校验、设备采集时间、服务端接收时间戳与经纬度。
   - 解决前端虚标“可信存证/已核验”问题，建立严密的服务端回执与验真链路。
2. **双端 API 对齐**：
   - `zw-insight-web/src/api/evidence.ts`
   - `zw-insight-app/src/api/evidence.ts`
3. **单元测试与一致性审计**：
   - `SiteEvidenceControllerTest.java`
   - 运行一致性审计工具：后端 API 785，审核模块 20/20，**Critical 0，Major 0**。
