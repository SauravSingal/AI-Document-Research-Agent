package com.example.AiAgent.kafka;

import com.example.AiAgent.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Publishes docId to Kafka after a document is uploaded.
// The upload API returns 202 immediately after calling this.
// The consumer picks it up and starts the ingestion pipeline in the background.

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publishDocumentUploaded(UUID docId) {
        kafkaTemplate.send(KafkaConfig.TOPIC_DOC_UPLOADED, docId.toString());
        log.info("Published to Kafka | topic={} | docId={}", KafkaConfig.TOPIC_DOC_UPLOADED, docId);
    }
}
