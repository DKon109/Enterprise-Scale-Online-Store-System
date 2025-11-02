# Ordering & Orchestration Module

This module implements the ordering saga that coordinates inventory, payments, and shipping to drive the end-to-end Reserve → Pay → Ship → Complete workflow with compensations, persistence, and observability hooks.

## Public API
- `POST /orders` – starts the saga, persists the order, and kicks off orchestration.
- `GET /orders/{id}` – returns current status and event timeline.
- `POST /orders/{id}/cancel` – cancels an order before shipment has been requested.

Controller-level DTOs live in `com.comp5348.store.order.api` and are framework-agnostic so they can be wired into the HTTP layer of your choice.

## Module Layout Highlights
- `domain` – aggregate (`Order`), saga state, timeline entries, and repository contracts.
- `application` – orchestrator service, query service, retry/circuit-breaker policies, outbound event factories.
- `infrastructure` – in-memory repositories (orders, timeline, saga state, outbox) plus structured call logger.
- `api` – request/response models and controller wrapper for the public API.
- `tests` – unit coverage for domain transitions, saga orchestration scenarios, and outbox behavior (`./gradlew test`).

## Testing
Run `./gradlew test` to execute the domain and application test suite, including saga happy path, payment compensation, cancellation, idempotent authorization checks, and outbox repository guarantees.
