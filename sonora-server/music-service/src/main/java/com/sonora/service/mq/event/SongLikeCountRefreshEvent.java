package com.sonora.service.mq.event;

import java.io.Serializable;
import java.time.LocalDateTime;

public record SongLikeCountRefreshEvent(Long songId, LocalDateTime occurredAt) implements Serializable {
}
