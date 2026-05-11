package com.example.AiAgent.controller;

import com.example.AiAgent.kafka.DocumentUploadProducer;
import com.example.AiAgent.repository.DocumentRepository;
import com.example.AiAgent.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final DocumentUploadProducer producer;
    private final DocumentRepository documentRepository;

    // ─── POST /api/documents/upload ───────────────────────────────────────────────
    // Returns 202 Accepted immediately.
    // Actual chunking + embedding happens async via Kafka consumer.
    // Poll /status/{id} to know when doc is ready for querying.
    //
    // Example:
    //   curl -X POST http://localhost:8080/api/documents/upload \
    //        -F "file=@contract.pdf"
    //
    //   Response: { "docId": "uuid", "status": "PENDING", "message": "..." }
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        UUID docId = ingestionService.uploadDocument(file);
        producer.publishDocumentUploaded(docId);

        return ResponseEntity.accepted().body(Map.of(
                "docId",   docId.toString(),
                "status",  "PENDING",
                "message", "Document uploaded. Chunking and embedding started in background."
        ));
    }

    // ─── GET /api/documents/{id}/status ──────────────────────────────────────────
    // Poll this after upload to know when the doc is READY for agent queries.
    // Status flow: PENDING → PROCESSING → READY (or FAILED)
    //
    // Example:
    //   curl http://localhost:8080/api/documents/{docId}/status
    //
    //   Response: { "docId": "uuid", "fileName": "contract.pdf", "status": "READY" }
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable UUID id) {
        return documentRepository.findById(id)
                .map(doc -> ResponseEntity.ok(Map.of(
                        "docId",    id.toString(),
                        "fileName", doc.getFileName(),
                        "status",   doc.getStatus().name()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── GET /api/documents ───────────────────────────────────────────────────────
    // List all uploaded documents and their current processing statuses.
    //
    // Example:
    //   curl http://localhost:8080/api/documents
    @GetMapping
    public ResponseEntity<?> listDocuments() {
        return ResponseEntity.ok(
                documentRepository.findAll().stream()
                        .map(doc -> Map.of(
                                "docId",        doc.getId().toString(),
                                "fileName",     doc.getFileName(),
                                "status",       doc.getStatus().name(),
                                "uploadedAt",   doc.getUploadedAt().toString()
                        ))
                        .toList()
        );
    }

    // ─── DELETE /api/documents/{id} ───────────────────────────────────────────────
    // Deletes document metadata from DB.
    // Note: does NOT delete vectors from pgvector (Spring AI doesn't expose delete by metadata yet).
    // For full cleanup you'd need to delete from the vector_store table directly.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        documentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
