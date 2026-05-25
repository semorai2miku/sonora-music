# Sonora Music

全栈音乐流媒体平台，包含管理端、服务端、Web 客户端、Android 客户端（规划中）。

## 项目结构

```
sonora-music/
├── sonora-client/       # Web 客户端 — Vue 3 + Vant UI + Pinia
├── sonora-admin/        # 管理端 — Vue 3 + Element Plus + Pinia
├── sonora-server/       # 服务端 — Spring Boot 3 + MyBatis-Plus + MySQL + Redis + MinIO
└── docs/                # 项目文档
```

## 技术栈

| 模块 | 技术 |
|------|------|
| **服务端** | Java 17, Spring Boot 3.3, MyBatis-Plus, MySQL 8, Redis, MinIO, Spring Security, JWT |
| **管理端** | Vue 3, Element Plus, Pinia, Vite, TypeScript, Tailwind CSS |
| **Web 客户端** | Vue 3, Vant UI, Pinia, Vite, TypeScript, Tailwind CSS, Web Audio API |

## 快速开始

### 1. 启动基础服务

```bash
cd sonora-server
docker compose up -d
```

### 2. 启动后端

```bash
cd sonora-server
mvn clean install -DskipTests
cd music-admin
mvn spring-boot:run
```

### 3. 启动前端

```bash
# 管理端
cd sonora-admin
pnpm install && pnpm dev

# Web 客户端
cd sonora-client
pnpm install && pnpm dev
```

## 参考框架

本项目基于以下开源项目构建：

| 角色 | 原始项目 | 上游仓库 | 基于版本 |
|------|---------|---------|---------|
| Web 客户端 | GlassMusicPlayer | [XiangZi7/GlassMusicPlayer](https://github.com/XiangZi7/GlassMusicPlayer) | `f19ae8e` |
| 管理端 | pure-admin-thin | [pure-admin/pure-admin-thin](https://github.com/pure-admin/pure-admin-thin) | `f0ff132` |
