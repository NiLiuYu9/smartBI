package com.yupi.springbootinit.manager;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

/*
 * 通用提供RedisLimiter能力
 * Redisson限流
 * */
@Service
public class RedisLimiterManager {
    private static final long MAX_IDLE_TIME = 10; // 限流器空闲时间（分钟）
    private static final long MAX_KEY_TTL = 20;  // 键的最大生存时间（分钟）

    @Autowired
    private RedissonClient redissonClient;

    public void doRateLimit(long userId) {
        String key = "rate_limiter:user:" + userId;
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // 首次初始化时设置速率 + TTL
        // 参数说明:
        // RateType.OVERALL - 限制所有实例的总速率
        // 5 - 令牌数量
        // 1 - 时间窗口
        // RateIntervalUnit.SECONDS - 时间单位（秒）
        if (rateLimiter.trySetRate(RateType.OVERALL, 3, 1, RateIntervalUnit.SECONDS)) {
            // 为新键设置TTL（防止永远存在）
            rateLimiter.expire(Duration.ofMinutes(MAX_KEY_TTL));
        }

        // 每次访问时刷新空闲时间（重置TTL倒计时）
        rateLimiter.expire(Duration.ofMinutes(MAX_IDLE_TIME));

        if (!rateLimiter.tryAcquire(1)) {
            throw new BusinessException(ErrorCode.TOO_FREQUENT);
        }
    }
}