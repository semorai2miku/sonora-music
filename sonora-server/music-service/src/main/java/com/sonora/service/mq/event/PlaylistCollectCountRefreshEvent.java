package com.sonora.service.mq.event;

import java.io.Serializable;
import java.time.LocalDateTime;

public record PlaylistCollectCountRefreshEvent(Long playlistId, LocalDateTime occurredAt) implements Serializable {
}
