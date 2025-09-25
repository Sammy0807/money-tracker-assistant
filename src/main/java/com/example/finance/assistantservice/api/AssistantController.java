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

    // Remote ingest properties (can be overridden per request)
    @Value("${app.ingest.remote.tokenUrl:http://localhost:8081/realms/finance/protocol/openid-connect/token}")
    private String tokenUrl;
    @Value("${app.ingest.remote.clientId:gateway}")
    private String clientId;
    @Value("${app.ingest.remote.clientSecret:}")
    private String clientSecret;
    @Value("${app.ingest.remote.scope:}")
    private String scope;
    @Value("${app.ingest.remote.accountsUrl:http://localhost:9009/api/accounts}")
    private String accountsUrl;
    @Value("${app.ingest.remote.transactionsUrl:http://localhost:9004/api/transactions}")
    private String transactionsUrl;

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

    @PostMapping("/ingest/remote")
    public Map<String, Object> ingestRemote(@RequestBody Map<String, Object> body) throws Exception {
        String u = asString(body, "username", null);
        String p = asString(body, "password", null);
        if (u == null || p == null) {
            return Map.of("error", "Missing username/password in body");
        }

        String tokenEndpoint = asString(body, "tokenUrl", tokenUrl);
        String cid = asString(body, "clientId", clientId);
        String csec = asString(body, "clientSecret", clientSecret);
        String scp = asString(body, "scope", scope);
        String accUrl = asString(body, "accountsUrl", accountsUrl);
        String txnUrl = asString(body, "transactionsUrl", transactionsUrl);

        int saved = ingestService.ingestFromApis(tokenEndpoint, cid, csec, scp, u, p, accUrl, txnUrl);
        return Map.of("ingestedChunks", saved, "source", "remote-apis");
    }

    private static String asString(Map<String, Object> m, String key, String def) {
        Object v = m != null ? m.get(key) : null;
        return v != null ? v.toString() : def;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody QueryReq req) {
        int k = (req.topK() != null && req.topK() > 0) ? req.topK() : 5;
        var hits = vectorSearchService.search(req.text(), k);

        var answer = answerService.generateAnswer(req.text(), hits);
        
        return Map.of("answer", answer);
    }
}
