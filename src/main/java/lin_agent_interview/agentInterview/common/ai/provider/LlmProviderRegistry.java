package lin_agent_interview.agentInterview.common.ai.provider;

import lin_agent_interview.agentInterview.config.AppAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据 providerId 获取 ChatClient 或 EmbeddingModel。
 * 默认 provider 来自配置 app.ai.default-provider。
 *
 * 注册方式：启动期由 LlmProviderInitializer 调用 putChatClient / putEmbeddingModel 写入。
 */
@Component
@Slf4j
public class LlmProviderRegistry {

    private final Map<String, ChatClient> chatClients = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingModels = new ConcurrentHashMap<>();
    private volatile ChatClient defaultChatClient;
    private volatile EmbeddingModel defaultEmbeddingModel;
    private volatile AppAiProperties properties;

    public LlmProviderRegistry(AppAiProperties properties) {
        this.properties = properties;
    }

    // ===================== 对外 API（业务调用） =====================

    /** 获取指定 provider 的 ChatClient，找不到则用默认 */
    public ChatClient getChatClient(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return defaultChatClient;
        }
        ChatClient client = chatClients.get(providerId);
        if (client == null) {
            log.warn("Provider {} 不存在，使用默认", providerId);
            return defaultChatClient;
        }
        return client;
    }

    /** 获取默认 ChatClient */
    public ChatClient getDefaultChatClient() {
        return defaultChatClient;
    }

    /** 获取指定 provider 的 EmbeddingModel，找不到或未指定则用默认 */
    public EmbeddingModel getEmbeddingModel(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return defaultEmbeddingModel;
        }
        EmbeddingModel embeddingModel = embeddingModels.get(providerId);
        if (embeddingModel == null) {
            log.warn("Provider {} 不存在,使用默认 {}", providerId, properties.getDefaultProvider());
            return defaultEmbeddingModel;
        }
        return embeddingModel;
    }

    /** 获取默认 EmbeddingModel */
    public EmbeddingModel getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    // ===================== 运行时注册（供 LlmProviderInitializer 调用） =====================

    /**
     * 注册一个 ChatClient 到 registry 中。
     *
     * 调用方：LlmProviderInitializer.initChatClients() 在 ApplicationRunner.run() 中调用。
     *
     * 行为：
     *   - 覆盖式注册（同一 providerId 多次调用会用最新 client 替换旧的）
     *   - 若注册的是当前默认 provider，自动同步更新 defaultChatClient 引用
     *
     * 线程安全：与 refresh 共用同一把锁，禁止与 put/refresh 并发。
     *
     * @param providerId 唯一标识（与配置 app.ai.providers 的 key 一致），如 "dashscope"
     * @param client     ChatClient 实例，不能为 null
     */
    public synchronized void putChatClient(String providerId, ChatClient client) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId 不能为空");
        }
        if (client == null) {
            throw new IllegalArgumentException("ChatClient 实例不能为 null (providerId=" + providerId + ")");
        }

        ChatClient previous = chatClients.put(providerId, client);
        if (previous != null) {
            log.info("覆盖注册 ChatClient: providerId={} (旧实例被替换)", providerId);
        } else {
            log.info("注册 ChatClient: providerId={}, class={}",
                    providerId, client.getClass().getSimpleName());
        }

        // 如果注册的是默认 provider，同步更新默认引用
        String defaultId = properties.getDefaultProvider();
        if (defaultId != null && defaultId.equals(providerId)) {
            this.defaultChatClient = client;
            log.info("同步设置默认 ChatClient: providerId={}", providerId);
        }
    }

    /**
     * 注册一个 EmbeddingModel 到 registry 中，语义与 putChatClient 一致。
     */
    public synchronized void putEmbeddingModel(String providerId, EmbeddingModel model) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId 不能为空");
        }
        if (model == null) {
            throw new IllegalArgumentException("EmbeddingModel 实例不能为 null (providerId=" + providerId + ")");
        }

        EmbeddingModel previous = embeddingModels.put(providerId, model);
        if (previous != null) {
            log.info("覆盖注册 EmbeddingModel: providerId={} (旧实例被替换)", providerId);
        } else {
            log.info("注册 EmbeddingModel: providerId={}, class={}",
                    providerId, model.getClass().getSimpleName());
        }

        // 如果注册的是默认 provider，同步更新默认引用
        String defaultId = properties.getDefaultProvider();
        if (defaultId != null && defaultId.equals(providerId)) {
            this.defaultEmbeddingModel = model;
            log.info("同步设置默认 EmbeddingModel: providerId={}", providerId);
        }
    }

    // ===================== 运行时刷新（设置页切换默认 provider 后调用） =====================

    /**
     * 刷新方法，用于在运行时更新 AI 属性配置。
     * 当用户在设置页面切换默认 provider 后调用此方法。
     *
     * 完成的 3 件事：
     *   1) 重新解析默认 ChatClient / EmbeddingModel 引用
     *   2) 清理掉在新 providers map 中已不存在的 provider（被禁用）
     *   3) 更新 properties 引用
     *
     * 注意：本方法只能移除现有 provider；新增 provider 需要重启或重新调用 put* 注册。
     *
     * @param newProperties 新的 AI 属性配置对象
     */
    public synchronized void refresh(AppAiProperties newProperties) {
        if (newProperties == null) {
            log.warn("refresh 被传入 null properties，跳过");
            return;
        }

        String oldDefault = this.properties.getDefaultProvider();
        String newDefault = newProperties.getDefaultProvider();
        log.info("刷新 LlmProviderRegistry: defaultProvider {} → {}", oldDefault, newDefault);

        // 1. 重新解析默认 ChatClient / EmbeddingModel
        ChatClient newDefaultChatClient = chatClients.get(newDefault);
        if (newDefaultChatClient == null) {
            log.warn("新默认 provider '{}' 未注册为 ChatClient，保留旧默认 '{}'", newDefault, oldDefault);
        } else {
            this.defaultChatClient = newDefaultChatClient;
        }

        EmbeddingModel newDefaultEmbedding = embeddingModels.get(newDefault);
        if (newDefaultEmbedding == null) {
            log.warn("新默认 provider '{}' 未注册为 EmbeddingModel，保留旧默认 '{}'", newDefault, oldDefault);
        } else {
            this.defaultEmbeddingModel = newDefaultEmbedding;
        }

        // 2. 清理已被禁用的 provider
        Set<String> enabledIds = newProperties.getProviders() != null
                ? newProperties.getProviders().keySet()
                : Set.of();

        int beforeChatCount = chatClients.size();
        int beforeEmbeddingCount = embeddingModels.size();

        chatClients.keySet().retainAll(enabledIds);
        embeddingModels.keySet().retainAll(enabledIds);

        // 3. 更新 properties 引用
        this.properties = newProperties;

        log.info("刷新完成: ChatClient {} → {}, EmbeddingModel {} → {}, 默认={}",
                beforeChatCount, chatClients.size(),
                beforeEmbeddingCount, embeddingModels.size(),
                newDefault);
    }
}
