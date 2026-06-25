package com.sonora.service.mq;

import com.sonora.mapper.PlaylistMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.service.mq.event.PlaylistCollectCountRefreshEvent;
import com.sonora.service.mq.event.PlaylistPlayEvent;
import com.sonora.service.mq.event.SongLikeCountRefreshEvent;
import com.sonora.service.mq.event.SongPlayEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MusicEventConsumer {

    private final SongMapper songMapper;
    private final PlaylistMapper playlistMapper;

    public MusicEventConsumer(SongMapper songMapper, PlaylistMapper playlistMapper) {
        this.songMapper = songMapper;
        this.playlistMapper = playlistMapper;
    }

    @RabbitListener(queues = MqConstants.SONG_PLAY_QUEUE)
    public void handleSongPlay(SongPlayEvent event) {
        if (event != null && event.songId() != null) {
            songMapper.increasePlayCount(event.songId(), 1);
        }
    }

    @RabbitListener(queues = MqConstants.PLAYLIST_PLAY_QUEUE)
    public void handlePlaylistPlay(PlaylistPlayEvent event) {
        if (event != null && event.playlistId() != null) {
            playlistMapper.increasePlayCount(event.playlistId(), 1);
        }
    }

    @RabbitListener(queues = MqConstants.SONG_LIKE_COUNT_QUEUE)
    public void handleSongLikeCountRefresh(SongLikeCountRefreshEvent event) {
        if (event != null && event.songId() != null) {
            songMapper.refreshLikeCount(event.songId());
        }
    }

    @RabbitListener(queues = MqConstants.PLAYLIST_COLLECT_COUNT_QUEUE)
    public void handlePlaylistCollectCountRefresh(PlaylistCollectCountRefreshEvent event) {
        if (event != null && event.playlistId() != null) {
            playlistMapper.refreshCollectCount(event.playlistId());
        }
    }
}
