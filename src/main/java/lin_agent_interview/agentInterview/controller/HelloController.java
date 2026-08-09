package lin_agent_interview.agentInterview.controller;

import lin_agent_interview.agentInterview.common.ai.provider.LlmProviderRegistry;
import lin_agent_interview.agentInterview.common.exception.BusinessException;
import lin_agent_interview.agentInterview.common.result.BaseResponse;
import lin_agent_interview.agentInterview.common.result.ResultUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private final LlmProviderRegistry llmProviderRegistry;

    public HelloController(LlmProviderRegistry llmProviderRegistry) {
        this.llmProviderRegistry = llmProviderRegistry;
    }

    @GetMapping("/hello")
    public BaseResponse<String> hello() {
        return ResultUtils.success("Hello, World!");
    }

    /**
     * M2 验收端点：用默认 ChatClient 调一次 LLM，把响应正文返回。
     *
     * 用法：GET /api/test/chat?msg=hello
     * 默认 message 是 "hello"，不传 msg 也能调
     */
    @GetMapping("/api/test/chat")
    public BaseResponse<String> testChat(@RequestParam(value = "msg", defaultValue = "hello") String msg) {
        ChatClient client = llmProviderRegistry.getDefaultChatClient();
        if (client == null) {
            throw new BusinessException(500, "没有可用的默认 ChatClient,请检查 application-dev.yaml 的 app.ai.providers 配置");
        }

        String answer = client.prompt().user(msg).call().content();
        return ResultUtils.success(answer);
    }
}
