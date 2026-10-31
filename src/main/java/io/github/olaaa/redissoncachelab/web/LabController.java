package io.github.olaaa.redissoncachelab.web;

import java.util.List;

import io.github.olaaa.redissoncachelab.catalog.ProductSource;
import io.github.olaaa.redissoncachelab.scenario.LabCache;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/* Служебные ручки для ручных экспериментов: сколько раз дошли до источника, сброс. */
@RestController
@RequestMapping("/lab")
public class LabController {

    public record LabStats(int sourceCalls) {
    }

    private final ProductSource source;
    private final List<LabCache> labCaches;
    private final RedissonClient redisson;

    public LabController(ProductSource source, List<LabCache> labCaches, RedissonClient redisson) {
        this.source = source;
        this.labCaches = labCaches;
        this.redisson = redisson;
    }

    @GetMapping("/stats")
    public LabStats stats() {
        return new LabStats(source.callCount());
    }

    @PostMapping("/source/fail")
    public void failSource() {
        source.setFailing(true);
    }

    @PostMapping("/source/recover")
    public void recoverSource() {
        source.setFailing(false);
    }

    /* Сбрасывает счётчик источника, локальную копию этой реплики и очищает текущую базу Redis целиком. */
    @PostMapping("/reset")
    public Mono<Void> reset() {
        source.reset();
        labCaches.forEach(LabCache::invalidateAll);
        return redisson.reactive().getKeys().flushdb();
    }
}
