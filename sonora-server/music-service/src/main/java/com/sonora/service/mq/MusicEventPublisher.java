package com.sonora.service.mq;

import com.sonora.service.mq.event.PlaylistCollectCountRefreshEvent;
import com.sonora.service.mq.event.PlaylistPlayEvent;
import com.sonora.service.mq.event.SongLikeCountRefreshEvent;
import com.sonora.service.mq.event.SongPlayEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MusicEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MusicEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public MusicEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishSongPlayed(Long songId, Long userId) {
        send(MqConstants.SONG_PLAY_ROUTING_KEY, new SongPlayEvent(songId, userId, LocalDateTime.now()));
    }

    public void publishPlaylistPlayed(Long playlistId, Long userId) {
        send(MqConstants.PLAYLIST_PLAY_ROUTING_KEY, new PlaylistPlayEvent(playlistId, userId, LocalDateTime.now()));
    }

    public void publishSongLikeCountRefresh(Long songId) {
        send(MqConstants.SONG_LIKE_COUNT_ROUTING_KEY, new SongLikeCountRefreshEvent(songId, LocalDateTime.now()));
    }

    public void publishPlaylistCollectCountRefresh(Long playlistId) {
        send(MqConstants.PLAYLIST_COLLECT_COUNT_ROUTING_KEY,
                new PlaylistCollectCountRefreshEvent(playlistId, LocalDateTime.now()));
    }

    private void send(String routingKey, Object event) {
        try {
            rabbitTemplate.convertAndSend(MqConstants.SONORA_EVENT_EXCHANGE, routingKey, event);
        } catch (Exception e) {
            log.warn("RabbitMQ 事件发送失败: routingKey={}, event={}", routingKey, event, e);
        }
    }
}
