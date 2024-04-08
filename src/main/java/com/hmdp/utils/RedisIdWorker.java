package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * description:
 * 1.全局唯一ID生成策略
 * - UUID
 * - Redis自增
 * - snowflake algorithm
 * - db increment
 * 2. Redis自增ID策略
 * - 每天一Key，利于统计订单量
 * - Id构造为 时间戳 + 计数器
 * @author xuxin
 * @since 2024/4/8
 */
@Component
public class RedisIdWorker {
    // 开始时间戳(2022/1/1 00:00:00)
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        @SuppressWarnings("all")
        long incr = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回
        return timestamp << COUNT_BITS | incr;
    }

}
