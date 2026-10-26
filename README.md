# SWEN3_Capa_Doc

[![Java](https://img.shields.io/badge/Java-25-orange.svg?style=flat&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16--alpine-blue.svg?style=flat&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg?style=flat&logo=docker)](https://www.docker.com/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3--management--alpine-orange.svg?style=flat&logo=rabbitmq)](https://www.rabbitmq.com/)

A Document Management System (DMS) designed for archiving documents with automated OCR processing, Gen-AI summaries, tagging, and full-text search.

---

## 📋 Features

- 📁 **Document Archiving:** Secure document storage in a dedicated FileStore.
- 🔍 **Automated OCR:** Queue-based optical character recognition for uploaded documents.
- 🤖 **Gen-AI Summaries:** Automatic generation of document summaries using generative AI.
- 🏷️ **Tagging & Full-Text Search:** High-performance search and categorization powered by Elasticsearch.
- ⚡ **RESTful API:** Spring Boot backend with full CRUD support for document metadata.

---

## 🛠️ Tech Stack

| Component | Technology | Version / Details |
| :--- | :--- | :--- |
| **Backend** | Java / Spring Boot | Eclipse Temurin 25 / Spring Boot 4.1.1 |
| **Database** | PostgreSQL | 16-Alpine |
| **Search Engine** | Elasticsearch | *(Planned / In Integration)* |
| **Containerization**| Docker & Docker Compose | Multi-stage build |
| **API Testing** | Bruno | Pre-configured collection |

---
## 🚀 Getting Started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) & Docker Compose
- [Bruno](https://www.usebruno.com/) *(optional, recommended for testing API endpoints)*
- Java 25 JDK *(only needed for local, non-containerized builds)*

---

### Run with Docker Compose

1. **Configure Environment Variables**

   Ensure a `.env` file exists in the project root alongside `docker-compose.yaml`:

   ```env
   # PostgreSQL Configuration
   DB_NAME=capadoc_db
   DB_USER=your_user
   DB_PASSWORD=your_password
   ```

2. **Start the Services**

   Build the containers and start services in the background:

   ```bash
   docker compose up -d --build
   ```

   - **Backend API:** Available at `http://localhost:8081`
   - **PostgreSQL:** Running on port `5432`
   - **RabbitMQ AMQP:** Available on port `5672`
   - **RabbitMQ Management UI:** Available at `http://localhost:15672`
   - **OCR Worker:** Running as the `ocr-worker` container

3. **Monitor Logs**

   To follow the logs of all containers in real time:

   ```bash
   docker compose logs -f
   ```

   Or follow logs for the API specifically:

   ```bash
   docker compose logs -f api
   ```

4. **Stop the Services**

   To stop and remove containers and networks:

   ```bash
   docker compose down
   ```

   *(Add `-v` to also remove the database volume: `docker compose down -v`)*

---

## 🐇 RabbitMQ and OCR Worker

RabbitMQ decouples document uploads from OCR processing. When the API accepts a
document that requires OCR, it publishes a JSON message to the OCR exchange.
The OCR Worker consumes the message asynchronously, so the API does not have
to wait for OCR processing to finish.

### RabbitMQ setup

RabbitMQ is started by Docker Compose with the `rabbitmq:3-management-alpine`
image. Configure its credentials in the root `.env` file:

```env
RABBITMQ_USER=your_user
RABBITMQ_PASSWORD=your_password
```

The credentials are used by both the API and the OCR Worker. Do not commit
real credentials to the repository.

The RabbitMQ Management UI is available at
[`http://localhost:15672`](http://localhost:15672) after startup. Log in with
the configured `RABBITMQ_USER` and `RABBITMQ_PASSWORD`. The AMQP protocol used
by the Spring Boot services remains available on port `5672`.

### Exchanges, queues, and routing keys

| Resource | Name | Purpose |
| :--- | :--- | :--- |
| OCR exchange | `ocr.exchange` | Direct exchange receiving OCR jobs |
| OCR queue | `ocr.queue` | Durable queue consumed by the OCR Worker |
| OCR routing key | `ocr.routingkey` | Routes messages from the exchange to `ocr.queue` |
| Dead-letter exchange | `ocr.dlx` | Receives rejected or failed OCR messages |
| Dead-letter queue | `ocr.dlq` | Stores messages that could not be processed |
| Dead-letter routing key | `ocr.dlq.routingkey` | Routes messages to `ocr.dlq` |

The API and Worker declare the OCR exchange, queue, and binding from their
`capadoc.rabbitmq.*` configuration. The main queue is configured with the
dead-letter exchange. Listener failures are not endlessly requeued; rejected
messages are routed to the dead-letter flow for inspection in the Management
UI.

The Worker connects to the Compose service name `rabbitmq`. When running the
Worker directly on the host, use `localhost` and ensure RabbitMQ is running
locally or through Docker Compose.

For the component relationships and message flow, see
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## 📡 API Endpoints

Base URL: `http://localhost:8081`

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/ping` | Health check endpoint |
| `GET` | `/api/documents` | Retrieve all documents |
| `GET` | `/api/documents/{id}` | Retrieve a document by ID |
| `POST` | `/api/documents` | Create document metadata |
| `PUT` | `/api/documents/{id}` | Update document metadata |
| `DELETE` | `/api/documents/{id}` | Delete a document |

---

## 🧪 API Testing with Bruno

A pre-configured Bruno collection is included in the repository.

1. Open the **[Bruno](https://www.usebruno.com/)** desktop application.
2. Select **Open Collection** and choose the directory:
   ```text
   CapaDocAPI/CapaDoc API Bruno
   ```
3. Make sure the services are running via Docker Compose (`http://localhost:8081`).
4. Select the environment or verify that `baseUrl` is set to `http://localhost:8081`.
5. Run the requests (e.g. `Ping`, `01-Documents`) to test the API.

---

## 💻 Local Development (Without Docker for Backend)

If you prefer to run the Spring Boot API directly on your host machine:

1. **Start only the PostgreSQL database**:
   ```bash
   docker compose up -d db
   ```

2. **Run the backend using the Maven wrapper**:
   ```bash
   cd CapaDocAPI
   ./mvnw spring-boot:run
   ```
