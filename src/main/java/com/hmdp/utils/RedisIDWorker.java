package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {
    private final static Long BEGIN_SECOND = 1704067200L ;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix){
        //1. 生成时间戳
        LocalDateTime now =LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long second = BEGIN_SECOND - nowSecond;
        //2. 生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3. 拼接并返回
        long res = second << 32 | increment;
        return res;
    }


    public static void main(String[] args) {
        LocalDateTime ldf = LocalDateTime.of(2024,1,1,0,0,0);
        long second = ldf.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);  //1704067200
    }
}
