package lin_agent_interview.agentInterview.common.ai.provider;

import lin_agent_interview.agentInterview.config.AppAiProperties;
import lin_agent_interview.agentInterview.config.ProviderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * LlmProviderRegistry 单元测试 —— 核心场景精简版
 *
 * 真实 key 必须通过环境变量提供（绝不能硬编码到源码里）：
 *   - AI_BAILIAN_API_KEY : 阿里百炼 DashScope
 *   - DEEPSEEK_API_KEY   : DeepSeek
 */
@DisplayName("LlmProviderRegistry 单元测试")
class LlmProviderRegistryTest {

    private AppAiProperties properties;
    private LlmProviderRegistry registry;

    private static final String DASHSCOPE_KEY = System.getenv("AI_BAILIAN_API_KEY");
    private static final String DEEPSEEK_KEY  = System.getenv("DEEPSEEK_API_KEY");

    static {
        if (DASHSCOPE_KEY == null || DASHSCOPE_KEY.isBlank()) {
            throw new IllegalStateException(
                "请设置环境变量 AI_BAILIAN_API_KEY 后再运行测试（key 不能硬编码进源码）");
        }
        if (DEEPSEEK_KEY == null || DEEPSEEK_KEY.isBlank()) {
            throw new IllegalStateException(
                "请设置环境变量 DEEPSEEK_API_KEY 后再运行测试（key 不能硬编码进源码）");
        }
    }

    @BeforeEach
    void setUp() {
        properties = baseProperties("dashscope",
                cfg("dashscope", DASHSCOPE_KEY, "qwen3.5-flash", "text-embedding-v3"),
                cfg("deepseek",  DEEPSEEK_KEY,  "deepseek-chat",  "text-embedding-v3"));
        registry = new LlmProviderRegistry(properties);
    }

    private static ProviderConfig cfg(String id, String key, String chat, String embed) {
        ProviderConfig c = new ProviderConfig();
        c.setBaseUrl("https://example.com/" + id);
        c.setApiKey(key);
        c.setChatModel(chat);
        c.setEmbeddingModel(embed);
        return c;
    }

    private static AppAiProperties baseProperties(String defaultId, ProviderConfig... entries) {
        AppAiProperties p = new AppAiProperties();
        p.setDefaultProvider(defaultId);
        Map<String, ProviderConfig> m = new HashMap<>();
        for (ProviderConfig c : entries) {
            m.put(c.getApiKey().equals(DASHSCOPE_KEY) ? "dashscope" : "deepseek", c);
        }
        p.setProviders(m);
        return p;
    }

    // ============== 核心 4 个场景 ==============

    @Test
    @DisplayName("put 注册后可 get 取出,且默认 provider 同步设置 defaultChatClient")
    void putThenGetWithDefaultSync() {
        ChatClient client = mock(ChatClient.class);
        EmbeddingModel embed = mock(EmbeddingModel.class);

        registry.putChatClient("dashscope", client);
        registry.putEmbeddingModel("dashscope", embed);

        assertSame(client, registry.getChatClient("dashscope"));
        assertSame(embed, registry.getEmbeddingModel("dashscope"));
        assertSame(client, registry.getDefaultChatClient(), "默认 provider 同步");
        assertSame(embed, registry.getDefaultEmbeddingModel());
    }

    @Test
    @DisplayName("put 同 key 二次会覆盖,且 default 引用也跟着新实例")
    void putOverwritesSameKey() {
        ChatClient a = mock(ChatClient.class), b = mock(ChatClient.class);
        registry.putChatClient("dashscope", a);
        registry.putChatClient("dashscope", b);
        assertSame(b, registry.getChatClient("dashscope"));
        assertSame(b, registry.getDefaultChatClient());
    }

    @Test
    @DisplayName("找不到的 provider 回退到默认")
    void unknownProviderFallsBack() {
        registry.putChatClient("dashscope", mock(ChatClient.class));
        assertSame(registry.getDefaultChatClient(), registry.getChatClient("nonexistent"));
    }

    @ParameterizedTest(name = "空 providerId={0} 时返回默认")
    @ValueSource(strings = {"", "   "})
    @DisplayName("providerId 为空/blank 返回默认(null 也由调用方先判空)")
    void blankProviderIdReturnsDefault(String empty) {
        registry.putChatClient("dashscope", mock(ChatClient.class));
        assertSame(registry.getDefaultChatClient(), registry.getChatClient(empty));
    }

    // ============== 多 provider 场景:dashscope + deepseek ==============

    @Test
    @DisplayName("多个 provider(dashscope + deepseek)都能正确注册和取出")
    void multipleProvidersIndependent() {
        ChatClient dash = mock(ChatClient.class);
        ChatClient deep = mock(ChatClient.class);
        EmbeddingModel dashEmbed = mock(EmbeddingModel.class);
        EmbeddingModel deepEmbed = mock(EmbeddingModel.class);

        registry.putChatClient("dashscope", dash);
        registry.putChatClient("deepseek",  deep);
        registry.putEmbeddingModel("dashscope", dashEmbed);
        registry.putEmbeddingModel("deepseek",  deepEmbed);

        assertSame(dash, registry.getChatClient("dashscope"));
        assertSame(deep, registry.getChatClient("deepseek"));
        assertSame(dashEmbed, registry.getEmbeddingModel("dashscope"));
        assertSame(deepEmbed, registry.getEmbeddingModel("deepseek"));

        // 切默认到 deepseek
        AppAiProperties newProps = baseProperties("deepseek",
                cfg("dashscope", DASHSCOPE_KEY, "qwen3.5-flash", "text-embedding-v3"),
                cfg("deepseek",  DEEPSEEK_KEY,  "deepseek-chat",  "text-embedding-v3"));
        registry.refresh(newProps);

        assertSame(deep, registry.getDefaultChatClient(), "refresh 切默认");
        assertSame(deepEmbed, registry.getDefaultEmbeddingModel());
        // 清理掉 dashscope(模拟用户在设置页禁用)
        AppAiProperties deepOnly = baseProperties("deepseek",
                cfg("deepseek", DEEPSEEK_KEY, "deepseek-chat", "text-embedding-v3"));
        registry.refresh(deepOnly);
        assertSame(deep, registry.getChatClient("dashscope"), "被禁用的 dashscope 回退到默认 deepseek");
    }

    // ============== 参数校验 ==============

    @ParameterizedTest(name = "putChatClient 拒收 providerId=\"{0}\"")
    @ValueSource(strings = {"", "   "})
    void putChatClientRejectsBlankProviderId(String empty) {
        assertThrows(IllegalArgumentException.class,
                () -> registry.putChatClient(empty, mock(ChatClient.class)));
    }

    @Test
    @DisplayName("putChatClient 拒收 null providerId / null client")
    void putChatClientRejectsNulls() {
        assertThrows(IllegalArgumentException.class, () -> registry.putChatClient(null, mock(ChatClient.class)));
        assertThrows(IllegalArgumentException.class, () -> registry.putChatClient("dashscope", null));
    }
}
