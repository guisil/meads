# Deployment Checklist — MEADS on DigitalOcean App Platform

**Target:** DigitalOcean App Platform (app) + Managed PostgreSQL (database)
**Domain:** meads.app (Namecheap)
**Email:** Resend SMTP
**Region:** Amsterdam (AMS) or Frankfurt (FRA) — EU

---

## Phase 1: Local preparation

### 1.1 Add Maven production profile

The Vaadin build currently has no production profile. Add one to `pom.xml` so that
`mvn package -Pproduction` produces an optimized JAR (bundled, minified frontend).

- [ ] Add `<profiles>` section with a `production` profile that sets
      `<vaadin.productionMode>true</vaadin.productionMode>`
- [ ] Verify: `mvn clean package -Pproduction -DskipTests` produces a runnable JAR
      in `target/meads-*.jar`
- [ ] Verify JAR size is reasonable (Vaadin production builds are significantly smaller)

### 1.2 Create Dockerfile

No Dockerfile exists yet. Create a multi-stage Dockerfile:

- [ ] **Stage 1 (build):** Use `eclipse-temurin:25-jdk` (or latest LTS matching Java 25),
      copy source, run `mvn clean package -Pproduction -DskipTests`
- [ ] **Stage 2 (runtime):** Use `eclipse-temurin:25-jre`, copy JAR from build stage,
      expose port 8080, set entrypoint
- [ ] Add `.dockerignore` (exclude `target/`, `.git/`, `node_modules/`, `*.md`, `docs/`)
- [ ] Verify locally: `docker build -t meads .` succeeds
- [ ] Verify locally: `docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod -e ... meads`
      starts and responds on `http://localhost:8080`

### 1.3 Add production logging configuration

No `logback-spring.xml` exists — Spring Boot defaults are used (console, no rotation).
For production, create `src/main/resources/logback-spring.xml`:

- [ ] **Console appender** for App Platform log capture (DO captures stdout/stderr)
- [ ] **JSON or structured format** (optional, makes DO log searching easier)
- [ ] Log levels:
  - `app.meads` → `INFO`
  - `org.springframework` → `WARN`
  - `org.hibernate.SQL` → `WARN` (prevent query spam)
  - `com.vaadin` → `WARN`
  - Root → `INFO`
- [ ] **Spring profile conditional:** `<springProfile name="dev">` with `DEBUG` for
      `app.meads`, `<springProfile name="prod">` with the above levels
- [ ] No file appender needed — DO App Platform captures stdout and provides its own
      log viewer with retention. No manual log rotation required.
- [ ] Verify: run app locally with `prod` profile and confirm log output is clean

### 1.4 Run full test suite

- [ ] `mvn clean test -Dsurefire.useFile=false` — all tests pass
- [ ] `mvn clean package -Pproduction -DskipTests` — production build succeeds

---

## Phase 2: Resend (email provider) setup

### 2.1 Create Resend account and API key

- [ ] Sign up at resend.com (free tier: 100 emails/day, 3000/month)
- [ ] Generate an API key — this will be used as `SPRING_MAIL_PASSWORD`
- [ ] Note: `spring.mail.username=resend` is already set in `application-prod.properties`

### 2.2 Add and verify domain in Resend

- [ ] Add `meads.app` as a sending domain in Resend dashboard
- [ ] Resend will provide DNS records to add. Go to Namecheap DNS settings and add:
  - [ ] **SPF** — TXT record on `meads.app` (or update existing): `v=spf1 include:resend.com ~all`
  - [ ] **DKIM** — CNAME record(s) as provided by Resend
  - [ ] **DMARC** — TXT record: `_dmarc.meads.app` → `v=DMARC1; p=none;` (start permissive)
- [ ] Wait for DNS propagation (can take up to 48h, usually minutes)
- [ ] Verify domain in Resend dashboard — status should show "Verified"
- [ ] Send a test email from Resend dashboard to confirm delivery

