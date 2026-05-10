package com.example.AiAgent.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ResearchAgent {
    @SystemMessage("""
            You are an intelligent document research agent.
            When given a goal:
            1. Think about what you need
            2. Use tools to retrieve information
            3. Reason over results
            4. Give a clear final answer
            Always use tools — never make up answers.
            """)

    String research(@UserMessage String userMessage);
}
