// src/main/java/com/example/finance/assistantservice/service/VectorSearchService.java
package com.example.finance.assistantservice.service;

import com.example.finance.assistantservice.model.Chunk;
import com.example.finance.assistantservice.repo.ChunkRepository;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final MongoTemplate mongoTemplate;
    private final EmbeddingModel embeddingModel;
    private final ChunkRepository repo;

    @Value("${app.vector.indexName:vector_index}")
    private String indexName;

    @Value("${app.vector.useAtlasVector:true}")
    private boolean useAtlasVector;

    public List<SearchHit> search(String query, int k) {
        Embedding emb = embeddingModel.embed(query).content();

        if (useAtlasVector) {
            MongoCollection<Document> col = mongoTemplate.getCollection("chunks");
            Document vectorSearch = new Document("$vectorSearch",
                    new Document("index", indexName)
                            .append("path", "embedding")
                            .append("queryVector", toList(emb))
                            .append("numCandidates", Math.max(200, k * 40))
                            .append("limit", k));

            Document addScore = new Document("$addFields", new Document("score", new Document("$meta", "vectorSearchScore")));
            Document project = new Document("$project", new Document("text", 1).append("metadata", 1).append("score", 1));

            List<Document> pipeline = List.of(vectorSearch, addScore, project);

            AggregateIterable<Document> agg = col.aggregate(pipeline);
            List<SearchHit> hits = new ArrayList<>();
            for (Document d : agg) {
                hits.add(new SearchHit(d.getObjectId("_id").toHexString(),
                        d.getString("text"),
                        (Double) d.get("score"),
                        (Map<String, Object>) d.get("metadata")));
            }
            return hits;
        } else {
            // Fallback: compute cosine in Java against all embeddings
            List<Chunk> all = repo.findAll();
            var vec = emb.vector();
            return all.stream()
                    .map(c -> new SearchHit(
                            c.getId(),
                            c.getText(),
                            cosine(vec, toFloatArray(c.getEmbedding())),
                            c.getMetadata()))
                    .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                    .limit(k)
                    .collect(Collectors.toList());
        }
    }

    private static List<Double> toList(Embedding e) {
        float[] v = e.vector();
        List<Double> out = new ArrayList<>(v.length);
        for (float f : v) out.add((double) f);
        return out;
    }
    private static double[] toPrimitive(List<Double> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }
    private static float[] toFloatArray(List<Double> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < list.size(); i++) a[i] = list.get(i).floatValue();
        return a;
    }

    // cosine that works on float[]
    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na  += a[i] * a[i];
            nb  += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-12);
    }

    public record SearchHit(String id, String text, double score, Map<String, Object> metadata){}
}