---

## Phase 3: DigitalOcean — Managed PostgreSQL

### 3.1 Create managed database cluster

- [ ] DigitalOcean Console → Databases → Create Database Cluster
- [ ] Engine: **PostgreSQL 18** (or latest available, must be ≥ 18)
- [ ] Plan: **Basic** — 1 vCPU, 1 GB RAM, 10 GB disk ($15/mo)
- [ ] Region: **Amsterdam** (AMS3) or **Frankfurt** (FRA1)
- [ ] Cluster name: `meads-db`
- [ ] Create cluster — wait for provisioning (~5 minutes)

### 3.2 Configure database

- [ ] Note the connection details from the cluster overview page:
  - Host, port, username (`doadmin`), password, database name (`defaultdb`)
  - SSL mode: `require` (DO enforces SSL by default)
- [ ] Create a dedicated database: go to "Users & Databases" tab
  - [ ] Create database: `meads`
  - [ ] (Optional) Create user: `meads_app` with a generated password
- [ ] Note the full connection URL:
      `jdbc:postgresql://<host>:<port>/meads?sslmode=require`

### 3.3 Verify backups

- [ ] Go to cluster → "Backups" tab
- [ ] Confirm: daily automatic backups enabled (default)
- [ ] Confirm: 7-day retention
- [ ] Confirm: point-in-time recovery (PITR) available
- [ ] No action needed — this is enabled by default on DO Managed Databases

### 3.4 Configure trusted sources (after app is created)

- [ ] After creating the App Platform app (Phase 4), return here
- [ ] Go to cluster → "Settings" → "Trusted Sources"
- [ ] Add the App Platform app as a trusted source (restricts DB access to your app only)

---

## Phase 4: DigitalOcean — App Platform

### 4.1 Create the app

- [ ] DigitalOcean Console → App Platform → Create App
- [ ] Source: **GitHub** → select `meads` repository, branch `main`
- [ ] Component type: **Web Service**
- [ ] Build: auto-detect Dockerfile (or specify `Dockerfile` path)
- [ ] Plan: **Basic** ($5/mo) — 512 MB RAM, 1 vCPU
- [ ] Region: **Same as database** (Amsterdam or Frankfurt)
- [ ] HTTP port: `8080`

### 4.2 Attach the managed database

- [ ] In the app creation flow (or after, via Settings → Components):
- [ ] Add an existing database → select `meads-db`
- [ ] DO may auto-inject `DATABASE_URL` — but Spring Boot needs individual properties,
      so set them manually in the next step

### 4.3 Set environment variables

Set all of these in App Platform → Settings → App-Level Environment Variables:

**Database (from Phase 3.2):**
- [ ] `SPRING_DATASOURCE_URL` = `jdbc:postgresql://<host>:<port>/meads?sslmode=require`
- [ ] `SPRING_DATASOURCE_USERNAME` = `meads_app` (or `doadmin`)
- [ ] `SPRING_DATASOURCE_PASSWORD` = (password from Phase 3.2) — mark as **secret**

**Spring profile:**
- [ ] `SPRING_PROFILES_ACTIVE` = `prod`

**Application secrets:**
- [ ] `APP_AUTH_JWT_SECRET` = (generate with `openssl rand -base64 32`) — mark as **secret**
- [ ] `APP_JUMPSELLER_HOOKS_TOKEN` = (your Jumpseller webhook token) — mark as **secret**

**Application config:**
- [ ] `APP_BASE_URL` = `https://meads.app`

**Email (Resend):**
- [ ] `SPRING_MAIL_PASSWORD` = (Resend API key from Phase 2.1) — mark as **secret**

**Initial admin (first deploy only):**
- [ ] `INITIAL_ADMIN_EMAIL` = (your admin email address)
- [ ] `INITIAL_ADMIN_PASSWORD` = (strong password) — mark as **secret**

### 4.4 Configure health check

