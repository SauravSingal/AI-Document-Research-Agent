package com.example.AiAgent.service;


import com.example.AiAgent.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import com.example.AiAgent.model.DocumentData;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ── Key difference from original ──────────────────────────────────────────────
// ORIGINAL approach (manual):
//   1. Call embeddingModel.embed(chunk) → float[1536]
//   2. Convert to pgvector string "[0.1,0.2,...]"
//   3. Save DocumentChunk entity with embedding to DB
//
// SPRING AI approach:
//   1. Wrap chunks in SpringAiDoc objects with metadata
//   2. Call vectorStore.add(docs)
//   3. Spring AI handles embedding + saving internally — one call
//
// You never see the float[] vectors. Spring AI manages them completely.
// ─────────────────────────────────────────────────────────────────────────────

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;      // Spring AI auto-configured pgvector store


    private int chunkSize = 500;

    private int chunkOverlap = 50;

    // ─── Step 1: Accept upload, save metadata, return docId immediately ──────────
    // Called by DocumentController before publishing to Kafka.
    // Returns fast — no embedding happens here.
    public UUID uploadDocument(MultipartFile file) throws IOException {
        // Save file to temp location so consumer can read it later
        Path uploadPath = Path.of("/tmp/uploads/" + file.getOriginalFilename());
        Files.createDirectories(uploadPath.getParent());
        file.transferTo(uploadPath.toFile());

        // Save document record to DB
        DocumentData doc = new DocumentData();
        doc.setFileName(file.getOriginalFilename());
        documentRepository.save(doc);

        log.info("Document saved: id={}, file={}", doc.getId(), doc.getFileName());
        return doc.getId();
    }

    // ─── Step 2: Called by Kafka consumer — heavy lifting ────────────────────────
    // This runs in background. Chunks the document, embeds all chunks,
    // and stores them in pgvector via Spring AI's VectorStore.
    public void ingest(UUID docId) {
        DocumentData doc = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + docId));

        try {
            doc.setStatus(DocumentData.ProcessingStatus.PROCESSING);
            documentRepository.save(doc);

            // 1. Extract raw text from the file
            String fullText = extractText(doc.getFileName());

            // 2. Split into overlapping chunks
            List<String> chunks = splitIntoChunks(fullText);
            log.info("docId={} split into {} chunks", docId, chunks.size());

            // 3. Wrap each chunk in a Spring AI Document with metadata
            //    Metadata is stored alongside the vector — you can filter on it later
            //    e.g. "find chunks where docId = X"
            List<Document> springAiDocs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                springAiDocs.add(new Document(
                        chunks.get(i),                       // the text content
                        Map.of(
                                "docId",      docId.toString(),
                                "fileName",   doc.getFileName(),
                                "chunkIndex", String.valueOf(i)
                        )
                ));
            }

            // 4. Spring AI does the rest:
            //    - Calls OpenAI embeddings API for each doc
            //    - Stores text + vector + metadata in pgvector
            //    No float[] handling, no SQL, no repository calls needed.
            vectorStore.add(springAiDocs);
            log.info("VectorStore.add() complete for docId={}, chunks={}", docId, chunks.size());

            // 5. Mark document as ready for querying
            doc.setStatus(DocumentData.ProcessingStatus.READY);
            doc.setProcessedAt(LocalDateTime.now());
            documentRepository.save(doc);

        } catch (Exception e) {
            log.error("Ingestion failed for docId={}", docId, e);
            doc.setStatus(DocumentData.ProcessingStatus.FAILED);
            documentRepository.save(doc);
        }
    }

    // ─── Extract raw text from file ──────────────────────────────────────────────
    private String extractText(String fileName) throws IOException {
        String filePath = "/tmp/uploads/" + fileName;
        if (fileName.toLowerCase().endsWith(".pdf")) {
            try (PDDocument pdf = Loader.loadPDF(new java.io.File(filePath))) {
                return new PDFTextStripper().getText(pdf);
            }
        }
        // Plain text / markdown / etc.
        return Files.readString(Path.of(filePath));
    }

    // ─── Split text into overlapping word chunks ──────────────────────────────────
    // Why overlap? Meaning at chunk boundaries isn't lost.
    // chunkSize=500 words, overlap=50 words
    // chunk1: words[0..499]
    // chunk2: words[450..949]  ← words 450-499 repeated for context continuity
    List<String> splitIntoChunks(String text) {
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            StringBuilder chunk = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) chunk.append(" ");
                chunk.append(words[i]);
            }
            chunks.add(chunk.toString());
            start += (chunkSize - chunkOverlap);    // step = 450 words
        }
        return chunks;
    }
}
