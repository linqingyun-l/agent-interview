package lin_agent_interview.agentInterview.common.exception;

import com.healthmarketscience.jackcess.ConstraintViolationException;
import lin_agent_interview.agentInterview.common.constant.ErrorCode;
import lin_agent_interview.agentInterview.common.result.BaseResponse;
import lin_agent_interview.agentInterview.common.result.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.BindException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        // 注意：业务异常仍然返回 HTTP 200，错误信息在 Result.code 中
        return ResultUtils.error(e.getCode(),"系统错误");
    }

    /** @Valid 校验失败（@RequestBody） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("参数校验失败");
        return ResultUtils.error(ErrorCode.PARAM_INVALID.getCode(),msg);
    }

    /** @Valid 校验失败（@RequestParam 等） */
    @ExceptionHandler(ConstraintViolationException.class)
    public BaseResponse<?> handleConstraint(ConstraintViolationException e) {
        return ResultUtils.error(ErrorCode.PARAM_INVALID.getCode(),"参数校验失败");
    }

    /** 表单绑定异常 */
    @ExceptionHandler(BindException.class)
    public BaseResponse<?> handleBind(BindException e) {
        return ResultUtils.error(ErrorCode.PARAM_INVALID.getCode(), e.getMessage());
    }

    /** 文件上传过大 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public BaseResponse<?> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ResultUtils.error(ErrorCode.FILE_UPLOAD_FAILED.getCode(), "上传文件过大");
    }

    /** 兜底：未捕获的异常 */
    @ExceptionHandler(Exception.class)
    public BaseResponse<?> handleUnknown(Exception e) {
        log.error("未捕获异常", e);
        return ResultUtils.error(ErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误");
    }
}