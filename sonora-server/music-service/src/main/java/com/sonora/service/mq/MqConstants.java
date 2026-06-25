package com.sonora.service.mq;

public final class MqConstants {

    public static final String SONORA_EVENT_EXCHANGE = "sonora.event.exchange";

    public static final String SONG_PLAY_QUEUE = "sonora.song.play.queue";
    public static final String SONG_PLAY_ROUTING_KEY = "song.play";

    public static final String PLAYLIST_PLAY_QUEUE = "sonora.playlist.play.queue";
    public static final String PLAYLIST_PLAY_ROUTING_KEY = "playlist.play";

    public static final String SONG_LIKE_COUNT_QUEUE = "sonora.song.like-count.queue";
    public static final String SONG_LIKE_COUNT_ROUTING_KEY = "song.like-count.refresh";

    public static final String PLAYLIST_COLLECT_COUNT_QUEUE = "sonora.playlist.collect-count.queue";
    public static final String PLAYLIST_COLLECT_COUNT_ROUTING_KEY = "playlist.collect-count.refresh";

    private MqConstants() {
    }
}
