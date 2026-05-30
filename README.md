# ✈️ Travel Agency — Command Side (CQRS Write Model)

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Kafka](https://img.shields.io/badge/Kafka-KRaft-black.svg)](https://kafka.apache.org/)
[![Avro](https://img.shields.io/badge/Avro-1.11.3-blue.svg)](https://avro.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

<a id="overview"></a>
## 📖 Overview
[Back to Table of Contents](#toc)

Travel Agency Command Side is the write model of a CQRS-based hotel booking platform. It handles all booking creation commands — enforcing availability via **Pessimistic Locking** on a per-day availability table, and publishing `BookingCreated` events to Kafka via the **Transactional Outbox Pattern**. Built on Hexagonal Architecture with a clean separation between domain, application, and infrastructure layers.

<a id="toc"></a>
## 📚 Table of Contents
- [📖 Overview](#overview)
- [🔄 How It Works](#how-it-works)
- [🌐 API Endpoints](#api-endpoints)
- [🚀 Getting Started](#getting-started)
- [⚙️ Environment Variables](#environment-variables)
- [🛠️ Common Issues](#common-issues)
- [🏗️ Architecture](#architecture)
- [💻 Tech Stack](#tech-stack)
- [🧪 Testing Strategy](#testing-strategy)
- [📂 Repository Structure](#repository-structure)
- [🤝 Contact](#contact)

---

<a id="how-it-works"></a>
## 🔄 How It Works
[Back to Table of Contents](#toc)

1. Client sends `POST /api/bookings` with hotel ID, user ID, and desired dates
2. `BookingController` maps the request to a `CreateBookingCommand` and delegates to `CreateBookingUseCase`
3. `TransactionalCreateBookingUseCase` wraps the entire operation in a single DB transaction (`READ_COMMITTED`)
4. `BookingService` fetches the `Hotel` aggregate and calls `reserveAvailability` on the repository port
5. `TravelPersistenceAdapter` runs `SELECT ... FOR UPDATE` (pessimistic write lock) on all rows in `daily_availabilities` matching the hotel and date range — with a 3 s lock timeout
6. For each date in the range: if a row exists it is checked against capacity and incremented; if no row exists it is created at `occupiedRooms = 1`. `OverbookingException` is thrown on any date that is full
7. The new `Booking` is persisted and an `OutboxEntity` record is saved **in the same transaction** (Transactional Outbox Pattern)
8. `OutboxScheduler` polls the outbox table every second, serialises pending entries to `BookingCreatedAvro`, and publishes them to the `travel.bookings` Kafka topic
9. On publish failure the entry's retry counter is incremented; after `max-retries` the message is moved to the **Dead Letter** table

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant BC as BookingController
    participant BS as BookingService
    participant PA as TravelPersistenceAdapter
    participant DB as MySQL
    participant OS as OutboxScheduler
    participant K as Kafka

    C->>BC: POST /api/bookings { hotelId, userId, start, end }
    BC->>BS: CreateBookingCommand

    BS->>PA: findHotel(hotelId)
    PA->>DB: SELECT * FROM hotels WHERE id = ?
    DB-->>BS: Hotel(id, capacity)

    BS->>PA: reserveAvailability(hotelId, capacity, start, end)
    PA->>DB: SELECT ... FROM daily_availabilities FOR UPDATE (3 s timeout)
    DB-->>PA: locked rows for date range

    loop For each date in [start, end]
        PA->>PA: get or create slot, check occupiedRooms < capacity
        Note over PA: OverbookingException if any date is full
        PA->>PA: slot.occupiedRooms++
    end

    PA->>DB: saveAll(daily_availabilities)
    BS->>DB: save(Booking) + save(OutboxEntity) [same TX]
    DB-->>BS: saved Booking

    BC-->>C: 201 Created { bookingId }

    loop Every 1 s
        OS->>DB: poll OutboxEntity (batch 50, FIFO)
        OS->>K: ProducerRecord → travel.bookings (Avro + Schema Registry)
        K-->>OS: ack
        OS->>DB: delete OutboxEntity
    end

    Note over OS,DB: On failure: increment retry_count → Dead Letter after max-retries
```

---

<a id="api-endpoints"></a>
## 🌐 API Endpoints
[Back to Table of Contents](#toc)

**Base URL:** `http://localhost:8080`

### Booking Endpoints

| Method | Path | Purpose | Request Body | Success | Common Errors |
|--------|------|---------|--------------|---------|---------------|
| `POST` | `/api/bookings` | Create a new booking | `CreateBookingRequestDto` | `201 Created` | `400`, `409` |

### Request Body — `CreateBookingRequestDto`

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `hotelId` | `Long` | `@NotNull` | ID of the hotel to book |
| `userId` | `Long` | `@NotNull` | ID of the user making the booking |
| `start` | `LocalDate` | `@NotNull @Future` | Check-in date |
| `end` | `LocalDate` | `@NotNull @Future` | Check-out date |

### Health Endpoint

| Method | Path | Purpose | Success |
|--------|------|---------|---------| 
| `GET` | `/actuator/health` | Application health check | `200 OK` |

### cURL Example

```bash
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "hotelId": 1,
    "userId": 42,
    "start": "2026-08-01",
    "end": "2026-08-07"
  }'
```

**Response `201 Created`:**
```json
{ "bookingId": 17 }
```

**Response `409 Conflict`** (hotel fully booked on any day in range):
```json
{
  "message": "Hotel 1 overbooked on 2026-08-03. Capacity: 2, occupied: 2"
}
```

**Response `409 Conflict`** (pessimistic lock timeout — concurrent request):
```json
{
  "message": "Resource is temporarily locked. Please retry."
}
```

---

<a id="getting-started"></a>
## 🚀 Getting Started
[Back to Table of Contents](#toc)

### Prerequisites

- Docker and Docker Compose v2+
- Java 25+ and Maven 3.9+ (for local builds only)
- Kafka broker reachable at `kafka:9092` (included in the Compose stack)
- Confluent Schema Registry reachable at `http://schema-registry:8200`

### Environment Configuration

Create a `.env` file in the project root (see [Environment Variables](#environment-variables) for all options):

```dotenv
# ─── MySQL ───────────────────────────────────────────────────────────────────
TA_COMMAND_SIDE_SERVICE_MYSQL_DB_HOST=travel-agency-command-side-mysql
TA_COMMAND_SIDE_SERVICE_MYSQL_DB_PORT=3307
TA_COMMAND_SIDE_SERVICE_MYSQL_DB_NAME=travels_db
TA_COMMAND_SIDE_SERVICE_MYSQL_DB_USER=user
TA_COMMAND_SIDE_SERVICE_MYSQL_DB_ROOT_PASSWORD=changeme_root
TA_COMMAND_SIDE_SERVICE_MYSQL_DB_PASSWORD=changeme_user

# ─── Application ─────────────────────────────────────────────────────────────
TA_COMMAND_SIDE_SERVICE_PORT=8080
TA_COMMAND_SIDE_SERVICE_APPLICATION_NAME=travel-agency-command-side

# ─── Kafka ───────────────────────────────────────────────────────────────────
KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk
KAFKA_BROKER_ID=1
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
```

### Start the Service

```bash
docker compose up -d --build
```

Verify: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

---

<a id="environment-variables"></a>
## ⚙️ Environment Variables
[Back to Table of Contents](#toc)

### MySQL

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `TA_COMMAND_SIDE_SERVICE_MYSQL_DB_HOST` | yes | MySQL hostname (Docker service name) | `travel-agency-command-side-mysql` |
| `TA_COMMAND_SIDE_SERVICE_MYSQL_DB_PORT` | yes | Host port mapped to MySQL | `3307` |
| `TA_COMMAND_SIDE_SERVICE_MYSQL_DB_NAME` | yes | Database name | `travels_db` |
| `TA_COMMAND_SIDE_SERVICE_MYSQL_DB_USER` | yes | Application DB user | `user` |
| `TA_COMMAND_SIDE_SERVICE_MYSQL_DB_ROOT_PASSWORD` | yes | MySQL root password | `root1234` |
| `TA_COMMAND_SIDE_SERVICE_MYSQL_DB_PASSWORD` | yes | Application DB user password | `user1234` |

### Application

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `TA_COMMAND_SIDE_SERVICE_PORT` | yes | HTTP port the service listens on | `8080` |
| `TA_COMMAND_SIDE_SERVICE_APPLICATION_NAME` | optional | Spring application name | `travel-agency-command-side` |

### Kafka

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `KAFKA_CLUSTER_ID` | yes | KRaft cluster ID | `MkU3OEVBNTcwNTJENDM2Qk` |
| `KAFKA_BROKER_ID` | yes | Broker ID | `1` |
| `KAFKA_ADVERTISED_LISTENERS` | yes | Advertised listener address | `PLAINTEXT://kafka:9092` |
| `KAFKA_HEAP_OPTS` | optional | JVM heap for Kafka broker | `-Xmx512M -Xms512M` |

---

<a id="common-issues"></a>
## 🛠️ Common Issues
[Back to Table of Contents](#toc)

1. **Application fails to start — DB connection refused** — MySQL healthcheck must pass before the app starts. Check with `docker compose ps travel-agency-command-side-mysql` and `docker compose logs travel-agency-command-side-mysql`. The app container waits on the healthcheck condition defined in `docker-compose.yml`.

2. **`OverbookingException` on every request** — the hotel's capacity in the DB may be 0 or the `daily_availabilities` table has stale data. Verify the `Hotel` record exists with `capacity > 0` and inspect the `daily_availabilities` rows for that hotel.

3. **`409 — Resource is temporarily locked. Please retry.`** — a concurrent request is holding the pessimistic write lock on `daily_availabilities` for this hotel. The lock timeout is 3 seconds. Retry after a short delay; the other transaction will have committed or rolled back by then.

4. **Outbox messages stuck / not published** — check that Schema Registry is reachable at `http://schema-registry:8200`. Inspect `docker compose logs travel-agency-command-side` for Kafka producer errors. After `max-retries` (default 5) failures, messages are moved to the `dead_letter` table — query it directly to inspect the error messages.

5. **Port conflict** — check for conflicts on `3307` (MySQL), `8080` (app), `9092` (Kafka), `8200` (Schema Registry), `8100` (Kafka UI): `netstat -ano | findstr :8080`.

---

<a id="architecture"></a>
## 🏗️ Architecture
[Back to Table of Contents](#toc)

```mermaid
graph LR
    classDef presentation fill:#4a90d9,stroke:#2c5f8a,color:#fff
    classDef application fill:#7b68ee,stroke:#4a3aa0,color:#fff
    classDef port fill:#9b59b6,stroke:#6c3483,color:#fff
    classDef domain fill:#27ae60,stroke:#1a7a42,color:#fff
    classDef infra fill:#e67e22,stroke:#a85a0f,color:#fff
    classDef external fill:#c0392b,stroke:#8e1a1a,color:#fff

    subgraph PRESENTATION["🖥️ Presentation"]
        C([Client])
        BC[BookingController]
    end

    subgraph APPLICATION["⚙️ Application"]
        BS[BookingService]
        subgraph PORTS["Ports"]
            UCI[CreateBookingUseCase]
            TR[TravelRepository]
        end
        TX[TransactionalCreateBookingUseCase]
    end

    subgraph DOMAIN["🏛️ Domain"]
        H[Hotel\nid · capacity]
        B[Booking]
        EX[OverbookingException\nResourceNotFoundException]
    end

    subgraph INFRASTRUCTURE["🔧 Infrastructure"]
        subgraph PERSISTENCE["Persistence"]
            PA[TravelPersistenceAdapter\nreserveAvailability — SELECT FOR UPDATE]
            JPA["JPA Repositories\nBooking · Hotel · Outbox\nDailyAvailability · DeadLetter"]
        end
        subgraph KAFKA["Kafka"]
            OS["OutboxScheduler\nevery 1 s · batch 50"]
            APC[AvroProducerConfig]
            KTC[KafkaTopicConfig]
        end
    end

    subgraph EXTERNAL["🌐 External"]
        DB[(MySQL)]
        K[(Kafka\ntravel.bookings)]
        SR[(Schema Registry)]
    end

    C --> BC --> TX --> BS
    BS --> UCI
    BS --> TR --> PA --> JPA --> DB
    BS --> H
    H --> B
    PA --> EX

    OS --> JPA
    OS --> APC --> K
    APC --> SR

    class C,BC presentation
    class BS,TX application
    class UCI,TR port
    class H,B,EX domain
    class PA,JPA,OS,APC,KTC infra
    class DB,K,SR external
```

**Technical Highlights:**

- **Hexagonal Architecture (Ports & Adapters):** Domain and application layers have zero infrastructure dependencies. `TravelRepository` is the only bridge between application and infrastructure, implemented by `TravelPersistenceAdapter`.
- **CQRS Write Model:** This service handles only commands. All reads are delegated to a separate query-side service that consumes events from Kafka.
- **Pessimistic Locking on `daily_availabilities`:** Each row represents one hotel on one date. `reserveAvailability` issues `SELECT ... FOR UPDATE` on the affected rows with a 3 s lock timeout, preventing any concurrent transaction from double-booking the same day. New date slots are protected by a unique constraint `(hotel_id, date)` as an additional safety net for the first-booking race condition.
- **Transactional Outbox Pattern:** `Booking` and `OutboxEntity` are persisted in one DB transaction — guarantees at-least-once Kafka delivery even if the broker is temporarily unavailable.
- **Dead Letter Table:** Failed Kafka publishes are retried up to `max-retries` times; after that the record is moved to `dead_letter` for manual inspection and reprocessing.
- **Virtual Threads + container-aware JVM:** `spring.threads.virtual.enabled=true` with `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC`.
- **JDBC Batching:** Hibernate batch size 50 with `order_inserts=true` and `order_updates=true` for efficient bulk writes.

---

<a id="tech-stack"></a>
## 💻 Tech Stack
[Back to Table of Contents](#toc)

| Layer | Technology |
|-------|------------|
| Language | Java 25 (virtual threads via Project Loom) |
| Framework | Spring Boot 4.0.6 |
| Web | Spring WebMVC, Spring Validation |
| Persistence | Spring Data JPA, Hibernate (batch writes, pessimistic locking) |
| Database | MySQL |
| Messaging | Apache Kafka (KRaft, no ZooKeeper) |
| Schema | Apache Avro 1.11.3, Confluent Schema Registry 7.5.11 |
| Serialisation | `kafka-avro-serializer`, `BookingCreatedAvro` generated from `.avsc` |
| Scheduling | Spring `@Scheduled` (OutboxScheduler — fixed delay 1 s) |
| Build | Maven 3.9, multi-stage Docker build |
| Containerisation | Docker, Docker Compose v2+, non-root user, layer extraction |
| Observability | Spring Boot Actuator (`/actuator/health`) |
| Utilities | Lombok |

---

<a id="testing-strategy"></a>
## 🧪 Testing Strategy
[Back to Table of Contents](#toc)

**4 unit test classes** — plain JUnit 5, no Spring context loaded.

| Class | Key Scenarios |
|-------|--------------|
| `CreateBookingCommandTest` | Command construction, validation of field constraints |
| `BookingServiceTest` | Happy path, `OverbookingException` on full hotel, `ResourceNotFoundException` on missing hotel, date order validation |
| `OutboxSchedulerTest` | Successful publish + delete, retry on failure, dead-letter promotion after max retries |
| `OutboxEntityTest` | Entity construction, retry counter increment, status transitions |

```bash
mvn test        # unit tests only
mvn verify      # unit tests + reports
```

---

<a id="repository-structure"></a>
## 📂 Repository Structure
[Back to Table of Contents](#toc)

```text
.
├── src/
│   ├── main/
│   │   ├── avro/
│   │   │   └── BookingCreated.avsc               # Avro schema → BookingCreatedAvro.java
│   │   ├── java/com/rzodeczko/
│   │   │   ├── application/
│   │   │   │   ├── command/
│   │   │   │   │   └── CreateBookingCommand.java
│   │   │   │   ├── port/
│   │   │   │   │   ├── in/  CreateBookingUseCase.java
│   │   │   │   │   └── out/ TravelRepository.java
│   │   │   │   └── service/
│   │   │   │       └── BookingService.java
│   │   │   ├── domain/
│   │   │   │   ├── exception/
│   │   │   │   │   ├── OverbookingException.java
│   │   │   │   │   └── ResourceNotFoundException.java
│   │   │   │   └── model/
│   │   │   │       ├── Booking.java               # record
│   │   │   │       └── Hotel.java                 # id + capacity
│   │   │   ├── infrastructure/
│   │   │   │   ├── configuration/
│   │   │   │   │   ├── BeansConfiguration.java
│   │   │   │   │   ├── SchedulingConfiguration.java
│   │   │   │   │   └── serializer/
│   │   │   │   │       ├── CustomLocalDateSerializer.java
│   │   │   │   │       └── CustomLocalDateDeserializer.java
│   │   │   │   ├── kafka/
│   │   │   │   │   ├── outbox/   OutboxScheduler.java
│   │   │   │   │   ├── producer/ AvroProducerConfig.java
│   │   │   │   │   ├── properties/ KafkaTopicProperties.java · OutboxProperties.java
│   │   │   │   │   └── topic/    KafkaTopicConfig.java
│   │   │   │   ├── persistence/
│   │   │   │   │   ├── adapter/    TravelPersistenceAdapter.java  # SELECT FOR UPDATE logic
│   │   │   │   │   ├── entity/     BookingEntity · HotelEntity · OutboxEntity
│   │   │   │   │   │               DailyAvailabilityEntity · DailyAvailabilityId
│   │   │   │   │   │               DeadLetterEntity
│   │   │   │   │   ├── mapper/     TravelMapper.java
│   │   │   │   │   └── repository/ JpaBookingRepository · JpaHotelRepository
│   │   │   │   │                   JpaOutboxRepository · JpaDailyAvailabilityRepository
│   │   │   │   │                   JpaDeadLetterRepository
│   │   │   │   └── tx/
│   │   │   │       └── TransactionalCreateBookingUseCase.java
│   │   │   ├── presentation/
│   │   │   │   ├── controller/  BookingController.java
│   │   │   │   ├── dto/         CreateBookingRequestDto · CreateBookingResponseDto · ErrorResponseDto
│   │   │   │   └── exception/   GlobalExceptionHandler.java
│   │   │   └── TravelAgencyCommandSideApplication.java
│   │   └── resources/
│   │       └── application.yaml
│   └── test/
│       └── java/com/rzodeczko/
│           ├── application/
│           │   ├── command/  CreateBookingCommandTest.java
│           │   └── service/  BookingServiceTest.java
│           └── infrastructure/
│               ├── kafka/outbox/           OutboxSchedulerTest.java
│               └── persistence/entity/     OutboxEntityTest.java
├── .env                                           # Environment variables (not committed)
├── docker-compose.yml                             # MySQL + Kafka KRaft + Schema Registry + Kafka UI + app
├── Dockerfile                                     # Multi-stage build (maven → jre-alpine, non-root)
├── pom.xml
└── README.md
```

---

<a id="contact"></a>
## 🤝 Contact
[Back to Table of Contents](#toc)

Designed and implemented by **Michał Rzodeczko**.

GitHub: [mrzodeczko-dev](https://github.com/mrzodeczko-dev)
