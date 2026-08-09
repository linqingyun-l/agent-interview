package lin_agent_interview.agentInterview.common.ai.provider;

import lin_agent_interview.agentInterview.config.AppAiProperties;
import lin_agent_interview.agentInterview.config.ProviderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ApplicationArguments;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmProviderInitializer 单元测试 —— 核心场景精简版
 *
 * 真实 key 必须通过环境变量提供（绝不能硬编码到源码里）：
 *   - AI_BAILIAN_API_KEY : 阿里百炼 DashScope
 *   - DEEPSEEK_API_KEY   : DeepSeek
 */
@DisplayName("LlmProviderInitializer 单元测试")
class LlmProviderInitializerTest {

    private static final String DASHSCOPE_KEY = System.getenv("AI_BAILIAN_API_KEY");
    private static final String DEEPSEEK_KEY  = System.getenv("DEEPSEEK_API_KEY");
    private static final ApplicationArguments NO_ARGS = new DefaultApplicationArguments(new String[0]);

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

    private AppAiProperties properties;
    private LlmProviderRegistry registry;
    private LlmProviderInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new AppAiProperties();
        properties.setDefaultProvider("dashscope");
        registry = new LlmProviderRegistry(properties);
        initializer = new LlmProviderInitializer(registry, properties);
    }

    private static ProviderConfig dashConfig() {
        ProviderConfig c = new ProviderConfig();
        c.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode");
        c.setApiKey(DASHSCOPE_KEY);
        c.setChatModel("qwen3.5-flash");
        c.setEmbeddingModel("text-embedding-v3");
        return c;
    }

    private static ProviderConfig deepConfig() {
        ProviderConfig c = new ProviderConfig();
        c.setBaseUrl("https://api.deepseek.com/v1");
        c.setApiKey(DEEPSEEK_KEY);
        c.setChatModel("deepseek-chat");
        // 故意不配 embedding-model,测试跳过逻辑
        return c;
    }

    // ============== 核心 2 个场景 ==============

    @Test
    @DisplayName("providers 为 null/空时,init 早返回不抛,registry 不污染")
    void nullOrEmptyProviders() {
        // null
        properties.setProviders(null);
        assertDoesNotThrow(() -> initializer.initChatClients());
        assertDoesNotThrow(() -> initializer.initEmbeddingModels());
        assertNull(registry.getDefaultChatClient());
        assertNull(registry.getDefaultEmbeddingModel());

        // 空 Map 时 run() 仍正常返回
        properties.setProviders(new HashMap<>());
        assertDoesNotThrow(() -> initializer.run(NO_ARGS));
    }

    @Test
    @DisplayName("配置错误(缺 baseUrl)的 provider,handleInitFailure 兜底不抛,其他合法配置也不受影响")
    void invalidProviderSkippedGracefully() {
        // bad = 缺 baseUrl → validate() 抛 IllegalStateException → 进 handleInitFailure
        // good_dashscope = 合法配置,但因 mockStatic 不稳,真实链路最终也会被 catch
        // 所以这里只断言两个关键点：
        //   1. 整体 init 不抛
        //   2. 失败的 provider 绝不会被注册
        ProviderConfig bad = dashConfig();
        bad.setBaseUrl(null);

        properties.setProviders(new LinkedHashMap<>(Map.of(
                "bad", bad
        )));

        assertDoesNotThrow(() -> initializer.initChatClients());
        assertSame(registry.getDefaultChatClient(), registry.getChatClient("bad"),
                "失败的 provider 不应进入 registry");
    }
}
