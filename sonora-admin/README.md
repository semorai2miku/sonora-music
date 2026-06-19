# Sonora Music Admin

Sonora Music 内容管理端，基于 Vue 3、TypeScript、Element Plus、Pinia 和 pure-admin-thin 开发。

## 当前功能

- 管理员登录、访问令牌刷新和动态权限路由
- 客户端用户管理与账号状态控制
- 歌手、专辑、歌曲、歌单和轮播图管理
- 音频、封面、头像和歌词上传
- 列表分页、筛选、批量删除和内容状态切换

## 环境要求

- Node.js `^20.19.0` 或 `>=22.13.0`
- pnpm 9+
- Sonora 后端运行在 `http://localhost:8080`

## 本地运行

```bash
pnpm install
pnpm dev
```

开发地址：`http://localhost:8848`。Vite 会将 `/login`、`/refresh-token`、`/get-async-routes` 和 `/api` 转发到本地后端。

默认开发管理员：`sonora-music` / `admin123`。

## 构建检查

```bash
pnpm typecheck
pnpm build
pnpm preview
```

生产产物位于 `dist`。生产环境需要由 Nginx 或其他网关接管开发阶段的 API 代理规则。

## 相关文档

- [项目总览](../README.md)
- [接口文档](../docs/API.md)
- [开发与部署指南](../docs/DEPLOYMENT.md)

## 上游与许可

管理端基于 `pure-admin/pure-admin-thin` 开发，上游采用 MIT License，具体许可见本目录 `LICENSE`。上游框架文档可访问 [pure-admin.cn](https://pure-admin.cn/)。
