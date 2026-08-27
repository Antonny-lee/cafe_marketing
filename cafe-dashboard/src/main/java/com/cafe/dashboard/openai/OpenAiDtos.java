package com.cafe.dashboard.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public class OpenAiDtos {

    public record ChatMessage(String role, String content) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            Map<String, String> response_format,
            Double temperature
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatChoice(ChatMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<ChatChoice> choices) {}

    /** Expected shape of the JSON the model returns (parsed from message.content). */
    public record InsightPayload(
            Double positive_ratio,
            Double negative_ratio,
            String word_summary,
            List<KeyPoint> key_points,
            List<InsightItem> insights,
            List<CompetitorComparison> competitor_comparisons
    ) {}

    public record InsightItem(String quote, String suggestion) {}

    public record KeyPoint(String icon, String text) {}

    public record CompetitorComparison(String rival_store_id, String strength, String difference) {}
}
