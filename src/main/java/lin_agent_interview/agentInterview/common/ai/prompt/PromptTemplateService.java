package lin_agent_interview.agentInterview.common.ai.prompt;

import lin_agent_interview.agentInterview.common.constant.ErrorCode;
import lin_agent_interview.agentInterview.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptTemplateService {

    private final Map<String, STGroup> groups = new ConcurrentHashMap<>();

    /**
     * 加载并渲染 prompt。
     * @param templateName 如 "resume-analysis-system"
     * @param model 参数 Map（key 对应模板中的变量名）
     */
    public String render(String templateName, Map<String, Object> model) {
        STGroup group = groups.computeIfAbsent("prompts", this::loadGroup);
        ST st = group.getInstanceOf(templateName);
        if (st == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Prompt 模板不存在: " + templateName);
        }
        model.forEach(st::add);
        return st.render();
    }

    private STGroup loadGroup(String dir) {
        // 从 classpath:prompts/ 加载所有 .st 文件
        URL url = null;
        try {
            url = ResourceUtils.getURL("classpath:" + dir + "/");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return new STGroupFile(url, "UTF-8", '$', '$');
    }
}