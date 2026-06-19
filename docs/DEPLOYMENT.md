# Sonora Music 开发与部署指南

> 最后更新：2026-06-19

本文覆盖本地开发、环境变量、生产构建和常见故障。接口定义见 [API.md](API.md)。

## 1. 环境要求

| 依赖 | 推荐版本 | 用途 |
|------|----------|------|
| JDK | 17 | Spring Boot 后端 |
| Maven | 3.9+ | 后端多模块构建 |
| Node.js | 22+ | 两个 Vue 前端 |
| pnpm | 10+ | 前端依赖管理 |
| Docker Compose | Docker Desktop 自带版本 | MySQL、Redis、MinIO |

Windows 下如果安装了多个 JDK，请在执行 Maven 前确认：

```powershell
java -version
mvn -version
```

两条命令都应显示 Java 17。

## 2. 本地基础设施

在仓库根目录执行：

```bash
cd sonora-server
docker compose up -d
docker compose ps
```

| 容器 | 主机端口 | 容器端口 | 默认凭据 |
|------|----------|----------|----------|
| `sonora-mysql` | `13306` | `3306` | `root` / `root123` |
| `sonora-redis` | `6379` | `6379` | 无密码 |
| `sonora-minio` | `9000`、`9001` | `9000`、`9001` | `admin` / `admin123456` |

数据库名为 `sonora_music`。首次创建 MySQL 数据卷时，Compose 会自动执行 `sonora-server/docs/sonora_music.sql`。修改 SQL 后，已有数据卷不会自动重新初始化；应使用数据库迁移或手动执行增量 SQL，不要直接删除有用数据卷。

常用命令：

```bash
docker compose logs -f mysql
docker compose restart mysql redis minio
docker compose down
```

## 3. 后端配置

启动模块为 `sonora-server/music-admin`，默认激活 `dev` 配置。开发环境可通过以下环境变量覆盖数据库连接：

| 环境变量 | 默认值 |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `dev` |
| `SERVER_PORT` | `8080` |
| `MYSQL_HOST` | `localhost` |
| `MYSQL_PORT` | `13306` |
| `MYSQL_USERNAME` | `root` |
| `MYSQL_PASSWORD` | `root123` |

Redis 和 MinIO 的开发默认值位于 `music-admin/src/main/resources/application-dev.yml`。生产环境应通过独立配置文件或密钥管理系统注入数据库密码、JWT 密钥和 MinIO 凭据，不要沿用仓库中的开发密码。

构建并启动：

```bash
cd sonora-server
mvn clean install -DskipTests
cd music-admin
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/client/ping
```

在线接口文档：

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

## 4. Web 客户端

客户端读取 `sonora-client/.env`：

```ini
VITE_PUBLIC_PATH = /
VITE_ROUTER_MODE = history
VITE_APP_BASE_API = 'http://localhost:8080'
```

启动：

```bash
cd sonora-client
pnpm install
pnpm dev
```

开发地址为 `http://localhost:5089`。音频播放地址会直接指向 `VITE_APP_BASE_API`，以避免长时间媒体流被 Vite 开发代理中断。

生产构建：

```bash
pnpm build
```

产物目录为 `sonora-client/dist`。

## 5. 管理端

管理端开发服务器在 `sonora-admin/vite.config.ts` 中将 `/login`、`/refresh-token`、`/get-async-routes` 和 `/api` 代理到 `http://localhost:8080`。

```bash
cd sonora-admin
pnpm install
pnpm dev
```

开发地址为 `http://localhost:8848`。默认管理员账号为 `sonora-music`，密码为 `admin123`。

生产构建：

```bash
pnpm typecheck
pnpm build
```

产物目录为 `sonora-admin/dist`。生产环境没有 Vite 开发代理，必须由反向代理转发后端路径，或为 Axios 配置生产 API 基础地址。

## 6. 生产部署建议

推荐使用同域名反向代理，让页面、普通 API、图片和音频流经过统一 HTTPS 入口：

```text
https://music.example.com/          -> sonora-client/dist
https://music.example.com/admin/    -> sonora-admin/dist
https://music.example.com/api/      -> Spring Boot :8080
https://music.example.com/login     -> Spring Boot :8080
https://music.example.com/refresh-token -> Spring Boot :8080
```

部署时重点检查：

1. Nginx 或网关必须转发 `Range`、`If-Range` 请求头，并保留 `Accept-Ranges`、`Content-Range`、`Content-Length` 响应头。
2. 音频流接口应关闭代理缓冲，避免整段缓存后才返回。
3. 所有公开访问使用 HTTPS，避免 JWT 和用户信息在明文链路上传输。
4. 后端、MySQL、Redis 和 MinIO 不应全部暴露到公网；只开放网关入口和必要的运维通道。
5. MinIO 桶保持私有，由后端提供预览和流式读取，不在前端暴露永久访问密钥。
6. 为后端设置合理的 JVM 内存、数据库连接池和请求超时，并接入日志采集与健康监控。

Nginx 音频流位置示例：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header Range $http_range;
    proxy_set_header If-Range $http_if_range;
    proxy_buffering off;
    proxy_request_buffering off;
}
```

## 7. 常见问题

### MySQL 容器提示端口不可用

项目主机端口已经改为 `13306`。确认没有旧容器或进程占用：

```powershell
Get-NetTCPConnection -LocalPort 13306 -ErrorAction SilentlyContinue
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

后端连接端口也必须是 `13306`，而不是容器内部的 `3306`。

### 前端出现 Network Error 或 `ERR_CONNECTION_REFUSED`

依次检查：

```powershell
Test-NetConnection localhost -Port 8080
Invoke-RestMethod http://localhost:8080/api/client/ping
```

如果后端已经退出，先查看后端控制台；如果只有图片或音频失败，检查 MinIO 容器和 `9000` 端口。

### 后台播放一段时间后卡住

浏览器媒体请求应直接访问后端或生产网关，不要让长连接经过 Vite 开发代理。网络面板中音频请求应返回 `206 Partial Content`，并包含有效的 `Content-Range`。若返回 `500`，检查后端与 MinIO 的连接以及服务端日志。

### 登录返回 401 或 403

- `401`：未登录、令牌过期或令牌格式错误，确认请求头为 `Authorization: Bearer <token>`。
- `403`：当前账号已登录但没有接口所需角色；`/api/admin/**` 仅允许 `ADMIN`。
- 客户端私有接口必须使用客户端登录接口返回的访问令牌。

### 数据库修改后功能仍不生效

Compose 初始化脚本只在空数据卷上执行一次。请检查当前表结构并执行对应增量 SQL，然后重启后端；不要把“重启容器”等同于“重建数据库”。
