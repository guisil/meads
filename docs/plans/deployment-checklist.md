# Deployment Checklist — MEADS on DigitalOcean App Platform

**Target:** DigitalOcean App Platform (app) + Managed PostgreSQL (database)
**Domain:** meads.app (Namecheap)
**Email:** Resend SMTP
**Region:** Amsterdam (AMS)
**CI/CD:** GitHub Actions (test + build Docker image + push to GHCR + update DO app)

**First deployed:** 2026-03-14

---

## Phase 1: Local preparation (code artifacts)

These files must exist in the repository before deployment.

### 1.1 Maven production profile (`pom.xml`)

Add a `production` profile that sets `vaadin.productionMode=true` and excludes
`vaadin-dev`. This produces an optimized JAR with bundled, minified frontend.

```xml
<profiles>
    <profile>
        <id>production</id>
        <properties>
            <vaadin.productionMode>true</vaadin.productionMode>
        </properties>
        <dependencies>
            <dependency>
                <groupId>com.vaadin</groupId>
                <artifactId>vaadin</artifactId>
                <exclusions>
                    <exclusion>
                        <groupId>com.vaadin</groupId>
                        <artifactId>vaadin-dev</artifactId>
                    </exclusion>
                </exclusions>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

Verify: `mvn clean package -Pproduction -DskipTests` produces a runnable JAR (~91 MB).

### 1.2 Dockerfile

Multi-stage build using Maven wrapper:

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src/ src/
RUN ./mvnw clean package -Pproduction -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Also create `.dockerignore` to exclude `target/`, `.git/`, `docs/`, `node_modules/`,
`*.md`, `.claude/`, `.idea/`, `*.iml`, `docker-compose.yml`.

### 1.3 Logging configuration (`logback-spring.xml`)

Create `src/main/resources/logback-spring.xml` with profile-aware log levels:

- **dev profile:** `app.meads` → DEBUG, `org.hibernate.SQL` → DEBUG
- **prod profile:** `app.meads` → INFO, `org.springframework` → WARN, `com.vaadin` → WARN,
  `org.flywaydb` → INFO (to see migration output)
- **Console appender only** — DO App Platform captures stdout

### 1.4 GitHub Actions CI (`.github/workflows/ci.yml`)

Two jobs:

- **test** (push/PR to `main` + tags): `mvn test` + `mvn package -Pproduction -DskipTests`.
  Uses `actions/setup-java@v4` with Temurin, caches `~/.m2/repository`.
  Testcontainers works natively on GitHub Actions (Docker is pre-installed).

- **deploy** (only on `v*` tags): Builds Docker image from the tagged commit, pushes to
  GHCR (`ghcr.io/guisil/meads:<tag>`), then updates the DO app spec to use that image
  via `doctl apps update`. This ensures the deployed version matches the tag exactly —
  no race condition with SNAPSHOT bumps on `main`.

### 1.5 Verify locally

- `mvn clean test -Dsurefire.useFile=false` — all tests pass
- `mvn clean package -Pproduction -DskipTests` — production build succeeds

---

## Phase 2: Resend (email provider) setup

### 2.1 Create Resend account and API key

- Sign up at resend.com (free tier: 100 emails/day, 3000/month)
- Generate an API key (Sending access only, restricted to your domain)
- Save the key — it becomes the `SPRING_MAIL_PASSWORD` env var
- `spring.mail.username=resend` is already set in `application-prod.properties`

### 2.2 Add and verify domain in Resend

- Resend → Domains → Add Domain → enter `meads.app`
- Resend shows DNS records in three sections: **DKIM**, **SPF**, **DMARC**

### 2.3 Add DNS records in Namecheap

Go to Namecheap → Domain List → meads.app → Advanced DNS.

**In Host Records** (3 TXT records):

| Type | Host | Value |
|------|------|-------|
| TXT | `resend._domainkey` | DKIM value from Resend (`p=...`) |
| TXT | `send` | SPF value from Resend (`v=spf1...`) |
| TXT | `_dmarc` | DMARC value from Resend (`v=DMARC1;...`) |

**In Mail Settings** (1 MX record):

Namecheap puts MX records in a separate **Mail Settings** section, not in Host Records.
Change the Mail Settings mode to **Custom MX**, then add:

| Type | Host | Value | Priority |
|------|------|-------|----------|
| MX | `send` | Feedback SMTP value from Resend | 10 |

**Note:** Changing Mail Settings mode may remove Namecheap's default SPF record for
email forwarding — this is fine since we're using Resend.

### 2.4 Verify domain

- Back in Resend → Domains → click Verify
- DNS propagation usually takes a few minutes, can take up to a few hours
- Status should change to "Verified"

---

## Phase 3: DigitalOcean — Managed PostgreSQL

### 3.1 Create managed database cluster

- DigitalOcean Console → Databases → Create Database Cluster
- Engine: **PostgreSQL 18** (or latest available)
- Plan: **Basic** — 1 vCPU, 1 GB RAM, 10 GB disk ($15/mo)
- Region: **Amsterdam** (AMS3)
- Cluster name: `meads-db`
- Provisioning takes ~5 minutes

### 3.2 Configure database

- Users & Databases tab → create database: `meads`
- Leave `defaultdb` (system default, harmless)
- Use the default `doadmin` user (no need for a separate user)
- Note connection details: host, port, username, password
- Connection URL format: `jdbc:postgresql://<host>:<port>/meads?sslmode=require`

