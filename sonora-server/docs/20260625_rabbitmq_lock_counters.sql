-- RabbitMQ 统计与分布式锁相关字段迁移
-- 用于已有开发库；全新建库可直接使用 sonora_music.sql。

ALTER TABLE t_song
    ADD COLUMN like_count BIGINT DEFAULT 0 COMMENT '喜欢次数' AFTER play_count;

UPDATE t_song s
SET like_count = (
    SELECT COUNT(*)
    FROM t_user_favorite f
    WHERE f.target_type = 'song'
      AND f.target_id = s.id
);

UPDATE t_playlist p
SET collect_count = (
    SELECT COUNT(*)
    FROM t_user_favorite f
    WHERE f.target_type = 'playlist'
      AND f.target_id = p.id
);
