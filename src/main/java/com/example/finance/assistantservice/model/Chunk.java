// src/main/java/com/example/finance/assistantservice/model/Chunk.java
package com.example.finance.assistantservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document("chunks")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Chunk {
    @Id
    private String id;
    private String docId;                 // source doc identifier
    private String text;                  // chunk content
    private List<Double> embedding;       // vector embedding
    private Map<String, Object> metadata; // title, section, etc.
}
