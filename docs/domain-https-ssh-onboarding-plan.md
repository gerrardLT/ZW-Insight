# 域名与 HTTPS / SSH 接入规划（zy.cn-zwny.com）

> 依据 2026-09-18 服务器实测（只读取证）编写。**本文件是规划，尚未执行任何变更。**

## 一、现状实测

| 项 | 实测结果 |
|---|---|
| 域名解析 | `zy.cn-zwny.com` → **129.204.3.200 已生效**（服务器侧 `getent hosts` 确认）。本地解析到 198.18.0.215 属 VPN fake-ip 劫持，可忽略 |
| 宝塔 | 已安装（`/www/server/panel`，`bt` 命令可用） |
| 80 / 443 | **已被宿主宝塔 nginx 占用** → 无法让容器直接监听 443，必须走宝塔站点反代 |
| 应用入口 | `zwi-frontend` 容器 `18081 → 80`；容器内 nginx 已含 SPA 路由 + `location /api/` → `backend:8080` |
| 后端 | `zwi-backend` 容器 `18080 → 8080` |
| 同源情况 | 前端与 API **同源**（均经 18081，容器内 nginx 已反代 `/api`）→ 加域名**无需改前端 baseURL 或后端 CORS** |
| 同机其他业务 | `ch-zkzd.com`、`workfusion.zwny-cn.com`、`bid.zwny-cn.com`、`hr.zwny-cn.com`、`hmjl.ch-zkzd.com`、`unified-auth*`、`influxdb`、另一个 `mysql/redis` → **多业务共享，改动必须隔离** |
| 前缀联通性 | `http://127.0.0.1:18081/` → 200；`http://127.0.0.1:18081/api/v1/captcha/image` → 200 |
| SSH | `PermitRootLogin yes`；未显式配置 `PasswordAuthentication`（即默认允许密码登录）；密钥 `keys/zwinsight.pem` 可用 |
| 证书工具 | 未装 certbot → 用宝塔面板一键 Let's Encrypt（内置 acme） |

## 二、阶段 1：域名可用（HTTP，最小改动）

**目标**：`http://zy.cn-zwny.com` 可访问，形同现在的 `http://129.204.3.200:18081`。

1. 宝塔面板 → **网站 → 添加站点**
   - 域名：`zy.cn-zwny.com`
   - 不创建数据库；PHP 版本选「纯静态」
   - 根目录保持默认（会被反代覆盖，不承载实际文件）
2. 该站点 → **反向代理 → 添加反向代理**
   - 代理名称：`zwinsight`
   - 目标 URL：`http://127.0.0.1:18081`
   - 发送域名：`$host`（**关键**，保留原始 Host 供内层 nginx 与后端识别）
3. 在反代生成的配置段中确认/补齐以下指令（宝塔默认可能缺）：
   ```nginx
   proxy_set_header X-Real-IP $remote_addr;
   proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
   proxy_set_header X-Forwarded-Proto $scheme;
   client_max_body_size 50m;          # 与内层 nginx 一致，否则大文件上传被外层 413
   proxy_http_version 1.1;            # WebSocket / 长连接（Flowable 推送等）
   proxy_set_header Upgrade $http_upgrade;
   proxy_set_header Connection "upgrade";
   proxy_read_timeout 120s;           # 与内层一致
   ```
4. 验证：
   ```bash
   curl -I http://zy.cn-zwny.com                       # 期望 200
   curl -s -o /dev/null -w '%{http_code}\n' http://zy.cn-zwny.com/api/v1/captcha/image   # 期望 200
   ```
   浏览器访问 `http://zy.cn-zwny.com/project-cost-control`，确认页面与 API 均正常。

**禁止事项**：不要修改 `0.default.conf` 或其他业务站点（`ch-zkzd.com`、`workfusion.*`）的配置；只新增本站点文件。

## 三、阶段 2：HTTPS（消除截图中的「不安全」）

1. 该站点 → **SSL → Let's Encrypt**
   - 勾选 `zy.cn-zwny.com` → 申请（要求 80 端口已能正确响应本域名，阶段 1 已验证）
2. 申请成功后开启 **强制 HTTPS**（HTTP 301 → HTTPS）
3. 确认**自动续签**已开启（宝塔默认开启，证书 90 天有效）
4. 后端 scheme 感知（仅当出现「HTTPS 页面里重定向回 http」时处理）：
   - 给 `zwi-backend` 增加环境变量 `SERVER_FORWARD_HEADERS_STRATEGY: framework`，使 Spring Boot 信任 `X-Forwarded-Proto`；
   - 若仅前后端同源 API 调用（本项目情况），通常无需该配置。
