package com.careercoach.coach;

import java.util.List;

/**
 * Structured shape the coach expects back from the LLM. The port returns raw
 * content; the {@code coach} module parses it into this record (never free text).
 */
record PlanningLlmResponse(List<GoalItem> goals, List<TaskItem> tasks) {

    record GoalItem(String title, String description) {
    }

    record TaskItem(String title, String description, String typeKey, List<String> skillKeys) {
    }
}