- [ ] App Platform → Settings → Health Checks
- [ ] HTTP path: `/` (root will redirect to login, returning 200)
- [ ] Or, if available, use Spring Boot Actuator `/actuator/health`
      (requires adding `spring-boot-starter-actuator` dependency — optional)
- [ ] Initial delay: **120 seconds** (Vaadin first-start takes time to compile frontend)
- [ ] Timeout: 10 seconds
- [ ] Interval: 30 seconds

### 4.5 Deploy

- [ ] Trigger first deploy (automatic if connected to GitHub)
- [ ] Monitor build logs in App Platform console
- [ ] Wait for build to complete (first build may take 5-10 minutes — Maven + Vaadin frontend)
- [ ] Wait for deployment to become "Active"
- [ ] Check runtime logs for:
  - [ ] `Flyway` migration output (tables created)
  - [ ] `Created initial admin user:` log line (AdminInitializer ran)
  - [ ] No stack traces or errors
  - [ ] `Started MeadsApplication in X seconds`

---

## Phase 5: DNS configuration

### 5.1 Point domain to App Platform

- [ ] In App Platform → Settings → Domains → Add Domain
- [ ] Enter `meads.app`
- [ ] DO will provide a CNAME target (e.g., `<app-name>.ondigitalocean.app`)
- [ ] Go to Namecheap → Domain List → meads.app → Advanced DNS:
  - [ ] If using root domain (`meads.app`): set **ALIAS** or **CNAME flattening** record
        pointing to the DO target. Namecheap may require an **A record** + **URL redirect**
        — check Namecheap docs for ALIAS/ANAME support
  - [ ] If using `www.meads.app`: add **CNAME** record → DO target
  - [ ] Consider: redirect `www.meads.app` → `meads.app` (or vice versa)
- [ ] Wait for DNS propagation

### 5.2 SSL/TLS

- [ ] DO App Platform auto-provisions a **Let's Encrypt** certificate once DNS resolves
- [ ] Verify: `https://meads.app` loads with a valid certificate (padlock icon)
- [ ] Verify: HTTP → HTTPS redirect works

---

## Phase 6: Verification

### 6.1 Application smoke test

- [ ] Open `https://meads.app` — should redirect to login page
- [ ] Log in with admin credentials (`INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD`)
- [ ] Verify redirect to `/competitions` (admin landing page)
- [ ] Navigate to Users (`/users`) — confirm admin user exists
- [ ] Create a test competition, add a division
- [ ] Verify all pages load without errors

### 6.2 Email delivery test

- [ ] Create a new user in the admin panel with a real email address
- [ ] Confirm the magic link email is received
- [ ] Click the magic link — confirm login works
- [ ] Check email headers: SPF pass, DKIM pass
- [ ] (Optional) Use mail-tester.com to score deliverability

### 6.3 Webhook test (if Jumpseller is configured)

- [ ] Configure Jumpseller webhook URL: `https://meads.app/api/webhooks/jumpseller/order-paid`
- [ ] Send a test webhook (or trigger a test order)
- [ ] Check app logs for webhook processing output

### 6.4 Database verification

- [ ] DO Console → Databases → meads-db → Insights
- [ ] Confirm active connections from the app
- [ ] Confirm storage usage is reasonable
- [ ] Check "Backups" tab — first automatic backup should appear within 24h

### 6.5 Log verification

- [ ] DO Console → App Platform → Runtime Logs
- [ ] Confirm logs are flowing and readable
- [ ] Confirm no recurring errors or warnings
- [ ] Confirm log levels match expectations (no DEBUG spam)

---

## Phase 7: Post-deploy hardening

### 7.1 Remove bootstrap credentials

After confirming admin login works:

- [ ] Remove `INITIAL_ADMIN_PASSWORD` env var (prevents accidental re-creation)
- [ ] Keep `INITIAL_ADMIN_EMAIL` (harmless, AdminInitializer skips if admin already exists)
- [ ] Redeploy (env var change triggers automatic redeploy)
- [ ] Change admin password via the application UI (Profile or admin tools)