5. 验证：
   ```bash
   curl -I https://zy.cn-zwny.com            # 期望 200，且带 HSTS 头（若开启）
   curl -I http://zy.cn-zwny.com             # 期望 301 → https
   ```

**TLS 终止位置**：由宿主宝塔 nginx 终止 TLS，容器内仍是 HTTP，**无需给容器挂证书**。

## 四、阶段 3：SSH

按实际意图选做；下面按「加固」与「用域名连接」两条独立方案给出。

### 3A. 用域名代替 IP 连接（已可用，零改动）

域名已解析，因此下列命令立即可用：
```bash
ssh -i keys/zwinsight.pem root@zy.cn-zwny.com
```
可选同步点（不强制）：
- `keys/*.ps1` 中 `$RemoteHost` 的默认值由 `root@129.204.3.200` 改为 `root@zy.cn-zwny.com`
- GitHub secret `SERVER_HOST` 保持 IP（见阶段 4，建议不动）

### 3B. SSH 登录加固（需谨慎，有自锁风险）

**前置保命措施（必须全部满足再动手）**：
1. 开**第二个** SSH 会话并保持不关闭（改配置后用于验证新会话能否登录）
2. 确认宝塔面板（默认 `https://129.204.3.200:8888`）可正常登录 —— 作为 SSH 全锁后的兜底通道
3. 先验证密钥登录可独立工作（不依赖密码）：
   ```bash
   ssh -i keys/zwinsight.pem -o PreferredAuthentications=publickey root@zy.cn-zwny.com whoami
   ```

**变更步骤**：
1. 备份：`cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak-$(date +%F)`
2. 编辑 `/etc/ssh/sshd_config`（或新增 `/etc/ssh/sshd_config.d/99-hardening.conf`，注意 `.d` 目录优先级）：
   ```
   PasswordAuthentication no
   PermitRootLogin prohibit-password
   PubkeyAuthentication yes
   ```
3. **语法预检**（关键，避免 reload 失败留下不可用状态）：`sshd -t`
4. 生效：`systemctl reload sshd`（reload 不影响已有连接）
5. 用**新会话**验证密钥登录仍可用；确认无误后再关闭保命会话
6. （可选）改端口：需同步放通云安全组与 firewalld/ufw，收益有限、成本高，非必要不做
7. （可选）安装 fail2ban 防爆破

**回滚**：`cp /etc/ssh/sshd_config.bak-<date> /etc/ssh/sshd_config && systemctl reload sshd`

## 五、阶段 4：CI / 脚本适配（可选，建议最小化）

现状：`.github/workflows/deploy.yml` 中 `SERVER_HOST` / `SERVER_USER` / `DEPLOY_DIR` 均来自 GitHub secrets，健康检查直连 **18080**：

```
http://${{ env.SERVER_HOST }}:18080/api/v1/system/menu/user   # 期望 200/401/403
```

**建议：部署通道与访问入口解耦 —— `SERVER_HOST` 保持 IP 不动。**

理由：健康检查走 `18080`，若把 `SERVER_HOST` 换成域名，则依赖「公网 80→域名→18080」链路与安全组放通；而部署链路（rsync + ssh）用 IP 更直接、少一层 DNS 依赖。换域名的唯一收益是「配置里不出现 IP」，收益低而引入新失败点。

若确需切换，注意：
- 云安全组需放通 `18080`（当前为 `0.0.0.0:18080` 监听，公网可直连）
- `ssh-keyscan -H $SERVER_HOST` 对域名同样可用
- 更稳做法：**保留** IP 健康检查，**新增**一条经域名的冒烟断言（`https://zy.cn-zwny.com` 返回 200），两者并存

**顺带建议**：`18080` 当前对公网直连暴露，可绕过 nginx 访问后端 API。接入域名后，建议在安全组**仅放通 80/443**，把 18080 收敛为内网（前端容器经 docker 网络访问，不受影响）。此项独立于域名接入，但同属「入口收敛」。

## 六、不需要改动的部分（明确结论）

| 项 | 结论 |
|---|---|
| 前端代码 | **不改**。`baseURL=/api` 为相对路径，同源经 nginx 反代，域名变更无感知 |
| 后端 CORS | **不改**。前后端同源，无跨域 |
| 容器端口映射 | **不改**。18081 保持，仅在宿主 nginx 前加一层域名站点 |
| 容器内 nginx.conf | **不改**。`server_name _` 通配，外层 `$host` 透传即可 |
| 数据库 / 中间件 | **不改**。均在内网，不涉及域名 |

## 七、风险与回滚

