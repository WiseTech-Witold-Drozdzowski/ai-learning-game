package com.careercoach.goals.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.service.GoalService;
import com.careercoach.goals.web.model.GoalCreateRequest;
import com.careercoach.goals.web.model.GoalNode;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Goal create(@Valid @RequestBody GoalCreateRequest req) {
        return goalService.createStrategic(req);
    }

    @GetMapping
    public List<GoalNode> tree() {
        return goalService.getTree();
    }

    @PostMapping("/{id}/accept")
    public Goal accept(@PathVariable Long id) {
        return goalService.accept(id);
    }

    @PostMapping("/{id}/close")
    public Goal close(@PathVariable Long id) {
        return goalService.close(id);
    }
}
