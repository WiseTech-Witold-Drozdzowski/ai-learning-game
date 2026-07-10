package com.careercoach.jobs;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Global SSE fan-out for job status events. */
@Component
public class SseHub {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Register a new emitter; it is pruned on completion/timeout/error. */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /** Send a named {@code job-status} event to all subscribers; no-op when none. */
    public void emit(JobStatusEvent event) {
        send(SseEventName.JOB_STATUS, event);
    }

    /** Send a named {@code exp-gain} event to all subscribers; no-op when none. */
    public void emit(ExpGainEvent event) {
        send(SseEventName.EXP_GAIN, event);
    }

    /** Send a named {@code level-up} event to all subscribers; no-op when none. */
    public void emit(LevelUpEvent event) {
        send(SseEventName.LEVEL_UP, event);
    }

    private void send(SseEventName name, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(name.wireName()).data(data));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(emitter);
            }
        }
    }
}
