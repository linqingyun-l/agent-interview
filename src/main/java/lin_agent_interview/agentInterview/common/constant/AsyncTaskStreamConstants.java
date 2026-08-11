package lin_agent_interview.agentInterview.common.constant;

public final class AsyncTaskStreamConstants {

    private AsyncTaskStreamConstants() {}

    // Stream 名（业务相关）
    public static final String STREAM_RESUME_ANALYSIS = "stream:resume:analysis";
    public static final String STREAM_KNOWLEDGE_VECTORIZE = "stream:knowledge:vectorize";
    public static final String STREAM_INTERVIEW_EVALUATION = "stream:interview:evaluation";
    public static final String STREAM_PDF_EXPORT = "stream:pdf:export";

    // 消费者组
    public static final String GROUP_RESUME = "group:resume";
    public static final String GROUP_KNOWLEDGE = "group:knowledge";
    public static final String GROUP_INTERVIEW = "group:interview";
    public static final String GROUP_PDF = "group:pdf";

    // 任务状态
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    // 重试
    public static final int MAX_RETRY_COUNT = 3;

    // 消息字段
    public static final String FIELD_TASK_ID = "taskId";
    public static final String FIELD_PAYLOAD = "payload";
    public static final String FIELD_RETRY_COUNT = "retryCount";
    public static final String FIELD_CREATED_AT = "createdAt";
}