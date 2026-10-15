package com.abhiman.taskflow.task;

import com.abhiman.taskflow.auth.AuthenticatedUser;
import com.abhiman.taskflow.user.Role;
import com.abhiman.taskflow.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPermissionsTest {

    private final TaskPermissions permissions = new TaskPermissions();

    private User creator;
    private User assignee;
    private Task task;

    private final AuthenticatedUser admin = new AuthenticatedUser(1L, "admin@x.com", Role.ADMIN);
    private final AuthenticatedUser manager = new AuthenticatedUser(2L, "manager@x.com", Role.MANAGER);
    private final AuthenticatedUser creatorPrincipal = new AuthenticatedUser(10L, "creator@x.com", Role.MEMBER);
    private final AuthenticatedUser assigneePrincipal = new AuthenticatedUser(11L, "assignee@x.com", Role.MEMBER);
    private final AuthenticatedUser stranger = new AuthenticatedUser(12L, "stranger@x.com", Role.MEMBER);

    @BeforeEach
    void setUp() {
        creator = user(10L, Role.MEMBER);
        assignee = user(11L, Role.MEMBER);
        task = new Task(null, "t", null, Priority.MEDIUM, null, creator);
        task.setAssignee(assignee);
    }

    private static User user(long id, Role role) {
        User user = new User("u" + id, "u" + id + "@x.com", "h", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void editRequiresManagerAdminCreatorOrAssignee() {
        assertThat(permissions.canEdit(task, admin)).isTrue();
        assertThat(permissions.canEdit(task, manager)).isTrue();
        assertThat(permissions.canEdit(task, creatorPrincipal)).isTrue();
        assertThat(permissions.canEdit(task, assigneePrincipal)).isTrue();
        assertThat(permissions.canEdit(task, stranger)).isFalse();
    }

    @Test
    void onlyManagersAndAdminsAssignAndDelete() {
        assertThat(permissions.canAssign(manager)).isTrue();
        assertThat(permissions.canAssign(admin)).isTrue();
        assertThat(permissions.canAssign(creatorPrincipal)).isFalse();
        assertThat(permissions.canDelete(manager)).isTrue();
        assertThat(permissions.canDelete(assigneePrincipal)).isFalse();
    }

    @Test
    void assigneeCanMoveTheirTaskButCreatorCannot() {
        assertThat(permissions.canTransition(task, TaskStatus.IN_PROGRESS, assigneePrincipal)).isTrue();
        assertThat(permissions.canTransition(task, TaskStatus.IN_PROGRESS, creatorPrincipal)).isFalse();
        assertThat(permissions.canTransition(task, TaskStatus.IN_PROGRESS, stranger)).isFalse();
    }

    @Test
    void onlyReviewersCanApproveToDone() {
        task.setStatus(TaskStatus.IN_REVIEW);

        assertThat(permissions.canTransition(task, TaskStatus.DONE, assigneePrincipal)).isFalse();
        assertThat(permissions.canTransition(task, TaskStatus.DONE, manager)).isTrue();
        assertThat(permissions.canTransition(task, TaskStatus.IN_PROGRESS, assigneePrincipal)).isTrue();
    }
}
