package com.campusOrder.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 閰嶇疆
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.150.101:6379").setPassword("123321");
        // 鍒涘缓RedissonClient瀵硅薄
        return Redisson.create(config);
    }
}

