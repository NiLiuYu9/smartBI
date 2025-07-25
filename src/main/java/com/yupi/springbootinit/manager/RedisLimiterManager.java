package com.yupi.springbootinit.manager;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/*
 * 通用提供RedisLimiter能力
 * Redisson限流
 * */
@Service
public class RedisLimiterManager {
    @Autowired
    private RedissonClient redissonClient;

    public void doRateLimit(long userId) {
        String key = "rate_limiter:user:" + userId;
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // 参数说明:
        // RateType.OVERALL - 限制所有实例的总速率
        // 5 - 令牌数量
        // 1 - 时间窗口
        // RateIntervalUnit.SECONDS - 时间单位（秒）
        rateLimiter.trySetRate(RateType.OVERALL, 3, 1, RateIntervalUnit.MINUTES);


        // 尝试获取1个令牌（非阻塞）
        boolean canOp = rateLimiter.tryAcquire(1);
        if (!canOp) {
            throw new BusinessException(ErrorCode.TOO_FREQUENT);
        }


        System.out.println("成功");
    }

}
