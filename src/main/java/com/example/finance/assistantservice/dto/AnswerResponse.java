// src/main/java/com/example/finance/assistantservice/dto/AnswerResponse.java
package com.example.finance.assistantservice.dto;

import java.util.List;
import java.util.Map;

public record AnswerResponse(
        String query,
        String answer,
        List<Result> results,
        Integer topK
) {
    public record Result(String id, String text, double score, Map<String,Object> metadata){}
}
