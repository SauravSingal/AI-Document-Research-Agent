package com.example.AiAgent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

// Stores conversation history (user messages + agent replies) in Redis.
// Each session gets its own Redis list key with a TTL.
//
// Why Redis for memory?
//   - Fast reads/writes
//   - Auto-expiry via TTL (session cleans itself up)
//   - Survives app restarts (unlike in-memory Map)
//
// Key format: "chat:memory:{sessionId}"
// Value: Redis List of strings like "USER: question" / "AGENT: answer"

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationMemoryService {

    private final RedisTemplate<String, String> redisTemplate;


    private long memoryTtlHours = 1;

    private static final String KEY_PREFIX = "chat:memory:";

    // ─── Add a single message to session history ─────────────────────────────────
    public void addMessage(String sessionId, String role, String content) {
        String key = KEY_PREFIX + sessionId;

        // Format: "USER: what is the refund policy?"
        String entry = role.toUpperCase() + ": " + content;

        // RPUSH appends to end of list — preserves chronological order
        redisTemplate.opsForList().rightPush(key, entry);

        // Reset TTL on every new message — session stays alive as long as it's active
        redisTemplate.expire(key, Duration.ofHours(memoryTtlHours));

        log.debug("Memory saved | session={} role={}", sessionId, role);
    }

    // ─── Get full history formatted as context string ─────────────────────────────
    // Returns empty string on first message (no history yet).
    // Returns formatted history for subsequent messages so agent knows what was discussed.
    public String getHistoryAsContext(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        List<String> history = redisTemplate.opsForList().range(key, 0, -1);

        if (history == null || history.isEmpty()) {
            return "";  // first message in this session
        }

        // Prepend history so the LLM understands prior conversation context
        return "=== Previous conversation ===\n" +
                String.join("\n", history) +
                "\n=== Current request ===\n";
    }

    // ─── Clear session — start fresh ─────────────────────────────────────────────
    public void clearSession(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
        log.info("Session cleared: {}", sessionId);
    }

    // ─── How many turns are in a session ─────────────────────────────────────────
    public long getSessionLength(String sessionId) {
        Long size = redisTemplate.opsForList().size(KEY_PREFIX + sessionId);
        return size != null ? size : 0;
    }
}
