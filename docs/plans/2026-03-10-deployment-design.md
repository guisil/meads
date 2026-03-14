# Deployment Design — MEADS Project

**Date:** 2026-03-10
**Status:** Deployed (2026-03-14) — DigitalOcean App Platform + Managed PostgreSQL
**Domain:** meads.app (Namecheap)

---

## Context

The app needs to be online for roughly 4-5 months (March–July 2026) for a single
competition in Portugal. Entry registration until June, judging mid-June, scoresheets
available for about a month after. Very low traffic — under 200 entries total, peak of
maybe a few dozen concurrent users during judging days.

### Non-negotiables

- **Managed PostgreSQL with automatic backups** — data must never be lost
- **EU region** — competition is in Portugal
- **Minimal ops** — limited time for server management, prefer managed/PaaS

### Preferences

- Budget: under $20/mo ideal, up to $30/mo acceptable, $50/mo ceiling
- Existing accounts: AWS, DigitalOcean
- Some server management is OK if needed, but prefer to avoid it

---

## Options Evaluated

### 1. Railway (~$8-12/month)

| Component | Service | Cost |
|-----------|---------|------|
| App | Railway service (usage-based, ~512MB-1GB) | ~$5-7/mo |
| Database | Railway Postgres (usage-based) | ~$1-3/mo |
| Email | Resend free tier (100 emails/day) | $0 |

- **Deploy:** Git push or Docker image
- **DB Backups:** None built-in. Must self-configure via template (Barman PITR or daily
  pg_dump to S3). Adds complexity and small S3 cost.
- **EU Region:** Frankfurt
- **Ops:** Low for app, medium for backups (must set up and verify)
- **Shutdown:** Delete services, stop billing immediately

**Assessment:** Cheapest option, but lack of native backups is a real gap. Must deploy a
backup template and trust it works — undermines the "deploy and mostly forget" appeal.
Conflicts with the "never lose data" non-negotiable without extra work.

### 2. DigitalOcean App Platform + Managed DB (~$20/month) — RECOMMENDED

| Component | Service | Cost |
|-----------|---------|------|
| App | App Platform Basic (512MB) | ~$5/mo |
| Database | Managed PostgreSQL (1GB RAM, 10GB storage) | $15/mo |
| Email | Resend free tier (100 emails/day) | $0 |

- **Deploy:** Git push (Dockerfile), auto-deploy on push
- **DB Backups:** Daily automatic backups + point-in-time recovery included. 7-day retention.
  No extra config needed.
- **EU Region:** Amsterdam, Frankfurt
- **Ops:** Minimal — fully managed
- **Shutdown:** Destroy app + DB, stop billing
- **Existing account:** Yes

**Assessment:** Best balance of cost, safety, and simplicity. Cheapest option with truly
automatic backups + PITR and zero ops.

### 3. DigitalOcean Droplet + Managed DB (~$21/month)

| Component | Service | Cost |
|-----------|---------|------|
| App | Droplet 1GB RAM | ~$6/mo |
| Database | Managed PostgreSQL (same as above) | $15/mo |
| Email | Resend free tier | $0 |

- **Deploy:** SSH, install Java, systemd service, nginx + Let's Encrypt for HTTPS
- **DB Backups:** Same as above — daily + PITR included
- **EU Region:** Amsterdam, Frankfurt
- **Ops:** Medium — manage server (Java updates, HTTPS renewal, etc.)
- **Shutdown:** Destroy droplet + DB

**Assessment:** Saves $1/mo vs App Platform but adds significant ops work. Not worth it.

### 4. AWS Lightsail Container + Managed DB (~$25/month)

| Component | Service | Cost |
|-----------|---------|------|
| App | Lightsail Container Micro (0.25 vCPU, 1GB) | $10/mo (3mo free) |
| Database | Lightsail Managed PostgreSQL (1GB RAM, 40GB SSD) | $15/mo |
| Email | SES (~$0.10/1000 emails) | ~$0 |

- **Deploy:** Docker image pushed to Lightsail container registry
- **DB Backups:** Daily automatic backups, 7-day retention. Point-in-time recovery. Included.
- **EU Region:** Frankfurt, London, Paris, Ireland
- **Ops:** Low — simplified AWS console
- **Shutdown:** Must delete (not just disable) to stop billing
- **Existing account:** Yes
- **Upgrade path:** Can migrate to full AWS services (RDS, ECS) later if needed

**Assessment:** Solid option if AWS is preferred. $5/mo more than DO for comparable features.
First 3 months: ~$15/mo (container free tier).

### 5. AWS Elastic Beanstalk + RDS (~$25-32/month)

| Component | Service | Cost |
|-----------|---------|------|
| App | Elastic Beanstalk (t4g.micro, 1GB) | ~$7-8/mo |
| Database | RDS PostgreSQL (db.t4g.micro, 20GB, single-AZ) | ~$15-22/mo |
| Storage | RDS gp3 (20GB) | ~$2/mo |
| Email | SES | ~$0 |

