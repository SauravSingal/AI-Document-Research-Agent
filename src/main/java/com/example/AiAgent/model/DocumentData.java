package com.example.AiAgent.model;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
public class DocumentData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProcessingStatus status;

    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        this.uploadedAt = LocalDateTime.now();
        this.status = ProcessingStatus.PENDING;
    }

    public enum ProcessingStatus {
        PENDING, PROCESSING, READY, FAILED
    }
}
