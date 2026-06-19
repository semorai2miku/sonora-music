# Sonora Music API 文档

> 基础地址：`http://localhost:8080`  
> 最后核对：2026-06-19  
> 在线文档：`/swagger-ui/index.html`，OpenAPI JSON：`/v3/api-docs`

本文是便于开发和评审的静态接口索引。请求字段与响应模型以当前代码和运行时 OpenAPI 为准。

## 1. 通用约定

### 1.1 数据格式

除文件上传和音频流外，请求与响应均使用 `application/json`，字符集为 UTF-8。普通接口统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | integer | 业务/HTTP 状态码，成功通常为 `200` |
| `message` | string | 结果说明或错误原因 |
| `data` | any | 实际数据，空结果可能为 `null` |

兼容层接口为适配客户端既有结构，直接返回网易云风格对象，不使用上述 `R<T>` 包装。

### 1.2 鉴权

登录成功后，将访问令牌放入请求头：

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

| 接口范围 | 权限 |
|----------|------|
| `/api/client/**` 公共内容接口 | 无需登录 |
| `/api/client/auth/me`、头像、密码 | 客户端用户登录 |
| `/api/client/me/**` | 客户端用户登录 |
| `/api/admin/**` | `ADMIN` 角色 |
| `/get-async-routes` | 已登录管理员 |

常见错误：

| HTTP 状态 | 含义 |
|-----------|------|
| `400` | 参数缺失、格式错误或业务校验失败 |
| `401` | 未登录、令牌缺失或过期 |
| `403` | 已登录但角色无权访问 |
| `404` | 资源不存在 |
| `409` | 重复数据或状态冲突 |
| `500` | 服务端异常，应结合后端日志定位 |

### 1.3 分页与状态

管理端分页接口通常接受：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `pageNum` | `1` | 页码，从 1 开始 |
| `pageSize` | `20` | 每页数量 |
| `keyword` | 空 | 名称等模糊搜索 |

列表响应通常在 `data` 中返回 `records`、`total`、`current` 和 `size`。业务状态一般使用 `1` 表示启用/公开/上架，`0` 表示禁用/私有/下架。

## 2. 认证与路由

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/login` | 公开 | 管理端登录 |
| POST | `/refresh-token` | 公开 | 使用刷新令牌获取新访问令牌 |
| GET | `/get-async-routes` | 登录 | 获取管理端动态路由 |
| POST | `/api/client/auth/register` | 公开 | 注册客户端用户，同时创建默认喜欢歌单 |
| POST | `/api/client/auth/login` | 公开 | 客户端用户登录 |

管理端登录：

```bash
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"sonora-music","password":"admin123"}'
```

刷新令牌：

```bash
curl -X POST http://localhost:8080/refresh-token \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh-token>"}'
```

客户端注册请求体：

```json
{
  "username": "demo-user",
  "email": "demo@example.com",
  "password": "password123"
}
```

客户端登录时使用相同结构，`username` 可按客户端表单传入，`email` 可为空。

## 3. 客户端公开接口

### 3.1 健康、轮播与搜索

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/api/client/ping` | 无 | 健康检查 |
| GET | `/api/client/banners` | 无 | 已启用轮播图 |
| GET | `/api/client/search` | `keyword`、`type=all`、`pageNum=1`、`pageSize=20` | 联合搜索 |
| GET | `/api/client/search/hot` | 无 | 播放量较高的 10 个歌曲名 |

`type` 可选 `all`、`song`、`artist`、`album`、`playlist`。`all` 会同时返回 `songs`、`artists`、`albums` 和 `playlists`，不返回 MV。当前搜索按每种类型使用 `pageSize` 限制结果，`pageNum` 为兼容保留参数。

```bash
curl "http://localhost:8080/api/client/search?keyword=魔法使之夜&type=all&pageSize=20"
```

### 3.2 歌曲与音频

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/api/client/songs` | `limit=100`、`sort=id_desc` | 曲库歌曲列表 |
| GET | `/api/client/songs/{id}` | 路径参数 `id` | 歌曲详情 |
| GET | `/api/client/songs/{id}/lyric` | 路径参数 `id` | LRC 歌词 |
| GET | `/api/client/songs/{id}/stream` | `Range` 请求头可选 | 音频流 |
| GET | `/api/client/songs/hot` | `limit=20` | 热门歌曲 |
| GET | `/api/client/songs/new` | `limit=20` | 最新歌曲 |

`sort` 支持当前服务实现定义的 ID 排序值，客户端曲库使用 `id_desc`。歌曲封面优先跟随所属专辑封面；没有专辑时返回默认封面。

音频 Range 请求示例：

```bash
curl -i "http://localhost:8080/api/client/songs/1/stream" \
  -H "Range: bytes=0-1048575" \
  -o song.part
