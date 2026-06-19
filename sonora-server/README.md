# Sonora Music Server

Sonora Music 的 Java 后端，负责认证授权、音乐内容、用户歌单、对象存储和音频流式播放。项目使用 Java 17、Spring Boot 3.3.5 和 Maven 多模块组织。

## 模块结构

| 模块 | 职责 |
|------|------|
| `music-common` | 统一响应、异常、常量和通用工具 |
| `music-model` | 实体、请求对象和响应对象 |
| `music-mapper` | MyBatis-Plus Mapper 与数据库访问 |
| `music-service` | 业务逻辑、事务和领域服务 |
| `music-security` | Spring Security、JWT 过滤器和权限配置 |
| `music-file` | MinIO 上传、预览、分段读取和删除 |
| `music-client` | 用户端 REST API 与网易云风格兼容层 |
| `music-admin` | 管理端 API、配置和应用启动入口 |

启动类：`music-admin/src/main/java/com/sonora/admin/SonoraAdminApplication.java`。

## 主要能力

- 管理员登录、访问令牌刷新、动态权限路由
- 用户、歌手、专辑、歌曲、歌单和轮播图管理
- 客户端注册登录、资料与头像、密码和账号维护
- 喜欢歌曲、默认喜欢歌单、自建歌单和收藏歌单
- 音频、图片和歌词文件管理
- HTTP Range 音频流，MinIO 按偏移读取
- 统一响应、参数校验和全局异常处理
- Swagger UI / OpenAPI 3 接口定义

Redis 当前用于保存刷新令牌；项目没有把“热点数据缓存”作为已完成能力。评论、反馈和邮件服务也不属于当前业务接口。

## 本地启动

```bash
# MySQL 8 / Redis 7 / MinIO
docker compose up -d

# 编译全部模块
mvn clean install -DskipTests

# 启动应用
cd music-admin
mvn spring-boot:run
```

默认后端地址为 `http://localhost:8080`，健康检查为 `GET /api/client/ping`。

| 服务 | 默认地址或端口 |
|------|----------------|
| MySQL | `localhost:13306`，数据库 `sonora_music` |
| Redis | `localhost:6379` |
| MinIO API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |

开发账号和密码仅用于本地运行：MySQL `root/root123`，MinIO `admin/admin123456`，管理端 `sonora-music/admin123`。

## 文档

- [项目总览](../README.md)
- [完整接口文档](../docs/API.md)
- [开发与部署指南](../docs/DEPLOYMENT.md)
- [后端架构](../docs/后端架构文档.md)
- [数据库初始化脚本](docs/sonora_music.sql)
- 在线文档：`http://localhost:8080/swagger-ui/index.html`

## 构建检查

```bash
mvn clean verify
```

生产环境必须覆盖开发密码、JWT 密钥和对象存储凭据，并通过 HTTPS 网关对外提供服务。
