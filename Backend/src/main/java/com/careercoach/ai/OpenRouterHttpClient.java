package com.careercoach.ai;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

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

    @Override
    public OpenRouterCompletion complete(String prompt) {
        ChatRequest body = new ChatRequest(props.getModel(), List.of(new Message("user", prompt)));
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

    private record ChatRequest(String model, List<Message> messages) {
    }

    private record Message(String role, String content) {
    }

    private record ChatResponse(List<Choice> choices) {
    }

    private record Choice(Message message) {
    }
}
