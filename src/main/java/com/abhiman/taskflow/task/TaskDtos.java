package com.abhiman.taskflow.task;

import com.abhiman.taskflow.user.User;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            Priority priority,
            @FutureOrPresent LocalDate dueDate,
            Long assigneeId) {
    }

    /** Full replacement of the editable fields (PUT semantics). Status and assignee have their own endpoints. */
    public record UpdateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull Priority priority,
            LocalDate dueDate) {
    }

    public record AssignRequest(Long assigneeId) {
    }

    public record TransitionRequest(@NotNull TaskStatus status) {
    }

    public record UserSummary(Long id, String fullName) {

        static UserSummary from(User user) {
            return user == null ? null : new UserSummary(user.getId(), user.getFullName());
        }
    }

    public record TaskResponse(Long id, Long projectId, String title, String description, TaskStatus status,
                               Set<TaskStatus> allowedNextStatuses, Priority priority, LocalDate dueDate,
                               UserSummary assignee, UserSummary createdBy, Instant createdAt, Instant updatedAt,
                               long version) {
    }

    public record HistoryEntry(TaskStatus fromStatus, TaskStatus toStatus, UserSummary changedBy, Instant changedAt) {

        static HistoryEntry from(TaskStatusHistory history) {
            return new HistoryEntry(history.getFromStatus(), history.getToStatus(),
                    UserSummary.from(history.getChangedBy()), history.getChangedAt());
        }
    }
}
