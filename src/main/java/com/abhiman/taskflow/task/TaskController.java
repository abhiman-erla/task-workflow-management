package com.abhiman.taskflow.task;

import com.abhiman.taskflow.auth.AuthenticatedUser;
import com.abhiman.taskflow.common.PageResponse;
import com.abhiman.taskflow.task.TaskDtos.AssignRequest;
import com.abhiman.taskflow.task.TaskDtos.CreateTaskRequest;
import com.abhiman.taskflow.task.TaskDtos.HistoryEntry;
import com.abhiman.taskflow.task.TaskDtos.TaskResponse;
import com.abhiman.taskflow.task.TaskDtos.TransitionRequest;
import com.abhiman.taskflow.task.TaskDtos.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@PathVariable Long projectId, @Valid @RequestBody CreateTaskRequest request,
                               @AuthenticationPrincipal AuthenticatedUser me) {
        return taskService.create(projectId, request, me);
    }

    @GetMapping("/api/tasks")
    public PageResponse<TaskResponse> search(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Priority priority,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return taskService.search(new TaskFilter(projectId, status, assigneeId, priority), pageable);
    }

    @GetMapping("/api/tasks/{id}")
    public TaskResponse get(@PathVariable Long id) {
        return taskService.get(id);
    }

    @PutMapping("/api/tasks/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request,
                               @AuthenticationPrincipal AuthenticatedUser me) {
        return taskService.update(id, request, me);
    }

    @PatchMapping("/api/tasks/{id}/assignee")
    public TaskResponse assign(@PathVariable Long id, @RequestBody AssignRequest request,
                               @AuthenticationPrincipal AuthenticatedUser me) {
        return taskService.assign(id, request.assigneeId(), me);
    }

    @PostMapping("/api/tasks/{id}/transitions")
    public TaskResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser me) {
        return taskService.transition(id, request.status(), me);
    }

    @GetMapping("/api/tasks/{id}/history")
    public List<HistoryEntry> history(@PathVariable Long id) {
        return taskService.history(id);
    }

    @DeleteMapping("/api/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser me) {
        taskService.delete(id, me);
    }
}
