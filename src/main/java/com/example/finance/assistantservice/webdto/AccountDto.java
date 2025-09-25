package com.example.finance.assistantservice.webdto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record AccountDto(
        String id,
        String name,
        String institution,
        String type,
        String currency,
        @JsonProperty("balanceCents") Long balanceCents,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) {}
