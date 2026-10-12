# 多服务器线上环境部署指南（runner 构建 → rsync 产物 → 目标机 docker compose）

> 最后校正：2026-10-12（对照 deploy.yml / docker-compose.deploy.yml 实证重写 ghcr 章节）

本项目采用 **runner 构建、产物同步、目标机 docker compose 起容器** 的部署模式。

后端 jar 与前端 dist 由 GitHub Actions Runner 侧构建，经 rsync 同步到目标服务器；服务器复用既有 runtime 镜像、以只读卷挂载 jar/dist 运行容器。**不使用 ghcr.io，也不依赖任何云镜像仓库**，全链路无 `docker build` / `docker push` / `docker compose pull`（证据：`deploy.yml:256` 的 ghcr 废弃注释；`deploy.yml:424-430` 部署步骤 `unset BACKEND_IMAGE FRONTEND_IMAGE IMAGE_TAG` 后执行 `up -d --no-build`）。

---

## 一、核心优势与架构要点

1. **零制品仓库依赖**：
   - 不推送镜像到 ghcr.io，也无需注册腾讯云 TCR / 阿里云 ACR。compose 里的 `${BACKEND_IMAGE}` / `${FRONTEND_IMAGE}` / `${IMAGE_TAG}` 仅为可选覆盖，默认值为本地 `zwinsight-backend:debug` / `zwinsight-frontend:debug`（`docker-compose.deploy.yml:68,129`）。
   - 产物由 CI runner 直接构建并 rsync 到目标机，省去镜像仓库的注册、鉴权与拉取带宽成本。

2. **构建与服务器彻底解耦**：
   - 后端 jar 与前端 dist 均在 Runner 侧构建（后端 `deploy.yml:91-93`；前端独立 job `frontend-build`，`deploy.yml:212-248`）。
   - 目标服务器不构建镜像、不拉取源码，仅接收 `deploy/` 编排目录与 `zw-insight-web/dist` 产物（rsync：`deploy.yml:366-376`），随后执行 `docker compose -f docker-compose.deploy.yml up -d --no-build --force-recreate backend frontend`（`deploy.yml:430`）。
   - jar / dist 以**只读卷**注入容器：`./zw-insight-app.jar:/app/app.jar:ro`、`../zw-insight-web/dist:/usr/share/nginx/html:ro`（`docker-compose.deploy.yml:118-119,136-138`）。
   - **严禁在生产服务器上构建镜像**：2026-09-24 实测该服务器（4 核 / 7.6GB，可用内存仅约 1.3GB）上执行 `vite build` 与在跑的生产容器争抢资源，load average 冲到 146、sshd 无法完成 banner 交换、宿主机 nginx（80）与业务端口全部超时，整机不可服务约 40 分钟（`deploy.yml:199-208`）。故前端 Dockerfile 已改为**单阶段**、只 `COPY zw-insight-web/dist`（`deploy/frontend/Dockerfile:6-11`），产物由 CI 的 `frontend-build` job 在 runner 上构建并以 `frontend-dist` artifact 传递（`deploy.yml:242-248`）。

3. **环境与端口彻底解耦**：
   - 编排文件 [`deploy/docker-compose.deploy.yml`](deploy/docker-compose.deploy.yml) 中的端口、容器命名前缀、内存限制、凭证等全部参数化。
   - 同一台物理机上可利用不同端口与前缀并行运行多套环境（如 Staging 监听 18080/18081，Demo 监听 28080/28081）。
   - 脚本中不再硬编码明文数据库密码，统一通过环境变量驱动。
   - **健康检查的准确表述**：应用容器（backend / frontend）未定义健康检查；但 **mysql 服务保留 healthcheck**（`docker-compose.deploy.yml:21-26`），backend 通过 `depends_on: mysql: condition: service_healthy` 等待其就绪（`docker-compose.deploy.yml:71-73`）。

4. **标准化环境变量模板**：
   - 提供 [`deploy/.env.example`](deploy/.env.example)。新服务器部署时复制该文件为 `.env` 并按需调整即可完成环境初始化；若首次部署时目标机不存在 `.env`，流水线会自动从 `.env.example` 复制生成（`deploy.yml:395-399`）。

5. **双机矩阵 + 固定 production 环境**：
   - `deploy` job 使用 `server1` / `server2` 矩阵并行部署（`deploy.yml:262-269`）；`main` 分支普通 push 默认展开两台服务器。
   - **无「按目标的 GitHub Environment」**：全仓唯一的 `environment:` 声明固定为 `production`，且挂在 performance-test job 上（`deploy.yml:802`），deploy 部署 job 未绑定任何 GitHub Environment。
   - 并发互斥固定为 `deploy-production`（`deploy.yml:50-52`），语义是「同一时间只跑一条部署流水线」，并非按目标环境隔离。

---

## 二、接入一台新的线上服务器

当需要为新客户或测试团队部署一套全新的服务器环境时，仅需 3 步：

### 第一步：准备服务器基础环境
确保新服务器已安装 Docker 与 Docker Compose 插件，放行必要端口（如宿主机 Nginx 80/443 或业务直接暴露的端口）。

