<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="src/main/resources/META-INF/resources/images/meads-logo-white.svg" />
    <source media="(prefers-color-scheme: light)" srcset="src/main/resources/META-INF/resources/images/meads-logo-dark-grey.svg" />
    <img src="src/main/resources/META-INF/resources/images/meads-logo-dark-grey.svg" alt="MEADS" width="300" />
  </picture>
</p>

<h3 align="center">Mead Evaluation and Awards Data System</h3>

<p align="center">
  A web application for managing mead competitions &mdash; from registration through judging and results.
</p>

---

## Overview

MEADS handles the full lifecycle of a mead competition: organizers create competitions and divisions, entrants register their meads through a self-service portal, and administrators manage the judging and awards process. The system supports multiple competitions, each with independent divisions, scoring systems, and participant roles.

## Tech Stack

- **Java 25** + **Spring Boot 4** + **Spring Modulith**
- **Vaadin 25** (Java Flow, server-side UI)
- **PostgreSQL 18** with Flyway migrations
- **Testcontainers** + **Karibu Testing** for integration and UI tests

## Architecture

The application uses **Spring Modulith** for modular domain-driven design. Each module has a clear public API and private internals, with inter-module communication via Spring application events.

```
app.meads
├── identity      User management, authentication, email
├── competition   Competitions, divisions, participants, categories
└── entry         Webhooks, credits, mead entries, PDF labels
```

## Features

### Identity & Authentication
- JWT magic link login (passwordless)
- Admin password login with forgot/reset flow
- Access code login for simplified onboarding
- User profiles with name, meadery name, and country
- Role-based access: `SYSTEM_ADMIN` and `USER`

### Competition Management
- Competition CRUD with logo, contact info, shipping address, website
- Divisions with independent scoring systems, entry limits, and status workflows
- Division status lifecycle: `DRAFT` -> `REGISTRATION_OPEN` -> `REGISTRATION_CLOSED` -> `JUDGING` -> `DELIBERATION` -> `RESULTS_PUBLISHED`
- Participant management with roles: `ADMIN`, `JUDGE`, `STEWARD`, `ENTRANT`
- Competition documents (PDF upload and external links)
- MJP category catalog with subcategories and guidance hints

### Entry Registration
- Jumpseller webhook integration for automated credit provisioning
- Append-only credit ledger per entrant per division
- Mead entry registration with full metadata (sweetness, strength, carbonation, ABV, ingredients, wood ageing)
- Entry limits enforcement (per subcategory, per main category, total)
- Entry status workflow: `DRAFT` -> `SUBMITTED` -> `RECEIVED` / `WITHDRAWN`
- PDF label generation (OpenPDF + ZXing QR codes) with individual and batch download
- Meadery name requirement per division with profile validation

### Email Notifications
- SMTP email with Thymeleaf HTML templates
- Magic link, password reset, credentials reminder emails
- Entry submission confirmation with entry summary
- Credit notification (webhook and admin-granted)
- Admin order review alerts
- Per-user rate limiting (5-min cooldown on user-triggered emails)

### Internationalization
- 5 languages: English, Spanish, Italian, Polish, Portuguese
- Vaadin I18NProvider + Spring MessageSource
- Locale-aware emails, PDF labels, date/timezone formatting
- Language switcher in navbar, auto-detection from country
- Embedded Liberation Sans font for full Unicode support in PDF labels

## Modules Roadmap

| Module | Status |
|--------|--------|
| `identity` | Complete |
| `competition` | Complete |
| `entry` | Complete |
| `judging` | Planned |
| `awards` | Planned |

## Development

### Prerequisites

- Java 25
- Docker (for PostgreSQL via Testcontainers or docker-compose)

### Running locally

```bash
# Start PostgreSQL
docker compose up -d

# Run the application
mvn spring-boot:run
```

### Testing

```bash
# Full test suite
mvn test

# Single test class
mvn test -Dtest=ClassName

# Single test method
mvn test -Dtest=ClassName#methodName

# Module structure verification
mvn test -Dtest=ModulithStructureTest
```

## Deployment

Deployed on DigitalOcean App Platform (Amsterdam) with Managed PostgreSQL and Resend for transactional email. Image-based deploys via GitHub Container Registry from tagged commits.

## License

Private repository. All rights reserved.
