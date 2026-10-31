# redisson-cache-lab — правила для ассистента

- Публичный репозиторий. Никаких корпоративных имён: сервисов, таблиц, пакетов, тикетов и названий компаний из рабочих проектов. Список запрещённых слов лежит в `.git/info/forbidden-words` и проверяется хуками `pre-commit` и `commit-msg`.
- Сценарии с Redisson. Отдельный репозиторий, чтобы зависимости Redisson не смешивались со Spring Data Redis в [distributed-cache-lab](https://github.com/olaaa/distributed-cache-lab). Redisson подключён библиотекой `org.redisson:redisson`, без стартера; Spring Data Redis и Lettuce в проекте нет. Фундамент перенесён из distributed-cache-lab: `ProductSource` со счётчиком обращений, `ScenarioTestSupport`.
- Стек: Java 25, Spring Boot 4.1, WebFlux, R2DBC через `DatabaseClient`, Reactor, Redisson 4.8 (реактивный API), Jackson 3. Без JPA.
- Каждый приём — отдельный сервис в `scenario` и отдельный тест `ScenarioN…Test`. Главная проверка — `ProductSource.callCount()`, плюс взгляд в Redis командами `redis-cli` внутри контейнера Testcontainers. У каждого сценария — описание в `docs/scenarios/NN-….md` и файл ручной проверки `http/NN-….http`; при изменении сценария обновлять все три.
- Тесты поднимают PostgreSQL и Redis в Testcontainers; адрес Redis передаётся в свойство `redisson.address` через `DynamicPropertyRegistrar`. Вторая реплика в тесте — второй `RedissonClient`. Без `sleep`: асинхронное ждать через `await`.
- Стиль: многострочные комментарии одним блоком `/* */`, фигурные скобки у тел `if`/`for`/`while`, говорящие имена.
- Ветка `main`. Коммит и пуш — по окончании задачи, `git add` по именам файлов, перед пушем `git fetch`.
