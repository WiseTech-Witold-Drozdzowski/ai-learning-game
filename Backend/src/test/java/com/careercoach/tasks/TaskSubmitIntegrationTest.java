package com.careercoach.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.config.web.TaskTypeUpsertRequest;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.repository.CareerProfileRepository;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.web.model.ProfileResponse;
import com.careercoach.gamification.web.model.SkillView;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.domain.exception.ArtifactRequiredException;
import com.careercoach.tasks.domain.exception.UnsupportedVerificationMethodException;
import com.careercoach.tasks.repository.TaskRepository;
import com.careercoach.tasks.service.TaskService;

/**
 * End-to-end HONOR/HONOR_WITH_PROOF submit loop (issue-6, BACKEND_DESIGN §2.3 / §5)
 * against a real Postgres (provided by {@code run-tests.sh}). Covers the full
 * start -> submit -> award -> profile/skills visibility path, idempotency, artifact
 * requirement and the unsupported-method guard, through the real {@link TaskService}.
 *
 * <p>Dummy OAuth2 client registration is supplied so the context starts once
 * {@code SecurityConfig} switches to {@code oauth2Login} in the implementation stage.
 * Red phase: the service still throws, so most assertions fail.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class TaskSubmitIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private ProfileQueryService profileQueryService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ExpEventRepository expEventRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillDefinitionRepository skillDefinitionRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private CareerProfileRepository careerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskTypeDefinitionService taskTypeDefinitionService;

    private User user;
    private Goal goal;

    @BeforeEach
    void setUp() {
        // FK order: exp_event/skill reference skill_definition only, task references goal
        // (cascading) and task_type_definition, goal is self-referential, career_profile
        // references users.
        expEventRepository.deleteAll();
        skillRepository.deleteAll();
        taskRepository.deleteAll();
        goalRepository.deleteAll();
        careerProfileRepository.deleteAll();
        userRepository.deleteAll();

        if (!skillDefinitionRepository.existsById("JAVA")) {
            skillDefinitionRepository.save(
                    SkillDefinition.builder().key("JAVA").displayName("Java").category("language").build());
        }

        taskTypeDefinitionService.upsert("HONOR_CHECK",
                new TaskTypeUpsertRequest("Honor check", VerificationMethod.HONOR, 10, false, false));
        taskTypeDefinitionService.upsert("HONOR_WITH_PROOF_CHECK",
                new TaskTypeUpsertRequest("Honor with proof", VerificationMethod.HONOR_WITH_PROOF, 20, false, true));
        taskTypeDefinitionService.upsert("DIALOG_CHECK",
                new TaskTypeUpsertRequest("AI dialog", VerificationMethod.AI_DIALOG, 15, false, false));

        user = userRepository.save(new User("submitter@example.com", "sub-submitter", "Submitter"));
        careerProfileRepository.save(new CareerProfile(user.getId(), 0L, 1, AvatarState.initial()));

        goal = goalRepository.save(Goal.builder()
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title("Goal")
                .description("desc")
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0L)
                .build());
    }

    private Task saveTask(String typeKey) {
        return taskRepository.save(Task.builder()
                .goalId(goal.getId())
                .typeKey(typeKey)
                .title("Task title")
                .description("desc")
                .state(TaskState.TODO)
                .skillKeys(List.of("JAVA"))
                .expAwarded(0L)
                .build());
    }

    @Test
    void submit_shouldCompleteHonorTaskAndBubbleExpVisibleThroughProfileAndSkills() {
        // Arrange
        Task task = saveTask("HONOR_CHECK");

        // Act
        Task started = taskService.start(task.getId());
        assertThat(started.getState()).isEqualTo(TaskState.IN_PROGRESS);
        Task done = taskService.submit(task.getId(), user.getId(), null);

        // Assert
        assertThat(done.getState()).isEqualTo(TaskState.DONE);
        assertThat(done.getExpAwarded()).isEqualTo(10L);
        assertThat(done.getVerificationJobId()).isNull();

        assertThat(expEventRepository.findBySourceTaskId(task.getId())).hasSize(1);

        Goal reloadedGoal = goalRepository.findById(goal.getId()).orElseThrow();
        assertThat(reloadedGoal.getExpEarned()).isEqualTo(10L);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isEqualTo(10L);

        ProfileResponse profileResponse = profileQueryService.getProfile(user.getId());
        assertThat(profileResponse.totalExp()).isEqualTo(10L);
        assertThat(profileResponse.skills())
                .anySatisfy(skill -> {
                    assertThat(skill.key()).isEqualTo("JAVA");
                    assertThat(skill.exp()).isEqualTo(10L);
                });

        List<SkillView> skills = profileQueryService.listSkills();
        assertThat(skills).anySatisfy(skill -> {
            assertThat(skill.key()).isEqualTo("JAVA");
            assertThat(skill.exp()).isEqualTo(10L);
        });
    }

    @Test
    void submit_calledTwice_shouldNotDoubleExpOrCorruptDoneState() {
        // Arrange
        Task task = saveTask("HONOR_CHECK");
        taskService.start(task.getId());

        // Act
        taskService.submit(task.getId(), user.getId(), null);
        Task secondSubmit = taskService.submit(task.getId(), user.getId(), null);

        // Assert
        assertThat(secondSubmit.getState()).isEqualTo(TaskState.DONE);
        assertThat(secondSubmit.getExpAwarded()).isEqualTo(10L);
        assertThat(expEventRepository.findBySourceTaskId(task.getId())).hasSize(1);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isEqualTo(10L);
    }

    @Test
    void submit_shouldRejectMissingArtifact_whenRequiresArtifact() {
        // Arrange
        Task task = saveTask("HONOR_WITH_PROOF_CHECK");
        taskService.start(task.getId());

        // Act / Assert
        assertThatThrownBy(() -> taskService.submit(task.getId(), user.getId(), null))
                .isInstanceOf(ArtifactRequiredException.class);

        assertThat(expEventRepository.findBySourceTaskId(task.getId())).isEmpty();
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TaskState.IN_PROGRESS);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isZero();
    }

    @Test
    void submit_shouldPersistArtifactAndAward_whenProofProvided() {
        // Arrange
        Task task = saveTask("HONOR_WITH_PROOF_CHECK");
        taskService.start(task.getId());

        // Act
        Task done = taskService.submit(task.getId(), user.getId(), "http://proof.example/shot.png");

        // Assert
        assertThat(done.getState()).isEqualTo(TaskState.DONE);
        assertThat(done.getArtifact()).isEqualTo("http://proof.example/shot.png");
        assertThat(done.getExpAwarded()).isEqualTo(20L);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isEqualTo(20L);

        Goal reloadedGoal = goalRepository.findById(goal.getId()).orElseThrow();
        assertThat(reloadedGoal.getExpEarned()).isEqualTo(20L);
    }

    @Test
    void submit_shouldRejectUnsupportedVerificationMethod_withoutAwardingOrCompletingTask() {
        // Arrange
        Task task = saveTask("DIALOG_CHECK");
        taskService.start(task.getId());

        // Act / Assert
        assertThatThrownBy(() -> taskService.submit(task.getId(), user.getId(), null))
                .isInstanceOf(UnsupportedVerificationMethodException.class);

        assertThat(expEventRepository.findBySourceTaskId(task.getId())).isEmpty();
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TaskState.IN_PROGRESS);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isZero();
    }
}
