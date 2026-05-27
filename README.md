# Sonora Music

Sonora Music 是一个全栈音乐流媒体平台，当前包含管理端、Web 客户端和 Spring Boot 后端。项目基于两个成熟前端模板做二次整合：管理端来自 pure-admin-thin，客户端来自 GlassMusicPlayer，后端负责统一数据、权限、文件存储和网易云兼容接口。

## 项目结构

```text
sonora-music/
├── sonora-server/       # 后端服务：Spring Boot 3 + MyBatis-Plus + MySQL + Redis + MinIO
├── sonora-admin/        # 管理端：Vue 3 + Element Plus + Pinia + Vite
├── sonora-client/       # Web 客户端：Vue 3 + Pinia + Vite + Web Audio API
├── docs/                # 项目文档、开发计划、阶段总结
└── test-music/          # 本地测试音乐资源，不纳入版本控制
```

## 技术栈

| 模块 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.3.5, MyBatis-Plus, MySQL 8, Redis 7, MinIO, Spring Security, JWT |
| 管理端 | Vue 3, Element Plus, Pinia, Vite, TypeScript, Tailwind CSS |
| Web 客户端 | Vue 3, Pinia, Vite, TypeScript, Tailwind CSS, Web Audio API |

## 快速启动

```bash
# 1. 启动基础服务：MySQL / Redis / MinIO
cd sonora-server
docker compose up -d

# 2. 编译并启动后端，端口 8080
# 如果本机有多个 JDK，请确认 JAVA_HOME 指向 Java 17
mvn clean install -DskipTests
cd music-admin
mvn spring-boot:run

# 3. 启动管理端，端口 8848
cd ../../sonora-admin
pnpm install
pnpm dev

# 4. 启动 Web 客户端，端口 5089
cd ../sonora-client
pnpm install
pnpm dev
```

## 默认入口

| 入口 | 地址 | 说明 |
|------|------|------|
| 管理端 | `http://localhost:8848` | 默认账号 `admin` / `admin123` |
| Web 客户端 | `http://localhost:5089` | Sonora 注册登录 + 网易云兼容层播放体验 |
| 后端 API | `http://localhost:8080` | Swagger: `/swagger-ui.html` |
| MinIO 控制台 | `http://localhost:9001` | 默认账号 `admin` / `admin123456` |

## 当前进度

- 后端：8 个 Maven 子模块，认证、动态路由、歌曲/专辑/歌手/歌单/轮播图/客户端用户管理、文件上传、流媒体播放和网易云兼容层已完成。
- 数据库：SQL 初始化脚本已包含客户端用户 `profileId`、用户收藏、默认喜欢歌单所需字段。
- 管理端：已接真实后端，完成登录、动态菜单、歌曲/专辑/歌手/歌单/轮播图/客户端用户管理页。
- Web 客户端：完成 Sonora 注册登录、个人资料、喜欢歌曲、默认“我喜欢的音乐”歌单和流媒体播放接入。
- 文档：`docs/` 和 `sonora-server/docs/sonora_music.sql` 已纳入项目可复现范围。

## 重要文档

| 文档 | 内容 |
|------|------|
| `docs/开发计划.md` | 阶段状态、优先级和下一步任务 |
| `docs/后端架构文档.md` | 后端模块、接口、数据库和运行环境 |
| `docs/已完成工作总结.md` | 已完成工作、联调结果和踩坑记录 |
| `docs/音乐平台-项目文档.md` | 项目定位、技术路线和学习/演进建议 |

## 下一步优先级

1. 自建歌单：客户端支持新建、编辑、置顶、删除普通歌单，默认喜欢歌单不可删除。
2. 测试音乐数据补齐：导入更多歌曲、封面和歌单关联，让客户端首页可完整演示。
3. 运营内容：首页内容配置、歌词独立编辑。
4. 稳定性：补充后端单元测试、前端类型检查、启动脚本和环境示例。

## 上游来源

| 模块 | 上游项目 | 说明 |
|------|----------|------|
| Web 客户端 | `XiangZi7/GlassMusicPlayer` | 保留视觉和播放器能力，通过后端兼容层对接 |
| 管理端 | `pure-admin/pure-admin-thin` | 保留后台框架和权限路由能力，业务页已改为 Sonora 内容管理 |
