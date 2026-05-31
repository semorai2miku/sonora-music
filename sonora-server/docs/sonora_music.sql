-- ========================================================
-- Sonora Music — 数据库初始化脚本
-- 数据库名: sonora_music
-- 兼容: MySQL 8.0+
-- ========================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS sonora_music
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE sonora_music;

-- ============================
-- 1. 用户体系 (sys_)
-- ============================

-- 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id  VARCHAR(32)  NOT NULL UNIQUE COMMENT '客户端展示用角色ID',
    username    VARCHAR(64)  NOT NULL UNIQUE COMMENT '用户名',
    password    VARCHAR(256) NOT NULL COMMENT '加密密码',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    email       VARCHAR(128) NOT NULL COMMENT '邮箱',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    avatar      VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    gender      TINYINT      DEFAULT 0 COMMENT '0-未知 1-男 2-女',
    bio         VARCHAR(512) DEFAULT NULL COMMENT '个人简介',
    status      TINYINT      DEFAULT 1 COMMENT '1-正常 0-禁用',
    last_login_at DATETIME   DEFAULT NULL COMMENT '最后登录时间',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除 1-已删除',
    INDEX idx_username (username),
    INDEX idx_profile_id (profile_id),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL UNIQUE COMMENT '角色编码',
    name        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    description VARCHAR(256) DEFAULT NULL COMMENT '描述',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '1-正常 0-禁用',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 权限表
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL UNIQUE COMMENT '权限编码',
    name        VARCHAR(64)  NOT NULL COMMENT '权限名称',
    type        VARCHAR(16)  NOT NULL COMMENT 'menu-菜单 button-按钮 api-接口',
    parent_id   BIGINT       DEFAULT 0 COMMENT '父权限ID',
    path        VARCHAR(256) DEFAULT NULL COMMENT '路由路径',
    component   VARCHAR(256) DEFAULT NULL COMMENT '组件路径',
    icon        VARCHAR(64)  DEFAULT NULL COMMENT '图标',
    sort        INT          DEFAULT 0 COMMENT '排序',
    visible     TINYINT      DEFAULT 1 COMMENT '1-可见 0-隐藏',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 用户-角色关联表
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 角色-权限关联表
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ============================
-- 2. 内容体系 (t_)
-- ============================

