package com.example.AiAgent.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_DOC_UPLOADED = "doc.uploaded";

    // Creates the Kafka topic on startup if it doesn't already exist.
    // partitions=3 means 3 consumers can process messages in parallel.
    @Bean
    public NewTopic docUploadedTopic() {
        return TopicBuilder.name(TOPIC_DOC_UPLOADED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
