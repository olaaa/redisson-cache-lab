package io.github.olaaa.redissoncachelab.web;

import java.math.BigDecimal;

import io.github.olaaa.redissoncachelab.catalog.Product;
import io.github.olaaa.redissoncachelab.scenario.LocalCachedMapProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/products")
public class ProductController {

    public record PriceChange(BigDecimal price) {
    }

    private final LocalCachedMapProductService localCachedMapProductService;

    public ProductController(LocalCachedMapProductService localCachedMapProductService) {
        this.localCachedMapProductService = localCachedMapProductService;
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Product>> findById(@PathVariable long id) {
        return localCachedMapProductService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/price")
    public Mono<ResponseEntity<Void>> changePrice(@PathVariable long id, @RequestBody PriceChange change) {
        return localCachedMapProductService.changePrice(id, change.price())
                .map(updated -> updated ? ResponseEntity.ok().<Void>build() : ResponseEntity.notFound().<Void>build());
    }
}