### 3.3 Verify backups

- Cluster → Backups tab
- Daily automatic backups + point-in-time recovery (PITR) — enabled by default
- 7-day retention, no configuration needed

### 3.4 Configure trusted sources (after app is created)

- May be configured automatically when attaching DB during app creation
- Verify: cluster → Settings → Trusted Sources → App Platform app is listed
- This restricts DB access to your app only

---

## Phase 4: DigitalOcean — App Platform

### 4.1 Create the app

- App Platform → Create App
- Source: **GHCR image** (`ghcr.io/guisil/meads`) — managed by GitHub Actions CI
- Plan: **Basic** ($12/mo) — 1 GB RAM, 1 vCPU
- Region: **Amsterdam** (same as database)
- HTTP port: `8080`
- Auto-deploy: **disabled** (deploys triggered by CI on release tags only)
- VPC network: **not needed** (DB connection uses SSL over public network)

### 4.2 Attach the managed database

- During app creation: Add database → select existing `meads-db` cluster
- Trusted sources may be configured automatically at this point

### 4.3 Set environment variables

All variables are **Run time** scope only (not needed during Docker build).

| Variable | Value | Encrypt? |
|----------|-------|----------|
| `SPRING_PROFILES_ACTIVE` | `prod` | No |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>:<port>/meads?sslmode=require` | No |
| `SPRING_DATASOURCE_USERNAME` | `doadmin` | No |
| `SPRING_DATASOURCE_PASSWORD` | DB password | **Yes** |
| `APP_AUTH_JWT_SECRET` | Generate with `openssl rand -base64 32` | **Yes** |
| `APP_JUMPSELLER_HOOKS_TOKEN` | Generate with `openssl rand -base64 32` (same value goes in Jumpseller later) | **Yes** |
| `APP_BASE_URL` | `https://meads.app` | No |
| `SPRING_MAIL_PASSWORD` | Resend API key | **Yes** |
| `INITIAL_ADMIN_EMAIL` | Admin email address | No |
| `INITIAL_ADMIN_PASSWORD` | Strong password (remove after first deploy) | **Yes** |

### 4.4 Configure health check

Found under: App Platform → Settings → Components → click web service component.

- HTTP path: `/`
- Initial delay: **120 seconds** (Vaadin first startup is slow)
- Timeout: 10 seconds
- Interval: 30 seconds
- Success/Failure thresholds: defaults are fine

### 4.5 Deploy

