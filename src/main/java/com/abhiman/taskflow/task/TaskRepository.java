package com.abhiman.taskflow.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    // @EntityGraph fetches the associations in the same query, avoiding the N+1 problem
    // when the API maps a page of tasks to DTOs.
    @Override
    @EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
    Optional<Task> findWithDetailsById(Long id);
}