```

服务端在有效 Range 下返回：

```http
HTTP/1.1 206 Partial Content
Accept-Ranges: bytes
Content-Range: bytes 0-1048575/7340032
Content-Length: 1048576
Content-Type: audio/mpeg
```

未发送 Range 时返回完整内容。后端不会先把整首音频读入 JVM 内存，而是把字节范围换算为 MinIO 的 `offset + length` 读取。

### 3.3 歌手、专辑和歌单

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/api/client/artists` | `region`、`limit` | 歌手列表 |
| GET | `/api/client/artists/{id}` | 路径参数 `id` | 歌手及全部歌曲/专辑 |
| GET | `/api/client/albums` | `region`、`limit` | 专辑列表 |
| GET | `/api/client/playlists` | `pageNum=1`、`pageSize=24`、`keyword` | 公开歌单分页 |
| GET | `/api/client/playlists/{id}` | 路径参数 `id` | 歌单详情与歌曲列表 |
| GET | `/api/client/playlists/recommend` | `limit=20` | 随机推荐部分歌单 |
| GET | `/api/client/playlists/top` | `limit=10` | 热门歌单 |

公开歌单列表有分页上限，客户端不应一次请求并渲染全部歌单。

## 4. 客户端用户接口

以下接口均需要客户端 Bearer Token。

### 4.1 账号资料

| 方法 | 路径 | Content-Type | 说明 |
|------|------|--------------|------|
| GET | `/api/client/auth/me` | JSON | 当前用户资料 |
| PUT | `/api/client/auth/me` | JSON | 修改资料 |
| POST | `/api/client/auth/avatar` | multipart | 上传头像，字段名 `file` |
| PUT | `/api/client/auth/password` | JSON | 修改密码 |
| DELETE | `/api/client/auth/me` | JSON | 注销当前账号 |

资料请求体字段：`username`、`profileId`、`email`、`phone`、`avatar`、`bio`。密码请求体：

```json
{
  "oldPassword": "old-password",
  "newPassword": "new-password"
}
```

头像上传：

```bash
curl -X POST http://localhost:8080/api/client/auth/avatar \
  -H "Authorization: Bearer <token>" \
  -F "file=@avatar.png"
```

### 4.2 我的歌单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/client/me/playlists` | 创建和收藏的歌单列表 |
| POST | `/api/client/me/playlists` | 新建歌单 |
| GET | `/api/client/me/playlists/{playlistId}` | 我的歌单详情 |
| PUT | `/api/client/me/playlists/{playlistId}` | 编辑歌单 |
| DELETE | `/api/client/me/playlists/{playlistId}` | 删除自建普通歌单 |
| POST | `/api/client/me/playlists/{playlistId}/cover` | 上传歌单封面，字段名 `file` |
| PUT | `/api/client/me/playlists/{playlistId}/pin` | `pinned=0/1`，置顶或取消置顶 |
| POST | `/api/client/me/playlists/{playlistId}/songs/{songId}` | 收藏歌曲到自建歌单 |
| DELETE | `/api/client/me/playlists/{playlistId}/songs/{songId}` | 从自建歌单移除歌曲 |

新建/编辑请求体：

```json
{
  "name": "通勤歌单",
  "description": "早晚通勤播放",
  "pinned": 0,
  "status": 1
}
```

封面通过上传接口维护；未上传封面时优先使用歌单第一首歌曲的封面，否则使用默认图片。每个账号注册时自动创建的“我喜欢的音乐”为系统歌单，不允许按普通歌单删除。

### 4.3 喜欢歌曲与收藏歌单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/client/me/likes/song-ids` | 当前用户喜欢的歌曲 ID |
| GET | `/api/client/me/likes/songs` | 当前用户喜欢的歌曲详情 |
| POST | `/api/client/me/likes/songs/{songId}` | 喜欢歌曲，并同步加入默认喜欢歌单 |
| DELETE | `/api/client/me/likes/songs/{songId}` | 取消喜欢，并从默认喜欢歌单移除 |
| POST | `/api/client/me/likes/playlists/{playlistId}` | 收藏公开歌单 |
| DELETE | `/api/client/me/likes/playlists/{playlistId}` | 取消收藏歌单 |

