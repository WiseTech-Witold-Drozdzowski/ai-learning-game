package com.careercoach.jobs.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careercoach.jobs.SseHub;

import lombok.RequiredArgsConstructor;

/** GET /api/events — global SSE stream (prefix applied via ApiPrefixWebConfig). */
@RestController
@RequiredArgsConstructor
public class EventController {

    private final SseHub sseHub;

    @GetMapping("/events")
    public SseEmitter events() {
        return sseHub.register();
    }
}
