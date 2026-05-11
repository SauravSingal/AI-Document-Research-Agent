package com.example.AiAgent.controller;

import com.example.AiAgent.service.CachedAgentService;
import com.example.AiAgent.service.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final CachedAgentService cachedAgentService;
    private final ConversationMemoryService memoryService;

    // ─── POST /api/agent/research ─────────────────────────────────────────────────
    // Main endpoint. Send a goal, get an AI answer.
    // The agent autonomously decides which tools to call and in what order.
    //
    // Request body:
    // {
    //   "sessionId": "user-abc-123",
    //   "goal": "Compare the refund policies across all uploaded contracts"
    // }
    //
    // What happens internally:
    //   1. Rate limit checked (Redis)
    //   2. Conversation history prepended (Redis)
    //   3. Cache checked — if same goal asked before, return instantly
    //   4. Agent ReAct loop runs (LangChain4j):
    //        Thought: "I need to find refund policies"
    //        → Tool: searchDocuments("refund policy")
    //        Thought: "Found 3 docs, let me get details from each"
    //        → Tool: extractField(doc1, "refund terms")
    //        → Tool: extractField(doc2, "refund terms")
    //        Thought: "I have enough to compare"
    //        → Final answer
    //   5. Response cached in Redis (1hr)
    //   6. Both turns saved to conversation memory (Redis)
    //
    // Example:
    //   curl -X POST http://localhost:8080/api/agent/research \
    //        -H "Content-Type: application/json" \
    //        -d '{"sessionId":"s1","goal":"What is the cancellation policy?"}'
    @PostMapping("/research")
    public ResponseEntity<Map<String, Object>> research(
            @RequestBody Map<String, String> request
    ) {
        String sessionId = request.get("sessionId");
        String goal      = request.get("goal");

        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "sessionId is required"));
        }
        if (goal == null || goal.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "goal is required"));
        }

        String answer = cachedAgentService.research(goal, sessionId);

        return ResponseEntity.ok(Map.of(
                "sessionId",     sessionId,
                "answer",        answer,
                "sessionLength", memoryService.getSessionLength(sessionId)
        ));
    }

    // ─── DELETE /api/agent/session/{sessionId} ────────────────────────────────────
    // Clears conversation history for a session.
    // Use when user wants to start a fresh conversation.
    //
    // Example:
    //   curl -X DELETE http://localhost:8080/api/agent/session/user-abc-123
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, String>> clearSession(
            @PathVariable String sessionId
    ) {
        memoryService.clearSession(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "message",   "Session history cleared. New conversation started."
        ));
    }
}