### 7.2 Restrict database access

- [ ] Complete Phase 3.4 (trusted sources) if not already done
- [ ] Confirm: only the App Platform app can reach the database
- [ ] (Optional) Test by trying to connect from your local machine — should be refused

### 7.3 Confirm auto-deploy

- [ ] Push a trivial change to `main` branch
- [ ] Confirm App Platform auto-builds and deploys
- [ ] Verify the app is running the new version after deploy

### 7.4 Set up monitoring alerts

**DigitalOcean resource alerts:**

- [ ] DO Console → Monitoring → Create Alert
- [ ] CPU usage > 80% for 5 minutes
- [ ] Memory usage > 85% for 5 minutes
- [ ] Database disk usage > 80%
- [ ] Database connection count anomaly
- [ ] Alert destination: your email

**Email sending — Resend limit awareness:**

Resend free tier: **100 emails/day**, **3,000 emails/month**. When the daily limit is
hit, sends fail with an API error (no auto-upgrade, no charges). The app logs the failure
and continues running — emails silently stop until the next day.

- [ ] Bookmark the Resend dashboard (resend.com → Usage) — check periodically during
      active registration windows
- [ ] Set up a **log-based alert** in DO App Platform for SMTP send failures:
  - DO Console → App Platform → Settings → Log Forwarding (or use Runtime Logs search)
  - Alert on `ERROR` logs containing `Failed to send` from `SmtpEmailService`
  - This catches both Resend limit hits and any other email delivery failures
- [ ] If approaching the limit regularly, upgrade to Resend Pro ($20/mo, 50k emails/month)
- [ ] **Estimated volume:** ~15-30 emails/day during active registration (magic links,
      credit notifications, submission confirmations, order alerts). Limit should only be
      a concern with 100+ entrants registering on the same day

---

## Quick reference — Environment variables summary

| Variable | Value | Secret? |
|----------|-------|---------|
| `SPRING_PROFILES_ACTIVE` | `prod` | No |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>:<port>/meads?sslmode=require` | No |
| `SPRING_DATASOURCE_USERNAME` | DB user | No |
| `SPRING_DATASOURCE_PASSWORD` | DB password | Yes |
| `APP_AUTH_JWT_SECRET` | `openssl rand -base64 32` | Yes |
| `APP_JUMPSELLER_HOOKS_TOKEN` | Jumpseller token | Yes |
| `APP_BASE_URL` | `https://meads.app` | No |
| `SPRING_MAIL_PASSWORD` | Resend API key | Yes |
| `INITIAL_ADMIN_EMAIL` | Admin email | No |
| `INITIAL_ADMIN_PASSWORD` | Admin password (remove after first deploy) | Yes |

---

## Redeployment (application updates)

DO App Platform uses **zero-downtime deployments** by default: it builds the new version,
starts it, health-checks it, then routes traffic to it before stopping the old instance.
The old instance keeps serving requests until the new one is confirmed healthy.

### Standard update (code-only, no DB migration)

1. [ ] Merge changes to `main` (or push directly)
2. [ ] App Platform auto-detects the push and starts a build
3. [ ] Monitor: DO Console → App Platform → Activity → current deployment
4. [ ] Watch build logs for errors (Maven compile, Vaadin frontend, Docker)
5. [ ] Once "Deployed successfully" appears, verify:
   - [ ] App loads at `https://meads.app`
   - [ ] Quick smoke test (login, navigate key pages)
   - [ ] Check runtime logs for startup errors

**If the deploy fails** (build error or health check failure):
- DO automatically keeps the previous version running — no downtime
- Fix the issue, push again

### Update with Flyway migration (DB schema change)

Flyway runs automatically on app startup. Because DO App Platform does zero-downtime
deploys, there is a brief window where the **old code** runs against the **new schema**.
This is safe only if migrations are **backward-compatible**.

