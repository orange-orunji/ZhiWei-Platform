package com.hmdp.config;

import io.lettuce.core.ReadFrom;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    /**
     * 配置Redisson
     * @return
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
//        改为虚拟机配置docker部署到虚拟机上
        config.useSingleServer().setAddress("redis://192.168.161.128:7000")
        .setPassword("Ww2301079399@");
        return Redisson.create(config);
    }

    /**
     * 配置Redis 哨兵客户端
     * @return
     */
    //=========================================================
//    @Bean
//    public LettuceClientConfigurationBuilderCustomizer clientConfigurationBuilderCustomizer(){
//        return clientConfigurationBuilder -> clientConfigurationBuilder.readFrom(ReadFrom.REPLICA_PREFERRED);
//    }
//    ==========================================================================================
}
