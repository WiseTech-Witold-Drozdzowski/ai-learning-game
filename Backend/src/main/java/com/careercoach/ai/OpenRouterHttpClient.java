package com.careercoach.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP adapter for the {@link OpenRouterClient} port — calls OpenRouter's
 * OpenAI-compatible {@code /chat/completions} endpoint and returns the assistant
 * message content. Replaced by a stub/mock in tests.
 */
@Component
@RequiredArgsConstructor
public class OpenRouterHttpClient implements OpenRouterClient {

    private final RestClient openRouterRestClient;
    private final OpenRouterProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public OpenRouterCompletion complete(String prompt) {
        ChatRequest body = new ChatRequest(props.getModel(), List.of(new Message("user", prompt)), false);
        ChatResponse response = openRouterRestClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + props.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ChatResponse.class);

        String content = "";
        if (response != null && response.choices() != null && !response.choices().isEmpty()) {
            Message message = response.choices().get(0).message();
            if (message != null && message.content() != null) {
                content = message.content();
            }
        }
        return new OpenRouterCompletion(content);
    }

    @Override
    public void stream(String prompt, OpenRouterStreamListener listener) {
        ChatRequest body = new ChatRequest(props.getModel(), List.of(new Message("user", prompt)), true);
        try {
            openRouterRestClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((request, response) -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring("data:".length()).trim();
                                if (data.isEmpty()) {
                                    continue;
                                }
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                String token = extractDelta(data);
                                if (token != null && !token.isEmpty()) {
                                    listener.onToken(token);
                                }
                            }
                        }
                        return null;
                    });
            listener.onComplete();
        } catch (RuntimeException ex) {
            listener.onError(ex);
        }
    }

    /** Extract the incremental {@code choices[0].delta.content} of one SSE data chunk, or null. */
    private String extractDelta(String json) {
        try {
            StreamChunk chunk = objectMapper.readValue(json, StreamChunk.class);
            if (chunk.choices() != null && !chunk.choices().isEmpty()) {
                Delta delta = chunk.choices().get(0).delta();
                if (delta != null) {
                    return delta.content();
                }
            }
        } catch (RuntimeException ex) {
            // Ignore a malformed/partial chunk rather than aborting the whole stream.
        }
        return null;
    }

    private record ChatRequest(String model, List<Message> messages, boolean stream) {
    }

    private record Message(String role, String content) {
    }

    private record ChatResponse(List<Choice> choices) {
    }

    private record Choice(Message message) {
    }

    private record StreamChunk(List<StreamChoice> choices) {
    }

    private record StreamChoice(Delta delta) {
    }

    private record Delta(String content) {
    }
}