| 风险 | 缓解 | 回滚 |
|---|---|---|
| 宝塔站点配置写错，影响同机其他站点 | 只新增 `zy.cn-zwny.com.conf`，不动现有站点文件；改前 `cp` 备份 | 删除该站点即恢复（原 `IP:18081` 入口始终可用） |
| SSL 申请失败（ACME 校验不通） | 阶段 1 已验证 80 可正确响应本域名；若被 WAF/安全组拦，改用 DNS 验证或手动上传证书 | 保持 HTTP 访问，不影响可用性 |
| SSH 加固自锁 | 双会话 + 宝塔面板兜底 + `sshd -t` 预检 + 改前备份 | 恢复 `sshd_config.bak` 并 reload |
| 换 `SERVER_HOST` 为域名后健康检查失败 | 建议保持 IP（见阶段 4） | secret 改回 IP |

## 八、执行顺序与工作量

| 阶段 | 内容 | 依赖 | 预计 |
|---|---|---|---|
| 1 | 宝塔建站 + 反代到 18081 | 域名解析（已完成） | 5 分钟 |
| 2 | Let's Encrypt + 强制 HTTPS | 阶段 1 | 5 分钟 |
| 3A | 用域名连接 SSH | 域名解析（已完成） | 0 分钟（已可用） |
| 3B | SSH 加固 | 阶段 3A + 兜底通道确认 | 10 分钟（含验证） |
| 4 | CI 适配（建议仅新增域名冒烟） | 阶段 2 | 10 分钟 |

**最小可用路径**：阶段 1 → 2（域名 + HTTPS 即可满足「加域名」诉求）；SSH 按需做 3A/3B。

## 九、验收标准

- [ ] `curl -I http://zy.cn-zwny.com` → 200
- [ ] `curl -I http://zy.cn-zwny.com` → 301 跳转 `https://`
- [ ] `curl -I https://zy.cn-zwny.com` → 200，证书有效、无浏览器「不安全」提示
- [ ] 浏览器 `https://zy.cn-zwny.com/project-cost-control` 页面与数据正常（本文档所修空状态插画应正常显示）
- [ ] `https://zy.cn-zwny.com/api/v1/captcha/image` → 200（API 经反代可达）
- [ ] 同机其他站点（`ch-zkzd.com`、`workfusion.*`）访问不受影响
- [ ] 若做 3B：新会话密钥登录可用，密码登录已被拒绝

## 十、执行记录（2026-09-18 实际执行）

| 阶段 | 结果 | 说明 |
|---|---|---|
| 1 域名反代 | ✅ **成功** | 新增 `/www/server/panel/vhost/nginx/zy.cn-zwny.com.conf`（反代 127.0.0.1:18081），`nginx -t` 通过后 reload。`http://zy.cn-zwny.com/`、`/api/v1/captcha/image`、`/project-cost-control` 均 200；原 IP:18081 与同机其他站点不受影响 |
| 2 HTTPS | ⚠️ **受阻（CA 速率限制）** | acme.sh webroot 申请，Let's Encrypt 与 ZeroSSL 均返回 `retryafter=86400`（CA 端 24h 限制，秒回 Retry-After，属下单拒绝而非 webroot 验证失败）。**未改动 nginx 443 配置**，站点保持 HTTP 可用 |
| 3A SSH 域名连接 | ✅ **成功** | `ssh root@zy.cn-zwny.com` 可直接登录（域名已解析） |
| 3B SSH 加固 | ⏸️ **未执行** | 有自锁风险（单会话执行无法保命），建议人工双会话操作或提供宝塔面板通道后再做 |
| 4 CI 适配 | ⏸️ **未执行** | 按规划建议 `SERVER_HOST` 保持 IP（部署通道与访问入口解耦） |

**注意（HTTPS 未配置时的现象）**：`https://zy.cn-zwny.com` 当前会被宝塔 default server 接住，返回**其他站点内容 + 证书不匹配警告**（既有行为，非本次引入）。证书配上后即恢复正常。

**HTTPS 重试方式**：24h 后重跑 `keys/_domain_step2b.sh`（ZeroSSL）或改用 `--server letsencrypt`；或在宝塔面板「站点 → SSL」手动申请（面板有独立申请通道）；或上传已有证书到 `/www/server/panel/vhost/nginx/ssl/zy.cn-zwny.com/{fullchain,privkey}.pem` 后启用 443 配置段。

**执行期间的一次时序冲突（已自愈）**：阶段 1 验证时恰逢 CI deploy（推送触发）处于 down→rebuild 中间态，frontend/backend 容器暂不存在导致 502；CI 完成后容器恢复，域名反代正常。非配置问题。
