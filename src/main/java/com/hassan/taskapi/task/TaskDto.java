package com.hassan.taskapi.task;

import java.io.Serializable;
import java.time.Instant;

/**
 * What the API returns and what gets cached in Redis.
 * Serializable because the default Redis cache serializer is JDK serialization.
 */
public record TaskDto(Long id, String title, String description, boolean completed, Instant createdAt)
        implements Serializable {

    static TaskDto from(Task task) {
        return new TaskDto(task.getId(), task.getTitle(), task.getDescription(),
                task.isCompleted(), task.getCreatedAt());
    }
}