- First deploy triggers automatically after app creation
- Monitor build logs (Maven + Vaadin frontend — first build takes 5-10 minutes)
- Watch runtime logs for:
  - Flyway migration output (tables created)
  - `Created initial admin user:` log line
  - `Started MeadsApplication in X seconds`
  - No stack traces or errors

---

## Phase 5: DNS configuration

### 5.1 Point domain to App Platform

- App Platform → Settings → Domains → Add Domain → enter `meads.app`
- DO provides a CNAME target (e.g., `meads-app-xxxxx.ondigitalocean.app`)
- Namecheap → Advanced DNS → Host Records:
  - Replace the default CNAME record (`parkingpage.namecheap.com`) with the DO target
  - Host: `@`, Value: DO CNAME target, TTL: Automatic

**Note:** Namecheap supports CNAME on the root domain (`@`) — it worked for `meads.app`.
Domain status in DO goes through: Pending → Configuring → Active. This can take
5-15 minutes while DO provisions the SSL certificate.

### 5.2 SSL/TLS

- DO App Platform auto-provisions a Let's Encrypt certificate once DNS resolves
- Verify: `https://meads.app` loads with valid certificate (padlock icon)
- HTTP → HTTPS redirect is automatic

---

## Phase 6: Verification

### 6.1 Application smoke test

- Open `https://meads.app` — should redirect to login page
- Log in with admin credentials
- Verify redirect to `/competitions` (admin landing page)
- Navigate key pages, verify no errors

### 6.2 Email delivery test

- Create a user with a real email address
- Confirm magic link email arrives
- Click magic link — confirm login works
- Check email headers: SPF pass, DKIM pass

### 6.3 Webhook test (when Jumpseller is configured)

- Configure Jumpseller webhook URL: `https://meads.app/api/webhooks/jumpseller/order-paid`
- Use the same token as `APP_JUMPSELLER_HOOKS_TOKEN`
- Trigger a test order and check app logs

### 6.4 Database and log verification

- DO Console → Databases → meads-db → Insights: active connections, storage usage
- Backups tab: first automatic backup appears within 24h
- App Platform → Runtime Logs: logs flowing, no recurring errors

---

## Phase 7: Post-deploy hardening

### 7.1 Remove bootstrap credentials

- Remove `INITIAL_ADMIN_PASSWORD` env var (prevents accidental re-creation on redeploy)
- Keep `INITIAL_ADMIN_EMAIL` (harmless — AdminInitializer skips if admin exists)
- Env var removal triggers automatic redeploy

### 7.2 Verify database access restriction

- Confirm trusted sources are configured (Phase 3.4)
- Only the App Platform app should reach the database

### 7.3 Set up monitoring alerts (optional)

**DigitalOcean resource alerts** (DO Console → Monitoring → Create Alert):

- CPU usage > 80% for 5 minutes
- Memory usage > 85% for 5 minutes
- Database disk usage > 80%
- Alert destination: your email

**Resend limits:**

- Free tier: 100 emails/day, 3,000 emails/month
- Estimated volume: ~15-30 emails/day during active registration
- Bookmark Resend dashboard (Usage) for periodic checks
- Watch app logs for SMTP send failures

---

## DigitalOcean operations reference

Quick reference for finding things in the DO Console.

| What | Where |
|------|-------|
| **DB backups + restore** | Databases → meads-db → **Backups** tab |
| **DB metrics** (connections, queries, disk) | Databases → meads-db → **Insights** tab |
| **DB connection details** | Databases → meads-db → **Overview** → Connection Details |
| **DB trusted sources** | Databases → meads-db → **Settings** → Trusted Sources |
| **App runtime logs** | App Platform → meads app → **Runtime Logs** tab |
| **Build/deploy logs** | App Platform → meads app → **Activity** tab → click deployment |
| **App metrics** (CPU, memory) | App Platform → meads app → **Insights** tab |
| **Deployments / rollback** | App Platform → meads app → **Activity** tab |
| **Env vars** | App Platform → meads app → **Settings** → App-Level Environment Variables |
| **Health check config** | App Platform → meads app → **Settings** → Components → web service |
| **Alert policies** | App Platform → meads app → **Settings** → Alert Policies |
| **Domain / SSL** | App Platform → meads app → **Settings** → Domains |
| **Resend usage / limits** | resend.com → Usage |
| **Resend domain status** | resend.com → Domains |
| **DNS records** | Namecheap → Domain List → meads.app → Advanced DNS |

