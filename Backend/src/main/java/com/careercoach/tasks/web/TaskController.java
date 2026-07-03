package com.careercoach.tasks.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.auth.service.CurrentUserService;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.service.TaskService;
import com.careercoach.tasks.web.model.SubmitRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final CurrentUserService currentUserService;

    @PostMapping("/{id}/start")
    public Task start(@PathVariable Long id) {
        return taskService.start(id);
    }

    @PostMapping("/{id}/submit")
    public Task submit(@PathVariable Long id, @RequestBody(required = false) SubmitRequest req) {
        Long userId = currentUserService.getCurrentUser().getId();
        String artifact = req == null ? null : req.artifact();
        return taskService.submit(id, userId, artifact);
    }

    @GetMapping("/{id}")
    public Task get(@PathVariable Long id) {
        return taskService.get(id);
    }
}
