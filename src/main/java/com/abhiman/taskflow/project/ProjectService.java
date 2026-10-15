package com.abhiman.taskflow.project;

import com.abhiman.taskflow.auth.AuthenticatedUser;
import com.abhiman.taskflow.common.ConflictException;
import com.abhiman.taskflow.common.NotFoundException;
import com.abhiman.taskflow.common.PageResponse;
import com.abhiman.taskflow.project.ProjectDtos.ProjectRequest;
import com.abhiman.taskflow.project.ProjectDtos.ProjectResponse;
import com.abhiman.taskflow.user.Role;
import com.abhiman.taskflow.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Services return DTOs, not entities: mapping happens inside the transaction, so lazy
 * associations are still loadable (open-in-view is off).
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final UserRepository users;

    public ProjectService(ProjectRepository projects, UserRepository users) {
        this.projects = projects;
        this.users = users;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    public ProjectResponse create(ProjectRequest request, AuthenticatedUser me) {
        String name = request.name().trim();
        if (projects.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A project named '" + name + "' already exists");
        }
        // getReferenceById returns a proxy without a SELECT; we only need the FK value.
        Project project = new Project(name, request.description(), users.getReferenceById(me.id()));
        return ProjectResponse.from(projects.save(project));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(Pageable pageable) {
        return PageResponse.of(projects.findAllBy(pageable), ProjectResponse::from);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return ProjectResponse.from(find(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request, AuthenticatedUser me) {
        Project project = find(id);
        requireOwnerOrAdmin(project, me);
        String name = request.name().trim();
        if (projects.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A project named '" + name + "' already exists");
        }
        project.setName(name);
        project.setDescription(request.description());
        // flush so updatedAt is set before we map the response
        projects.flush();
        return ProjectResponse.from(project);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional
    public void delete(Long id, AuthenticatedUser me) {
        Project project = find(id);
        requireOwnerOrAdmin(project, me);
        projects.delete(project); // tasks are removed by ON DELETE CASCADE in the schema
    }

    Project find(Long id) {
        return projects.findById(id).orElseThrow(() -> new NotFoundException("Project", id));
    }

    private static void requireOwnerOrAdmin(Project project, AuthenticatedUser me) {
        if (!me.hasRole(Role.ADMIN) && !project.getOwner().getId().equals(me.id())) {
            throw new AccessDeniedException("Only the project owner or an admin can change this project");
        }
    }
}
