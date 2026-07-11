package com.careercoach.coach.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careercoach.coach.service.MockSessionService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import com.careercoach.coach.web.model.MockFinishResponse;
import com.careercoach.coach.web.model.MockMessageRequest;
import com.careercoach.coach.web.model.MockSessionView;

/**
 * Mock-interview endpoints (issue-6, BACKEND_DESIGN §7). {@code start} opens a session and
 * streams the coach's opening question; {@code messages} streams each turn token-by-token
 * (reusing the issue-3 SSE seam); {@code finish} enqueues the grading EVALUATION job; the
 * {@code GET} exposes the persisted transcript for review (prefix via {@code ApiPrefixWebConfig}).
 */
@RestController
@RequiredArgsConstructor
public class MockController {

    private final MockSessionService mockSessionService;

    @PostMapping("/tasks/{id}/mock/start")
    public SseEmitter start(@PathVariable Long id) {
        return mockSessionService.start(id);
    }

    @PostMapping("/mock/{sessionId}/messages")
    public SseEmitter messages(@PathVariable Long sessionId, @Valid @RequestBody MockMessageRequest request) {
        return mockSessionService.respond(sessionId, request.message());
    }

    @PostMapping("/mock/{sessionId}/finish")
    public MockFinishResponse finish(@PathVariable Long sessionId) {
        return new MockFinishResponse(mockSessionService.finish(sessionId));
    }

    @GetMapping("/mock/{sessionId}")
    public MockSessionView view(@PathVariable Long sessionId) {
        return MockSessionView.from(
                mockSessionService.getSession(sessionId),
                mockSessionService.transcript(sessionId));
    }
}
