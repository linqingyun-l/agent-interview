package lin_agent_interview.agentInterview.common.constant;

public enum ErrorCode {
    // 通用
    SUCCESS(200, "success"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    PARAM_INVALID(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    RATE_LIMIT_EXCEEDED(429, "请求过于频繁"),

    // Resume 模块 (10xxx)
    RESUME_NOT_FOUND(10001, "简历不存在"),
    RESUME_FILE_TYPE_INVALID(10002, "简历文件类型不支持"),
    RESUME_FILE_TOO_LARGE(10003, "简历文件过大"),
    RESUME_DUPLICATE(10004, "简历内容重复"),
    RESUME_ANALYSIS_FAILED(10005, "简历分析失败"),

    // Interview 模块 (11xxx)
    INTERVIEW_NOT_FOUND(11001, "面试会话不存在"),
    INTERVIEW_INVALID_STATE(11002, "面试状态不合法"),
    INTERVIEW_QUESTION_GENERATE_FAILED(11003, "题目生成失败"),
    INTERVIEW_EVALUATION_FAILED(11004, "面试评估失败"),
    SKILL_NOT_FOUND(11005, "面试方向不存在"),

    // KnowledgeBase 模块 (12xxx)
    KB_NOT_FOUND(12001, "知识库不存在"),
    KB_DOCUMENT_PARSE_FAILED(12002, "文档解析失败"),
    KB_VECTORIZATION_FAILED(12003, "向量化失败"),
    KB_QUERY_FAILED(12004, "知识库检索失败"),

    // LLM Provider (13xxx)
    PROVIDER_NOT_FOUND(13001, "LLM Provider 不存在"),
    PROVIDER_CONNECTION_FAILED(13002, "LLM Provider 连通性测试失败"),

    // VoiceInterview (14xxx)
    VOICE_SESSION_NOT_FOUND(14001, "语音会话不存在"),
    VOICE_ASR_FAILED(14002, "语音识别失败"),
    VOICE_TTS_FAILED(14003, "语音合成失败"),

    // File/Storage (15xxx)
    FILE_UPLOAD_FAILED(15001, "文件上传失败"),
    FILE_NOT_FOUND(15002, "文件不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}