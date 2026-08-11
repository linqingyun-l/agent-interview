package lin_agent_interview.agentInterview.common.async;

import tools.jackson.databind.ObjectMapper;
import lin_agent_interview.agentInterview.common.constant.AsyncTaskStreamConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResumeAnalysisProducer extends AbstractStreamProducer<ResumeAnalysisTask> {

    public ResumeAnalysisProducer(StringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper, AsyncTaskStreamConstants.STREAM_RESUME_ANALYSIS);
    }
}