-- 歌手表
DROP TABLE IF EXISTS t_artist;
CREATE TABLE t_artist (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL COMMENT '歌手名',
    avatar      VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    description TEXT         DEFAULT NULL COMMENT '简介',
    region      VARCHAR(32)  DEFAULT NULL COMMENT '地区',
    status      TINYINT      DEFAULT 1 COMMENT '1-正常 0-下架',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0,
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌手表';

-- 专辑表
DROP TABLE IF EXISTS t_album;
CREATE TABLE t_album (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(256) NOT NULL COMMENT '专辑名',
    cover        VARCHAR(512) DEFAULT NULL COMMENT '封面URL',
    artist_id    BIGINT       NOT NULL COMMENT '歌手ID',
    release_date DATE         DEFAULT NULL COMMENT '发行日期',
    description  TEXT         DEFAULT NULL COMMENT '专辑描述',
    type         VARCHAR(16)  DEFAULT 'album' COMMENT 'album-专辑 single-单曲 ep-EP',
    status       TINYINT      DEFAULT 1 COMMENT '1-正常 0-下架',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      DEFAULT 0,
    INDEX idx_artist (artist_id),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专辑表';

-- 歌曲表
DROP TABLE IF EXISTS t_song;
CREATE TABLE t_song (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(256) NOT NULL COMMENT '歌曲名',
    artist_ids  VARCHAR(256) DEFAULT NULL COMMENT '歌手ID列表 逗号分隔',
    album_id    BIGINT       DEFAULT NULL COMMENT '专辑ID',
    file_key    VARCHAR(512) DEFAULT NULL COMMENT 'MinIO objectKey',
    duration    INT          DEFAULT 0 COMMENT '时长(秒)',
    file_size   BIGINT       DEFAULT 0 COMMENT '文件大小(字节)',
    format      VARCHAR(16)  DEFAULT NULL COMMENT '格式 mp3/flac/wav',
    bitrate     INT          DEFAULT NULL COMMENT '比特率(bps)',
    cover       VARCHAR(512) DEFAULT NULL COMMENT '封面URL',
    lyrics      MEDIUMTEXT   DEFAULT NULL COMMENT 'LRC歌词',
    play_count  BIGINT       DEFAULT 0 COMMENT '播放次数',
    status      TINYINT      DEFAULT 1 COMMENT '1-上架 0-下架',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0,
    INDEX idx_name (name),
    INDEX idx_album (album_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲表';

-- 歌单表
DROP TABLE IF EXISTS t_playlist;
CREATE TABLE t_playlist (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(256) NOT NULL COMMENT '歌单名称',
    cover         VARCHAR(512) DEFAULT NULL COMMENT '封面URL',
    user_id       BIGINT       NOT NULL COMMENT '创建者ID',
    description   TEXT         DEFAULT NULL COMMENT '描述',
    type          VARCHAR(16)  DEFAULT 'normal' COMMENT 'liked-喜欢的音乐 normal-自建歌单',
    pinned        TINYINT      DEFAULT 0 COMMENT '1-置顶 0-普通',
    tags          VARCHAR(256) DEFAULT NULL COMMENT '标签 逗号分隔',
    play_count    BIGINT       DEFAULT 0 COMMENT '播放次数',
    collect_count BIGINT       DEFAULT 0 COMMENT '收藏次数',
    status        TINYINT      DEFAULT 1 COMMENT '1-公开 0-私有',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      DEFAULT 0,
    INDEX idx_user (user_id),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单表';

-- 歌单-歌曲关联表
DROP TABLE IF EXISTS t_playlist_song;
CREATE TABLE t_playlist_song (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    playlist_id BIGINT   NOT NULL,
    song_id     BIGINT   NOT NULL,
    sort        INT      DEFAULT 0 COMMENT '排序',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_playlist (playlist_id),
    UNIQUE KEY uk_playlist_song (playlist_id, song_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单歌曲关联表';

-- ============================
-- 3. 互动体系
-- ============================

-- 评论表
DROP TABLE IF EXISTS t_comment;
CREATE TABLE t_comment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '评论者ID',
    target_type VARCHAR(16)  NOT NULL COMMENT 'song / playlist',
    target_id   BIGINT       NOT NULL COMMENT '目标ID',
    content     TEXT         NOT NULL COMMENT '评论内容',
    parent_id   BIGINT       DEFAULT 0 COMMENT '父评论ID',
    like_count  INT          DEFAULT 0 COMMENT '点赞数',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0,
    INDEX idx_target (target_type, target_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- 用户收藏表
DROP TABLE IF EXISTS t_user_favorite;
CREATE TABLE t_user_favorite (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    target_type VARCHAR(16) NOT NULL COMMENT 'song / playlist',
    target_id   BIGINT      NOT NULL,
    created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_target (user_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏表';

-- 播放历史表
DROP TABLE IF EXISTS t_user_history;
CREATE TABLE t_user_history (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id   BIGINT   NOT NULL,
    song_id   BIGINT   NOT NULL,
    played_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, played_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放历史表';

-- ============================
-- 4. 运营体系
-- ============================

-- 轮播图表
DROP TABLE IF EXISTS t_banner;
CREATE TABLE t_banner (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(128) DEFAULT NULL COMMENT '标题',
    image_url  VARCHAR(512) NOT NULL COMMENT '图片URL',
    link_url   VARCHAR(512) DEFAULT NULL COMMENT '跳转链接',
    sort       INT          DEFAULT 0 COMMENT '排序',
    status     TINYINT      DEFAULT 1 COMMENT '1-启用 0-禁用',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轮播图表';

-- ============================
-- 5. 初始化数据
-- ============================

-- 管理员账号: sonora-music / admin123 (BCrypt)
INSERT INTO sys_user (profile_id, username, password, nickname, email, avatar, status) VALUES
('ADMIN000001', 'sonora-music', '$2a$10$r53nFvBJ0t5CGwB8q5bHX.V/6phBCwsaCjmKFNDdLFQi0Jzs5pV0q', 'sonora-music', 'admin@sonora.local', '/default-avatar.svg', 1);

-- 角色
INSERT INTO sys_role (code, name, description, sort) VALUES
('ADMIN',  '超级管理员', '拥有所有权限', 1),
('EDITOR', '编辑员',     '内容管理权限', 2),
('USER',   '普通用户',   '客户端用户',   3);

-- 赋予 sonora-music 用户 ADMIN 角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 权限 (管理端菜单)
INSERT INTO sys_permission (code, name, type, parent_id, path, component, icon, sort) VALUES
('dashboard',      '仪表盘',   'menu', 0, '/dashboard',      'dashboard/index',  'dashboard', 1),
('content',        '内容管理', 'menu', 0, '/content',         'content/parent',   'document',  2),
('content:artist', '歌手管理', 'menu', 2, '/content/artist',  'content/artist',  'user',      1),
('content:song',   '歌曲管理', 'menu', 2, '/content/song',   'content/song',     'music',     2),
('content:album',  '专辑管理', 'menu', 2, '/content/album',  'content/album',    'album',     3),
('content:playlist', '歌单管理', 'menu', 2, '/content/playlist', 'content/playlist', 'list',   4),
('user',           '客户端用户', 'menu', 0, '/user',           'user/index',       'people',    4),
('system',         '系统管理', 'menu', 0, '/system',         NULL,               'setting',   5),
('system:role',    '角色管理', 'menu', 8, '/system/role',    'system/role',      'role',      1),
('system:perm',    '权限管理', 'menu', 8, '/system/perm',    'system/perm',      'lock',      2),
('content:banner', '轮播图管理', 'menu', 2, '/content/banner', 'content/banner', 'picture',   5);

-- 赋予 ADMIN 角色所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;
