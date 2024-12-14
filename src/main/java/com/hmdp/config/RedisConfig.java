package com.hmdp.config;

import io.lettuce.core.RedisClient;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient redisClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://39.96.116.202:6379");
        return Redisson.create(config);
    }

    @Bean
    public RedissonClient redisClient2(){
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://39.96.116.202:6380");
        return Redisson.create(config);
    }


    @Bean
    public RedissonClient redisClient3(){
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://39.96.116.202:6381");
        return Redisson.create(config);
    }

}
