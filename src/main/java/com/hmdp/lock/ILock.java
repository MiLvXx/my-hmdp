package com.hmdp.lock;

/**
 * description:
 *
 * @author xuxin
 * @since 2024/4/10
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的过期时间，超时后自动释放
     * @return true代表获取锁成功；false代表获取失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
