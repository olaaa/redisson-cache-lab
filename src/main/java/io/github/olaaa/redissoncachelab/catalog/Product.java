package io.github.olaaa.redissoncachelab.catalog;

import java.math.BigDecimal;

public record Product(long id, String name, BigDecimal price) {
}
