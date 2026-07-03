package com.careercoach.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.config.web.TaskTypeUpsertRequest;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.repository.CareerProfileRepository;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.gamification.service.AwardCommand;
import com.careercoach.gamification.service.GamificationService;
import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.service.SkillAward;
import com.careercoach.gamification.web.model.ExpEventView;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.repository.TaskRepository;

/**
 * End-to-end skill history tests (issue-7) against a real Postgres.
 * Covers ledger querying and filtering by skill.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class SkillHistoryIntegrationTest {

    @Autowired
    private GamificationService gamificationService;

    @Autowired
    private ProfileQueryService profileQueryService;

    @Autowired
    private ExpEventRepository expEventRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private CareerProfileRepository careerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskTypeDefinitionService taskTypeDefinitionService;

    private User user;
    private Goal root;
    private Goal child;

    @BeforeEach
    void setUp() {
        expEventRepository.deleteAll();
        taskRepository.deleteAll();
        skillRepository.deleteAll();
        goalRepository.deleteAll();
        careerProfileRepository.deleteAll();
        userRepository.deleteAll();

        taskTypeDefinitionService.upsert(
                "HONOR_CHECK", new TaskTypeUpsertRequest("Honor check", VerificationMethod.HONOR, 50, false, false));

        user = userRepository.save(new User("history@example.com", "sub-history", "History"));
        careerProfileRepository.save(new CareerProfile(user.getId(), 0L, 1, AvatarState.initial()));

        root = goalRepository.save(Goal.builder()
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title("Root goal")
                .description("desc")
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0L)
                .build());
        child = goalRepository.save(Goal.builder()
                .parentId(root.getId())
                .kind(GoalKind.LEVEL)
                .title("Child goal")
                .description("desc")
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0L)
                .build());
    }

    @Test
    void listSkillHistory_shouldReturnOnlyThatSkillsEventsChronologically() {
        // Arrange: award tasks touching JAVA and SPRING
        Task task1 = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task 1").state(TaskState.TODO).expAwarded(0L)
                .build());

        Task task2 = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task 2").state(TaskState.TODO).expAwarded(0L)
                .build());

        Task task3 = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task 3").state(TaskState.TODO).expAwarded(0L)
                .build());

        AwardCommand cmd1 = new AwardCommand(user.getId(), task1.getId(), 1L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        AwardCommand cmd2 = new AwardCommand(user.getId(), task2.getId(), 2L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("SPRING", 20)));
        AwardCommand cmd3 = new AwardCommand(user.getId(), task3.getId(), 3L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 15)));

        gamificationService.award(cmd1);
        gamificationService.award(cmd2);
        gamificationService.award(cmd3);

        // Act
        List<ExpEventView> javaHistory = profileQueryService.listSkillHistory("JAVA");

        // Assert
        assertThat(javaHistory).hasSize(2);
        assertThat(javaHistory.get(0).sourceTaskId()).isEqualTo(task1.getId());
        assertThat(javaHistory.get(0).amount()).isEqualTo(10L);
        assertThat(javaHistory.get(1).sourceTaskId()).isEqualTo(task3.getId());
        assertThat(javaHistory.get(1).amount()).isEqualTo(15L);
        // Verify chronological order (ordering is pinned by sourceTaskId above; timestamps may
        // collide under sub-microsecond execution, so allow equality here to avoid a flake).
        assertThat(javaHistory.get(0).createdAt()).isBeforeOrEqualTo(javaHistory.get(1).createdAt());
    }

    @Test
    void listSkillHistory_shouldReturnEmpty_whenSkillHasNoLedgerEvents() {
        // Arrange: award only JAVA, query for SPRING
        Task task = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task").state(TaskState.TODO).expAwarded(0L)
                .build());

        AwardCommand cmd = new AwardCommand(user.getId(), task.getId(), 1L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        gamificationService.award(cmd);

        // Act
        List<ExpEventView> springHistory = profileQueryService.listSkillHistory("SPRING");

        // Assert
        assertThat(springHistory).isEmpty();
    }
}
