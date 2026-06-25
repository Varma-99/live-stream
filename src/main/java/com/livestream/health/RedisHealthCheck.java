package com.livestream.health;

import com.codahale.metrics.health.HealthCheck;
import com.livestream.redis.RedisService;

/** Admin health check — PING when Redis is enabled. */
public class RedisHealthCheck extends HealthCheck {

    private final RedisService redisService;

    public RedisHealthCheck(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    protected Result check() {
        if (!redisService.isEnabled()) {
            return Result.unhealthy("Redis pool not started");
        }
        if (redisService.ping()) {
            return Result.healthy("Redis PING ok");
        }
        return Result.unhealthy("Redis PING failed");
    }
}
