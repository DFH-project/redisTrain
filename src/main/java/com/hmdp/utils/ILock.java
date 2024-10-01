package com.hmdp.utils;

public interface ILock {


    /**
     *  获取锁
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();
}
