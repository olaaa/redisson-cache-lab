package io.github.olaaa.redissoncachelab;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /* @ServiceConnection сам выставляет R2DBC-подключение к контейнеру. */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }

    @Bean
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
    }

    /*
     * @ServiceConnection для Redis работает только со Spring Data Redis, а его здесь нет:
     * адрес контейнера передаётся в свойство redisson.address, из которого RedissonConfiguration
     * создаёт клиент.
     */
    @Bean
    DynamicPropertyRegistrar redissonAddress(GenericContainer<?> redisContainer) {
        return registry -> registry.add("redisson.address",
                () -> "redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379));
    }
}
