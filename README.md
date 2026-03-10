# Schema Sync — синхронизация схем PostgreSQL через Liquibase

[![CI](https://github.com/rzwnz/schemasync/actions/workflows/ci.yml/badge.svg)](https://github.com/rzwnz/schemasync/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/rzwnz/schemasync/branch/main/graph/badge.svg?flag=schemasync)](https://codecov.io/gh/rzwnz/schemasync)
![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.0-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)

Синхронизация схем PostgreSQL (test → prod) через Liquibase diff с управлением из Telegram-бота и автоматизацией через Jenkins.

---

## Оглавление

1. [Архитектура](#1-архитектура)
2. [Модули](#2-модули)
3. [Требования](#3-требования)
4. [Переменные окружения](#4-переменные-окружения)
5. [Сборка и запуск](#5-сборка-и-запуск)
6. [REST API микросервиса](#6-rest-api-микросервиса)
7. [Telegram-бот](#7-telegram-бот)
8. [Jenkins Pipeline](#8-jenkins-pipeline)
9. [Жизненный цикл diff'а](#9-жизненный-цикл-diffа)
10. [Тестирование](#10-тестирование)
11. [CI/CD](#11-cicd)
12. [Docker Compose](#12-docker-compose)
13. [Зависимости](#13-зависимости)
14. [Частые проблемы](#14-частые-проблемы)

---

## 1. Архитектура

```
┌───────────────────────────────────────────────────────────────────────────┐
│                              Инфраструктура                               │
│                                                                           │
│  ┌───────────────────────┐      ┌──────────────────────────────────────┐  │
│  │   Jenkins :8080       │◄────►│  Schema Sync Microservice :9090      │  │
│  │   (CI/CD pipeline)    │      │  (Spring Boot 3.1, Liquibase,        │  │
│  │                       │      │   JPA, Bucket4j, Scalar API docs)    │  │
│  └───────────┬───────────┘      └───────────┬──────────┬───────────────┘  │
│              │                              │          │                  │
│              │ trigger / poll               │ JDBC     │ JDBC             │
│              │                              ▼          ▼                  │
│  ┌───────────▼────────────┐      ┌───────────────┐  ┌──────────────┐      │
│  │  Telegram Bot :8080    │      │  Test DB      │  │  Prod DB     │      │
│  │  (Spring Boot WebFlux, │      │  (PostgreSQL) │  │  (PostgreSQL)│      │
│  │   java-telegram-bot-api│      └───────────────┘  └──────────────┘      │
│  │   Redis, RxJava)       │                                               │
│  └───────────┬────────────┘      ┌───────────────┐  ┌──────────────┐      │
│              │                   │  Metadata DB  │  │  Validation  │      │
│              │                   │  (PostgreSQL) │  │  DB (sandbox)│      │
│              ▼                   └───────────────┘  └──────────────┘      │
│  ┌────────────────────────┐                                               │
│  │  Redis :6379           │      ┌───────────────┐                        │
│  │  (сессии бота, 24h TTL)│      │  Diff Store   │                        │
│  └────────────────────────┘      │  (/diff-store)│                        │
│                                  └───────────────┘                        │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Модули

| Модуль | Артефакт | Описание |
|--------|----------|----------|
| `microservice/` | `schema-sync-microservice-0.1.0.jar` | REST API: генерация Liquibase diff, валидация в sandbox, управление changeset'ами, rollback, интеграция с Jenkins. Защищён API-ключом (`X-API-KEY`), rate limiting (Bucket4j, 20 req/60s на IP). Liquibase миграции схемы метаданных. OWASP dependency-check. |
| `bot/` | `schema-sync-telegram-bot-0.1.0.jar` | Telegram-интерфейс: FSM (6 состояний), inline-клавиатуры, Redis-сессии (24h TTL). Взаимодействие с микросервисом через REST и Jenkins через remote trigger/API. |

---

## 3. Требования

- **Java 17** (Temurin)
- **Maven 3.9+**
- **Docker >= 24.0**, **Docker Compose >= 2.20**
- **PostgreSQL** — 4 инстанса: metadata, test, prod, validation (sandbox)
- **Redis 7+** — хранение сессий бота
- **Jenkins LTS** — с поддержкой remote trigger и CSRF crumb

**Протестировано на:** Arch Linux (host) + 2 VM Ubuntu Server 22.04 (QEMU/KVM).

---

## 4. Переменные окружения

### microservice/.env

```bash
# Метаданные (SchemaDiff entity хранится здесь)
METADB_URL=jdbc:postgresql://metadata-db:5432/schema_sync_db
METADB_USER=sync_user
METADB_PASS=sync_pass

# API-ключ (заголовок X-API-KEY)
API_KEY=ваш_секретный_ключ

# Тестовая БД (source Liquibase diff)
TEST_DB_URL=jdbc:postgresql://servertest-db:5432/myapp_test
TEST_DB_USER=schema_admin
TEST_DB_PASS=testPassword

# Prod БД (target Liquibase diff)
PROD_DB_URL=jdbc:postgresql://192.168.122.140:5432/myapp
PROD_DB_USER=schema_admin
PROD_DB_PASS=prodPassword

# Sandbox БД (валидация changelog)
VALIDATION_DB_URL=jdbc:postgresql://validation-db:5432/validation_db
VALIDATION_DB_USER=val_user
VALIDATION_DB_PASS=valPassword

# Jenkins
JENKINS_URL=http://jenkins:8080
JENKINS_USER=jenkinsUser
JENKINS_TOKEN=jenkinsApiToken
JENKINS_JOB_NAME=schema-apply

# Diff-файлы (Docker volume)
HOST_DIFF_STORE=/srv/schema-diffs
```

### bot/.env

```bash
# Telegram
TELEGRAM_BOT_TOKEN=ваш_токен_бота
ADMIN_CHAT_ID=ваш_chat_id

# Jenkins
JENKINS_URL=http://192.168.122.37:8080/
JENKINS_USER=admin
JENKINS_TOKEN=jenkins_api_token
JENKINS_TRIGGER_TOKEN=SCHEMA_SYNC_TOKEN

# Микросервис
MICROSERVICE_URL=http://192.168.122.37:9090
MICROSERVICE_API_KEY=ваш_секретный_ключ

# Redis
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379
# SPRING_REDIS_PASSWORD=yourpassword
```

---

## 5. Сборка и запуск

### Микросервис

```bash
cd microservice

# Через Docker (рекомендуется)
cp .env.example .env  # заполнить переменные
./build-and-run.sh
# Maven package → Docker build → docker run -p 9090:8080 -v ${HOST_DIFF_STORE}:/diff-store

# Без Docker
mvn clean package -DskipTests=false
java -jar target/schema-sync-microservice-0.1.0.jar
```

Проверка:
```bash
curl http://localhost:9090/api/diffs/health
# → {"status":"UP"}

# API-документация (Scalar UI)
open http://localhost:9090/scalar.html
```

### Бот

```bash
cd bot

# Docker Compose (Redis + бот)
cp .env.example .env  # заполнить переменные
docker-compose up -d

# Или через скрипт
./build-and-run.sh
```

Проверка: написать `/start` боту в Telegram.

### Логи

```bash
docker logs -f schema-sync    # микросервис
docker logs -f telegram-bot   # бот
```

---

## 6. REST API микросервиса

API-документация: Scalar UI (`/scalar.html`), OpenAPI JSON (`/v3/api-docs`).

Аутентификация: заголовок `X-API-KEY`.
Rate limit: **20 запросов / 60 секунд** на IP (Bucket4j, LRU-кэш на 10 000 IP).

### SchemaDiffController — `/api/diffs`

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/diffs` | Создать diff (запускает `@Async` генерацию) |
| `GET` | `/api/diffs` | Список diff'ов (фильтр: `?status=VALID`) |
| `GET` | `/api/diffs/{id}` | Метаданные diff'а |
| `GET` | `/api/diffs/{id}/content` | Скачать changelog XML |
| `GET` | `/api/diffs/{id}/validation-log` | Лог валидации |
| `GET` | `/api/diffs/{id}/changesets` | Список changeset'ов (`ChangeSetDto`) |
| `GET` | `/api/diffs/{id}/parsed` | Структурированный diff (таблицы, колонки, индексы, constraints, views) |
| `POST` | `/api/diffs/{id}/approve` | Одобрить → запуск Jenkins |
| `POST` | `/api/diffs/{id}/reject` | Отклонить с указанием причины |
| `POST` | `/api/diffs/{id}/apply-filtered` | Применить выбранные changeset'ы |
| `POST` | `/api/diffs/{id}/update-sql` | Предпросмотр SQL для changeset'ов |
| `POST` | `/api/diffs/{id}/rollback` | Откатить changeset'ы |
| `POST` | `/api/diffs/{id}/rollback-sql` | Предпросмотр SQL отката |
| `GET` | `/api/diffs/jenkins/status` | Статус последнего Jenkins-билда |
| `GET` | `/api/diffs/health` | Health-check |

### MergeController — `/api/merge`

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/merge/validate-config` | Валидация `DbMergeConfig` |
| `POST` | `/api/merge/validate-changelog` | Офлайн-валидация changelog |
| `POST` | `/api/merge/validate-source` | Проверка JDBC-подключения к source |
| `POST` | `/api/merge/validate-target` | Проверка JDBC-подключения ко всем targets |
| `POST` | `/api/merge/transfer-data` | Перенос данных source → targets (batch INSERT) |
| `POST` | `/api/merge/changelog-to-json` | Конвертация XML → JSON |

### Пример: создание diff'а

```bash
curl -X POST http://localhost:9090/api/diffs \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: ваш_ключ" \
  -d '{
    "author": "developer",
    "description": "Sprint 42 schema changes",
    "includeSchemas": ["public"],
    "excludeSchemas": []
  }'
```

---

## 7. Telegram-бот

### Конечный автомат (FSM)

```
IDLE → SELECTING_PIPELINE → CONFIGURING_PARAMS → AWAITING_DIFF → REVIEWING_DIFF → APPLYING → IDLE
```

Сессии хранятся в **Redis** (TTL 24 часа). Каждое состояние FSM контролирует, какие команды доступны.

### Команды

| Команда | FSM-состояние | Действие |
|---------|---------------|----------|
| `/start` | Любое | Сброс сессии → IDLE, приветствие |
| `/help` | Любое | Справка (Workflow Guide / Parameter Reference) |
| `/commands` | Любое | Список команд |
| `/status` | Любое | Состояние: pipeline, параметры, progress bar |
| `/pipelines` | Любое | Список Jenkins-job'ов → inline-кнопки → SELECTING_PIPELINE |
| `/diff` | Pipeline + параметры | Запуск Jenkins (APPLY_DIFF=false) → AWAITING_DIFF, polling |
| `/confirm` | Параметры подтверждены | Запуск Jenkins напрямую |
| `/approve` | REVIEWING_DIFF | Запуск Jenkins (APPLY_DIFF=true) → APPLYING |
| `/logs` | Pipeline выбран | Лог Jenkins-билда (до 4000 символов) |
| `/cancel` | Любое | Сброс → IDLE |
| `/delete <KEY>` | CONFIGURING_PARAMS | Удалить параметр из сессии |

### Inline-клавиатуры

- Выбор pipeline (`select_pipeline:имя`)
- Редактирование параметров (`param:ключ`, `editval:ключ`, `confirmval:ключ`)
- Подтверждение / отмена (`confirm_all_params`, `cancel_params`)
- Одобрение diff'а (`approve_diff`, `cancel_diff`)
- Навигация по справке (`help_workflow`, `help_parameters`)

### Архитектура бота

```
Telegram Update
    │
    ▼
TelegramCommandHandler (диспетчер)
    ├── /команда → CommandRegistry → BotCommand
    ├── callback_query → CallbackQueryHandler
    └── текст → ParameterInputService (ввод значений)
```

Все команды — Spring `@Component`, внедрение через конструктор. Логирование через SLF4J.

---

## 8. Jenkins Pipeline

Jenkins используется для генерации diff, ожидания одобрения и применения к prod.

### Credentials

| Credential ID | Тип | Описание |
|---------------|-----|----------|
| `schema-sync-url` | Secret text | URL микросервиса |
| `schema-sync-api-key` | Secret text | API-ключ |
| `telegram-bot-token` | Secret text | Telegram bot token |
| `prod-db-creds` | Username/Password | Логин/пароль prod БД |

### Параметры pipeline

| Параметр | Тип | Описание |
|----------|-----|----------|
| `SOURCE_DB_URL` | string | JDBC URL тестовой БД |
| `TARGET_DB_URL` | string | JDBC URL prod БД |
| `SCHEMA_NAME` | string | Схема для сравнения |
| `SYNC_STRATEGY` | string | `diff` / `validate` / `full` |
| `APPLY_DIFF` | boolean | Применить diff после одобрения |
| `DIFF_ID` | string | ID существующего diff'а (опционально) |
| `BOT_USER_ID` | string | Telegram user ID для уведомлений |
| `PIPELINE_NAME` | string | Имя pipeline для логирования |

### Этапы

```
Validate Parameters
  → Notify Bot: Started
  → Request or Use Diff
  → Wait for VALID (polling /api/diffs/{id}, таймаут 5 мин)
  → Download Diff File
  → Notify Bot: Diff Ready
  → Wait for User Approval (если APPLY_DIFF=false)
  → Apply to Prod via Liquibase (если APPLY_DIFF=true)
  → Notify Bot: Complete / Failed
```

Применение через `liquibase/liquibase:latest` в Docker-контейнере внутри `ci-network`.

---

## 9. Жизненный цикл diff'а

```
PENDING → VALIDATING → VALID → APPROVED → APPLYING → APPLIED
                     ↘ INVALID
         VALID/INVALID → REJECTED
```

| Статус | Описание |
|--------|----------|
| **PENDING** | Diff создан, задача на генерацию в очереди (`@Async`) |
| **VALIDATING** | Liquibase генерирует snapshot обеих БД и строит changelog |
| **VALID** | Changelog успешно валидирован в sandbox-БД |
| **INVALID** | Валидация в sandbox не пройдена |
| **APPROVED** | Пользователь одобрил через бота / API |
| **APPLYING** | Jenkins применяет changelog к prod |
| **APPLIED** | Успешно применено |
| **REJECTED** | Пользователь отклонил diff |

---

## 10. Тестирование

**278 тестов суммарно, 0 failures, 10 skipped.**

### Микросервис — 144 теста

```bash
cd microservice && mvn test
```

| Класс | Тестов | Проверяет |
|-------|--------|-----------|
| `SchemaDiffControllerTest` | 22 | CRUD diff'ов, API-ключ (401/200), файлы, валидация, changeset'ы |
| `SchemaDiffServiceImplTest` | 18 | Бизнес-логика: создание, одобрение, отклонение, фильтрация |
| `DiffXmlParserTest` | 30 | Парсинг changelog XML: таблицы, колонки, constraints, индексы, views |
| `LiquibaseDiffServiceImplTest` | 11 | Unit + интеграционный тест Liquibase |
| `DataTransferServiceTest` | 9 | Перенос данных source → targets, batch INSERT |
| `MergeControllerTest` | 8 | CRUD merge-эндпоинтов, validate, transfer-data |
| `DiffProcessingServiceTest` | 4 | Обработка diff'ов, transition статусов |
| `JenkinsServiceTest` | 7 | Trigger build, get status, download artifact |
| `ApiKeyFilterTest` | 3 | Фильтрация без / с валидным API-ключом |
| `RateLimitingFilterTest` | 2 | Rate limiting по IP |
| Остальные (DTO, util, model) | 30 | JsonUtil, ValidationResult, ChangeSetDto, etc. |

### Бот — 134 теста (10 skipped)

```bash
cd bot && mvn test
```

| Класс | Тестов | Проверяет |
|-------|--------|-----------|
| `MessageTemplatesTest` | 26 | Все шаблоны сообщений (~25 статических методов) |
| `TelegramCommandHandlerTest` | 13 | Маршрутизация команд, callback, null/blank/unknown |
| `BotStateTest` | 10 | FSM enum: displayName, description, emoji |
| `SessionDataTest` | 9 | Модель сессии: параметры, reset, missing params |
| `ConfirmCommandTest` | 7 | Подтверждение: no pipeline, missing params, all confirmed |
| `ApproveCommandTest` | 6 | Одобрение diff'а, trigger Jenkins |
| `DiffCommandTest` | 6 | Запуск diff, trigger + polling |
| `LogsCommandTest` | 6 | Jenkins-логи, truncation, ошибки |
| `ParameterInputServiceTest` | 6 | Ввод параметров: key=value, plain text |
| `PipelinesCommandTest` | 5 | Список pipeline'ов, inline-кнопки |
| `DeleteCommandTest` | 5 | Удаление параметра из сессии |
| `StartCommandTest` | 4 | Сброс сессии, приветствие |
| Остальные (Status, Help, Commands, Cancel) | 12 | UI-команды |
| `TelegramBotIntegrationTest` | 10 | Интеграционный (skipped — требует реальный token) |

---

## 11. CI/CD

GitHub Actions (`.github/workflows/ci.yml`):

- **Триггер:** push / PR в `main` или `master`
- **Матрица:** `[bot, microservice]` — оба модуля параллельно
- **JDK:** Temurin 17, Maven cache
- **Шаги:**
  1. `mvn clean verify`
  2. JaCoCo report → проверка порога покрытия (≥ 70% instruction)
  3. OWASP dependency-check (CVSS < 7)
  4. Upload артефактов (JaCoCo HTML + XML)

---

## 12. Docker Compose

### Корневой `docker-compose.yml`

Инициализация 4 PostgreSQL-инстансов:

| База | Порт | Назначение |
|------|------|------------|
| `metadata-db` | 5440 | Метаданные diff'ов (SchemaDiff entity) |
| `servertest-db` | 5438 | Тестовая БД (source) |
| `validation-db` | 5439 | Sandbox для валидации changelog |
| `serverprod-db` | 5437 | Prod БД (target) |

Инициализация: `init-databases.sql` создаёт пользователей, БД и базовые таблицы.

### `bot/docker-compose.yml`

| Сервис | Образ | Порт |
|--------|-------|------|
| `telegram-bot` | build `./Dockerfile` | — |
| `redis` | `redis:7-alpine` | 6379 |

Docker health checks и resource limits настроены для обоих контейнеров.

---

## 13. Зависимости

### Микросервис

| Зависимость | Версия | Назначение |
|-------------|--------|------------|
| Spring Boot Starter Web | 3.1.0 | REST API |
| Spring Boot Starter Data JPA | 3.1.0 | PostgreSQL через JPA/Hibernate |
| Liquibase Core | (managed) | Diff, snapshot, apply, rollback |
| PostgreSQL Driver | (runtime) | JDBC |
| springdoc-openapi | 2.6.0 | OpenAPI 3.1 + Scalar UI |
| Bucket4j Core | 8.14.0 | Rate limiting |
| OWASP dependency-check | 10.0.3 | CVE-сканирование |
| JaCoCo | 0.8.11 | Code coverage |
| H2 Database | (test) | In-memory БД для тестов |
| Testcontainers | 1.19.7 | PostgreSQL в Docker для тестов |

### Бот

| Зависимость | Версия | Назначение |
|-------------|--------|------------|
| Spring Boot Starter WebFlux | 3.1.0 | Неблокирующий HTTP (WebClient) |
| Spring Boot Starter Data Redis | 3.1.0 | Redis-сессии |
| java-telegram-bot-api (pengrad) | 8.3.0 | Telegram Bot API |
| Jedis | 4.4.3 | Redis-клиент |
| RxJava | 3.1.8 | Реактивность |
| Lombok | 1.18.30 | Codegen |
| dotenv-java | 3.2.0 | Загрузка .env |
| reactor-test | (BOM) | Тесты WebFlux |
| JaCoCo | 0.8.11 | Code coverage |

---

## 14. Частые проблемы

| Проблема | Решение |
|----------|---------|
| Порты заняты (9090 / 6379) | `lsof -i :9090` / `docker ps`, остановить конфликт |
| Переменные не заданы | Проверить `.env`, все обязательные переменные заполнены |
| Контейнеры не видят друг друга | Оба контейнера в одной Docker-сети (`ci-network`) |
| Нет прав на diff-store | `chmod -R 775 /srv/schema-diffs`, `chown` на пользователя Docker |
| Jenkins не триггерится | Включить «Trigger builds remotely», проверить CSRF crumb, credentials |
| Бот не отвечает | Проверить `TELEGRAM_BOT_TOKEN`, `docker logs telegram-bot` |
| Diff зависает в PENDING | Проверить доступность TEST_DB и PROD_DB, логи микросервиса |
| Rate limit (429) | По умолчанию 20 req/60s — подождать или увеличить в `application.properties` |

### Остановка

```bash
docker stop schema-sync        # микросервис
cd bot && docker-compose down   # бот + Redis
```
