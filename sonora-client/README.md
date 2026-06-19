# Sonora Music Web Client

Sonora Music 用户侧 Web 客户端，基于 Vue 3、TypeScript、Pinia 和 Vite 开发。界面源自 GlassMusicPlayer，并已接入 Sonora 自有账号、内容、歌单和流媒体接口。

## 当前功能

- 首页轮播和歌曲、歌单、歌手、专辑推荐
- 曲库及歌曲、歌手、专辑、歌单详情页
- 歌曲、歌手、专辑和歌单联合搜索
- 全局播放队列、上一首/下一首、顺序/随机/单曲循环
- LRC 歌词同步、滚轮预览和跳转
- 用户注册登录、资料头像、密码和账号管理
- 喜欢歌曲、默认“我喜欢的音乐”歌单
- 自建歌单、收藏歌单、封面上传和歌曲管理
- 深浅色主题、自定义主题色和中/英/日语言切换

评论、MV 搜索结果和音频可视化已从当前产品中移除，不应再按上游模板文档理解。

## 环境要求

- Node.js 22+
- pnpm 10+
- Sonora 后端运行在 `http://localhost:8080`

## 配置

开发配置位于 `.env`：

```ini
VITE_PUBLIC_PATH = /
VITE_ROUTER_MODE = history
VITE_APP_BASE_API = 'http://localhost:8080'
```

播放器的媒体 URL 会直接使用 `VITE_APP_BASE_API`。生产环境请将其改为 HTTPS API 或同域网关地址。

## 运行与构建

```bash
pnpm install
pnpm dev
```

开发地址：`http://localhost:5089`。

```bash
pnpm build
pnpm preview
```

生产产物位于 `dist`。

## 相关文档

- [项目总览](../README.md)
- [接口文档](../docs/API.md)
- [开发与部署指南](../docs/DEPLOYMENT.md)

## 上游与许可

本客户端基于 `XiangZi7/GlassMusicPlayer` 改造，继续遵守模块内 `LICENSE` 与上游许可。项目仅用于学习、研究和非商业展示。
