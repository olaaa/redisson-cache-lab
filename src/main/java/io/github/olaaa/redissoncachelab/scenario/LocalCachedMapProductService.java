package io.github.olaaa.redissoncachelab.scenario;

import java.math.BigDecimal;
import java.time.Duration;

import io.github.olaaa.redissoncachelab.catalog.Product;
import io.github.olaaa.redissoncachelab.catalog.ProductRepository;
import io.github.olaaa.redissoncachelab.catalog.ProductSource;
import org.redisson.api.RLocalCachedMapReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.LocalCachedMapOptions;
import org.redisson.api.options.LocalCachedMapOptions.EvictionPolicy;
import org.redisson.api.options.LocalCachedMapOptions.ReconnectionStrategy;
import org.redisson.api.options.LocalCachedMapOptions.SyncStrategy;
import org.redisson.codec.TypedJsonJackson3Codec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/*
 * Сценарий 1. Двухуровневый кэш и инвалидация между репликами на RLocalCachedMap.
 *
 * То, что в distributed-cache-lab собрано вручную (сценарии 3 и 4), здесь делает Redisson:
 * - первый уровень — локальная копия в памяти этой реплики;
 * - второй уровень — хеш productsLocalCached в Redis;
 * - при изменении записи Redisson сам рассылает инвалидацию через Pub/Sub, и остальные
 *   экземпляры с тем же именем удаляют свою локальную копию (SyncStrategy.INVALIDATE);
 * - после обрыва связи локальная копия очищается целиком (ReconnectionStrategy.CLEAR),
 *   потому что за время обрыва реплика могла пропустить инвалидации.
 */
@Service
public class LocalCachedMapProductService implements LabCache {

    public static final String MAP_NAME = "productsLocalCached";
    public static final Duration LOCAL_TTL = Duration.ofSeconds(30);

    private final ProductSource source;
    private final ProductRepository repository;
    private final RLocalCachedMapReactive<Long, Product> products;

    @Autowired
    public LocalCachedMapProductService(ProductSource source, ProductRepository repository, RedissonClient redisson) {
        this(source, repository, redisson, ReconnectionStrategy.CLEAR);
    }

    /* Для теста: реплика с другой стратегией после обрыва связи. */
    public LocalCachedMapProductService(ProductSource source, ProductRepository repository, RedissonClient redisson,
                                        ReconnectionStrategy reconnectionStrategy) {
        this.source = source;
        this.repository = repository;
        LocalCachedMapOptions<Long, Product> options = LocalCachedMapOptions.<Long, Product>name(MAP_NAME)
                .codec(new TypedJsonJackson3Codec(Long.class, Product.class))
                .cacheSize(10_000)
                .evictionPolicy(EvictionPolicy.LRU)
                .timeToLive(LOCAL_TTL)
                .syncStrategy(SyncStrategy.INVALIDATE)
                .reconnectionStrategy(reconnectionStrategy);
        this.products = redisson.reactive().getLocalCachedMap(options);
    }

    /*
     * get сначала смотрит в локальную копию и ходит в Redis (HGET) только при промахе.
     * Если нет и в Redis — источник, затем fastPut: запись в хеш и рассылка остальным экземплярам.
     */
    public Mono<Product> findById(long id) {
        return products.get(id)
                .switchIfEmpty(Mono.defer(() -> source.load(id)
                        .flatMap(product -> products.fastPut(id, product).thenReturn(product))));
    }

    /*
     * Сначала база, потом fastRemove: Redisson удаляет запись из хеша в Redis и из своей локальной
     * копии и публикует инвалидацию — остальные экземпляры удалят свою копию сами.
     */
    public Mono<Boolean> changePrice(long id, BigDecimal price) {
        return repository.updatePrice(id, price)
                .flatMap(updatedRows -> updatedRows == 0
                        ? Mono.just(false)
                        : products.fastRemove(id).thenReturn(true));
    }

    /* Есть ли товар в локальной копии этой реплики — без обращения к Redis. */
    public boolean isCachedLocally(long id) {
        return products.cachedKeySet().contains(id);
    }

    /* Сброс лаборатории: очистить локальную копию этой реплики. */
    @Override
    public void invalidateAll() {
        products.getCachedMap().clear();
    }
}
