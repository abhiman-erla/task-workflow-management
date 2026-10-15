package com.abhiman.taskflow.task;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {

    @EntityGraph(attributePaths = "changedBy")
    List<TaskStatusHistory> findByTaskIdOrderByChangedAtAscIdAsc(Long taskId);
}
