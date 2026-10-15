package com.abhiman.taskflow.task;

import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the WHERE clause for GET /api/tasks from optional filters.
 * Each method returns null when its filter is absent, and Specification.allOf skips nulls.
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> matching(TaskFilter filter) {
        return Specification.allOf(
                inProject(filter.projectId()),
                hasStatus(filter.status()),
                assignedTo(filter.assigneeId()),
                hasPriority(filter.priority()));
    }

    static Specification<Task> inProject(Long projectId) {
        return projectId == null ? null : (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    static Specification<Task> hasStatus(TaskStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Task> assignedTo(Long assigneeId) {
        return assigneeId == null ? null : (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    static Specification<Task> hasPriority(Priority priority) {
        return priority == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }
}
