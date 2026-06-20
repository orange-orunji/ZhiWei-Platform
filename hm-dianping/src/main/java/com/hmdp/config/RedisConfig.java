package src.main.java.com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
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
        config.useSingleServer().setAddress("redis://192.168.161.128:6379")
        .setPassword("Ww2301079399@");
        return Redisson.create(config);
    }
}
