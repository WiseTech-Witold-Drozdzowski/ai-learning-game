package com.careercoach.coach.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careercoach.coach.service.CoachChatService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import com.careercoach.coach.web.model.CoachMessageRequest;

/**
 * {@code POST /api/coach/messages} — strategic chat with the coach, replying over SSE
 * token-by-token (prefix applied via {@code ApiPrefixWebConfig}).
 */
@RestController
@RequestMapping("/coach")
@RequiredArgsConstructor
public class CoachController {

    private final CoachChatService coachChatService;

    @PostMapping("/messages")
    public SseEmitter messages(@Valid @RequestBody CoachMessageRequest request) {
        return coachChatService.streamReply(request);
    }
}