---

## Deployment pipeline (how updates work)

```
Push to main     → GitHub Actions: test + build (CI only, no deploy)
Push v* tag      → GitHub Actions: test + build → build Docker image → push to GHCR → update DO app
```

- **Push to `main` / PRs:** GitHub Actions runs tests + production build. No deployment.
- **Release tags (`v*`):** GitHub Actions runs tests, builds a Docker image from the
  **tagged commit**, pushes it to GHCR (`ghcr.io/guisil/meads:<tag>`), then updates the
  DO app spec to use that image via `doctl apps update`. This eliminates the race condition
  where the SNAPSHOT bump on `main` could be deployed instead of the tagged version.
- **Zero-downtime deploys:** DO pulls the pre-built image, health-checks it, then routes
  traffic. Old version keeps serving until new one is confirmed healthy.
- **If build fails:** DO keeps previous version running. Fix and push again.

### GitHub Actions secrets (required)

| Secret | Description |
|--------|-------------|
| `DIGITALOCEAN_ACCESS_TOKEN` | DO API token with `app:create` scope |
| `DIGITALOCEAN_APP_ID` | App Platform app UUID |
| `GHCR_REGISTRY_CREDENTIALS` | `guisil:<PAT>` — GitHub PAT with `read:packages` scope, for DO to pull images |

To create the PAT: GitHub → Settings → Developer settings → Personal access tokens →
Fine-grained tokens → Generate. Scope: `read:packages` only. Format the secret as
`guisil:<token>`.

### Release process

1. Update version in `pom.xml` (remove `-SNAPSHOT`)
2. Commit: `Release vX.Y.Z`
3. Tag: `git tag -a vX.Y.Z -m "vX.Y.Z — description"`
4. Push: `git push && git push origin vX.Y.Z`
5. CI automatically: runs tests → builds image → pushes to GHCR → updates DO app
6. Create GitHub release: `gh release create vX.Y.Z --title "..." --notes "..."`
7. Bump version to next `-SNAPSHOT` and push
8. Monitor: App Platform → Activity → current deployment

### Standard update (code-only)

1. Follow the release process above
2. Monitor: App Platform → Activity → current deployment
3. Verify: app loads, quick smoke test, check runtime logs

### Update with Flyway migration

Flyway runs on app startup. During zero-downtime deploy, old code briefly runs against
new schema. This is safe only if migrations are **backward-compatible**:

- Adding a new table → **safe**
- Adding a nullable column → **safe**
- Adding a NOT NULL column → **unsafe** (old code inserts fail)
- Renaming/dropping a column → **unsafe** (old code queries fail)

For non-backward-compatible changes, use a **two-phase deploy**:
1. Deploy backward-compatible migration (add new structure alongside old)
2. Deploy code that uses the new structure + cleanup migration

### Rollback

- App Platform → Activity → previous deployment → "Rollback to this deployment"
- **Does NOT roll back Flyway migrations** — assess schema compatibility before rolling back
- For risky migrations: take a manual backup first (Databases → Backups → Create Backup Now)

### Environment variable changes

- App Platform → Settings → App-Level Environment Variables → Save
- Triggers automatic redeploy
- **JWT secret rotation** invalidates all outstanding magic link tokens

---

## Teardown (after competition ends)

1. Export data if needed (pg_dump via DO console)
2. DO Console → App Platform → Destroy App
3. DO Console → Databases → Destroy Cluster (take final manual backup first)
4. Resend → optionally remove domain
5. Namecheap → remove/update DNS records
6. Billing stops immediately on resource destruction
