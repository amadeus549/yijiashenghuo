package com.yijia.utils;

public interface ILock {
    boolean tryLock(long timeoutSec);
    void unLock();
}
