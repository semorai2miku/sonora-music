package com.sonora.service.mq.event;

import java.io.Serializable;
import java.time.LocalDateTime;

public record PlaylistPlayEvent(Long playlistId, Long userId, LocalDateTime occurredAt) implements Serializable {
}
