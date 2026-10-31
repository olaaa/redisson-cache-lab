package io.github.olaaa.redissoncachelab.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RedissonConfiguration {

    /*
     * Один RedissonClient на приложение — одна реплика. Клиент держит свои соединения с Redis,
     * в том числе отдельное соединение для Pub/Sub, по которому приходят инвалидации.
     */
    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(@Value("${redisson.address}") String address) {
        return Redisson.create(clientConfig(address));
    }

    public static Config clientConfig(String address) {
        Config config = new Config();
        config.useSingleServer().setAddress(address);
        return config;
    }
}
