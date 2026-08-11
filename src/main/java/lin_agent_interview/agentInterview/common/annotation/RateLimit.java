package lin_agent_interview.agentInterview.common.annotation;

import java.lang.annotation.*;

/**
 * 自定义注解：RateLimit
 * 用于标记需要限流的方法，支持重复注解，可以在同一个方法上使用多个RateLimit注解
 * 该注解可以针对不同的维度进行限流控制，如全局、IP或用户维度
 */
@Target(ElementType.METHOD)             // 指定该注解只能用于方法上
@Retention(RetentionPolicy.RUNTIME)      // 指定该注解在运行时仍然保留，可通过反射获取
@Repeatable(RateLimits.class)            //必须有，否则同方法只能用一个
public @interface RateLimit {

    /** 限流维度：global / ip / user */
    Dimension dimension() default Dimension.GLOBAL;

    /** 时间窗口（秒） */
    int windowSeconds() default 60;

    /** 窗口内允许的请求数 */
    int limit() default 10;

    /** 限流维度 key 前缀（同一方法多个注解时区分） */
    String key() default "";

    enum Dimension { GLOBAL, IP, USER }
}

