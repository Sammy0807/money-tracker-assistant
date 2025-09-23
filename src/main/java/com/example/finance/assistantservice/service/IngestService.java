// src/main/java/com/example/finance/assistantservice/service/IngestService.java
package com.example.finance.assistantservice.service;

import com.example.finance.assistantservice.model.Chunk;
import com.example.finance.assistantservice.repo.ChunkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngestService {

    private final EmbeddingModel embeddingModel;
    private final ChunkRepository repo;
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
