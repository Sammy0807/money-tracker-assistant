// src/main/java/com/example/finance/assistantservice/api/AssistantController.java
package com.example.finance.assistantservice.api;

import com.example.finance.assistantservice.dto.AnswerResponse;
import com.example.finance.assistantservice.service.AnswerService;
import com.example.finance.assistantservice.service.IngestService;
import com.example.finance.assistantservice.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final IngestService ingestService;
    private final VectorSearchService vectorSearchService;
    private final AnswerService answerService;

    // Single request type
    public record QueryReq(String text, Integer topK) {}

    // Make default path optional
    @Value("${app.ingest.path:}")
    private String defaultPath;

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody(required = false) Map<String, Object> body) throws Exception {
        // prefer body.path, then configured default, else fail with a clear message
        String path = (body != null && body.get("path") != null)
                ? body.get("path").toString()
                : (defaultPath != null && !defaultPath.isBlank() ? defaultPath : null);

        if (path == null) {
            return Map.of(
                    "error", "No path provided. Pass {\"path\": \"/abs/path/file.json\"} or set app.ingest.path"
            );
        }

        // Support "classpath:" prefix in addition to filesystem paths
        Path filePath;
        if (path.startsWith("classpath:")) {
            // copies the classpath resource to a temp file so your existing service can read it
            var resName = path.substring("classpath:".length());
            var res = new org.springframework.core.io.ClassPathResource(resName);
            if (!res.exists()) {
                return Map.of("error", "Classpath resource not found: " + resName);
            }
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("ingest-", ".json");
            try (var in = res.getInputStream()) {
                java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            filePath = tmp;
        } else {
            filePath = java.nio.file.Path.of(path).toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(filePath)) {
                return Map.of("error", "File not found: " + filePath);
            }
        }

        int saved = ingestService.ingestFile(filePath);
        return Map.of("ingestedChunks", saved, "source", path);
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody QueryReq req) {
        int k = (req.topK() != null && req.topK() > 0) ? req.topK() : 5;
        var hits = vectorSearchService.search(req.text(), k);
        var answer = answerService.generateAnswer(req.text(), hits);
        
        return Map.of("answer", answer);
    }
}