- **Deploy:** Upload JAR via EB CLI or console
- **DB Backups:** Daily automatic, configurable retention up to 35 days. PITR included.
- **EU Region:** Frankfurt, Ireland
- **Ops:** Low-medium — more AWS console config than Lightsail
- **Shutdown:** Terminate EB environment + delete RDS. Can snapshot RDS before deleting.
- **Existing account:** Yes

**Assessment:** Standard AWS, most documentation and flexibility. But costs more and has
more moving parts than needed for this use case.

---

## Side-by-side summary

| | Railway | DO App Platform | AWS Lightsail | AWS EB + RDS |
|---|---|---|---|---|
| **Monthly cost** | $8-12 | ~$20 | ~$25 | ~$25-32 |
| **DB backups** | DIY | Daily + PITR | Daily (7-day) | Daily + PITR (35-day) |
| **Deploy** | Git push / Docker | Git push / Docker | Docker | JAR / Docker |
| **Ops effort** | Low app, medium backups | Minimal | Low | Low-medium |
| **EU region** | Frankfurt | Amsterdam/Frankfurt | Frankfurt+ | Frankfurt/Ireland |
| **Existing account** | No | Yes | Yes (AWS) | Yes (AWS) |

---

## Email: Resend vs SES

| | Resend | AWS SES |
|---|---|---|
| **Free tier** | 100 emails/day | 62,000/mo (if sending from EC2/Beanstalk) |
| **Setup** | API key + DNS records (SPF, DKIM) on meads.app | SES console + DNS records + request production access |
| **Integration** | REST API or SMTP | SMTP (works with spring-boot-starter-mail) |
| **Effort** | Very easy | Medium (must exit SES sandbox — requires AWS support request) |

For DO/Railway: Resend is simpler. For AWS options: SES is natural and potentially free.

---

## Recommendation

**DigitalOcean App Platform + Managed PostgreSQL** ($20/mo):
- Meets the "never lose data" requirement with zero extra config
- Minimal ops — git push deploys, managed everything
- Existing account, EU region
- Within budget
- Easy to shut down after the competition

---

## Configuration audit (completed)

Properties have been reorganized so that no secrets or environment-specific values live
in the main `application.properties`. The file structure is:

| File | Committed | Loaded when | Contents |
|------|-----------|-------------|----------|
| `application.properties` | Yes | Always | Non-sensitive, environment-agnostic defaults only |
| `application-dev.properties` | Yes | `dev` profile active | Local DB, dev secrets, seed emails |
| `application-prod.properties` | Yes | `prod` profile active | `vaadin.launch-browser=false` only |
| `src/test/resources/application.properties` | Yes | Tests (overrides main) | Test-safe secrets, vaadin off |

### Deployment configuration checklist

These must be set as environment variables on the deployment platform:

| Variable | Type | Description |
|----------|------|-------------|
| `SPRING_PROFILES_ACTIVE` | Config | Set to `prod` |
| `SPRING_DATASOURCE_URL` | Connection | PostgreSQL connection URL (e.g., `jdbc:postgresql://host:5432/meads`) |
| `SPRING_DATASOURCE_USERNAME` | Credential | DB username (may be auto-configured by managed DB) |
| `SPRING_DATASOURCE_PASSWORD` | Secret | DB password (may be auto-configured by managed DB) |
| `APP_AUTH_JWT_SECRET` | Secret | JWT signing key (min 32 chars, unique per env, generate with `openssl rand -base64 32`) |
| `APP_JUMPSELLER_HOOKS_TOKEN` | Secret | Jumpseller webhook HMAC token |
| `APP_BASE_URL` | Config | Public URL (e.g., `https://meads.app`) |
| `INITIAL_ADMIN_EMAIL` | Config | Bootstrap admin email |
| `INITIAL_ADMIN_PASSWORD` | Secret | Bootstrap admin password |

**Note:** Spring Boot automatically maps env vars to properties — e.g., `APP_AUTH_JWT_SECRET`
resolves to `app.auth.jwt-secret`. DB credentials may be auto-injected by managed database
providers (DO Managed DB, RDS, Lightsail) — check provider docs.

---

## Implementation notes (for when this is picked up)

### Prerequisites
- Dockerfile for the Spring Boot app (doesn't exist yet — needs to be created)
- DNS: point meads.app to the deployment (A record or CNAME)
- Email: configure Resend (or SES) + DNS records (SPF, DKIM, DMARC) on meads.app
- Set all env vars from the deployment configuration checklist above

### Ties to other priorities
- **Email sending (Priority 5):** Blocked until deployment provider chosen (determines
  SES vs Resend). DNS setup for email can be done in parallel.
- **i18n (Priority 1):** No deployment impact — resource bundles deploy as part of the JAR
