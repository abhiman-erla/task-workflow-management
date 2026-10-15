package com.abhiman.taskflow.task;

import com.abhiman.taskflow.auth.AuthenticatedUser;
import com.abhiman.taskflow.common.ConflictException;
import com.abhiman.taskflow.common.NotFoundException;
import com.abhiman.taskflow.common.PageResponse;
import com.abhiman.taskflow.project.Project;
import com.abhiman.taskflow.project.ProjectRepository;
import com.abhiman.taskflow.task.TaskDtos.CreateTaskRequest;
import com.abhiman.taskflow.task.TaskDtos.HistoryEntry;
import com.abhiman.taskflow.task.TaskDtos.TaskResponse;
import com.abhiman.taskflow.task.TaskDtos.UpdateTaskRequest;
import com.abhiman.taskflow.task.TaskDtos.UserSummary;
import com.abhiman.taskflow.user.User;
import com.abhiman.taskflow.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository tasks;
    private final TaskStatusHistoryRepository history;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final TaskWorkflow workflow;
    private final TaskPermissions permissions;

    public TaskService(TaskRepository tasks, TaskStatusHistoryRepository history, ProjectRepository projects,
                       UserRepository users, TaskWorkflow workflow, TaskPermissions permissions) {
        this.tasks = tasks;
        this.history = history;
        this.projects = projects;
        this.users = users;
        this.workflow = workflow;
        this.permissions = permissions;
    }

    @Transactional
    public TaskResponse create(Long projectId, CreateTaskRequest request, AuthenticatedUser me) {
        Project project = projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project", projectId));
        User creator = users.findById(me.id()).orElseThrow(() -> new NotFoundException("User", me.id()));
        Task task = new Task(project, request.title().trim(), request.description(), request.priority(),
                request.dueDate(), creator);
        if (request.assigneeId() != null) {
            if (!permissions.canAssign(me)) {
                throw new AccessDeniedException("Only managers and admins can assign tasks");
            }
            task.setAssignee(findUser(request.assigneeId()));
        }
        return toResponse(tasks.save(task));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> search(TaskFilter filter, Pageable pageable) {
        return PageResponse.of(tasks.findAll(TaskSpecifications.matching(filter), pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request, AuthenticatedUser me) {
        Task task = find(id);
        if (!permissions.canEdit(task, me)) {
            throw new AccessDeniedException("Only the creator, the assignee, or a manager can edit this task");
        }
        requireOpen(task);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        tasks.flush(); // send the UPDATE now so version/updatedAt in the response are current
        return toResponse(task);
    }

    @Transactional
    public TaskResponse assign(Long id, Long assigneeId, AuthenticatedUser me) {
        if (!permissions.canAssign(me)) {
            throw new AccessDeniedException("Only managers and admins can assign tasks");
        }
        Task task = find(id);
        requireOpen(task);
        if (assigneeId == null && task.getStatus() != TaskStatus.TODO) {
            throw new ConflictException("A task that has been started must keep an assignee");
        }
        task.setAssignee(assigneeId == null ? null : findUser(assigneeId));
        tasks.flush();
        return toResponse(task);
    }

    @Transactional
    public TaskResponse transition(Long id, TaskStatus target, AuthenticatedUser me) {
        Task task = find(id);
        // Check permission first: a user who may not touch the task shouldn't learn its workflow state.
        if (!permissions.canTransition(task, target, me)) {
            throw new AccessDeniedException("You are not allowed to move this task to " + target);
        }
        TaskStatus previous = workflow.apply(task, target);
        history.save(new TaskStatusHistory(task, previous, target, users.getReferenceById(me.id())));
        tasks.flush();
        return toResponse(task);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser me) {
        if (!permissions.canDelete(me)) {
            throw new AccessDeniedException("Only managers and admins can delete tasks");
        }
        tasks.delete(find(id));
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(Long id) {
        if (!tasks.existsById(id)) {
            throw new NotFoundException("Task", id);
        }
        return history.findByTaskIdOrderByChangedAtAscIdAsc(id).stream().map(HistoryEntry::from).toList();
    }

    private Task find(Long id) {
        return tasks.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Task", id));
    }

    private User findUser(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private static void requireOpen(Task task) {
        if (task.getStatus() == TaskStatus.DONE || task.getStatus() == TaskStatus.CANCELLED) {
            throw new ConflictException("Task is " + task.getStatus() + " and can no longer be changed");
        }
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getTitle(), task.getDescription(),
                task.getStatus(), workflow.allowedNext(task.getStatus()), task.getPriority(), task.getDueDate(),
                UserSummary.from(task.getAssignee()), UserSummary.from(task.getCreatedBy()),
                task.getCreatedAt(), task.getUpdatedAt(), task.getVersion());
    }
}
