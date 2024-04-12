package com.hmdp.lock;

import com.hmdp.utils.RedisConstants;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

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
       // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(RedisConstants.LOCK_KEY + name),
                RedisConstants.ID_PREFIX + Thread.currentThread().getId());
    }
}
