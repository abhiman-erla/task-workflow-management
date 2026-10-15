package com.abhiman.taskflow.task;

import com.abhiman.taskflow.auth.AuthenticatedUser;
import com.abhiman.taskflow.common.ConflictException;
import com.abhiman.taskflow.common.NotFoundException;
import com.abhiman.taskflow.project.Project;
import com.abhiman.taskflow.project.ProjectRepository;
import com.abhiman.taskflow.task.TaskDtos.CreateTaskRequest;
import com.abhiman.taskflow.task.TaskDtos.TaskResponse;
import com.abhiman.taskflow.task.TaskDtos.UpdateTaskRequest;
import com.abhiman.taskflow.user.Role;
import com.abhiman.taskflow.user.User;
import com.abhiman.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TaskService: repositories are mocked, while the pure rule classes
 * (TaskWorkflow, TaskPermissions) are real so we test the behaviour, not the wiring.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository tasks;
    @Mock
    private TaskStatusHistoryRepository history;
    @Mock
    private ProjectRepository projects;
    @Mock
    private UserRepository users;

    private TaskService service;

    private final User manager = user(1L, Role.MANAGER);
    private final User member = user(2L, Role.MEMBER);
    private final AuthenticatedUser managerPrincipal = new AuthenticatedUser(1L, "u1@x.com", Role.MANAGER);
    private final AuthenticatedUser memberPrincipal = new AuthenticatedUser(2L, "u2@x.com", Role.MEMBER);
    private Project project;

    @BeforeEach
    void setUp() {
        service = new TaskService(tasks, history, projects, users, new TaskWorkflow(), new TaskPermissions());
        project = new Project("Apollo", null, manager);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    private static User user(long id, Role role) {
        User user = new User("User " + id, "u" + id + "@x.com", "h", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Task existingTask(long id, User assignee, TaskStatus status) {
        Task task = new Task(project, "Existing", null, Priority.MEDIUM, null, manager);
        ReflectionTestUtils.setField(task, "id", id);
        task.setAssignee(assignee);
        task.setStatus(status);
        when(tasks.findWithDetailsById(id)).thenReturn(Optional.of(task));
        return task;
    }

    @Test
    void createDefaultsToTodoAndMediumPriority() {
        when(projects.findById(100L)).thenReturn(Optional.of(project));
        when(users.findById(2L)).thenReturn(Optional.of(member));
        when(tasks.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse response = service.create(100L,
                new CreateTaskRequest("  Write tests ", null, null, null, null), memberPrincipal);

        assertThat(response.title()).isEqualTo("Write tests");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(response.createdBy().id()).isEqualTo(2L);
        assertThat(response.allowedNextStatuses()).containsExactlyInAnyOrder(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED);
    }

    @Test
    void createInMissingProjectIs404() {
        when(projects.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(999L,
                new CreateTaskRequest("t", null, null, null, null), memberPrincipal))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void memberCannotAssignWhileCreating() {
        when(projects.findById(100L)).thenReturn(Optional.of(project));
        when(users.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.create(100L,
                new CreateTaskRequest("t", null, null, null, 1L), memberPrincipal))
                .isInstanceOf(AccessDeniedException.class);
        verify(tasks, never()).save(any());
    }

    @Test
    void transitionRecordsHistory() {
        existingTask(5L, member, TaskStatus.TODO);
        when(users.getReferenceById(2L)).thenReturn(member);

        TaskResponse response = service.transition(5L, TaskStatus.IN_PROGRESS, memberPrincipal);

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        ArgumentCaptor<TaskStatusHistory> saved = ArgumentCaptor.forClass(TaskStatusHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getFromStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(saved.getValue().getToStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(saved.getValue().getChangedBy()).isSameAs(member);
    }

    @Test
    void memberCannotMoveSomeoneElsesTask() {
        existingTask(5L, manager, TaskStatus.TODO);

        assertThatThrownBy(() -> service.transition(5L, TaskStatus.IN_PROGRESS, memberPrincipal))
                .isInstanceOf(AccessDeniedException.class);
        verify(history, never()).save(any());
    }

    @Test
    void illegalTransitionIsConflictAndRecordsNothing() {
        existingTask(5L, member, TaskStatus.TODO);

        assertThatThrownBy(() -> service.transition(5L, TaskStatus.DONE, managerPrincipal))
                .isInstanceOf(ConflictException.class);
        verify(history, never()).save(any());
    }

    @Test
    void closedTaskCannotBeEdited() {
        existingTask(5L, member, TaskStatus.DONE);

        assertThatThrownBy(() -> service.update(5L,
                new UpdateTaskRequest("New title", null, Priority.HIGH, null), managerPrincipal))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DONE");
    }

    @Test
    void memberCannotAssign() {
        assertThatThrownBy(() -> service.assign(5L, 2L, memberPrincipal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void startedTaskCannotBeUnassigned() {
        existingTask(5L, member, TaskStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.assign(5L, null, managerPrincipal))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void managerAssignsTask() {
        Task task = existingTask(5L, null, TaskStatus.TODO);
        when(users.findById(2L)).thenReturn(Optional.of(member));

        TaskResponse response = service.assign(5L, 2L, managerPrincipal);

        assertThat(task.getAssignee()).isSameAs(member);
        assertThat(response.assignee().id()).isEqualTo(2L);
    }
}
