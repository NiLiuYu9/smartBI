package com.yupi.springbootinit;

import com.yupi.springbootinit.manager.RedisLimiterManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RateLimiterTest {
    @Autowired
    private RedisLimiterManager redisLimiterManager;

    @Test
    public void rateLimiterTest(){
        for (int i=0;i<5;i++) {
            redisLimiterManager.doRateLimit(1);
        }
    }
}
