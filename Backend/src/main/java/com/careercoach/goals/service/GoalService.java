package com.careercoach.goals.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.domain.exception.GoalInvariantViolationException;
import com.careercoach.goals.domain.exception.GoalNotFoundException;
import com.careercoach.goals.domain.exception.IllegalGoalStateTransitionException;
import com.careercoach.goals.repository.GoalRepository;
import com.careercoach.goals.web.model.GoalCreateRequest;
import com.careercoach.goals.web.model.GoalNode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;

    public Goal createStrategic(GoalCreateRequest req) {
        if (req.parentId() != null) {
            throw new GoalInvariantViolationException("A STRATEGIC goal must not have a parentId");
        }
        Goal goal = Goal.builder()
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title(req.title())
                .description(req.description())
                .state(GoalState.PROPOSED)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex((int) goalRepository.countByParentIdIsNull())
                .expEarned(0L)
                .build();
        return goalRepository.save(goal);
    }

    @Transactional(readOnly = true)
    public List<GoalNode> getTree() {
        List<Goal> goals = goalRepository.findAllByOrderByOrderIndexAscIdAsc();
        Map<Long, List<Goal>> byParent = goals.stream()
                .filter(g -> g.getParentId() != null)
                .collect(Collectors.groupingBy(Goal::getParentId));
        List<GoalNode> roots = new ArrayList<>();
        for (Goal goal : goals) {
            if (goal.getParentId() == null) {
                roots.add(buildNode(goal, byParent));
            }
        }
        return roots;
    }

    public Goal accept(Long id) {
        Goal goal = goalRepository.findById(id)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + id));
        if (goal.getState() != GoalState.PROPOSED) {
            throw new IllegalGoalStateTransitionException(
                    "Cannot accept goal " + id + " in state " + goal.getState());
        }
        goal.setState(GoalState.ACTIVE);
        return goalRepository.save(goal);
    }

    public Goal close(Long id) {
        Goal goal = goalRepository.findById(id)
                .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + id));
        if (goal.getState() != GoalState.ACTIVE) {
            throw new IllegalGoalStateTransitionException(
                    "Cannot close goal " + id + " in state " + goal.getState());
        }
        goal.setState(GoalState.CLOSED);
        return goalRepository.save(goal);
    }

    private GoalNode buildNode(Goal goal, Map<Long, List<Goal>> byParent) {
        List<GoalNode> children = new ArrayList<>();
        for (Goal child : byParent.getOrDefault(goal.getId(), List.of())) {
            children.add(buildNode(child, byParent));
        }
        return new GoalNode(
                goal.getId(),
                goal.getParentId(),
                goal.getKind(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getState(),
                goal.getCreatedBy(),
                goal.getOrderIndex(),
                goal.getExpEarned(),
                children);
    }
}
