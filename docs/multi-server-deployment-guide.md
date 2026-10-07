# 多服务器线上环境部署与 GitHub Packages (ghcr.io) 接入指南

本项目已完成向 **方案 A（多套独立环境部署：一次构建、到处运行）** 的架构升级。

现在，项目通过 **GitHub Packages (`ghcr.io`)** 进行开箱即用的集中制品管理，**无需配置任何国内云厂商第三方账号/Secret**，即可一键将同一制品发布到任意多台物理机/云服务器。

---

## 一、核心优势与架构要点

1. **零外部配置负担**：
   - 镜像直接托管在当前 GitHub 仓库附带的 GitHub Container Registry (`ghcr.io`)。
   - CI 流水线利用内置的 `GITHUB_TOKEN` 自动完成权限校验并推送镜像，省去额外注册、绑定腾讯云/阿里云的麻烦。

2. **构建与服务器彻底解耦（秒级发布）**：
   - 前后端镜像均在 GitHub Actions Runner 侧完成打包并推送（打上 `git sha` 前 12 位与 `latest` 标签）。
   - 目标服务器不再需要同步几百兆的源码或 Jar 包，仅需同步 `deploy/` 下的 Compose 编排与 SQL 脚本，直接执行 `docker compose pull && up -d --no-build`，几秒内即可拉起服务。

3. **环境与端口彻底解耦**：
   - 编排文件 [`deploy/docker-compose.deploy.yml`](deploy/docker-compose.deploy.yml) 中的端口、容器命名前缀、内存限制、凭证等全部参数化。
   - 同一台物理机上可以利用不同端口与前缀并行运行多套环境（如 Staging 监听 18080/18081，Demo 监听 28080/28081）。
   - 移除了容器健康检查及脚本中硬编码的明文数据库密码，统一通过环境变量驱动。

4. **标准化环境变量模板**：
   - 新增 [`deploy/.env.example`](deploy/.env.example)。新服务器部署时只需复制该文件为 `.env` 并按需调整，即可完成环境初始化。

5. **GitHub Environments 多环境隔离**：
   - CI 流水线并发控制改为 `deploy-${{ inputs.target_env }}`，部署 Staging 不会取消 Production 的流水线。
   - 手动触发流水线时，可自由选择目标部署环境和指定回滚版本 Tag。

---

## 二、接入一台新的线上服务器

当需要为新客户或测试团队部署一套全新的服务器环境时，仅需 3 步：

### 第一步：准备服务器基础环境
确保新服务器已安装 Docker 与 Docker Compose 插件，放行必要端口（如宿主机 Nginx 80/443 或业务直接暴露的端口）。

> 💡 **国内服务器拉取 ghcr.io 提示**：若服务器拉取 `ghcr.io` 较慢，可在 Docker daemon 配置国内 Docker 镜像加速代理，或配置海外代理通道。

### 第二步：在 GitHub 配置环境（GitHub Environment）
1. 打开 GitHub 仓库：**Settings -> Environments -> New environment**。
2. 创建一个新环境名（例如 `staging` 或 `customer-bj`）。
3. （可选）为生产环境开启 **Required reviewers**（人工审批门禁）。
4. 在该环境下的 **Environment secrets** 中配置目标服务器凭据：
   - `SERVER_HOST`：新服务器的公网 IP 或域名
   - `SERVER_USER`：登录用户名（通常为 `root`）
   - `SSH_PRIVATE_KEY`：用于连接该服务器的私钥
   - `DEPLOY_DIR`：（可选，默认 `/root/zw-insight`）

### 第三步：初始化服务器 `.env` 文件（或让流水线自动生成）
登录新服务器，在部署目录创建 `deploy/.env`（也可由流水线在首次部署时从 `.env.example` 自动复制生成）：

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

---

## 三、触发多环境部署与版本回滚

### 1. 日常代码提交部署
- 代码合并至 `main` 分支后，默认自动构建 `ghcr.io` 镜像并部署至默认的 `production` 环境。

### 2. 手动部署到指定环境
在 GitHub Actions 界面找到 **CI/CD Deploy to BaoTa Server**，点击 **Run workflow**：
- **Target Environment**：输入目标环境名（如 `staging`、`customer-bj`）。
- **Fast Deploy**：勾选时可跳过单元测试快速打包交付（日常仅编译约 3~5 分钟）。
- **Run Tests**：是否在部署后触发全量业务测试套件。

### 3. 一键版本回滚
如遇线上故障需快速回退版本：
1. 打开 **Run workflow**。
2. 在 **image_tag** 处填入想要回滚的历史 Commit SHA 前 12 位（或在 GitHub 仓库主页右侧 Packages 中查看到的历史 Tag）。
3. 点击运行后，流水线将直接从 `ghcr.io` 拉取历史稳定制品并在目标服务器秒级部署生效，无需重新拉取代码编译。

