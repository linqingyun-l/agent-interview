package lin_agent_interview.agentInterview.common.aspect;

import lin_agent_interview.agentInterview.common.annotation.RateLimit;
import lin_agent_interview.agentInterview.common.annotation.RateLimits;
import lin_agent_interview.agentInterview.common.constant.ErrorCode;
import lin_agent_interview.agentInterview.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 限流切面类，用于处理带有@RateLimit和@RateLimits注解的方法
 * 实现基于滑动窗口的限流算法，支持全局、IP和用户维度的限流控制
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitScript rateLimitScript;  // Redis限流脚本执行器
    private final HttpServletRequestHolder requestHolder;  // 提供当前请求上下文

    /**
     * 处理单个@RateLimit注解的方法
     * @param pjp 切点信息
     * @param rateLimit 限注解信息
     * @return 方法执行结果
     * @throws Throwable 可能抛出的异常
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        // 构建限流key
        String key = buildKey(pjp, rateLimit);
        // 尝试获取限流许可
        RateLimitResult result = rateLimitScript.tryAcquire(
                key, rateLimit.windowSeconds(), rateLimit.limit()
        );

        // 如果未获取到许可，抛出限流异常
        if (!result.allowed()) {
            long retryAfterSeconds = (result.retryAfterMs() + 999) / 1000;
            throw new BusinessException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "请求过于频繁，请在 " + retryAfterSeconds + " 秒后重试"
            );
        }
        return pjp.proceed();
    }

    /**
     * 处理@RateLimits注解（多个限流规则）的方法
     * 会依次检查所有限流规则，任一规则不通过即拒绝请求
     * @param pjp 切点信息
     * @param rateLimits 多个限流注解信息
     * @return 方法执行结果
     * @throws Throwable 可能抛出的异常
     */
    @Around("@annotation(rateLimits)")
    public Object aroundMulti(ProceedingJoinPoint pjp, RateLimits rateLimits) throws Throwable {
        // 多个注解顺序校验（任一不通过即拒绝）
        for (RateLimit rl : rateLimits.value()) {
            String key = buildKey(pjp, rl);
            RateLimitResult r = rateLimitScript.tryAcquire(key, rl.windowSeconds(), rl.limit());
            if (!r.allowed()) {
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "请求过于频繁");
            }
        }
        return pjp.proceed();
    }

    /**
     * 构建限流key
     * @param pjp 切点信息
     * @param rl 限流注解信息
     * @return 构建好的限流key
     */
    private String buildKey(ProceedingJoinPoint pjp, RateLimit rl) {
        // 获取方法全限定名
        String methodKey = pjp.getSignature().getDeclaringTypeName()
                + ":" + pjp.getSignature().getName();
        // 根据维度类型获取对应的维度值
        String dimensionValue = switch (rl.dimension()) {
            case GLOBAL -> "global";
            case IP -> requestHolder.getClientIp();
            case USER -> requestHolder.getCurrentUserId();  // 本项目简化为 anonymous 或固定值
        };
        // 组装完整的限流key
        return "ratelimit:" + methodKey + ":" + rl.key() + ":" + dimensionValue;
    }
}
