package io.github.olaaa.redissoncachelab.scenario;

import java.math.BigDecimal;
import java.util.List;

import io.github.olaaa.redissoncachelab.TestcontainersConfiguration;
import io.github.olaaa.redissoncachelab.catalog.Product;
import io.github.olaaa.redissoncachelab.catalog.ProductRepository;
import io.github.olaaa.redissoncachelab.catalog.ProductSource;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;

/*
 * Общая основа сценариев: один контекст Spring, один контейнер PostgreSQL и один Redis на все тесты.
 * Перед каждым тестом — чистая база Redis, пустые локальные копии, обнулённый счётчик и одинаковые данные.
 */
@SpringBootTest(properties = "catalog.source.delay=100ms")
@Import(TestcontainersConfiguration.class)
abstract class ScenarioTestSupport {

    static final long KETTLE_ID = 1;
    static final BigDecimal KETTLE_PRICE = new BigDecimal("2490.00");

    @Autowired
    ProductRepository repository;

    @Autowired
    ProductSource source;

    @Autowired
    RedissonClient redisson;

    @Autowired
    List<LabCache> labCaches;

    /* Контейнер Redis: через него тест смотрит в Redis командами redis-cli, в обход Redisson. */
    @Autowired
    GenericContainer<?> redisContainer;

    @Value("${redisson.address}")
    String redissonAddress;

    @BeforeEach
    void resetLab() {
        repository.deleteAll()
                .then(repository.save(new Product(KETTLE_ID, "Чайник", KETTLE_PRICE)))
                .block();
        redisson.reactive().getKeys().flushdb().block();
        labCaches.forEach(LabCache::invalidateAll);
        source.reset();
    }

    /* Выполнить команду redis-cli внутри контейнера и вернуть её вывод. */
    String redisCli(String... arguments) {
        try {
            String[] command = new String[arguments.length + 1];
            command[0] = "redis-cli";
            System.arraycopy(arguments, 0, command, 1, arguments.length);
            return redisContainer.execInContainer(command).getStdout().trim();
        } catch (Exception exception) {
            throw new IllegalStateException("redis-cli не выполнилась", exception);
        }
    }
}
