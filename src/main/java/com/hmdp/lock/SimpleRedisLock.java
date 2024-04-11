package com.hmdp.lock;

import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * description:
 *
 * @author xuxin
 * @since 2024/4/10
 */
public class SimpleRedisLock implements ILock {

    private final String name;
    private final StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = RedisConstants.ID_PREFIX + Thread.currentThread().getId();

        // acquire lock
        String key = RedisConstants.LOCK_KEY + name;
        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isSuccess);
    }

    @Override
    public void unlock() {
        String key = RedisConstants.LOCK_KEY + name;
        String threadId = RedisConstants.ID_PREFIX + Thread.currentThread().getId();
        // 判断是否与自己的锁一致
        String id = stringRedisTemplate.opsForValue().get(key);
        if (threadId.equals(id)) {
            // release lock
            stringRedisTemplate.delete(key);
        }
    }
}
