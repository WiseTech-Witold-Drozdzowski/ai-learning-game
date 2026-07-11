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
import com.careercoach.tasks.domain.Quiz;
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
    private final AiVerificationLauncher aiVerificationLauncher;

    @Transactional(readOnly = true)
    public Task get(Long id) {
        return findOrThrow(id);
    }

    /** Most recently updated completed tasks — context for the coach (BACKEND_DESIGN §6). */
    @Transactional(readOnly = true)
    public List<Task> recentCompleted() {
        return taskRepository.findTop5ByStateOrderByUpdatedAtDesc(TaskState.DONE);
    }

    /** Create a task in {@code TODO} under {@code goalId} (used when task proposals are accepted). */
    public Task createTodo(Long goalId, String title, String description, String typeKey,
                           List<String> skillKeys) {
        Task task = Task.builder()
                .goalId(goalId)
                .typeKey(typeKey)
                .title(title)
                .description(description)
                .state(TaskState.TODO)
                .skillKeys(skillKeys == null ? new ArrayList<>() : new ArrayList<>(skillKeys))
                .expAwarded(0L)
                .build();
        return taskRepository.save(task);
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
        return submit(id, userId, artifact, null);
    }

    public Task submit(Long id, Long userId, String artifact, List<String> answers) {
        Task task = findOrThrow(id);
        if (task.getState() == TaskState.DONE) {
            return task;
        }

        TaskTypeDefinition typeDefinition = taskTypeDefinitionService.get(task.getTypeKey());
        VerificationMethod method = typeDefinition.getVerificationMethod();

        // AI verification is asynchronous: enqueue an EVALUATION job and wait IN_PROGRESS.
        // The grade, award and terminal state arrive when the job finishes (see EvaluationJobHandler).
        if (method == VerificationMethod.AI_ARTIFACT_REVIEW) {
            return launchEvaluation(task, artifact, null);
        }

        // AUTO_QUIZ is graded deterministically against the stored answer key inside the
        // EVALUATION job; the user's answers ride along as the material to grade.
        if (method == VerificationMethod.AUTO_QUIZ) {
            return launchEvaluation(task, null, answers);
        }

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

    private Task launchEvaluation(Task task, String artifact, List<String> answers) {
        if (StringUtils.hasText(artifact)) {
            task.setArtifact(artifact);
        }
        Long jobId = aiVerificationLauncher.launchEvaluation(task, artifact, answers);
        task.setVerificationJobId(jobId);
        task.setState(TaskState.IN_PROGRESS);
        return taskRepository.save(task);
    }

    /** Persist an AI-generated quiz + answer key onto an AUTO_QUIZ task (issue-5). */
    public Task saveQuiz(Long taskId, Quiz quiz) {
        Task task = findOrThrow(taskId);
        task.setQuiz(quiz);
        return taskRepository.save(task);
    }

    /** Record an EVALUATION outcome: set granted exp + terminal state (backend-only, never AI/client). */
    public Task recordEvaluation(Long taskId, boolean passed, long expAwarded) {
        Task task = findOrThrow(taskId);
        task.setExpAwarded(expAwarded);
        task.setState(passed ? TaskState.DONE : TaskState.REJECTED);
        return taskRepository.save(task);
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

    @Transactional(readOnly = true)
    public Long getGoalId(Long taskId) {
        return findOrThrow(taskId).getGoalId();
    }
}
