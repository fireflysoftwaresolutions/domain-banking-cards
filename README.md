<a href="https://fireflysoftware.io/openplatform/">
<picture>
  <source media="(max-width: 640px) and (prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-dark.svg">
  <source media="(max-width: 640px)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-narrow-light.svg">
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-dark.svg">
  <img src="https://raw.githubusercontent.com/firefly-oss/.github/main/profile/assets/banner-light.svg" alt="Firefly OpenPlatform - Domain Banking - Cards" width="100%">
</picture>
</a>

# Firefly OpenPlatform - Domain Banking - Cards

Developed by the Firefly Software Foundation. [dev@fireflysoftware.io](mailto:dev@fireflysoftware.io)

[Website](https://fireflysoftware.io/) · [OpenPlatform](https://fireflysoftware.io/openplatform/) · [Module catalogue](https://fireflysoftware.io/openplatform/modules/)

Domain layer microservice for banking card orchestration within the Firefly platform. This service acts as the orchestration layer for card lifecycle operations, including card issuance, activation, blocking, replacement, cancellation, virtual cards, limits, and security settings.

Repository: [https://github.com/firefly-oss/domain-banking-cards](https://github.com/firefly-oss/domain-banking-cards)

---

## Overview

`domain-banking-cards` provides a reactive REST API that coordinates complex, multi-step card management workflows. It sits between upstream consumers (experience services) and the `core-banking-cards` and `core-lending-credit-cards` core services, applying domain orchestration through the FireflyFramework Saga Engine and CQRS patterns.

### Key Features

- **Card lifecycle management** -- issue, activate, block, unblock, replace, and cancel debit and credit cards.
- **Virtual card issuance** -- create virtual cards linked to physical cards with spending limits.
- **Card limits management** -- configure daily, monthly, and per-transaction limits.
- **Security settings** -- manage PIN, CVV, contactless, and online transaction settings.
- **Credit line management** -- manage credit limits for credit cards.
- **Backoffice operations** -- administrative endpoints for card details, balances, transactions, and security.
- **Saga-orchestrated transactions** -- every write operation is executed through the FireflyFramework `SagaEngine` with compensating steps for rollback on failure.
- **CQRS architecture** -- commands dispatched via `CommandBus`, queries via `QueryBus`, with configurable timeouts and caching.
- **Event-driven architecture** -- Kafka-based event publishing for domain events via the FireflyFramework EDA module.
- **Reactive, non-blocking** -- built on Spring WebFlux with virtual threads enabled.
- **SDK generation** -- auto-generated Java client SDK from the OpenAPI specification.

---

## Architecture

### Module Structure

| Module | Purpose |
|--------|---------|
| `domain-banking-cards-core` | Business logic: services, commands, handlers, saga workflows, and queries. Organized by subdomain (card, virtual, limits, security, creditline). |
| `domain-banking-cards-interfaces` | DTOs and API contracts bridging the web layer to core domain logic. |
| `domain-banking-cards-infra` | Infrastructure: `ClientFactory` beans for external API clients, `@ConfigurationProperties` configuration. |
| `domain-banking-cards-web` | Deployable Spring Boot application: REST controllers, application config, actuator, and OpenAPI setup. |
| `domain-banking-cards-sdk` | Auto-generated Java client SDK from the OpenAPI spec (WebClient-based, reactive). |

### Dependency Flow

```
web --> interfaces --> core --> infra
sdk (generated from openapi.yml)
```

### Core Subdomains

| Subdomain | Description |
|-----------|-------------|
| `card` | Card lifecycle orchestration; activation delegates to the Core SDK and issuance is unavailable pending the owner integration |
| `virtual` | Virtual card creation and management via `IssueVirtualCardSaga` |
| `limits` | Card spending limits (daily, monthly, per-transaction) |
| `security` | Security settings (PIN, CVV, contactless, online) |
| `creditline` | Credit line management for credit cards |

### Technology Stack

| Technology | Purpose |
|------------|---------|
| Java 25 | Language runtime |
| Spring Boot (WebFlux) | Reactive web framework |
| Project Reactor | Reactive streams |
| Virtual Threads | Enabled via `spring.threads.virtual.enabled: true` |
| FireflyFramework Saga Engine | Distributed saga orchestration |
| FireflyFramework CQRS | Command/Query bus pattern |
| FireflyFramework EDA | Event-driven architecture with Kafka |
| core-banking-cards-sdk | SDK client for the Banking Cards core service |
| core-lending-credit-cards-sdk | SDK client for Credit Cards core service |
| SpringDoc OpenAPI | API documentation |
| Micrometer + Prometheus | Metrics and monitoring |

---

## Setup

### Prerequisites

- **Java 25** (or later)
- **Apache Maven 3.9+**
- Access to FireflyFramework Maven repository
- Access to `core-banking-cards-sdk` and `core-lending-credit-cards-sdk` artifacts
- **Apache Kafka** (for event publishing)

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_ADDRESS` | `localhost` | Address the server binds to |
| `SERVER_PORT` | `8080` | HTTP port |

### Application Configuration

Key settings from `application.yaml`:

```yaml
spring:
  application:
    name: domain-banking-cards
  threads:
    virtual:
      enabled: true

firefly:
  cqrs:
    enabled: true
    command:
      timeout: 30s
      metrics-enabled: true
      tracing-enabled: true
    query:
      timeout: 15s
      caching-enabled: true
      cache-ttl: 15m
  saga.performance.enabled: true
  eda:
    enabled: true
    default-publisher-type: KAFKA
    publishers:
      kafka:
        default:
          default-topic: domain-layer
          bootstrap-servers: localhost:9092

api-configuration:
  core-banking:
    cards:
      base-path: http://localhost:8081
  core-lending:
    credit-cards:
      base-path: http://localhost:8082
```

### Build

```bash
# Full build (includes SDK generation)
mvn clean install

# Skip tests
mvn clean install -DskipTests
```

### Run

```bash
# Run via Spring Boot Maven plugin
mvn -pl domain-banking-cards-web spring-boot:run

# Run with a specific profile
mvn -pl domain-banking-cards-web spring-boot:run -Dspring-boot.run.profiles=dev

# Or run the packaged JAR
java -jar domain-banking-cards-web/target/domain-banking-cards.jar
```

---

## API Endpoints

All endpoints return reactive `Mono<ResponseEntity>` or `Flux<ResponseEntity>` responses.

### Cards (`/api/v1/cards`)

| Method | Path | Summary |
|--------|------|---------|
| `POST` | `/api/v1/cards` | Issue a new card (saga-orchestrated) |
| `POST` | `/api/v1/cards/{cardId}/activate` | Activate a card |
| `POST` | `/api/v1/cards/{cardId}/block` | Block a card temporarily |
| `POST` | `/api/v1/cards/{cardId}/unblock` | Unblock a card |
| `POST` | `/api/v1/cards/{cardId}/replace` | Replace a lost/stolen/damaged card |
| `POST` | `/api/v1/cards/{cardId}/cancel` | Cancel a card permanently |

### Card Backoffice (`/api/v1/backoffice/cards`)

| Method | Path | Summary |
|--------|------|---------|
| `GET` | `/api/v1/backoffice/cards/{cardId}` | Get card details |
| `GET` | `/api/v1/backoffice/cards/{cardId}/balance` | Get card balance |
| `GET` | `/api/v1/backoffice/cards/{cardId}/limits` | Get card limits |
| `GET` | `/api/v1/backoffice/cards/{cardId}/security` | Get security settings |
| `GET` | `/api/v1/backoffice/cards/{cardId}/transactions` | Get transaction history |
| `POST` | `/api/v1/backoffice/cards/{cardId}/virtual` | Create virtual card (saga) |
| `PUT` | `/api/v1/backoffice/cards/{cardId}/security` | Update security settings |

### Card Limits (`/api/v1/cards/{cardId}/limits`)

| Method | Path | Summary |
|--------|------|---------|
| `PUT` | `/api/v1/cards/{cardId}/limits` | Update card limits |
| `GET` | `/api/v1/cards/{cardId}/limits` | Get card limits |

### Card Security (`/api/v1/cards/{cardId}/security`)

| Method | Path | Summary |
|--------|------|---------|
| `PUT` | `/api/v1/cards/{cardId}/security` | Update security settings |
| `POST` | `/api/v1/cards/{cardId}/security/pin/reset` | Reset PIN |

### Virtual Cards (`/api/v1/cards/{cardId}/virtual`)

| Method | Path | Summary |
|--------|------|---------|
| `POST` | `/api/v1/cards/{cardId}/virtual` | Issue virtual card |
| `GET` | `/api/v1/cards/{cardId}/virtual` | List virtual cards |
| `DELETE` | `/api/v1/cards/{cardId}/virtual/{virtualCardId}` | Cancel virtual card |

### Credit Line (`/api/v1/cards/{cardId}/credit-line`)

| Method | Path | Summary |
|--------|------|---------|
| `GET` | `/api/v1/cards/{cardId}/credit-line` | Get credit line |
| `PUT` | `/api/v1/cards/{cardId}/credit-line` | Update credit line |

---

## Monitoring

### Actuator Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Application health with liveness and readiness probes |
| `/actuator/info` | Build information |
| `/actuator/prometheus` | Prometheus-compatible metrics |

### API Documentation

- **Swagger UI**: `/swagger-ui.html` (disabled in `prod` profile)
- **OpenAPI JSON**: `/v3/api-docs`

---

## Spring Profiles

| Profile | Logging | Swagger | Notes |
|---------|---------|---------|-------|
| `default` | INFO | Enabled | Standard development |
| `dev` | DEBUG | Enabled | Verbose debugging |
| `prod` | WARN | Disabled | Production |

## Caller-owned activation and native authority

`POST /api/v1/cards/{cardId}/activate` requires the caller's stable UUID `X-Idempotency-Key` and the actual issued `activationProof`. Domain delegates through the complete generated Core SDK. It preserves the action, owner state and historical activation timestamp; it does not derive a proof from the card ID or manufacture an activation receipt.

Enable `firefly.cards.activation.delegation.enabled` only with `firefly.security.resource-server.strict-policy=true`. Configure the native receiver's issuer, JWKS, audience and tenant projection, and the delegation settings' exact incoming client, target audience, delegating client, registration, tenant and canonical actor/party claim mappings. The OAuth client registration must use token exchange with the same issuer and a deployment-provided client secret. The factory checks current native identity and scoped policy before and after exchange, verifies the exchanged token and rechecks authority before the SDK call. Never persist bearer tokens or replace the original action after an uncertain result.

A deployment can provide a `PolicyDecisionPort`, or explicitly configure the published OPA adapter through `firefly.cards.authorization.opa.enabled`, `endpoint`, `decision-path` and `evaluation-path`. HTTPS origins and exact document paths are required. Loopback HTTP requires explicit qualification opt-in. Missing or unavailable policy fails closed; the adapter has a two-second deadline and bounded response buffering. Core additionally requires engine-provided policy provenance for its current ownership evaluation.

Timeouts and transport I/O errors return HTTP 503 with an unknown outcome. Owner 5xx responses remain unknown; explicit business refusals remain distinct. No error creates a receipt, repeats the POST or chooses a new action. `GET /api/v1/cards/{cardId}/activation/{actionId}` reads the original Core receipt through the same generated SDK chain. It accepts no proof or new action key, returns all retained states with HTTP 200 and `Cache-Control: no-store`, and revalidates current native authority before and after the read. An absent or out-of-scope owner receipt remains 404; an unavailable read never creates a terminal receipt. Recovery does not send an issuer command or change business state. A lost GET response may be read again with the same original action and fresh current authority, including after service restart. An uncertain receipt remains uncertain.

Issuance remains unavailable until the Core managed enrollment/provider transition actually issues and persists an instrument and its proof. The removed successful issuance/activation simulations are not replacements for that integration. The default external issuer adapter executes nothing. Controlled native transport qualification does not establish a live issuer or a completed issuance/activation journey.

The controlled recovery test initializes an existing `UNCERTAIN` action through the original Core reservation and dispatch functions, then reads it through all three packaged services. It compares every public business table before and after reads, refusals, response loss and service restart. That fixture establishes receipt recovery and transport behavior; it is not a real original issuer attempt or completed issuance.
