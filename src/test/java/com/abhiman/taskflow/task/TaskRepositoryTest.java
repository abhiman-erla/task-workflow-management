package com.abhiman.taskflow.task;

import com.abhiman.taskflow.TestcontainersConfiguration;
import com.abhiman.taskflow.project.Project;
import com.abhiman.taskflow.project.ProjectRepository;
import com.abhiman.taskflow.user.Role;
import com.abhiman.taskflow.user.User;
import com.abhiman.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TaskRepositoryTest {

    @Autowired
    private TaskRepository tasks;
    @Autowired
    private ProjectRepository projects;
    @Autowired
    private UserRepository users;
    @Autowired
    private TaskStatusHistoryRepository history;

    private User alice;
    private User bob;
    private Project apollo;
    private Project gemini;

    @BeforeEach
    void setUp() {
        alice = users.save(new User("Alice", "alice@example.com", "hash", Role.MANAGER));
        bob = users.save(new User("Bob", "bob@example.com", "hash", Role.MEMBER));
        apollo = projects.save(new Project("Apollo", null, alice));
        gemini = projects.save(new Project("Gemini", null, alice));

        save(apollo, "Design schema", Priority.HIGH, bob, TaskStatus.IN_PROGRESS);
        save(apollo, "Write API", Priority.MEDIUM, bob, TaskStatus.TODO);
        save(apollo, "Write docs", Priority.LOW, null, TaskStatus.TODO);
        save(gemini, "Plan launch", Priority.URGENT, alice, TaskStatus.DONE);
    }

    private Task save(Project project, String title, Priority priority, User assignee, TaskStatus status) {
        Task task = new Task(project, title, null, priority, null, alice);
        task.setAssignee(assignee);
        task.setStatus(status);
        return tasks.save(task);
    }

    @Test
    void noFiltersReturnsEverything() {
        Page<Task> page = tasks.findAll(TaskSpecifications.matching(new TaskFilter(null, null, null, null)),
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    void filtersCombineWithAnd() {
        Page<Task> page = tasks.findAll(
                TaskSpecifications.matching(new TaskFilter(apollo.getId(), TaskStatus.TODO, bob.getId(), null)),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Task::getTitle).containsExactly("Write API");
    }

    @Test
    void filtersByPriority() {
        Page<Task> page = tasks.findAll(
                TaskSpecifications.matching(new TaskFilter(null, null, null, Priority.URGENT)),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Task::getTitle).containsExactly("Plan launch");
    }

    @Test
    void paginatesAndSorts() {
        Page<Task> page = tasks.findAll(
                TaskSpecifications.matching(new TaskFilter(apollo.getId(), null, null, null)),
                PageRequest.of(0, 2, Sort.by("title")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Task::getTitle).containsExactly("Design schema", "Write API");
    }

    @Test
    void historyIsReturnedInOrder() {
        Task task = tasks.findAll().getFirst();
        history.save(new TaskStatusHistory(task, TaskStatus.TODO, TaskStatus.IN_PROGRESS, bob));
        history.save(new TaskStatusHistory(task, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW, bob));

        assertThat(history.findByTaskIdOrderByChangedAtAscIdAsc(task.getId()))
                .extracting(TaskStatusHistory::getToStatus)
                .containsExactly(TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW);
    }
}
