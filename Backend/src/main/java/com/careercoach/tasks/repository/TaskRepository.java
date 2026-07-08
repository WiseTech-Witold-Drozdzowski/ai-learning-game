package com.careercoach.tasks.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findTop5ByStateOrderByUpdatedAtDesc(TaskState state);
}
