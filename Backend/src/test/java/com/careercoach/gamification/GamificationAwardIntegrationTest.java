package com.careercoach.gamification;

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
import com.careercoach.gamification.service.GamificationService;
import com.careercoach.gamification.service.LevelCurve;
import com.careercoach.gamification.service.SkillAward;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.domain.exception.GoalNotFoundException;
import com.careercoach.goals.repository.GoalRepository;

/**
 * End-to-end gamification engine (issue-5, BACKEND_DESIGN §2.5 / §5) against a real
 * Postgres (provided by {@code run-tests.sh}). Covers ledger writes, counter bubbling,
 * idempotency and clamping through the real {@link GamificationService}.
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
class GamificationAwardIntegrationTest {

    @Autowired
    private GamificationService gamificationService;

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
    private TaskTypeDefinitionService taskTypeDefinitionService;

    private User user;
    private Goal root;
    private Goal child;

    @BeforeEach
    void setUp() {
        // FK order: exp_event/skill reference skill_definition only, goal is self-referential
        // (cascading), career_profile references users.
        expEventRepository.deleteAll();
        skillRepository.deleteAll();
        goalRepository.deleteAll();
        careerProfileRepository.deleteAll();
        userRepository.deleteAll();

        // Other test classes (e.g. ConfigSeederIntegrationTest) edit HONOR_CHECK at runtime
        // and share this DB, so restore the seeded exp_base=10 this test depends on.
        taskTypeDefinitionService.upsert(
                "HONOR_CHECK", new TaskTypeUpsertRequest("Honor check", VerificationMethod.HONOR, 10, false, false));

        user = userRepository.save(new User("gamify@example.com", "sub-gamify", "Gamify"));
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
    void award_shouldPersistExpEventAndBubbleCounters() {
        // Arrange
        AwardCommand cmd = new AwardCommand(user.getId(), 1000L, 1L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 8)));

        // Act
        AwardResult result = gamificationService.award(cmd);

        // Assert
        assertThat(result.applied()).isTrue();
        List<ExpEvent> events = expEventRepository.findBySourceTaskId(1000L);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getSkillKey()).isEqualTo("JAVA");
        assertThat(events.get(0).getAmount()).isEqualTo(8L);

        Skill skill = skillRepository.findById("JAVA").orElseThrow();
        assertThat(skill.getExp()).isEqualTo(8L);
        assertThat(skill.getLevel()).isEqualTo(LevelCurve.levelForExp(8L));

        Goal reloadedChild = goalRepository.findById(child.getId()).orElseThrow();
        Goal reloadedRoot = goalRepository.findById(root.getId()).orElseThrow();
        assertThat(reloadedChild.getExpEarned()).isEqualTo(8L);
        assertThat(reloadedRoot.getExpEarned()).isEqualTo(8L);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isEqualTo(8L);
        assertThat(profile.getLevel()).isEqualTo(LevelCurve.levelForExp(8L));
    }

    @Test
    void award_calledTwiceWithSameAttempt_shouldNotDoubleEventsOrCounters() {
        // Arrange
        AwardCommand cmd = new AwardCommand(user.getId(), 2000L, 2L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 5)));

        // Act
        gamificationService.award(cmd);
        AwardResult secondResult = gamificationService.award(cmd);

        // Assert
        assertThat(secondResult.applied()).isFalse();
        assertThat(expEventRepository.findBySourceTaskId(2000L)).hasSize(1);

        Skill skill = skillRepository.findById("JAVA").orElseThrow();
        assertThat(skill.getExp()).isEqualTo(5L);

        Goal reloadedChild = goalRepository.findById(child.getId()).orElseThrow();
        assertThat(reloadedChild.getExpEarned()).isEqualTo(5L);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isEqualTo(5L);
    }

    @Test
    void award_shouldClampAmountToTypeExpBase_whenProposedAboveLimit() {
        // Arrange — seeded HONOR_CHECK has exp_base=10 (application.yml).
        AwardCommand cmd = new AwardCommand(user.getId(), 3000L, 3L, "HONOR_CHECK", child.getId(), "task-complete",
                List.of(new SkillAward("JAVA", 999)));

        // Act
        gamificationService.award(cmd);

        // Assert
        List<ExpEvent> events = expEventRepository.findBySourceTaskId(3000L);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAmount()).isEqualTo(10L);

        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isEqualTo(10L);
    }

    @Test
    void award_shouldRollBackLedgerAndCounters_whenGoalBubblingFailsMidTransaction() {
        // Arrange — a goalId that does not exist makes bubbling fail after the ledger write.
        Long missingGoalId = child.getId() + 999_999L;
        AwardCommand cmd = new AwardCommand(user.getId(), 4000L, 4L, "HONOR_CHECK", missingGoalId, "task-complete",
                List.of(new SkillAward("JAVA", 5)));

        // Act / Assert
        assertThatThrownBy(() -> gamificationService.award(cmd)).isInstanceOf(GoalNotFoundException.class);

        assertThat(expEventRepository.findBySourceTaskId(4000L)).isEmpty();
        assertThat(skillRepository.findById("JAVA")).isEmpty();
        CareerProfile profile = careerProfileRepository.findById(user.getId()).orElseThrow();
        assertThat(profile.getTotalExp()).isZero();
    }
}
