package com.careercoach.goals.web.model;

import java.util.List;

import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;

public record GoalNode(
        Long id,
        Long parentId,
        GoalKind kind,
        String title,
        String description,
        GoalState state,
        GoalCreatedBy createdBy,
        int orderIndex,
        long expEarned,
        List<GoalNode> children
) {
}
