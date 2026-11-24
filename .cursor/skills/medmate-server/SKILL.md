---
name: medmate-server
description: >-
  Guides changes to the pharmacy_server Spring Boot service: module layout,
  tests, Spotless, Docker, actuator, and Bruno. Use when editing
  apps/pharmacy_server, its Dockerfile, its Makefile, or bruno/medmate.
---

# pharmacy_server

Pharmacy CRM API. Java 21, Spring Boot 3.5.6, Maven reactor at `apps/pharmacy_server`. Host: `pharmacy-server.medmate.in`. The process listens on port 8080. Health: `/actuator/health`.

Requirement docs and the Bruno collection were not in the app folder and were not copied. The old paths are `packages/docs/medmate-server/architecture.md` and `bruno/medmate/<module>/`.

## Layout

- Runnable code stays in `pharmacy_server_app`, package `in.medmate.pharmacy_server`.
- A new CRM area is a sibling Maven module. Do not create a second deployable without an ADR in `packages/docs/medmate-server/decisions/`.
- Controllers delegate. Rules that can be tested without Spring are plain types.
- Errors go through `ApiExceptionHandler` as `ProblemDetail`. Do not add another error JSON shape.
- No Spring Security, JPA, Flyway, S3, or extra starter unless the accepted requirement and an ADR require it.

## Checks

From `apps/pharmacy_server`: `make format`, then `make verify`.

- Surefire runs unit tests and `@WebMvcTest` slices.
- Failsafe runs classes named `*IT`.
- Spotless uses Palantir Java Format during `verify` and as the `format` deploy gate.
- A new HTTP route adds a Bruno request under `bruno/medmate/<module>/` and reuses `examplePharmacyId`, `examplePhone`, and `exampleRequestId`.

## Runtime

`make run` from `apps/pharmacy_server` runs Java 21 on the host with the `local` profile and Postgres 16 on `127.0.0.1:5432`, database `medmate_server`. Install missing tools with `brew install openjdk@21 postgresql@16`.

The default and `prod` profiles do not open a database. Tests and the deployed container must stay that way until a requirement enables production Postgres.

The Docker image is what production runs. Do not use it as the local development loop.
