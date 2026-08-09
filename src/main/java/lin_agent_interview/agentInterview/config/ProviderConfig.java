package lin_agent_interview.agentInterview.config;

import lombok.Data;

/**
 * 单个 LLM Provider 的配置项（嵌套在 AppAiProperties.providers Map 的 value 里）。
 * 此类不需要 @ConfigurationProperties：Spring Boot 在绑定 Map 的 value 时会递归处理 POJO。
 */
@Data
public class ProviderConfig {
    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String embeddingModel;
    private Integer embeddingDimensions;
}
