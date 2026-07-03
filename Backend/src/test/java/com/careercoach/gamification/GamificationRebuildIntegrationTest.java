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
import com.careercoach.gamification.domain.ExpEvent;
import com.careercoach.gamification.domain.Skill;
import com.careercoach.gamification.repository.CareerProfileRepository;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.gamification.service.AwardCommand;
import com.careercoach.gamification.service.AwardResult;
import com.careercoach.gamification.service.GamificationRebuildService;
import com.careercoach.gamification.service.GamificationService;
import com.careercoach.gamification.service.LevelCurve;
import com.careercoach.gamification.service.RebuildResult;
import com.careercoach.gamification.service.SkillAward;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.repository.TaskRepository;

/**
 * End-to-end rebuild tests (issue-7) against a real Postgres (provided by {@code run-tests.sh}).
 * Covers ledger replay, counter recomputation, idempotency, and level restoration from curve.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class GamificationRebuildIntegrationTest {

    @Autowired
    private GamificationService gamificationService;

    @Autowired
    private GamificationRebuildService rebuildService;

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
        // FK order: exp_event/skill reference skill_definition only, goal is self-referential
        // (cascading), career_profile references users, tasks reference goals.
        expEventRepository.deleteAll();
        taskRepository.deleteAll();
        skillRepository.deleteAll();
        goalRepository.deleteAll();
        careerProfileRepository.deleteAll();
        userRepository.deleteAll();

        // Other test classes (e.g. ConfigSeederIntegrationTest) edit HONOR_CHECK at runtime
        // and share this DB, so restore the seeded exp_base=10 this test depends on.
        taskTypeDefinitionService.upsert(
                "HONOR_CHECK", new TaskTypeUpsertRequest("Honor check", VerificationMethod.HONOR, 10, false, false));

        user = userRepository.save(new User("rebuild@example.com", "sub-rebuild", "Rebuild"));
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
    void rebuild_shouldReproduceCountersIdenticalToIncremental_whenBalanceRulesUnchanged() {
        // Arrange: award multiple tasks to populate the ledger and counters incrementally
        Task task1 = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task 1").state(TaskState.TODO).expAwarded(0L)
                .build());

        Task task2 = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task 2").state(TaskState.TODO).expAwarded(0L)
                .build());

        AwardCommand cmd1 = new AwardCommand(user.getId(), task1.getId(), 1L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 8)));
        AwardCommand cmd2 = new AwardCommand(user.getId(), task2.getId(), 2L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 12)));

        gamificationService.award(cmd1);
        gamificationService.award(cmd2);

        // Snapshot the incremental counters
        Skill javaSkillBefore = skillRepository.findById("JAVA").orElseThrow();
        Goal childBefore = goalRepository.findById(child.getId()).orElseThrow();
        Goal rootBefore = goalRepository.findById(root.getId()).orElseThrow();
        CareerProfile profileBefore = careerProfileRepository.findById(user.getId()).orElseThrow();

        // Act: rebuild from the ledger
        RebuildResult result = rebuildService.rebuild(user.getId());

        // Assert: counters match the snapshot
        Skill javaSkillAfter = skillRepository.findById("JAVA").orElseThrow();
        assertThat(javaSkillAfter.getExp()).isEqualTo(javaSkillBefore.getExp());
        assertThat(javaSkillAfter.getLevel()).isEqualTo(javaSkillBefore.getLevel());

        Goal childAfter = goalRepository.findById(child.getId()).orElseThrow();
        assertThat(childAfter.getExpEarned()).isEqualTo(childBefore.getExpEarned());

        Goal rootAfter = goalRepository.findById(root.getId()).orElseThrow();
        assertThat(rootAfter.getExpEarned()).isEqualTo(rootBefore.getExpEarned());

        CareerProfile profileAfter = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profileAfter.getTotalExp()).isEqualTo(profileBefore.getTotalExp());
        assertThat(profileAfter.getLevel()).isEqualTo(profileBefore.getLevel());
    }

    @Test
    void rebuild_shouldBeIdempotent_whenRunTwice() {
        // Arrange: award to populate ledger
        Task task = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task").state(TaskState.TODO).expAwarded(0L)
                .build());

        AwardCommand cmd = new AwardCommand(user.getId(), task.getId(), 1L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 30)));
        gamificationService.award(cmd);

        // Act: rebuild once
        rebuildService.rebuild(user.getId());

        // Snapshot after first rebuild
        Skill skillAfterFirst = skillRepository.findById("JAVA").orElseThrow();
        CareerProfile profileAfterFirst = careerProfileRepository.findById(user.getId()).orElseThrow();

        // Act: rebuild again
        rebuildService.rebuild(user.getId());

        // Assert: second rebuild produces identical counters
        Skill skillAfterSecond = skillRepository.findById("JAVA").orElseThrow();
        assertThat(skillAfterSecond.getExp()).isEqualTo(skillAfterFirst.getExp());
        assertThat(skillAfterSecond.getLevel()).isEqualTo(skillAfterFirst.getLevel());

        CareerProfile profileAfterSecond = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profileAfterSecond.getTotalExp()).isEqualTo(profileAfterFirst.getTotalExp());
        assertThat(profileAfterSecond.getLevel()).isEqualTo(profileAfterFirst.getLevel());
    }

    @Test
    void rebuild_shouldNotCreateExpEvents() {
        // Arrange: award to populate ledger
        Task task = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task").state(TaskState.TODO).expAwarded(0L)
                .build());

        AwardCommand cmd = new AwardCommand(user.getId(), task.getId(), 1L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 30)));
        gamificationService.award(cmd);

        long eventCountBefore = expEventRepository.count();

        // Act
        rebuildService.rebuild(user.getId());

        // Assert
        long eventCountAfter = expEventRepository.count();
        assertThat(eventCountAfter).isEqualTo(eventCountBefore);
    }

    @Test
    void rebuild_shouldRecomputeLevelPerCurrentCurve_whenStoredLevelCorrupted() {
        // Arrange: award to populate ledger
        Task task = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task").state(TaskState.TODO).expAwarded(0L)
                .build());

        AwardCommand cmd = new AwardCommand(user.getId(), task.getId(), 1L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 50)));
        gamificationService.award(cmd);

        // Manually corrupt the levels
        Skill java = skillRepository.findById("JAVA").orElseThrow();
        java.setLevel(99);
        skillRepository.save(java);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        profile.setLevel(99);
        careerProfileRepository.save(profile);

        // Act: rebuild should restore levels from curve
        rebuildService.rebuild(user.getId());

        // Assert
        Skill javaRestored = skillRepository.findById("JAVA").orElseThrow();
        assertThat(javaRestored.getLevel()).isEqualTo(LevelCurve.levelForExp(50L));

        CareerProfile profileRestored = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profileRestored.getLevel()).isEqualTo(LevelCurve.levelForExp(50L));
    }

    @Test
    void rebuild_shouldZeroAllCountersFromLedger_whenLedgerEmptied() {
        // Arrange: award to populate ledger and counters
        Task task = taskRepository.save(Task.builder()
                .goalId(child.getId()).typeKey("HONOR_CHECK").title("Task").state(TaskState.TODO).expAwarded(0L)
                .build());

        AwardCommand cmd = new AwardCommand(user.getId(), task.getId(), 1L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 50)));
        gamificationService.award(cmd);

        // Verify counters are non-zero
        assertThat(skillRepository.findById("JAVA").orElseThrow().getExp()).isGreaterThan(0);

        // Clear the ledger
        expEventRepository.deleteAll();

        // Act: rebuild from empty ledger
        rebuildService.rebuild(user.getId());

        // Assert
        Skill java = skillRepository.findById("JAVA").orElseThrow();
        assertThat(java.getExp()).isEqualTo(0L);
        assertThat(java.getLevel()).isEqualTo(1);

        Goal childAfter = goalRepository.findById(child.getId()).orElseThrow();
        assertThat(childAfter.getExpEarned()).isEqualTo(0L);

        Goal rootAfter = goalRepository.findById(root.getId()).orElseThrow();
        assertThat(rootAfter.getExpEarned()).isEqualTo(0L);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isEqualTo(0L);
        assertThat(profile.getLevel()).isEqualTo(1);
    }
}
