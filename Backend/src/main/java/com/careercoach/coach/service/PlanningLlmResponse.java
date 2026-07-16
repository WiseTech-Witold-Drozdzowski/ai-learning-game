package com.careercoach.coach.service;

import java.util.List;

/**
 * Structured shape the coach expects back from the LLM. The port returns raw
 * content; the {@code coach} module parses it into this record (never free text).
 * {@code coachNotes} carries the autonomous memory operations the coach chose to make
 * during planning (issue-7) — applied through the {@link CoachNoteOp} tool.
 */
record PlanningLlmResponse(List<GoalItem> goals, List<TaskItem> tasks, List<CoachNoteOp> coachNotes) {

    record GoalItem(String title, String description) {
    }

    record TaskItem(String title, String description, String typeKey, List<String> skillKeys) {
    }
}
