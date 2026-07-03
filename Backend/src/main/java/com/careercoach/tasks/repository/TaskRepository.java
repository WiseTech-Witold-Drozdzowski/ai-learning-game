package com.careercoach.tasks.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.careercoach.tasks.domain.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
