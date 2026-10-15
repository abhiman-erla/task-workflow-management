package com.abhiman.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the real HTTP layer, security filters, services and Postgres.
 * Runs in the "integration-test" phase via maven-failsafe (the *IT suffix).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskFlowApiIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    void fullTaskLifecycleWithRoles() throws Exception {
        String admin = login("admin@taskflow.local", "Admin@12345");

        String aliceEmail = uniqueEmail("alice");
        long aliceId = register("Alice", aliceEmail);
        String bobEmail = uniqueEmail("bob");
        long bobId = register("Bob", bobEmail);

        // Members cannot create projects
        String bobToken = login(bobEmail, "password123");
        call(post("/api/projects"), bobToken, Map.of("name", "Nope")).andExpect(status().isForbidden());

        // Admin promotes Alice; she logs in again so her token carries the new role
        call(patch("/api/users/" + aliceId + "/role"), admin, Map.of("role", "MANAGER"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
        String alice = login(aliceEmail, "password123");

        String projectName = "Apollo " + UUID.randomUUID();
        long projectId = id(call(post("/api/projects"), alice, Map.of("name", projectName))
                .andExpect(status().isCreated()));
        call(post("/api/projects"), alice, Map.of("name", projectName.toUpperCase()))
                .andExpect(status().isConflict());

        // Bob (member) creates a task; it starts in TODO
        long taskId = id(call(post("/api/projects/" + projectId + "/tasks"), bobToken,
                Map.of("title", "Write the API", "priority", "HIGH"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO")));

        // Bob isn't the assignee, so he may not move it at all (permission is checked first)
        transition(taskId, "IN_PROGRESS", bobToken).andExpect(status().isForbidden());
        // Even a manager can't start a task that has no assignee (workflow rule)
        transition(taskId, "IN_PROGRESS", alice).andExpect(status().isConflict());

        // Members can't assign; managers can
        call(patch("/api/tasks/" + taskId + "/assignee"), bobToken, Map.of("assigneeId", bobId))
                .andExpect(status().isForbidden());
        call(patch("/api/tasks/" + taskId + "/assignee"), alice, Map.of("assigneeId", bobId))
                .andExpect(jsonPath("$.assignee.id").value(bobId));

        transition(taskId, "IN_PROGRESS", bobToken).andExpect(status().isOk());
        transition(taskId, "DONE", bobToken).andExpect(status().isConflict()); // can't skip review
        transition(taskId, "IN_REVIEW", bobToken).andExpect(status().isOk());
        transition(taskId, "DONE", bobToken).andExpect(status().isForbidden()); // can't approve own work
        transition(taskId, "DONE", alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.allowedNextStatuses").isEmpty());

        // Closed tasks are read-only
        call(put("/api/tasks/" + taskId), alice, Map.of("title", "Changed", "priority", "LOW"))
                .andExpect(status().isConflict());

        // Audit trail
        mvc.perform(get("/api/tasks/" + taskId + "/history").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].toStatus").value("DONE"))
                .andExpect(jsonPath("$[2].changedBy.fullName").value("Alice"));

        // Filtering + pagination
        mvc.perform(get("/api/tasks")
                        .param("projectId", String.valueOf(projectId))
                        .param("status", "DONE")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(taskId));
    }

    @Test
    void requestsWithoutTokenAreRejectedWithProblemJson() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void validationErrorsListEachField() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@taskflow.local\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void unknownSortPropertyIs400() throws Exception {
        String token = login("admin@taskflow.local", "Admin@12345");
        mvc.perform(get("/api/tasks").param("sort", "bogus").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // --- helpers -------------------------------------------------------------------------

    private static String uniqueEmail(String name) {
        return name + "-" + UUID.randomUUID() + "@example.com";
    }

    private long register(String fullName, String email) throws Exception {
        return id(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", fullName, "email", email, "password", "password123"))))
                .andExpect(status().isCreated()));
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mvc.perform(request
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions transition(long taskId, String status, String token) throws Exception {
        return call(post("/api/tasks/" + taskId + "/transitions"), token, Map.of("status", status));
    }

    private long id(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }
}
