package com.example.AiAgent.kafka;

import com.example.AiAgent.config.KafkaConfig;
import com.example.AiAgent.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Listens to doc.uploaded topic.
// When a message arrives, picks up the docId and runs the full ingestion:
//   extract text → split into chunks → embed via Spring AI → save to pgvector
//
// This runs completely asynchronously — totally separate from the upload request.
// The user already got their 202 response by the time this runs.
//
// groupId = "agent-group": if you run multiple app instances,
// Kafka distributes messages across them (each message processed once).

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingConsumer {

    private final DocumentIngestionService ingestionService;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_DOC_UPLOADED,
            groupId = "agent-group"
    )
    public void handleDocumentUploaded(String docIdStr) {
        log.info("Kafka consumer received | docId={}", docIdStr);
        try {
            ingestionService.ingest(UUID.fromString(docIdStr));
        } catch (Exception e) {
            // Document status will be set to FAILED inside ingest()
            // We log and swallow here to avoid Kafka retrying indefinitely
            log.error("Failed to process document | docId={}", docIdStr, e);
        }
    }
}
