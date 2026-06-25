package com.sonora.service.mq.event;

import java.io.Serializable;
import java.time.LocalDateTime;

public record SongPlayEvent(Long songId, Long userId, LocalDateTime occurredAt) implements Serializable {
}
