package com.careercoach.coach.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.careercoach.coach.domain.CoachNote;
import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.service.SkillDefinitionService;
import com.careercoach.gamification.service.CareerProfileService;
import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.web.model.SkillView;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.service.GoalService;
import com.careercoach.goals.web.model.GoalNode;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.service.TaskService;

import lombok.RequiredArgsConstructor;

/**
 * Builds the coach prompt from hard DB state (BACKEND_DESIGN §6): profile +
 * strategic/target goal, the active goal tree, skill levels, the available
 * {@code SkillDefinition} catalog (the coach chooses from it, never invents),
 * the last N completed tasks and the active {@code coach_notes} (issue-7 — the coach's
 * durable memory). Inactive notes and mock transcripts are deliberately NOT included.
 */
@Service
@RequiredArgsConstructor
public class ContextAssembler {

    static final int RECENT_TASK_LIMIT = 5;

    private final GoalService goalService;
    private final CareerProfileService careerProfileService;
    private final ProfileQueryService profileQueryService;
    private final SkillDefinitionService skillDefinitionService;
    private final TaskService taskService;
    private final CoachNoteService coachNoteService;

    /** Assemble the full context prompt for planning around {@code goalId}. */
    public String assemble(Long goalId) {
        Goal target = goalService.get(goalId);
        StringBuilder sb = new StringBuilder();
        sb.append("# Coach planning context\n\n");

        sb.append("## Profile\n");
        careerProfileService.getSingle().ifPresentOrElse(
                p -> sb.append("Level ").append(p.getLevel())
                        .append(", total exp ").append(p.getTotalExp()).append('\n'),
                () -> sb.append("(no profile yet)\n"));
        sb.append('\n');

        sb.append("## Target goal\n");
        sb.append('[').append(target.getState()).append("] ").append(target.getTitle());
        if (target.getDescription() != null) {
            sb.append(" — ").append(target.getDescription());
        }
        sb.append("\n\n");

        sb.append("## Goal tree\n");
        List<GoalNode> tree = goalService.getTree();
        if (tree.isEmpty()) {
            sb.append("(empty)\n");
        } else {
            tree.forEach(node -> appendNode(sb, node, 0));
        }
        sb.append('\n');

        sb.append("## Skill levels\n");
        List<SkillView> skills = profileQueryService.listSkills();
        if (skills.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (SkillView skill : skills) {
                sb.append("- ").append(skill.displayName()).append(" (").append(skill.key())
                        .append("): level ").append(skill.level())
                        .append(", exp ").append(skill.exp()).append('\n');
            }
        }
        sb.append('\n');

        sb.append("## Available skills (choose from this catalog)\n");
        List<SkillDefinition> defs = skillDefinitionService.list();
        if (defs.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (SkillDefinition def : defs) {
                sb.append("- ").append(def.getKey()).append(": ").append(def.getDisplayName())
                        .append(" [").append(def.getCategory()).append("]\n");
            }
        }
        sb.append('\n');

        sb.append("## Recent completed tasks\n");
        List<Task> recent = taskService.recentCompleted();
        if (recent.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (Task task : recent) {
                sb.append("- ").append(task.getTitle()).append('\n');
            }
        }
        sb.append('\n');

        // Only ACTIVE notes enter the prompt (issue-7); inactive notes and mock transcripts
        // are deliberately NOT assembled here.
        sb.append("## Coach notes\n");
        List<CoachNote> notes = coachNoteService.listActive();
        if (notes.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (CoachNote note : notes) {
                sb.append("- ").append(note.getContent()).append('\n');
            }
        }

        return sb.toString();
    }

    private void appendNode(StringBuilder sb, GoalNode node, int depth) {
        sb.append("  ".repeat(depth)).append("- [").append(node.state()).append("] ")
                .append(node.title()).append('\n');
        node.children().forEach(child -> appendNode(sb, child, depth + 1));
    }
}
