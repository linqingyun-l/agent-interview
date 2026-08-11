package lin_agent_interview.agentInterview.common.async;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisCommandTimeoutException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lin_agent_interview.agentInterview.common.constant.AsyncTaskStreamConstants;
import lin_agent_interview.agentInterview.common.constant.ErrorCode;
import lin_agent_interview.agentInterview.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Thread.sleep;
import static org.springframework.data.redis.connection.stream.Consumer.from;

@Slf4j

public abstract class AbstractStreamConsumer<T> {

    private static final long POLL_TIMEOUT_MS = 5000;
    private static final int BATCH_SIZE = 5;

    private final StringRedisTemplate redisTemplate;
    protected final ObjectMapper objectMapper;
    protected final String streamKey;
    protected final String consumerGroup;
    protected final String consumerName;

    private volatile boolean running = true;

    protected AbstractStreamConsumer(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     String streamKey,
                                     String consumerGroup,
                                     String consumerName) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
        this.consumerName = consumerName;
    }

    public String getStreamKey()       { return streamKey; }
    public String getConsumerGroup()   { return consumerGroup; }
    public String getConsumerName()    { return consumerName; }

    /** 业务处理逻辑，由子类实现 */
    protected abstract void processMessage(T payload);

    /** 实体不存在时是否 ACK 丢弃（默认 true） */
    protected boolean ackWhenEntityMissing() {
        return true;
    }

    /** 判断是否为「实体不存在」类异常；子类可按需覆盖扩展 */
    protected boolean isEntityMissingError(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return code == ErrorCode.RESUME_NOT_FOUND
            || code == ErrorCode.INTERVIEW_NOT_FOUND
            || code == ErrorCode.SKILL_NOT_FOUND
            || code == ErrorCode.KB_NOT_FOUND
            || code == ErrorCode.PROVIDER_NOT_FOUND
            || code == ErrorCode.VOICE_SESSION_NOT_FOUND
            || code == ErrorCode.FILE_NOT_FOUND
            || code == ErrorCode.NOT_FOUND;
    }

    /** 启动时执行 */
    @PostConstruct
    public void start() {
        // 1. 确保 Stream 存在
        if (Boolean.FALSE.equals(redisTemplate.hasKey(streamKey))) {
            // 不创建空 Stream，等第一条消息自然创建
        }
        // 2. 确保消费者组存在
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), consumerGroup);
        } catch (DataAccessException e) {
            // 消费者组已存在，忽略 BUSYGROUP 错误
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) throw e;
        }
        // 3. 启动消费线程
        Thread.ofVirtual().name("consumer-" + streamKey).start(this::pollLoop);
        log.info("消费者启动: stream={}, group={}", streamKey, consumerGroup);
    }

    @PreDestroy
    public void stop() {
        running = false;
    }

    private void pollLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                pollOnce();
            } catch (Exception e) {
                log.error("消费循环异常", e);
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void pollOnce() {
        // XREADGROUP GROUP <group> <consumer> COUNT <n> BLOCK <ms> STREAMS <streamKey> >
        List<MapRecord<String, Object, Object>> records = null;
        try {
            records = redisTemplate.opsForStream().read(
                from(consumerGroup, consumerName),
                StreamReadOptions.empty().count(BATCH_SIZE).block(Duration.ofMillis(POLL_TIMEOUT_MS)),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed())
            );
        } catch (RedisCommandTimeoutException ignored) {
            // BLOCK 超时是正常的，继续下一轮
        }

        if (records == null || records.isEmpty()) return;

        for (MapRecord<String, Object, Object> record : records) {
            try {
                handleRecord(record);
            } catch (Exception e) {
                log.error("处理消息异常: recordId={}", record.getId(), e);
                // 不 ACK，让消息留在 pending list，等下一轮或手动处理
            }
        }
    }

    private void handleRecord(MapRecord<String, Object, Object> record) throws Exception {
        Map<Object, Object> body = record.getValue();
        String payloadJson = (String) body.get(AsyncTaskStreamConstants.FIELD_PAYLOAD);
        int retryCount = Integer.parseInt((String) body.getOrDefault(
            AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"));

        try {
            T payload = objectMapper.readValue(payloadJson, payloadType());
            processMessage(payload);
            // 处理成功，ACK
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
        } catch (BusinessException e) {
            if (isEntityMissingError(e)) {
                if (ackWhenEntityMissing()) {
                    log.warn("实体不存在，丢弃消息: {}", e.getMessage());
                    redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
                }
            } else {
                retryOrFail(record, retryCount, e);
            }
        } catch (Exception e) {
            retryOrFail(record, retryCount, e);
        }
    }

    private void retryOrFail(MapRecord<String, Object, Object> record, int retryCount, Exception e) {
        if (retryCount < AsyncTaskStreamConstants.MAX_RETRY_COUNT) {
            // 重试：重新投递 + retryCount + 1
            String payloadJson = (String) record.getValue().get(AsyncTaskStreamConstants.FIELD_PAYLOAD);
            try {
                T payload = objectMapper.readValue(payloadJson, payloadType());
                send(payload, retryCount + 1);
                log.warn("任务重试: retryCount={}, recordId={}", retryCount + 1, record.getId());
            } catch (Exception ex) {
                log.error("重试投递失败", ex);
            }
            // ACK 原消息
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
        } else {
            log.error("任务重试 {} 次仍失败，标记 FAILED: recordId={}",
                AsyncTaskStreamConstants.MAX_RETRY_COUNT, record.getId(), e);
            // 更新业务表状态为 FAILED（由子类决定如何标记）
            markAsFailed(record, e);
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
        }
    }

    /** 向 Stream 投递一条消息（重试时由本类内部调用；子类也可主动 send） */
    protected void send(T payload, int retryCount) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put(AsyncTaskStreamConstants.FIELD_PAYLOAD, objectMapper.writeValueAsString(payload));
            body.put(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount));
            redisTemplate.opsForStream().add(streamKey, body);
        } catch (JacksonException ex) {
            throw new RuntimeException("序列化 payload 失败", ex);
        }
    }

    protected void markAsFailed(MapRecord<String, Object, Object> record, Exception e) {
        // 默认无操作，子类按需覆盖
    }

    protected abstract Class<T> payloadType();
}
