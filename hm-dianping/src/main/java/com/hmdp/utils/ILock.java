package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁对象
     * @param timeoutSec
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     * 关闭锁对象
     */
    void unlock();
}
