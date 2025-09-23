package com.example.finance.assistantservice.repo;

import com.example.finance.assistantservice.model.Chunk;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChunkRepository extends MongoRepository<Chunk, String> {}