```bash
curl -X POST http://localhost:8080/api/client/me/likes/songs/12 \
  -H "Authorization: Bearer <token>"
```

喜欢歌曲操作在事务中维护收藏关系和默认歌单；关联表唯一约束用于避免重复写入。

## 5. 管理端接口

除登录和刷新令牌外，管理端请求需携带具有 `ADMIN` 角色的 Bearer Token。

### 5.1 用户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/users` | 分页查询，支持 `username`、`email`、`phone`、`status` |
| POST | `/api/admin/users` | 创建客户端用户 |
| GET | `/api/admin/users/{id}` | 用户详情 |
| PUT | `/api/admin/users/{id}` | 编辑用户 |
| PUT | `/api/admin/users/{id}/status?status=0|1` | 禁用/启用 |
| DELETE | `/api/admin/users/{id}` | 删除用户 |
| POST | `/api/admin/users/batch-delete` | 批量删除，正文 `{"ids":[1,2]}` |

用户正文支持：`username`、`password`、`profileId`、`email`、`phone`、`avatar`、`bio`、`status`。

### 5.2 歌手管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/artists` | 分页查询，支持 `keyword`、`name`、`region` |
| GET | `/api/admin/artists/all` | 全量下拉数据 |
| GET | `/api/admin/artists/options` | 分页选项，支持 `keyword`、`name`、`ids` |
| POST | `/api/admin/artists` | 新增歌手 |
| GET | `/api/admin/artists/{id}` | 歌手详情 |
| PUT | `/api/admin/artists/{id}` | 编辑歌手 |
| DELETE | `/api/admin/artists/{id}` | 删除歌手 |
| POST | `/api/admin/artists/batch-delete` | 批量删除 |

歌手主要字段：`name`、`avatar`、`description`、`region`、`status`。简介最大长度由当前数据库和后端校验控制，目标上限为 10000 字。

### 5.3 专辑管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/albums` | 分页查询，支持 `keyword`、`artistId` |
| GET | `/api/admin/albums/options` | 分页选项，支持 `keyword`、`name`、`artistId`、`ids` |
| POST | `/api/admin/albums` | 新增专辑 |
| GET | `/api/admin/albums/{id}` | 专辑详情 |
| PUT | `/api/admin/albums/{id}` | 编辑专辑 |
| DELETE | `/api/admin/albums/{id}` | 删除专辑 |
| POST | `/api/admin/albums/batch-delete` | 批量删除 |

专辑主要字段：`name`、`cover`、`artistId`、`releaseDate`、`description`、`type`、`status`。一首歌曲只关联一个 `albumId`。

### 5.4 歌曲管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/songs` | 分页查询，支持 `keyword`、`albumId`、`artistId` |
| POST | `/api/admin/songs` | multipart 上传并创建歌曲 |
| GET | `/api/admin/songs/{id}` | 歌曲详情 |
| PUT | `/api/admin/songs/{id}` | JSON 编辑歌曲元数据 |
| POST | `/api/admin/songs/{id}/replace` | multipart 替换歌曲文件及元数据 |
| PUT | `/api/admin/songs/{id}/status?status=0|1` | 上架/下架 |
| DELETE | `/api/admin/songs/{id}` | 删除歌曲 |
| POST | `/api/admin/songs/batch-delete` | 批量删除 |

创建和替换歌曲的 multipart 字段：

| 字段 | 必填 | 说明 |
|------|------|------|
| `audioFile` | 是 | 音频文件 |
| `name` | 是 | 歌曲名 |
| `artistIds` | 否 | 多个歌手 ID，使用逗号分隔 |
| `albumId` | 否 | 唯一所属专辑 |
| `duration` | 否 | 毫秒时长 |
| `lyrics` | 否 | LRC 文本 |
| `coverFile` / `cover` | 否 | 兼容字段；正式封面以专辑封面为准 |

```bash
curl -X POST http://localhost:8080/api/admin/songs \
  -H "Authorization: Bearer <admin-token>" \
  -F "audioFile=@demo.mp3" \
  -F "name=Demo Song" \
  -F "artistIds=1,2" \
  -F "albumId=3" \
  -F "lyrics=<demo.lrc"
```

