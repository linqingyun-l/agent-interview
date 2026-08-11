package lin_agent_interview.agentInterview.common.async;

import cn.hutool.core.lang.UUID;
import tools.jackson.databind.ObjectMapper;
import lin_agent_interview.agentInterview.common.constant.AsyncTaskStreamConstants;
import lin_agent_interview.agentInterview.common.constant.ErrorCode;
import lin_agent_interview.agentInterview.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
@Slf4j

public abstract class AbstractStreamProducer<T> {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    protected final String streamKey;

    protected AbstractStreamProducer(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     String streamKey) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.streamKey = streamKey;
    }

    /**
     * 发送任务到 Stream。
     */
    public String send(T payload) {
        return send(UUID.randomUUID().toString(), payload, 0);
    }

    public String send(String taskId, T payload, int retryCount) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            Map<String, String> body = Map.of(
                AsyncTaskStreamConstants.FIELD_TASK_ID, taskId,
                AsyncTaskStreamConstants.FIELD_PAYLOAD, json,
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount),
                AsyncTaskStreamConstants.FIELD_CREATED_AT, String.valueOf(System.currentTimeMillis())
            );
            // XADD streamKey * field value ...
            RecordId recordId = redisTemplate.opsForStream().add(streamKey, body);
            log.info("生产者 {} 投递任务成功: taskId={}, recordId={}", streamKey, taskId, recordId);
            return taskId;
        } catch (Exception e) {
            log.error("生产者 {} 投递失败", streamKey, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "异步任务投递失败");
        }
    }
}