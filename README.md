# Sonora Music

全栈音乐流媒体平台，包含管理端、服务端、Web 客户端。

## 项目结构

```
sonora-music/
├── sonora-server/       # 服务端 — Spring Boot 3 + MyBatis-Plus + MySQL + Redis + MinIO
├── sonora-admin/        # 管理端 — Vue 3 + Element Plus（已对接真实后端）
├── sonora-client/       # Web 客户端 — Vue 3 + Vant UI（通过网易云兼容层对接）
└── docs/                # 项目文档
```

## 技术栈

| 模块 | 技术 |
|------|------|
| **服务端** | Java 17, Spring Boot 3.3, MyBatis-Plus, MySQL 8, Redis 7, MinIO, Spring Security, JWT |
| **管理端** | Vue 3, Element Plus, Pinia, Vite, TypeScript, Tailwind CSS |
| **Web 客户端** | Vue 3, Vant UI, Pinia, Vite, TypeScript, Tailwind CSS, Web Audio API |

## 快速开始

```bash
# 1. 启动基础服务
cd sonora-server
docker compose up -d

# 2. 启动后端 (端口 8080)
mvn clean install -DskipTests
cd music-admin
mvn spring-boot:run

# 3. 启动管理端 (端口 8848)
cd sonora-admin
pnpm install && pnpm dev

# 4. 启动客户端 (端口 5089)
cd sonora-client
pnpm install && pnpm dev
```

## 管理端登录

- URL: `http://localhost:8848`
- 账号: `admin` / `admin123`

## 当前进度

- 后端: 43 个 API 端点 (含网易云兼容层)
- 数据库: 12 张表
- 管理端: 歌曲/专辑/歌手管理页
- 客户端: 搜索 + 流媒体播放 (零代码改动对接)

## 参考框架

| 角色 | 原始项目 | 上游仓库 | 基于版本 |
|------|---------|---------|---------|
| Web 客户端 | GlassMusicPlayer | [XiangZi7/GlassMusicPlayer](https://github.com/XiangZi7/GlassMusicPlayer) | `f19ae8e` |
| 管理端 | pure-admin-thin | [pure-admin/pure-admin-thin](https://github.com/pure-admin/pure-admin-thin) | `f0ff132` |
