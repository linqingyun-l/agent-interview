package lin_agent_interview.agentInterview.common.aspect;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
@Component

public class RateLimitScript {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> script;

    public RateLimitScript(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("scripts/ratelimit_sliding_window.lua"));
        this.script.setResultType(List.class);
    }

    /**
     * @return RateLimitResult(allowed, currentCount, retryAfterMs)
     */
    public RateLimitResult tryAcquire(String key, int windowSeconds, int limit) {
        List<Long> result = redisTemplate.execute(
            script,
            List.of(key),
            String.valueOf(windowSeconds),
            String.valueOf(limit),
            String.valueOf(System.currentTimeMillis()),
            UUID.randomUUID().toString()
        );
        boolean allowed = result.get(0) == 1L;
        long count = result.get(1);
        long retryAfterMs = result.get(2);
        return new RateLimitResult(allowed, count, retryAfterMs);
    }
}