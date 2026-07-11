package com.careercoach.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Unit tests for {@link SseHub} (issue-1) — global SSE fan-out. {@code ResponseBodyEmitter.Handler}
 * is package-private in Spring, so emitters are wired to a recording/failing dynamic proxy for that
 * type via reflection, letting sends be observed without a real servlet response.
 */
class SseHubTest {

    private static final Class<?> HANDLER_TYPE = resolveHandlerType();

    private static Class<?> resolveHandlerType() {
        try {
            return Class.forName(
                    "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void initialize(SseEmitter emitter, Object handler) throws Exception {
        Method initialize = SseEmitter.class.getSuperclass().getDeclaredMethod("initialize", HANDLER_TYPE);
        initialize.setAccessible(true);
        initialize.invoke(emitter, handler);
    }

    private static Object recordingHandler(List<Object> sent) {
        return Proxy.newProxyInstance(HANDLER_TYPE.getClassLoader(), new Class<?>[] {HANDLER_TYPE},
                (proxy, method, args) -> {
                    if ("send".equals(method.getName())) {
                        // SseEmitter renders each send as a Set<DataWithMediaType> SSE frame;
                        // unwrap so the raw payload objects (not the frame wrappers) are recorded.
                        Object arg = args[0];
                        if (arg instanceof Collection<?> items) {
                            for (Object item : items) {
                                sent.add(item instanceof DataWithMediaType dwmt ? dwmt.getData() : item);
                            }
                        } else {
                            sent.add(arg);
                        }
                    }
                    return null;
                });
    }

    private static Object failingHandler() {
        return Proxy.newProxyInstance(HANDLER_TYPE.getClassLoader(), new Class<?>[] {HANDLER_TYPE},
                (proxy, method, args) -> {
                    if ("send".equals(method.getName())) {
                        throw new IOException("boom");
                    }
                    return null;
                });
    }

    @Test
    void emit_shouldSendEvent_toRegisteredEmitter() throws Exception {
        // Arrange
        SseHub hub = new SseHub();
        SseEmitter emitter = hub.register();
        List<Object> sent = new ArrayList<>();
        initialize(emitter, recordingHandler(sent));
        JobStatusEvent event = new JobStatusEvent(1L, JobType.ECHO, JobStatus.DONE);

        // Act
        hub.emit(event);

        // Assert
        assertThat(sent).contains(event);
    }

    @Test
    void emit_shouldBeNoOp_whenNoSubscribers() {
        // Arrange
        SseHub hub = new SseHub();

        // Act / Assert
        assertThatCode(() -> hub.emit(new JobStatusEvent(1L, JobType.ECHO, JobStatus.DONE)))
                .doesNotThrowAnyException();
    }

    @Test
    void emit_shouldRemoveFailingEmitter_andStillDeliverToOthers() throws Exception {
        // Arrange
        SseHub hub = new SseHub();
        SseEmitter failing = hub.register();
        initialize(failing, failingHandler());
        SseEmitter healthy = hub.register();
        List<Object> sent = new ArrayList<>();
        initialize(healthy, recordingHandler(sent));
        JobStatusEvent event = new JobStatusEvent(1L, JobType.ECHO, JobStatus.DONE);

        // Act / Assert
        assertThatCode(() -> hub.emit(event)).doesNotThrowAnyException();
        assertThat(sent).contains(event);
    }
}
