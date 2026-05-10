package com.example.AiAgent.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentTools {

    private final VectorStore vectorStore;
    private final ChatClient ragChatClient;

    // ─── Tool 1: Search across ALL documents ─────────────────────────────────────
    // Open search — no docId filter, threshold applied to filter low-relevance results
    @Tool("Search all uploaded documents for relevant information about a topic or question. " +
            "Use this as the first step for any research query. Returns the most relevant passages.")
    public String searchDocuments(
            @P("Specific search query, e.g. 'refund policy' or 'monthly pricing tier'")
            String query
    ) {
        log.info("[AGENT TOOL CALLED] searchDocuments | query='{}'", query);

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(5)
                        .similarityThreshold(0.65)  // filter out low-relevance chunks
                        .build()
        );

        if (results.isEmpty()) {
            return "No relevant content found for query: '" + query + "'";
        }

        return results.stream()
                .map(doc -> String.format("--- From: %s (docId: %s) ---\n%s",
                        doc.getMetadata().getOrDefault("fileName", "unknown"),
                        doc.getMetadata().getOrDefault("docId", "unknown"),
                        doc.getText()))                          // ✅ getText() not getContent()
                .collect(Collectors.joining("\n\n"));
    }

    // ─── Tool 2: Summarize a full document ───────────────────────────────────────
    // Filters by docId so we only get chunks from that specific document.
    // No similarityThreshold here — we want ALL chunks from this doc, not just similar ones.
    @Tool("Summarize the full content of a specific document. " +
            "Use when you need an overall summary of one document. Requires the document ID.")
    public String summarizeDocument(
            @P("The UUID of the document to summarize — get this from a prior searchDocuments result")
            String docId
    ) {
        log.info("[AGENT TOOL CALLED] summarizeDocument | docId='{}'", docId);

        List<Document> chunks = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("document overview summary content")
                        .topK(20)                                        // fetch many chunks to cover full doc
                        .filterExpression("docId == '" + docId + "'")   // only this document
                        .build()                                         // no threshold — don't exclude chunks
        );

        if (chunks.isEmpty()) {
            return "No content found for document ID: " + docId +
                    ". Make sure the document has finished processing (status = READY).";
        }

        // Sort by chunkIndex so document reads in correct order
        chunks.sort((a, b) -> {
            int ia = Integer.parseInt((String) a.getMetadata().getOrDefault("chunkIndex", "0"));
            int ib = Integer.parseInt((String) b.getMetadata().getOrDefault("chunkIndex", "0"));
            return Integer.compare(ia, ib);
        });

        String fullText = chunks.stream()
                .map(Document::getText)
                .collect(Collectors.joining(" "));

        return ragChatClient.prompt()
                .user("Summarize the following document in 4-5 clear bullet points. " +
                        "Focus on the most important information:\n\n" + fullText)
                .call()
                .content();
    }

    // ─── Tool 3: Extract a specific field or data point ──────────────────────────
    // Filters by docId + searches for the specific field.
    // No similarityThreshold — don't risk excluding the chunk that has the answer.
    @Tool("Extract a specific piece of information from a document. " +
            "Use for targeted facts like: price, date, policy name, CEO name, contract clause. " +
            "More precise than searchDocuments when you know exactly what you're looking for.")
    public String extractField(
            @P("The UUID of the document to search in")
            String docId,

            @P("What to extract — be specific, e.g. 'cancellation fee amount', " +
                    "'contract start date', 'monthly subscription price'")
            String fieldToExtract
    ) {
        log.info("[AGENT TOOL CALLED] extractField | docId='{}' | field='{}'", docId, fieldToExtract);

        List<Document> chunks = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(fieldToExtract)
                        .topK(3)                                         // just need top 3 for a specific field
                        .filterExpression("docId == '" + docId + "'")   // only this document
                        .build()                                         // no threshold
        );

        if (chunks.isEmpty()) {
            return "Could not find '" + fieldToExtract + "' in document " + docId;
        }

        String context = chunks.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        return ragChatClient.prompt()
                .user(String.format(
                        "From the text below, extract only: '%s'.\n" +
                                "Respond with just the value — no explanation.\n" +
                                "If not found, respond with: 'Not found in document.'\n\n" +
                                "Text:\n%s",
                        fieldToExtract, context))
                .call()
                .content();
    }
}