### 5.5 歌单管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/playlists` | 分页查询，支持 `keyword`、`status`、`userId` |
| GET | `/api/admin/playlists/publishers/options` | 发布者分页选项 |
| POST | `/api/admin/playlists` | 创建平台歌单 |
| GET | `/api/admin/playlists/{id}` | 歌单详情 |
| PUT | `/api/admin/playlists/{id}` | 编辑歌单 |
| PUT | `/api/admin/playlists/{id}/songs` | 替换歌曲，正文 `{"songIds":[1,2]}` |
| PUT | `/api/admin/playlists/{id}/status?status=0|1` | 私有/公开 |
| DELETE | `/api/admin/playlists/{id}` | 删除歌单 |
| POST | `/api/admin/playlists/batch-delete` | 批量删除 |

歌单正文：`name`、`cover`、`description`、`tags`、`status`、`songIds`。

### 5.6 轮播图与上传

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/banners` | 分页查询，支持 `keyword`、`status` |
| POST | `/api/admin/banners` | 新增轮播图 |
| GET | `/api/admin/banners/{id}` | 轮播图详情 |
| PUT | `/api/admin/banners/{id}` | 编辑轮播图 |
| PUT | `/api/admin/banners/{id}/status?status=0|1` | 启用/禁用 |
| DELETE | `/api/admin/banners/{id}` | 删除轮播图 |
| POST | `/api/admin/banners/batch-delete` | 批量删除 |
| POST | `/api/admin/upload` | 通用文件上传，`file` 必填，`dir` 默认 `general` |

轮播图主要字段：`title`、`imageUrl`、`linkUrl`、`sort`、`status`。

## 6. 文件预览

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/files/preview?key={objectKey}` | 公开 | 读取 MinIO 图片等对象 |

`key` 应为后端返回的对象键或规范化地址，不要把 MinIO 访问密钥放到前端。接口会根据对象元数据设置合适的 `Content-Type`。

## 7. 网易云风格兼容层

这组接口服务于 Web 客户端迁移，不建议新的第三方调用方把它当作稳定领域 API。新功能优先使用 `/api/client/**`。

| 方法 | 路径 | 主要参数 | 说明 |
|------|------|----------|------|
| GET | `/banner` | 无 | 轮播图 |
| GET | `/search`、`/cloudsearch` | `keywords`、`type=1`、`limit=30` | 兼容搜索 |
| GET | `/search/default` | 无 | 默认搜索词 |
| GET | `/search/suggest` | `keywords`、`type` | 搜索建议 |
| GET | `/song/url/v1` | `id`、`level=standard` | 播放 URL |
| GET | `/song/detail` | `ids` | 歌曲详情 |
| GET | `/lyric` | `id` | 歌词 |
| GET | `/playlist/detail` | `id` | 歌单详情 |
| GET | `/playlist/track/all` | `id`、`limit=1000` | 歌单歌曲 |
| GET | `/personalized` | `limit=10` | 推荐歌单 |
| GET | `/top/playlist` | `order=hot`、`limit=20` | 热门歌单 |
| GET | `/top/song` | `type=0` | 最新歌曲 |
| GET | `/recommend/songs` | 无 | 推荐歌曲 |
| GET | `/recommend/resource` | 无 | 推荐资源 |
| GET | `/artist/detail` | `id` | 歌手详情 |
| GET | `/artist/top/song` | `id` | 歌手歌曲 |
| GET | `/album` | `id` | 专辑详情 |
| GET | `/record/recent/song` | 无 | 最近播放兼容数据 |
| GET | `/user/playlist` | 无 | 用户歌单兼容数据 |

兼容搜索类型沿用网易云数字约定，当前客户端主要使用歌曲、专辑、歌手和歌单类型。兼容层返回字段如 `al`、`ar`、`dt`、`tracks`，与 Sonora 内部实体并非一一同名。

## 8. 联调检查清单

1. `GET /api/client/ping` 返回 `code=200`。
2. Swagger UI 能加载 `/v3/api-docs`。
3. 登录后普通请求包含 `Authorization: Bearer ...`。
4. 音频请求返回 `200` 或 `206`，Range 请求必须有正确的 `Content-Range`。
5. 图片预览地址指向 `/api/files/preview?key=...` 且后端、MinIO 均在线。
6. 管理端开发环境通过 Vite 代理访问后端；Web 客户端媒体流直接访问 `VITE_APP_BASE_API`。
7. 创建、喜欢、收藏等写操作出现 `403` 时，先核对使用的是客户端令牌还是管理员令牌及对应权限。
