package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData<T> {
    // 过期时间
    private LocalDateTime expireTime;
    // 存储一个实体
    private T data;
}
