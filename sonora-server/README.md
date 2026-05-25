Sonora Music Server 是 Sonora Music 项目的后端 API 服务。本项目基于 Spring Boot 3 构建，采用 Java 17、Maven、MyBatis-Plus、MySQL、Redis 和 MinIO 等技术，为 Sonora Music 的客户端和管理端提供稳定、高效的数据支持和业务逻辑处理。

主要功能
本服务提供以下核心功能 API：

用户认证与管理: 提供用户注册、登录、信息修改、头像上传、注销等接口，支持管理员对用户进行管理（查询、禁用/启用）。
内容管理:
  歌手管理: 添加、编辑、删除歌手信息。
  歌曲管理: 添加、编辑、删除歌曲信息，处理歌曲文件上传。
  歌单管理: 创建、编辑、删除歌单，管理歌单歌曲。
  轮播图管理: 添加、编辑、删除首页轮播图。
用户互动:
  评论管理: 发表、查看、删除歌曲或歌单的评论。
  收藏管理: 用户收藏/取消收藏歌曲、歌单。
  反馈管理: 提交、查看、处理用户反馈。
文件服务: 使用 MinIO 存储和管理音乐文件、图片（如头像、封面）等静态资源。
权限控制: 基于 JWT 和角色进行 API 访问权限控制。
数据缓存: 利用 Redis 缓存热点数据，提高访问速度。
邮件服务: 支持发送验证码等邮件通知。

技术栈
后端框架: Spring Boot 3
开发语言: Java 17
构建工具: Maven
数据库: MySQL (推荐 8.0+)
ORM: MyBatis-Plus
缓存: Redis
对象存储: MinIO
认证: JWT (java-jwt)
数据库连接池: Druid
工具库: Lombok, Spring Boot Validation, Java Mail
