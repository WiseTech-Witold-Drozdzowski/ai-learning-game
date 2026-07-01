package com.careercoach.goals.domain;

/**
 * Goal kind. STRATEGIC = root goal (parentId null); LEVEL = nested under a parent.
 */
public enum GoalKind {
    STRATEGIC,
    LEVEL
}