**Before deploying:**

1. [ ] Confirm the migration is backward-compatible with the current running code:
   - Adding a new table → safe (old code doesn't reference it)
   - Adding a nullable column → safe (old code ignores it)
   - Adding a NOT NULL column → **unsafe** (old code inserts will fail)
   - Renaming/dropping a column → **unsafe** (old code still queries it)
   - Adding a constraint to existing data → **risky** (may fail if data violates it)
2. [ ] If the migration is **not** backward-compatible, follow the "Breaking schema change"
   procedure below

**Deploy:**

3. [ ] Push to `main`
4. [ ] Monitor deployment — Flyway output appears in runtime logs before Spring Boot starts
5. [ ] Verify:
   - [ ] Check runtime logs for `Successfully applied N migration(s)`
   - [ ] No Flyway errors (migration failure will prevent app startup, triggering rollback
         to previous version — but the **migration itself is NOT rolled back** in the DB)
   - [ ] Smoke test the affected functionality

### Breaking schema change (non-backward-compatible migration)

Use a **two-phase deploy** to avoid the old-code-new-schema window:

**Phase 1 — Prepare (backward-compatible migration):**

1. [ ] Deploy a migration that adds the new structure alongside the old one
   - Example: add new column (nullable), add new table, create a view
   - Old code continues working, new structure is unused
2. [ ] Verify Phase 1 deploy is stable

**Phase 2 — Switch (code uses new structure):**

3. [ ] Deploy the code that uses the new structure
4. [ ] (Optional) Deploy a cleanup migration to remove the old structure

If the change is too complex for two phases (e.g., major restructuring):

1. [ ] Schedule a **maintenance window** during low-traffic hours
2. [ ] Notify affected users (competition admins, entrants)
3. [ ] DO Console → App Platform → Settings → toggle off "Autodeploy"
4. [ ] Push changes to `main`
5. [ ] Trigger manual deploy from DO Console
6. [ ] Monitor deployment closely
7. [ ] Re-enable "Autodeploy" after confirming success

### Rollback

DO App Platform keeps previous deployment images:

- [ ] DO Console → App Platform → Activity
- [ ] Click on the previous successful deployment
- [ ] "Rollback to this deployment" (redeploys old image)
- [ ] **Important:** This does NOT roll back Flyway migrations. If the new version applied
      a migration, rolling back the app may cause `hibernate.ddl-auto=validate` to fail
      if the old code doesn't match the new schema
- [ ] If rollback is needed after a migration: assess whether the old code is compatible
      with the new schema. If not, you may need to manually revert the migration via
      `psql` on the managed database (DO Console → Databases → Connection Pool / `psql`)

### Environment variable changes

- [ ] DO Console → App Platform → Settings → App-Level Environment Variables
- [ ] Make changes → Save
- [ ] This triggers an automatic redeploy (rebuild + restart)
- [ ] For secret rotation (JWT secret, DB password): update the env var, redeploy,
      then rotate the credential on the provider side
- [ ] **JWT secret rotation** will invalidate all outstanding magic link tokens —
      users with pending links will need new ones

### Data safety during updates

- DO Managed PostgreSQL runs independently of the app — redeployments never touch the DB
  cluster itself
- Flyway migrations are the only thing that modifies the schema, and they run forward-only
- DO daily backups + PITR provide recovery if a migration causes data issues
- Before risky migrations: take a **manual backup** (DO Console → Databases → Backups →
  "Create Backup Now") so you have a known-good restore point

---

## Teardown (after competition ends)

- [ ] Export any data needed (pg_dump via DO console, or app-level export)
- [ ] DO Console → App Platform → Destroy App
- [ ] DO Console → Databases → Destroy Cluster (take a final manual backup first)
- [ ] Resend → optionally remove domain
- [ ] Namecheap → remove/update DNS records
- [ ] Billing stops immediately on resource destruction
