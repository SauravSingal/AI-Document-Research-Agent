package com.example.AiAgent.config;

import com.example.AiAgent.service.AgentTools;
import com.example.AiAgent.service.ResearchAgent;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import dev.langchain4j.service.AiServices;

@Configuration
public class LangChain4jConfig {

    @Bean(name = "langchain4jChatModel")
    public OpenAiChatModel langchain4jChatModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.3)
                .maxTokens(2048)
                .build();
    }

    @Bean
    public ResearchAgent researchAgent(
            @Value("#{@langchain4jChatModel}") OpenAiChatModel chatModel,
            AgentTools agentTools
    ) {
        return AiServices.builder(ResearchAgent.class)
                .chatLanguageModel(chatModel)
                .tools(agentTools)
                .build();
    }
}
