package io.github.olaaa.redissoncachelab.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.olaaa.redissoncachelab.config.RedissonConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.LocalCachedMapCacheOptions;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.LocalCachedMapOptions.ReconnectionStrategy;
import org.springframework.beans.factory.annotation.Autowired;

/* Сценарий 1. RLocalCachedMap: двухуровневый кэш и инвалидация между репликами средствами Redisson. */
class Scenario1LocalCachedMapTest extends ScenarioTestSupport {

    private static final BigDecimal NEW_PRICE = new BigDecimal("1990.00");

    /* Бин сервиса — «реплика A». */
    @Autowired
    LocalCachedMapProductService replicaA;

    private final List<RedissonClient> startedClients = new ArrayList<>();

    @AfterEach
    void shutdownStartedReplicas() {
        startedClients.forEach(RedissonClient::shutdown);
    }

    @Test
    void repeatedReadsAreServedLocallyWithoutRedis() {
        long hgetCallsBefore = redisCommandCalls("hget");

        for (int readNumber = 0; readNumber < 10; readNumber++) {
            assertThat(replicaA.findById(KETTLE_ID).block()).isNotNull();
        }

        assertThat(source.callCount()).isEqualTo(1);
        assertThat(redisCommandCalls("hget") - hgetCallsBefore)
                .as("в Redis ушла одна команда HGET — первое чтение, дальше локальная копия")
                .isEqualTo(1);
        assertThat(replicaA.isCachedLocally(KETTLE_ID)).isTrue();
    }

    @Test
    void anotherReplicaGetsValueFromRedisNotFromSource() {
        LocalCachedMapProductService replicaB = startReplica(ReconnectionStrategy.CLEAR);

        replicaA.findById(KETTLE_ID).block();
        assertThat(replicaB.findById(KETTLE_ID).block()).isNotNull();

        assertThat(source.callCount())
                .as("реплика B нашла товар в хеше Redis и в источник не ходила")
                .isEqualTo(1);
        assertThat(replicaB.isCachedLocally(KETTLE_ID)).isTrue();
    }

    @Test
    void priceChangeOnOneReplicaInvalidatesLocalCopyOnAnother() {
        LocalCachedMapProductService replicaB = startReplica(ReconnectionStrategy.CLEAR);
        replicaA.findById(KETTLE_ID).block();
        replicaB.findById(KETTLE_ID).block();

        assertThat(replicaA.changePrice(KETTLE_ID, NEW_PRICE).block()).isTrue();

        await().atMost(Duration.ofSeconds(5)).until(() -> !replicaB.isCachedLocally(KETTLE_ID));
        assertThat(replicaB.findById(KETTLE_ID).block().price())
                .as("Redisson сам разослал инвалидацию: реплика B удалила свою копию и видит новую цену")
                .isEqualByComparingTo(NEW_PRICE);
        assertThat(source.callCount()).isEqualTo(2);
    }

    @Test
    void reconnectionStrategyClearDropsLocalCopyAfterDisconnect() {
        LocalCachedMapProductService replicaWithClear = startReplica(ReconnectionStrategy.CLEAR);
        LocalCachedMapProductService replicaWithNone = startReplica(ReconnectionStrategy.NONE);
        replicaWithClear.findById(KETTLE_ID).block();
        replicaWithNone.findById(KETTLE_ID).block();

        /* Обрыв связи: Redis закрывает все соединения в режиме Pub/Sub, Redisson переподключается. */
        redisCli("CLIENT", "KILL", "TYPE", "pubsub");

        await().atMost(Duration.ofSeconds(10)).until(() -> !replicaWithClear.isCachedLocally(KETTLE_ID));
        assertThat(replicaWithNone.isCachedLocally(KETTLE_ID))
                .as("со стратегией NONE локальная копия пережила обрыв: пропущенные инвалидации не учтены")
                .isTrue();
    }

    @Test
    void redisSideIsHashWithoutPerEntryTimeToLiveInOpenSourceVersion() {
        replicaA.findById(KETTLE_ID).block();

        assertThat(redisCli("TYPE", LocalCachedMapProductService.MAP_NAME)).isEqualTo("hash");
        assertThat(redisCli("HGET", LocalCachedMapProductService.MAP_NAME, "1"))
                .isEqualTo("{\"id\":1,\"name\":\"Чайник\",\"price\":2490.00}");
        assertThat(redisCli("TTL", LocalCachedMapProductService.MAP_NAME))
                .as("у хеша нет срока жизни, у отдельных полей хеша — тоже")
                .isEqualTo("-1");
        assertThatThrownBy(() -> redisson.getLocalCachedMapCache("productsWithTtl", LocalCachedMapCacheOptions.defaults()))
                .as("локальный кэш со сроком жизни записей в Redis — только в Redisson PRO")
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Redisson PRO");
    }

    /* Ещё одна реплика: свой RedissonClient — свои соединения и своя локальная копия. */
    private LocalCachedMapProductService startReplica(ReconnectionStrategy reconnectionStrategy) {
        RedissonClient client = Redisson.create(RedissonConfiguration.clientConfig(redissonAddress));
        startedClients.add(client);
        return new LocalCachedMapProductService(source, repository, client, reconnectionStrategy);
    }

    /* Сколько раз Redis выполнил команду — из INFO commandstats. */
    private long redisCommandCalls(String command) {
        String prefix = "cmdstat_" + command + ":";
        for (String line : redisCli("INFO", "commandstats").split("\\R")) {
            if (line.startsWith(prefix)) {
                return Long.parseLong(line.substring(line.indexOf("calls=") + 6, line.indexOf(',')));
            }
        }
        return 0;
    }
}
