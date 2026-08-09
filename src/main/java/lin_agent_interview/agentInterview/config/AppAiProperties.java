package lin_agent_interview.agentInterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@ConfigurationProperties(prefix = "app.ai")
@Component
public class AppAiProperties {
    private String defaultProvider;
    private Map<String, ProviderConfig> providers;
}
class ProviderConfig{
      private String baseUrl;
      private  String apiKey;
      private String chatModel;
      private String embeddingModel;
      private  Integer embeddingDimensions ;
}