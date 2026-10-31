# redisson-cache-lab

Каталог товаров на WebFlux, R2DBC и Redisson: приёмы кэширования готовыми механизмами Redisson, каждый — отдельный сервис и тест со счётчиком обращений к источнику.

Связанные репозитории:

- [caching-lab](https://github.com/olaaa/caching-lab) — локальный кэш на Caffeine;
- [distributed-cache-lab](https://github.com/olaaa/distributed-cache-lab) — Redis как кэш на Spring Data Redis и Lettuce, в том числе двухуровневый кэш и инвалидация между репликами, собранные вручную.

Здесь — то же на Redisson. Отдельный репозиторий нужен, чтобы зависимости не путались: Redisson подключён через `redisson-spring-boot-starter`, а Lettuce в проекте нет: стартер приносит Spring Data Redis без Lettuce и Jedis и ставит фабрикой соединений свою `RedissonConnectionFactory`.

## Как запустить

Нужны Java 25 и Docker.

```bash
mvn test
```

Тесты поднимают PostgreSQL и Redis в контейнерах Testcontainers, по одному на все сценарии.

```bash
mvn spring-boot:run
```

Spring Boot сам поднимет PostgreSQL и Redis из `compose.yaml`, а стартер Redisson возьмёт адрес Redis оттуда же. Запросы для IntelliJ HTTP Client — в папке `http`, счётчик обращений к источнику — `GET /lab/stats`, сброс — `POST /lab/reset`.

## Устройство

- `catalog` — таблица `product`, `ProductRepository` на `DatabaseClient` и `ProductSource`: чтение товара с задержкой, счётчиком обращений и умением падать.
- `scenario` — по сервису на приём.
- `web` — `GET /products/{id}`, `PUT /products/{id}/price` и служебные ручки `/lab`.

## Сценарии

Подробное описание — [docs/scenarios](docs/scenarios/README.md).

### 1. `RLocalCachedMap`: двухуровневый кэш и инвалидация

Тест: `Scenario1LocalCachedMapTest`. Сервис: `LocalCachedMapProductService`. Подробно: [docs/scenarios/01-local-cached-map.md](docs/scenarios/01-local-cached-map.md), вручную: `http/01-local-cached-map.http` на двух репликах.

Локальная копия в каждой реплике, хеш в Redis и инвалидация через Pub/Sub — без своего кода рассылки. Десять чтений — одна команда `HGET`. Изменение на одной реплике — и другая сразу видит новую цену. `ReconnectionStrategy.CLEAR` очищает локальную копию после обрыва связи, по умолчанию стоит `NONE`. В открытой версии нет срока жизни записей в Redis (`RLocalCachedMapCache` — только PRO) и нет `@Cacheable` с локальным кэшем.

## Дальше

- Демо-сценарий для `RSetReactive`.
