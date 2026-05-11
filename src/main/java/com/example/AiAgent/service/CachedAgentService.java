package com.example.AiAgent.service;


import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedAgentService {

    private final ResearchAgent researchAgent;
    private final ConversationMemoryService memoryService;
    private final RedisTemplate<String, String> redisTemplate;

    private long cacheTtlHours = 1;

    private long rateLimitMax = 10;

    private long rateLimitWindowSeconds = 60;

    private static final String CACHE_PREFIX      = "agent:cache:";
    private static final String RATE_LIMIT_PREFIX = "agent:ratelimit:";

    public String research(String userGoal, String sessionId) {

        // 1. Rate limit
        if (isRateLimited(sessionId)) {
            return "Rate limit exceeded. Please wait before sending another request.";
        }

        // 2. Build message with history
        String history = memoryService.getHistoryAsContext(sessionId);
        String fullMessage = history + userGoal;

        // 3. Cache check
        String cacheKey = CACHE_PREFIX + sessionId + ":" + userGoal;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            memoryService.addMessage(sessionId, "user", userGoal);
            memoryService.addMessage(sessionId, "agent", cached);
            return cached;
        }

        // 4. Call agent — ReAct loop runs here
        String response = researchAgent.research(fullMessage);

        // 5. Cache + save memory
        redisTemplate.opsForValue().set(cacheKey, response, Duration.ofHours(cacheTtlHours));
        memoryService.addMessage(sessionId, "user", userGoal);
        memoryService.addMessage(sessionId, "agent", response);

        return response;
    }

    private boolean isRateLimited(String sessionId) {
        String key = RATE_LIMIT_PREFIX + sessionId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) return false;
        if (count == 1) redisTemplate.expire(key, Duration.ofSeconds(rateLimitWindowSeconds));
        if (count > rateLimitMax) {
            return true;
        }
        return false;
    }
}
