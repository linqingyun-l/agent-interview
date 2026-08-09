package lin_agent_interview.agentInterview.config;

import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@ConfigurationProperties(prefix = "app.ai")
@Component
@Data
public class AppAiProperties {
    private String defaultProvider;
    private Map<String, ProviderConfig> providers;
}