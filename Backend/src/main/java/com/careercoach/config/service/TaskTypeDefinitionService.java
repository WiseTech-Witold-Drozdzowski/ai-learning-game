package com.careercoach.config.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.repository.TaskTypeDefinitionRepository;
import com.careercoach.config.web.TaskTypeUpsertRequest;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class TaskTypeDefinitionService {

    private final TaskTypeDefinitionRepository taskTypeDefinitionRepository;

    @Transactional(readOnly = true)
    public List<TaskTypeDefinition> list() {
        return taskTypeDefinitionRepository.findAll(Sort.by("key"));
    }

    @Transactional(readOnly = true)
    public TaskTypeDefinition get(String key) {
        return taskTypeDefinitionRepository.findById(key)
                .orElseThrow(() -> new ConfigEntryNotFoundException("No task type definition for key: " + key));
    }

    public TaskTypeDefinition upsert(String key, TaskTypeUpsertRequest req) {
        TaskTypeDefinition def = taskTypeDefinitionRepository.findById(key)
                .orElseGet(() -> TaskTypeDefinition.builder().key(key).build());
        def.setDisplayName(req.displayName());
        def.setVerificationMethod(req.verificationMethod());
        def.setExpBase(req.expBase());
        def.setExpScaleByScore(req.expScaleByScore());
        def.setRequiresArtifact(req.requiresArtifact());
        return taskTypeDefinitionRepository.save(def);
    }

    public boolean seedIfAbsent(TaskTypeDefinition def) {
        if (taskTypeDefinitionRepository.existsById(def.getKey())) {
            return false;
        }
        taskTypeDefinitionRepository.save(def);
        return true;
    }
}
