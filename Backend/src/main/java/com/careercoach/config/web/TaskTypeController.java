package com.careercoach.config.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.service.TaskTypeDefinitionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/task-types")
@RequiredArgsConstructor
public class TaskTypeController {

    private final TaskTypeDefinitionService taskTypeDefinitionService;

    @GetMapping
    public List<TaskTypeDefinition> list() {
        return taskTypeDefinitionService.list();
    }

    @GetMapping("/{key}")
    public TaskTypeDefinition get(@PathVariable String key) {
        return taskTypeDefinitionService.get(key);
    }

    @PutMapping("/{key}")
    public TaskTypeDefinition upsert(@PathVariable String key, @Valid @RequestBody TaskTypeUpsertRequest req) {
        return taskTypeDefinitionService.upsert(key, req);
    }
}
