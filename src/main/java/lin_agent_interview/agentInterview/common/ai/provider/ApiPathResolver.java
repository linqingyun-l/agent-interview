package lin_agent_interview.agentInterview.common.ai.provider;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.Timeout;
import com.openai.credential.BearerTokenCredential;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 解析不同 Provider 的 baseUrl 差异 + 构造 Spring AI 2.0 推荐的官方 OpenAIClient。
 *
 */
public final class ApiPathResolver {

    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;   // 10s
    private static final int DEFAULT_READ_TIMEOUT = 300000;     // 5min（LLM 长输出）

    /** 匹配 URL 末尾的版本段：/v1 /v2 /v3beta 等 */
    private static final Pattern TRAILING_VERSION = Pattern.compile("/v\\d+[a-zA-Z0-9]*$");

    private ApiPathResolver() {}

    /**
     * 构造一个 OpenAIClient（Spring AI 2.0 推荐方式）。
     *
     * @param baseUrl provider 的 baseUrl，例如：
     *                - "https://dashscope.aliyuncs.com/compatible-mode"
     *                - "https://api.deepseek.com/v1"
     * @param apiKey  provider 的 API key
     */
    public static OpenAIClient buildOpenAiClient(String baseUrl, String apiKey) {
        return buildOpenAiClient(baseUrl, apiKey, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    /**
     * 构造一个 OpenAIClient，可自定义超时时间。
     */
    public static OpenAIClient buildOpenAiClient(String baseUrl, String apiKey,
                                                 int connectTimeoutMs, int readTimeoutMs) {
        Timeout timeout = Timeout.builder()
                .connect(Duration.ofMillis(connectTimeoutMs))
                .read(Duration.ofMillis(readTimeoutMs))
                .build();

        ClientOptions options = ClientOptions.Companion.builder()
                .apiKey(apiKey)
                .credential(BearerTokenCredential.create(apiKey))
                .baseUrl(resolveVersionedBaseUrl(baseUrl))
                .timeout(timeout)
                .httpClient(SpringAiOpenAiHttpClient.builder().timeout(timeout).build())
                .build();

        return new OpenAIClientImpl(options);
    }

    /**
     * 规范化 baseUrl：
     *   - 去掉尾部斜杠
     *   - 如果末尾没有 /v[数字]，自动补 /v1
     */
    public static String resolveVersionedBaseUrl(String baseUrl) {
        String stripped = stripTrailingSlashes(baseUrl);
        if (baseUrlContainsVersion(stripped)) {
            return stripped;
        }
        return stripped + "/v1";
    }

    public static boolean baseUrlContainsVersion(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        return TRAILING_VERSION.matcher(stripTrailingSlashes(baseUrl.trim())).find();
    }

    public static String stripTrailingSlashes(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
