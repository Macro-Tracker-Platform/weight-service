# Weight Service

[**← Back to Main Architecture**](https://github.com/Macro-Tracker-Platform)

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/postgres-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-000?style=for-the-badge&logo=apachekafka)
![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)

---

[![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=for-the-badge)](LICENSE)
[![Swagger](https://img.shields.io/badge/Swagger-API_Docs-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)](https://macrotracker.uk/webjars/swagger-ui/index.html?urls.primaryName=weight-service)
[![Docker Hub](https://img.shields.io/badge/Docker%20Hub-Image-blue?style=for-the-badge&logo=docker)](https://hub.docker.com/repository/docker/olehprukhnytskyi/macro-tracker-weight-service/general)

**Core Physical Metrics Tracking Service.**

Responsible for managing user weight records, tracking historical physical data, and maintaining progress metrics.

## :zap: Service Specifics

* **Smart Batch Deletion (Recursive Event Pattern)**: To prevent database locks and Kafka consumer timeouts during GDPR data cleanup (e.g., deleting a user with years of logs):
  * The service deletes records in small batches.
  * If more data exists, it republishes a `user-deleted` event to Kafka to process the next batch asynchronously.
* **Idempotent Upsert Mechanism**: Utilizes a native SQL `INSERT ... ON CONFLICT DO UPDATE` strategy for logging weights. If a user logs multiple weights on the same day, it automatically overrides the existing record rather than failing or creating duplicates.

---

## :electric_plug: API & Communication

* **Sync Communication**:
  * Exposes a REST API (`/api/weights`) for creating, reading, updating, and deleting weight records.
* **Async Communication (Kafka)**:
  * **Consumes**: `user-deleted` topic (from User Service).
  * **Produces**: `user-deleted` topic (Loopback for recursive batch deletion).

---

## :hammer_and_wrench: Tech Details

| Component     | Implementation                      |
|:--------------|:------------------------------------|
| **Framework** | Spring Boot 3, Java 21              |
| **Database**  | PostgreSQL, Liquibase               |
| **Messaging** | Apache Kafka (`Spring Kafka`)       |
| **Mapping**   | MapStruct (Component Model: Spring) |

---

## :gear: Environment Variables

Required variables for `local` or `k8s` deployment:

| Variable            | Purpose                                      |
|:--------------------|:---------------------------------------------|
| **Database**        |                                              |
| `DB_HOST`           | Database hostname (e.g., `postgres`).        |
| `DB_PORT`           | Database port (e.g., `5432`).                |
| `DB_NAME`           | Database name.                               |
| `DB_USERNAME`       | Database user.                               |
| `DB_PASSWORD`       | Database password.                           |
| **Integrations**    |                                              |
| `KAFKA_URL`         | Kafka bootstrap servers address.             |
| **Application**     |                                              |
| `MACRO_TRACKER_URL` | Public URL of the application (for Swagger). |

---

## :whale: Quick Start

```bash
# Pull from Docker Hub
docker pull olehprukhnytskyi/macro-tracker-weight-service:latest

# Run (Ensure your .env file contains all required variables)
docker run -p 8080:8080 --env-file .env olehprukhnytskyi/macro-tracker-weight-service:latest
```

---

## :balance_scale: License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.