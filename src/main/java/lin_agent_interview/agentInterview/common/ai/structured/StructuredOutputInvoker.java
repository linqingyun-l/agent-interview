package lin_agent_interview.agentInterview.common.ai.structured;

import lin_agent_interview.agentInterview.common.ai.provider.LlmProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 结构化输出调用器：把 LLM 自由格式回答按指定 record 类型反序列化。
 *
 * 用法：
 *   record Greeting(String text) {}
 *   Greeting g = structuredOutputInvoker.invoke("say hello", Greeting.class);
 *
 * 设计：
 *   - 通过 LlmProviderRegistry 拿 ChatClient（与其它业务共享一套 provider 抽象）
 *   - 默认重试 2 次（合计 3 次），适合 LLM 偶发的 schema 漂移
 *   - 不带睡眠的紧循环重试（轻量级，应用层快速失败暴露问题）
 */
@Component
@Slf4j
public class StructuredOutputInvoker {

    /** 默认最大重试次数（不含首次，即总调用次数 = maxRetries + 1） */
    public static final int DEFAULT_MAX_RETRIES = 2;

    private final LlmProviderRegistry registry;
    private final int maxRetries;
    @Autowired
    public StructuredOutputInvoker(LlmProviderRegistry registry) {
        this(registry, DEFAULT_MAX_RETRIES);
    }

    /** 测试用：允许注入固定重试次数 */
    StructuredOutputInvoker(LlmProviderRegistry registry, int maxRetries) {
        this.registry = registry;
        this.maxRetries = maxRetries;
    }

    /** 用默认 provider 调一次 LLM，按 type 反序列化 */
    public <T> T invoke(String userPrompt, Class<T> type) {
        return invoke(null, userPrompt, type);
    }

    /** 显式指定 providerId 的版本 */
    public <T> T invoke(String providerId, String userPrompt, Class<T> type) {
        ChatClient client = providerId == null || providerId.isBlank()
                ? registry.getDefaultChatClient()
                : registry.getChatClient(providerId);

        if (client == null) {
            throw new IllegalStateException(
                    "没有可用的 ChatClient(providerId=" + providerId + "),请检查 application.yaml 的 app.ai.providers 配置");
        }

        Exception last = null;
        int totalAttempts = maxRetries + 1;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                T result = client.prompt().user(userPrompt).call().entity(type);
                if (log.isDebugEnabled()) {
                    log.debug("StructuredOutput 解析成功: type={}, attempt={}/{}", type.getSimpleName(), attempt, totalAttempts);
                }
                return result;
            } catch (Exception e) {
                last = e;
                log.warn("StructuredOutput 解析失败: type={}, attempt={}/{}, 错误={}",
                        type.getSimpleName(), attempt, totalAttempts, e.getMessage());
            }
        }
        throw new IllegalStateException(
                "StructuredOutput 解析失败,已重试 " + maxRetries + " 次: " + type.getSimpleName(), last);
    }
}
