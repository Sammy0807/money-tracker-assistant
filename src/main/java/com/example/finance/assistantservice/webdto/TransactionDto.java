package com.example.finance.assistantservice.webdto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record TransactionDto(
        String id,
        @JsonProperty("accountId") String accountId,
        @JsonProperty("amountCents") Long amountCents,
        String currency,
        String merchant,
        String category,
        @JsonProperty("occurredAt") OffsetDateTime occurredAt,
        String note,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) {}
