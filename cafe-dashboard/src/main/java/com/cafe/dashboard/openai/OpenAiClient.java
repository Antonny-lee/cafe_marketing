package com.cafe.dashboard.openai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiClient {

    private final RestClient restClient;
    private final String model;

    public OpenAiClient(@Value("${openai.base-url}") String baseUrl,
                         @Value("${openai.api-key}") String apiKey,
                         @Value("${openai.model}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    public String chatJson(String systemPrompt, String userPrompt) {
        OpenAiDtos.ChatRequest request = new OpenAiDtos.ChatRequest(
                model,
                List.of(
                        new OpenAiDtos.ChatMessage("system", systemPrompt),
                        new OpenAiDtos.ChatMessage("user", userPrompt)
                ),
                Map.of("type", "json_object"),
                0.3
        );

        return call(request);
    }

    public String chat(List<OpenAiDtos.ChatMessage> messages) {
        OpenAiDtos.ChatRequest request = new OpenAiDtos.ChatRequest(model, messages, null, 0.5);
        return call(request);
    }

    private String call(OpenAiDtos.ChatRequest request) {
        OpenAiDtos.ChatResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(OpenAiDtos.ChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI 응답이 비어있습니다.");
        }
        return response.choices().get(0).message().content();
    }
}
