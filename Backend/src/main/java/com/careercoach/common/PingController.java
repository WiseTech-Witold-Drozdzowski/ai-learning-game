package com.careercoach.common;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trivial sanity-check endpoint confirming the web layer comes up.
 * Temporary — to be removed once real controllers exist (BACKEND_DESIGN §7).
 */
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "app", "career-coach",
                "time", Instant.now().toString());
    }
}
