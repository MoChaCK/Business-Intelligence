package com.ck.manager;

import com.ck.common.ErrorCode;
import com.ck.exception.BusinessException;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 专门提供 RedisLimiter 限流基础服务
 */
@Service
public class RedisLimiterManager {

    @Resource
    public RedissonClient redissonClient;

    /**
     * 限流操作
     * @param key
     */
    public void doRateLimit(String key){
        // 创建限流器
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        // 设置每秒最多访问两次
        rateLimiter.trySetRate(RateType.OVERALL, 2, 1, RateIntervalUnit.SECONDS);
        // 每当一个操作来，就请求一个令牌
        boolean canOp = rateLimiter.tryAcquire(1);
        if(!canOp){
            throw new BusinessException(ErrorCode.TO_MANY_REQUEST);
        }

    }
}
