package com.abhiman.taskflow.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // Load the owner in the same query so listing N projects doesn't fire N extra SELECTs.
    @EntityGraph(attributePaths = "owner")
    Page<Project> findAllBy(Pageable pageable);
}