### 第二步：在 GitHub 配置仓库级 Secrets
本项目**不使用按目标的 GitHub Environment**，部署凭据走**仓库级 secrets**，由 `deploy` job 的矩阵按服务器 ID 选择（`deploy.yml:308-333`）：

- **server1（主环境 129.204.3.200）**：
  - `SERVER_HOST`：服务器公网 IP 或域名
  - `SERVER_USER`：登录用户名（缺省回退 `root`，`deploy.yml:321`）
  - `SSH_PRIVATE_KEY`：连接该服务器的私钥
  - `DEPLOY_DIR`：（可选，默认 `/root/zw-insight`，`deploy.yml:325`）
- **server2（徽颖 43.142.44.145）**：
  - `SERVER2_HOST` / `SERVER2_USER` / `SERVER2_SSH_KEY` / `SERVER2_DEPLOY_DIR`
  - 四项均有内置默认值（host 默认 `43.142.44.145`、user 默认 `root`、dir 默认 `/root/zw-insight`，`deploy.yml:327-332`），**`SERVER2_*` 优先于默认值**。

> 如需扩展第三台服务器，须修改 `deploy.yml` 的部署矩阵与凭据选择逻辑（当前矩阵仅硬编码 `server1` / `server2`），并非配置一个新 Environment 即可。

### 第三步：初始化服务器 `.env` 文件（或让流水线自动生成）
登录新服务器，在部署目录创建 `deploy/.env`（也可由流水线在首次部署时从 `.env.example` 自动复制生成，`deploy.yml:395-399`）：

```bash
mkdir -p /root/zw-insight/deploy
cd /root/zw-insight/deploy

# 从仓库模板创建配置并修改强密码与端口
curl -fsSL https://raw.githubusercontent.com/your-repo/main/deploy/.env.example -o .env
vim .env
```

核心需确认配置项：
- `SPRING_PROFILES_ACTIVE=prod`（生产环境建议改为 prod）
- `MYSQL_ROOT_PASSWORD` 与 `SPRING_DATASOURCE_PASSWORD`（生产强密码）
- `BACKEND_PORT` 与 `FRONTEND_PORT`（若默认 18080/18081 已被占用，可改为其他端口）
- `SMS_PROVIDER` 及对应 Provider 的凭证（`deploy/.env.example:54-70`；凭证仅存各服务器 `.env`，不入版本库）

---

## 三、触发部署与版本回滚

### 1. 日常代码提交部署
- 代码合并至 `main` 分支后，`deploy` job 默认展开 **server1 + server2 双机矩阵并行部署**（`deploy.yml:262-269`）；server2 使用 `SERVER2_*` 凭据（优先于内置默认值）。
- push 事件默认执行 `mvn -B -T 1C clean verify -Djacoco.skip=true`：**单测照跑、跳过 JaCoCo 覆盖率门禁**（2026-10-07 用户决策，`deploy.yml:88-93`）。
- 纯文档提交（`**.md` / `docs/**` / `.kiro/**` / `audit-reports/**`）会跳过整条流水线（`deploy.yml:8-12`）。

### 2. 手动部署到指定服务器
在 GitHub Actions 界面找到 **CI/CD Deploy to BaoTa Server**，点击 **Run workflow**，有三个输入项（`deploy.yml:14-35`）：

- **target_server**（choice，默认 `all`）：`all`（全部部署）/ `server1`（主环境 129.204.3.200）/ `server2`（徽颖 43.142.44.145）。
- **run_tests**（choice，默认 `false`）：是否跑**全量测试套件**（L2 Testcontainers、L3/L4/L5 集成测试与 k6）。设为 `true` 时后端走 `mvn -B -T 1C clean verify`（JaCoCo 门禁真实生效），并触发 `deploy.yml:129-136`（L2）、`deploy.yml:492-503`（L3/L4/L5）、`deploy.yml:791-802`（k6）。
- **fast_deploy**（boolean，默认 `false`）：**快速部署（2026-09-16）**——跳过后端单测与 JaCoCo、以及三端前端测试，仅编译打包 + 部署 + 冒烟，约 3-5 分钟。生效时后端命令降级为 `mvn -B -DskipTests clean package`（`deploy.yml:91-93`），前端测试 job 被跳过（`deploy.yml:149`）。仅限本地已完成测试验证的日常推送。

### 3. 版本回滚
当前流水线**没有 `image_tag` 输入，也没有制品仓库回滚路径**（既不从 ghcr.io 拉取历史镜像，服务器上也没有历史镜像 tag——应用镜像恒为本地 `debug`）。正确的回滚方式是回到源码层面重跑部署：

1. 在 Git 中将 `main` 回退到历史稳定 commit（`git revert` 生成反向提交，或基于历史 commit 分支修复后合并）。
2. 将该 commit 推送到 `main`（或针对该 commit 手动 `workflow_dispatch` 运行部署），流水线会**重新构建并 rsync 该版本的 jar + dist** 覆盖目标机，再以 `up -d --no-build --force-recreate backend frontend`（`deploy.yml:430`）切换生效。

> 注意：构建产物不保留历史——`backend-jar` 与 `frontend-dist` artifact 的 `retention-days` 均为 3（`deploy.yml:120,247`），服务器上也没有历史镜像 tag。因此回滚必须回到源码重新走一遍流水线，无法「秒级拉取历史制品」。
