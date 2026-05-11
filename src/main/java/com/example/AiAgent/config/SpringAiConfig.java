package com.example.AiAgent.config;


import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

        @Bean
        public ChatClient ragChatClient(ChatModel chatModel, VectorStore vectorStore) {
                return ChatClient.builder(chatModel)
                        .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                        .defaultSystem("""
                        You are a helpful document research assistant.
                        Answer using ONLY the provided document context.
                        If not found, say so clearly.
                        """)
                        .build();
        }
}
