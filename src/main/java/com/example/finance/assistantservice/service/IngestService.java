// src/main/java/com/example/finance/assistantservice/service/IngestService.java
package com.example.finance.assistantservice.service;

import com.example.finance.assistantservice.model.Chunk;
import com.example.finance.assistantservice.repo.ChunkRepository;
import com.example.finance.assistantservice.webdto.AccountDto;
import com.example.finance.assistantservice.webdto.TokenResponse;
import com.example.finance.assistantservice.webdto.TransactionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IngestService {

    private final EmbeddingModel embeddingModel;
    private final ChunkRepository repo;
    private final BankApiClient bankApiClient;
    private final ObjectMapper om = new ObjectMapper();

    public int ingestFile(Path path) throws Exception {
        JsonNode root = om.readTree(new File(path.toString()));
        List<String> texts = new ArrayList<>();
        flattenJson(root, texts);

        // naive chunking ~600–900 chars per chunk
        List<String> chunks = chunk(texts, 800);

        List<TextSegment> segments = chunks.stream()
                .map(TextSegment::from)
                .toList();

        List<Embedding> embs = embeddingModel.embedAll(segments).content();

        List<Chunk> toSave = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            toSave.add(Chunk.builder()
                    .docId(path.getFileName().toString())
                    .text(chunks.get(i))
                    .embedding(toList(embs.get(i)))
                    .metadata(Map.of("source", "json", "pos", i))
                    .build());
        }
        repo.deleteAll(); // clean slate for demo
        repo.saveAll(toSave);
        return toSave.size();
    }

    /**
     * Ingest data by calling remote APIs: token -> accounts -> transactions.
     * The endpoints and client info are passed in, so controller can bind from
     * config/request.
     */
    public int ingestFromApis(String tokenUrl,
            String clientId,
            String clientSecret,
            String scope,
            String username,
            String password,
            String accountsUrl,
            String transactionsUrl) throws Exception {

        TokenResponse token = bankApiClient.fetchToken(tokenUrl, clientId, clientSecret, username, password, scope);
        String accessToken = token.accessToken();

        List<AccountDto> accounts = bankApiClient.fetchAccounts(accountsUrl, accessToken);
        List<TransactionDto> txns = bankApiClient.fetchTransactions(transactionsUrl, accessToken);

        // Build meaningful text for RAG: include normalized amounts and ISO dates
        List<String> docs = new ArrayList<>();
        for (AccountDto a : accounts) {
            docs.add(String.format("Account %s (%s) at %s, balance: %s cents, currency: %s, created: %s",
                    a.id(), a.name(), a.institution(), a.balanceCents(), a.currency(),
                    a.createdAt() != null ? a.createdAt() : "unknown"));
        }
        for (TransactionDto t : txns) {
            docs.add(String.format("Transaction: %s spent %s cents %s on %s (account %s) note: %s",
                    t.merchant() != null ? t.merchant() : "unknown-merchant",
                    t.amountCents() != null ? t.amountCents() : 0,
                    t.currency() != null ? t.currency() : "USD",
                    t.occurredAt() != null ? t.occurredAt().toLocalDate() : "unknown-date",
                    t.accountId(),
                    t.note() != null ? t.note() : ""));
        }

        List<String> chunks = chunk(docs, 800);
        List<TextSegment> segments = chunks.stream().map(TextSegment::from).toList();
        List<Embedding> embs = embeddingModel.embedAll(segments).content();

        List<Chunk> toSave = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            toSave.add(Chunk.builder()
                    .docId("remote-apis")
                    .text(chunks.get(i))
                    .embedding(toList(embs.get(i)))
                    .metadata(Map.of("source", "apis", "pos", i))
                    .build());
        }
        repo.deleteAll();
        repo.saveAll(toSave);
        return toSave.size();
    }

    private static void flattenJson(JsonNode node, List<String> out) {
        if (node.isTextual()) {
            String s = node.asText().trim();
            if (!s.isEmpty()) out.add(s);
        } else if (node.isNumber()) {
            // Include numeric values, especially important for amounts
            out.add(node.asText());
        } else if (node.isContainerNode()) {
            node.elements().forEachRemaining(child -> flattenJson(child, out));
            node.fields().forEachRemaining(e -> flattenJson(e.getValue(), out));
        } // booleans ignored
    }

    private static List<String> chunk(List<String> texts, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String t : texts) {
            if (buf.length() + t.length() + 1 > maxChars) {
                if (buf.length() > 0) { chunks.add(buf.toString()); buf.setLength(0); }
            }
            if (t.length() >= maxChars) {
                // hard-split long strings
                for (int i = 0; i < t.length(); i += maxChars) {
                    chunks.add(t.substring(i, Math.min(i + maxChars, t.length())));
                }
            } else {
                if (buf.length() > 0) buf.append(" ");
                buf.append(t);
            }
        }
        if (buf.length() > 0) chunks.add(buf.toString());
        return chunks;
    }

    private static List<Double> toList(Embedding e) {
        float[] v = e.vector();
        List<Double> out = new ArrayList<>(v.length);
        for (float f : v) out.add((double) f);
        return out;
    }
}
