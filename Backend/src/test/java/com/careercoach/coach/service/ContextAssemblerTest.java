package com.careercoach.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.coach.domain.CoachNote;
import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.service.SkillDefinitionService;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.service.CareerProfileService;
import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.web.model.SkillView;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.service.GoalService;
import com.careercoach.goals.web.model.GoalNode;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.service.TaskService;

/**
 * Unit test for {@link ContextAssembler} (BACKEND_DESIGN §6). Verifies the prompt
 * is assembled from every state section — including the available
 * {@code SkillDefinition} catalog — and never leaks mock transcripts. Red phase:
 * {@code assemble} throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class ContextAssemblerTest {

    @Mock
    private GoalService goalService;

    @Mock
    private CareerProfileService careerProfileService;

    @Mock
    private ProfileQueryService profileQueryService;

    @Mock
    private SkillDefinitionService skillDefinitionService;

    @Mock
    private TaskService taskService;

    @Mock
    private CoachNoteService coachNoteService;

    @InjectMocks
    private ContextAssembler assembler;

    private static Goal goal(Long id, String title, GoalState state) {
        return Goal.builder()
                .id(id)
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title(title)
                .description("desc")
                .state(state)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0L)
                .build();
    }

    private static GoalNode node(Long id, String title, GoalState state) {
        return GoalNode.builder()
                .id(id)
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title(title)
                .description("desc")
                .state(state)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0L)
                .children(List.of())
                .build();
    }

    private static Task doneTask(String title) {
        return Task.builder()
                .id(1L)
                .goalId(7L)
                .typeKey("HONOR_CHECK")
                .title(title)
                .description("desc")
                .state(TaskState.DONE)
                .skillKeys(List.of("JAVA"))
                .expAwarded(10L)
                .build();
    }

    @Test
    void assemble_shouldIncludeAllStateSections_whenCalled() {
        // Arrange
        when(goalService.get(7L)).thenReturn(goal(7L, "Behavioural preparation", GoalState.ACTIVE));
        when(goalService.getTree()).thenReturn(List.of(node(7L, "Behavioural preparation", GoalState.ACTIVE)));
        when(careerProfileService.getSingle())
                .thenReturn(Optional.of(new CareerProfile(1L, 340L, 5, AvatarState.initial())));
        when(profileQueryService.listSkills())
                .thenReturn(List.of(new SkillView("JAVA", "Java", 3, 120L)));
        when(skillDefinitionService.list()).thenReturn(List.of(
                SkillDefinition.builder().key("COMMUNICATION").displayName("Communication").category("SOFT").build()));
        when(taskService.recentCompleted()).thenReturn(List.of(doneTask("Completed a mock interview")));

        // Act
        String prompt = assembler.assemble(7L);

        // Assert — every section of hard state is present
        assertThat(prompt).contains("Behavioural preparation");        // target / strategic goal + tree
        assertThat(prompt).contains("340");                            // profile total exp
        assertThat(prompt).contains("Java");                           // current skill levels
        assertThat(prompt).contains("COMMUNICATION").contains("Communication"); // available skill catalog
        assertThat(prompt).contains("Completed a mock interview");     // last N completed tasks
        assertThat(prompt.toLowerCase()).contains("coach notes");      // coach_notes section (empty seam)
    }

    @Test
    void assemble_shouldNotLeakMockTranscripts_whenCalled() {
        // Arrange — same hard state, no transcript source is ever consulted
        when(goalService.get(7L)).thenReturn(goal(7L, "Behavioural preparation", GoalState.ACTIVE));
        when(goalService.getTree()).thenReturn(List.of(node(7L, "Behavioural preparation", GoalState.ACTIVE)));
        when(careerProfileService.getSingle())
                .thenReturn(Optional.of(new CareerProfile(1L, 0L, 1, AvatarState.initial())));
        when(profileQueryService.listSkills()).thenReturn(List.of());
        when(skillDefinitionService.list()).thenReturn(List.of());
        when(taskService.recentCompleted()).thenReturn(List.of());

        // Act
        String prompt = assembler.assemble(7L);

        // Assert — mock transcripts are deliberately excluded (BACKEND_DESIGN §6)
        assertThat(prompt.toLowerCase()).doesNotContain("transcript");
    }

    @Test
    void assemble_shouldStillBuildPrompt_whenNoProfileProvisioned() {
        // Arrange — edge: no career profile yet
        when(goalService.get(7L)).thenReturn(goal(7L, "Behavioural preparation", GoalState.ACTIVE));
        when(goalService.getTree()).thenReturn(List.of());
        when(careerProfileService.getSingle()).thenReturn(Optional.empty());
        when(profileQueryService.listSkills()).thenReturn(List.of());
        when(skillDefinitionService.list()).thenReturn(List.of(
                SkillDefinition.builder().key("JAVA").displayName("Java").category("TECHNICAL").build()));
        when(taskService.recentCompleted()).thenReturn(List.of());

        // Act
        String prompt = assembler.assemble(7L);

        // Assert — catalog still present, no exception on missing profile
        assertThat(prompt).contains("JAVA");
        assertThat(prompt).contains("Behavioural preparation");
    }

    @Test
    void assemble_shouldIncludeActiveCoachNotes_whenPresent() {
        // Arrange — issue-7: the active coach notes fill the seam left in issue-2
        when(goalService.get(7L)).thenReturn(goal(7L, "Behavioural preparation", GoalState.ACTIVE));
        when(goalService.getTree()).thenReturn(List.of());
        when(careerProfileService.getSingle())
                .thenReturn(Optional.of(new CareerProfile(1L, 0L, 1, AvatarState.initial())));
        when(profileQueryService.listSkills()).thenReturn(List.of());
        when(skillDefinitionService.list()).thenReturn(List.of());
        when(taskService.recentCompleted()).thenReturn(List.of());
        when(coachNoteService.listActive()).thenReturn(List.of(
                CoachNote.builder().id(1L).content("Prefers hands-on over theory").active(true).build()));

        // Act
        String prompt = assembler.assemble(7L);

        // Assert — the active note's content is injected into the prompt
        assertThat(prompt).contains("Prefers hands-on over theory");
    }
}
