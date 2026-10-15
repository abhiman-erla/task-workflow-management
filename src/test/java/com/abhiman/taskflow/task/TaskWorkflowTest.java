package com.abhiman.taskflow.task;

import com.abhiman.taskflow.common.ConflictException;
import com.abhiman.taskflow.user.Role;
import com.abhiman.taskflow.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static com.abhiman.taskflow.task.TaskStatus.CANCELLED;
import static com.abhiman.taskflow.task.TaskStatus.DONE;
import static com.abhiman.taskflow.task.TaskStatus.IN_PROGRESS;
import static com.abhiman.taskflow.task.TaskStatus.IN_REVIEW;
import static com.abhiman.taskflow.task.TaskStatus.TODO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskWorkflowTest {

    private final TaskWorkflow workflow = new TaskWorkflow();

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "TODO, IN_PROGRESS",
            "TODO, CANCELLED",
            "IN_PROGRESS, IN_REVIEW",
            "IN_PROGRESS, TODO",
            "IN_PROGRESS, CANCELLED",
            "IN_REVIEW, DONE",
            "IN_REVIEW, IN_PROGRESS",
            "IN_REVIEW, CANCELLED"
    })
    void allowedTransitions(TaskStatus from, TaskStatus to) {
        assertThat(workflow.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "TODO, IN_REVIEW",
            "TODO, DONE",
            "IN_PROGRESS, DONE",
            "IN_REVIEW, TODO",
            "TODO, TODO",
            "DONE, IN_PROGRESS",
            "DONE, CANCELLED",
            "CANCELLED, TODO"
    })
    void forbiddenTransitions(TaskStatus from, TaskStatus to) {
        assertThat(workflow.canTransition(from, to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"DONE", "CANCELLED"})
    void terminalStatesHaveNoExits(TaskStatus terminal) {
        assertThat(workflow.allowedNext(terminal)).isEmpty();
    }

    @Test
    void applyMovesTaskAndReturnsPreviousStatus() {
        Task task = taskAssignedTo(new User("Bob", "bob@example.com", "h", Role.MEMBER));

        TaskStatus previous = workflow.apply(task, IN_PROGRESS);

        assertThat(previous).isEqualTo(TODO);
        assertThat(task.getStatus()).isEqualTo(IN_PROGRESS);
    }

    @Test
    void illegalTransitionExplainsWhatIsAllowed() {
        Task task = taskAssignedTo(new User("Bob", "bob@example.com", "h", Role.MEMBER));

        assertThatThrownBy(() -> workflow.apply(task, DONE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("TODO")
                .hasMessageContaining("DONE")
                .hasMessageContaining("IN_PROGRESS")
                .hasMessageContaining("CANCELLED");
        assertThat(task.getStatus()).isEqualTo(TODO);
    }

    @Test
    void cannotStartUnassignedTask() {
        Task task = taskAssignedTo(null);

        assertThatThrownBy(() -> workflow.apply(task, IN_PROGRESS))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("assignee");
    }

    @Test
    void unassignedTaskCanStillBeCancelled() {
        Task task = taskAssignedTo(null);

        workflow.apply(task, CANCELLED);

        assertThat(task.getStatus()).isEqualTo(CANCELLED);
    }

    private static Task taskAssignedTo(User assignee) {
        Task task = new Task(null, "t", null, Priority.MEDIUM, null, null);
        task.setAssignee(assignee);
        return task;
    }
}
