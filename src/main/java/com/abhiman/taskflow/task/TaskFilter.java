package com.abhiman.taskflow.task;

/** Optional filters for GET /api/tasks; any field may be null. */
public record TaskFilter(Long projectId, TaskStatus status, Long assigneeId, Priority priority) {
}
