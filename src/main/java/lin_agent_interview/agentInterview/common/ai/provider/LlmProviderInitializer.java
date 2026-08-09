package lin_agent_interview.agentInterview.common.ai.provider;

import com.openai.client.OpenAIClient;
import lin_agent_interview.agentInterview.config.AppAiProperties;
import lin_agent_interview.agentInterview.config.ProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把所有 Provider 预热好并写入 LlmProviderRegistry。
 * 选用 ApplicationRunner 而非 @PostConstruct：
 *   - 保证所有 Bean（包括 LlmProviderRegistry）已就绪
 *   - 启动失败可以抛出，Spring Boot 会显示在日志里
 */
@Component
@Slf4j
public class LlmProviderInitializer implements ApplicationRunner {

    private final LlmProviderRegistry registry;
    private final AppAiProperties properties;

    public LlmProviderInitializer(LlmProviderRegistry registry, AppAiProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        int total = properties.getProviders() == null ? 0 : properties.getProviders().size();
        log.info("开始预热 LLM Provider，共 {} 个", total);
        initChatClients();
        initEmbeddingModels();
        log.info("LLM Provider 预热完毕");
    }

    /**
     * 遍历所有 provider 配置，为每个创建一个 ChatClient 并注册到 Registry。
     */
    public void initChatClients() {
        if (properties.getProviders() == null) {
            return;
        }
        properties.getProviders().forEach((providerId, config) -> {
            try {
                validate(config, true);
                ChatClient client = createChatClient(config);
                registry.putChatClient(providerId, client);
                log.info("[init] chatClient-{} ✓", providerId);
            } catch (Exception e) {
                handleInitFailure(providerId, "ChatClient", e);
            }
        });
    }

    /**
     * 遍历所有 provider 配置，为配置了 embedding-model 的 provider 创建 EmbeddingModel 并注册。
     * 没配 embedding-model 的 provider 跳过（不强求每个 provider 都支持向量化）。
     */
    public void initEmbeddingModels() {
        if (properties.getProviders() == null) {
            return;
        }
        properties.getProviders().forEach((providerId, config) -> {
            if (config.getEmbeddingModel() == null || config.getEmbeddingModel().isBlank()) {
                return;
            }
            try {
                EmbeddingModel model = createEmbeddingModel(config);
                registry.putEmbeddingModel(providerId, model);
                log.info("[init] embeddingModel-{} ✓", providerId);
            } catch (Exception e) {
                handleInitFailure(providerId, "EmbeddingModel", e);
            }
        });
    }

    // ============ 工厂方法 ============

    private ChatClient createChatClient(ProviderConfig config) {
        // Spring AI 2.0：baseUrl/apiKey 走官方 OpenAIClient（已替代旧的 OpenAiApi）
        OpenAIClient openAiClient = ApiPathResolver.buildOpenAiClient(
                config.getBaseUrl(), config.getApiKey());

        // OpenAiChatOptions 只装单次请求参数（model、temperature 等），不再负责 baseUrl/apiKey
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getChatModel())
                .temperature(0.2)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .openAiClientAsync(openAiClient.async())
                .options(options)
                .build();

        return ChatClient.builder(chatModel).build();
    }

    private EmbeddingModel createEmbeddingModel(ProviderConfig config) {
        OpenAIClient openAiClient = ApiPathResolver.buildOpenAiClient(
                config.getBaseUrl(), config.getApiKey());
        return new OpenAiEmbeddingModel(openAiClient);
    }

    // ============ 辅助方法 ============

    private void validate(ProviderConfig config, boolean requireChatModel) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new IllegalStateException("baseUrl 不能为空");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("apiKey 不能为空");
        }
        if (requireChatModel && (config.getChatModel() == null || config.getChatModel().isBlank())) {
            throw new IllegalStateException("chatModel 不能为空");
        }
    }

    /**
     * 单个 provider 初始化失败的兜底。
     * 默认策略：优雅降级（记 ERROR 日志，应用继续启动）。
     * 如需 Fail-Fast，取消下方 throw 注释即可。
     */
    private void handleInitFailure(String providerId, String type, Exception e) {
        log.error("[init] {} 初始化失败: providerId={}, 错误类型={}, message={}",
                type, providerId, e.getClass().getSimpleName(), e.getMessage());
        log.debug("[init] {} 初始化失败详情 (providerId={})", type, providerId, e);

        // ==== 策略 A：优雅降级（当前默认） ====
        // 不抛异常，其他 provider 继续初始化。

        // ==== 策略 B：Fail-fast（取消下面注释切换） ====
        // throw new IllegalStateException(
        //         String.format("[init] %s 初始化失败: providerId=%s", type, providerId), e);
    }
}
