package lin_agent_interview.agentInterview.common.exception;

import lin_agent_interview.agentInterview.common.constant.ErrorCode;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码（数值，详见 ErrorCode）
     */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 反查对应的 ErrorCode 枚举；找不到则返回 null。
     * 用于异步消费等需要按错误类型分支处理的场景。
     */
    public ErrorCode getErrorCode() {
        return Arrays.stream(ErrorCode.values())
            .filter(ec -> ec.getCode() == this.code)
            .findFirst()
            .orElse(null);
    }
}
