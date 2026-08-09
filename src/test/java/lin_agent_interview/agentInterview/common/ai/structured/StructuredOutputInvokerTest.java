package lin_agent_interview.agentInterview.common.ai.structured;

import lin_agent_interview.agentInterview.common.ai.provider.LlmProviderRegistry;
import lin_agent_interview.agentInterview.config.AppAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * StructuredOutputInvoker 单元测试 —— 核心场景
 *
 * 覆盖验收清单要求:
 *   ① invoke 能从一个简单 record(如 Greeting) 反序列化成功
 *   ② mock 重试场景: 首次失败 → 重试成功
 *   ③ 默认 provider / 显式 provider 两种调用路径
 *   ④ registry 没 client 时抛 IllegalStateException
 *
 * 不依赖环境变量/网络 —— 全部 mock。
 */
@DisplayName("StructuredOutputInvoker 单元测试")
class StructuredOutputInvokerTest {

    /** 简单 record,验证 Spring AI entity() 反序列化 */
    record Greeting(String text) {}

    private LlmProviderRegistry registry;
    private ChatClient chatClient;
    private StructuredOutputInvoker invoker;

    @BeforeEach
    void setUp() {
        AppAiProperties props = new AppAiProperties();
        props.setDefaultProvider("dashscope");
        registry = mock(LlmProviderRegistry.class);
        // RETURNS_DEEP_STUBS 让 mock 自动处理 prompt().user().call().entity() 整条链
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

        when(registry.getDefaultChatClient()).thenReturn(chatClient);
        when(registry.getChatClient("dashscope")).thenReturn(chatClient);
        when(registry.getChatClient("missing")).thenReturn(null);

        // 默认重试 2 次
        invoker = new StructuredOutputInvoker(registry);
    }

    // ============== 验收点 ①:简单 record 反序列化成功 ==============

    @Test
    @DisplayName("从一个简单 record(Greeting)反序列化成功")
    void deserializeSimpleRecord() {
        when(chatClient.prompt().user(anyString()).call().entity(Greeting.class))
                .thenReturn(new Greeting("hi from llm"));

        Greeting result = invoker.invoke("say hello", Greeting.class);

        assertNotNull(result);
        assertEquals("hi from llm", result.text());
        verify(registry).getDefaultChatClient();
        verify(chatClient.prompt().user("say hello").call()).entity(Greeting.class);
    }

    // ============== 验收点 ②:mock 重试场景 ==============

    @Test
    @DisplayName("首次失败,第二次成功 —— 验证重试逻辑")
    void retryOnFirstFailure() {
        // 第 1 次 entity() 抛错,第 2 次成功
        when(chatClient.prompt().user(anyString()).call().entity(Greeting.class))
                .thenThrow(new RuntimeException("schema mismatch: missing field 'text'"))
                .thenReturn(new Greeting("eventual success"));

        // 重试 2 次 (即最多 3 次调用),只要有一次成功即可
        StructuredOutputInvoker retryer = new StructuredOutputInvoker(registry, 2);

        Greeting result = retryer.invoke("hi", Greeting.class);

        assertNotNull(result);
        assertEquals("eventual success", result.text());
        // entity() 被调了 2 次 (1 失败 + 1 成功)
        verify(chatClient.prompt().user(anyString()).call(), times(2)).entity(Greeting.class);
    }

    @Test
    @DisplayName("重试耗尽仍失败 —— 抛 IllegalStateException")
    void exhaustRetriesThrows() {
        when(chatClient.prompt().user(anyString()).call().entity(Greeting.class))
                .thenThrow(new RuntimeException("always failing"));

        StructuredOutputInvoker retryer = new StructuredOutputInvoker(registry, 2);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> retryer.invoke("test", Greeting.class));
        assertTrue(ex.getMessage().contains("StructuredOutput 解析失败"));
        assertTrue(ex.getMessage().contains("Greeting"));
        // 首次 + 2 次重试 = 3 次
        verify(chatClient.prompt().user(anyString()).call(), times(3)).entity(Greeting.class);
    }

    // ============== 补充:显式 provider / registry 异常 ==============

    @Test
    @DisplayName("显式指定 providerId,registry.getChatClient(providerId) 被调用")
    void invokeWithExplicitProviderId() {
        when(chatClient.prompt().user(anyString()).call().entity(Greeting.class))
                .thenReturn(new Greeting("from dashscope"));

        Greeting result = invoker.invoke("dashscope", "hi", Greeting.class);

        assertEquals("from dashscope", result.text());
        verify(registry, never()).getDefaultChatClient();
        verify(registry).getChatClient("dashscope");
    }

    @Test
    @DisplayName("providerId 显式传 null/blank 时回退到默认 provider")
    void blankProviderIdFallsBackToDefault() {
        when(chatClient.prompt().user(anyString()).call().entity(Greeting.class))
                .thenReturn(new Greeting("default"));

        invoker.invoke("", "hi", Greeting.class);
        invoker.invoke("   ", "hi", Greeting.class);
        invoker.invoke(null, "hi", Greeting.class);

        verify(registry, times(3)).getDefaultChatClient();
    }

    @Test
    @DisplayName("registry 返回 null 时 invoke 抛 IllegalStateException,不进入重试")
    void noClientThrowsIllegalState() {
        AppAiProperties props = new AppAiProperties();
        props.setDefaultProvider("dashscope");
        LlmProviderRegistry emptyRegistry = mock(LlmProviderRegistry.class);
        when(emptyRegistry.getDefaultChatClient()).thenReturn(null);

        StructuredOutputInvoker empty = new StructuredOutputInvoker(emptyRegistry);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> empty.invoke("test", Greeting.class));
        assertTrue(ex.getMessage().contains("没有可用的 ChatClient"));
    }
}
