package com.abhiman.taskflow.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    /** Used for both create (POST) and full update (PUT). */
    public record ProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description) {
    }

    public record ProjectResponse(Long id, String name, String description, Long ownerId, String ownerName,
                                  Instant createdAt, Instant updatedAt) {

        public static ProjectResponse from(Project project) {
            return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                    project.getOwner().getId(), project.getOwner().getFullName(),
                    project.getCreatedAt(), project.getUpdatedAt());
        }
    }
}
