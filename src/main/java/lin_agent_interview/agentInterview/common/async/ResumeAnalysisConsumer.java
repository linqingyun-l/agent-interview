package lin_agent_interview.agentInterview.common.async;

import tools.jackson.databind.ObjectMapper;
import lin_agent_interview.agentInterview.common.constant.AsyncTaskStreamConstants;
import lin_agent_interview.agentInterview.common.constant.ErrorCode;
import lin_agent_interview.agentInterview.common.exception.BusinessException;
import lin_agent_interview.agentInterview.entity.ResumeEntity;
import lin_agent_interview.agentInterview.repository.ResumeRepository;
import lin_agent_interview.agentInterview.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResumeAnalysisConsumer extends AbstractStreamConsumer<ResumeAnalysisTask> {

    private final ResumeService resumeService;
    private final ResumeRepository resumeRepository;

    public ResumeAnalysisConsumer(StringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper,
                                  ResumeService resumeService,
                                  ResumeRepository resumeRepository) {
        super(redisTemplate, objectMapper,
              AsyncTaskStreamConstants.STREAM_RESUME_ANALYSIS,
              AsyncTaskStreamConstants.GROUP_RESUME,
              "consumer-resume-1");
        this.resumeService = resumeService;
        this.resumeRepository = resumeRepository;
    }

    @Override
    protected Class<ResumeAnalysisTask> payloadType() {
        return ResumeAnalysisTask.class;
    }

    @Override
    protected void processMessage(ResumeAnalysisTask task) {
        log.info("处理简历分析任务: resumeId={}", task.resumeId());

        // 1. 检查实体是否存在；不存在抛 RESUME_NOT_FOUND，由 AbstractStreamConsumer 统一 ACK 丢弃
        ResumeEntity entity = resumeRepository.findById(task.resumeId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));

        // 2. 更新状态为 PROCESSING
        entity.setAnalyzeStatus(AsyncTaskStreamConstants.STATUS_PROCESSING);
        resumeRepository.save(entity);

        // 3. 调 LLM 分析（具体实现见 ResumeService，简历模块落地）
        resumeService.doAnalysis(entity);

        // 4. 更新状态为 COMPLETED
        entity.setAnalyzeStatus(AsyncTaskStreamConstants.STATUS_COMPLETED);
        resumeRepository.save(entity);
    }

    @Override
    protected void markAsFailed(MapRecord<String, Object, Object> record, Exception e) {
        try {
            ResumeAnalysisTask task = objectMapper.readValue(
                (String) record.getValue().get(AsyncTaskStreamConstants.FIELD_PAYLOAD),
                ResumeAnalysisTask.class);
            resumeRepository.findById(task.resumeId()).ifPresent(entity -> {
                entity.setAnalyzeStatus(AsyncTaskStreamConstants.STATUS_FAILED);
                entity.setAnalyzeError(e.getMessage());
                resumeRepository.save(entity);
            });
        } catch (Exception ex) {
            log.error("标记失败状态异常", ex);
        }
    }
}
