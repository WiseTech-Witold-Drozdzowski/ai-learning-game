package com.careercoach.tasks.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.gamification.service.AwardCommand;
import com.careercoach.gamification.service.AwardResult;
import com.careercoach.gamification.service.GamificationService;
import com.careercoach.gamification.service.SkillAward;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.domain.exception.ArtifactRequiredException;
import com.careercoach.tasks.domain.exception.IllegalTaskStateTransitionException;
import com.careercoach.tasks.domain.exception.TaskNotFoundException;
import com.careercoach.tasks.domain.exception.UnsupportedVerificationMethodException;
import com.careercoach.tasks.repository.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskTypeDefinitionService taskTypeDefinitionService;
    private final GamificationService gamificationService;

    @Transactional(readOnly = true)
    public Task get(Long id) {
        return findOrThrow(id);
    }

    public Task start(Long id) {
        Task task = findOrThrow(id);
        if (task.getState() != TaskState.TODO) {
            throw new IllegalTaskStateTransitionException(id, "start", task.getState());
        }
        task.setState(TaskState.IN_PROGRESS);
        return taskRepository.save(task);
    }

    public Task submit(Long id, Long userId, String artifact) {
        Task task = findOrThrow(id);
        if (task.getState() == TaskState.DONE) {
            return task;
        }

        TaskTypeDefinition typeDefinition = taskTypeDefinitionService.get(task.getTypeKey());
        VerificationMethod method = typeDefinition.getVerificationMethod();

        if (method == VerificationMethod.HONOR_WITH_PROOF) {
            if (typeDefinition.isRequiresArtifact() && !StringUtils.hasText(artifact)) {
                throw new ArtifactRequiredException(id);
            }
            if (StringUtils.hasText(artifact)) {
                task.setArtifact(artifact);
            }
        } else if (method != VerificationMethod.HONOR) {
            throw new UnsupportedVerificationMethodException(method);
        }

        return awardAndComplete(task, userId, typeDefinition);
    }

    private Task awardAndComplete(Task task, Long userId, TaskTypeDefinition typeDefinition) {
        List<SkillAward> skillAwards = new ArrayList<>();
        for (String skillKey : task.getSkillKeys()) {
            skillAwards.add(new SkillAward(skillKey, typeDefinition.getExpBase()));
        }

        AwardCommand cmd = new AwardCommand(
                userId, task.getId(), null, task.getTypeKey(), task.getGoalId(), "task-submit", skillAwards);
        AwardResult result = gamificationService.award(cmd);

        task.setExpAwarded(result.totalGranted());
        task.setState(TaskState.DONE);
        return taskRepository.save(task);
    }

    private Task findOrThrow(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }
}
