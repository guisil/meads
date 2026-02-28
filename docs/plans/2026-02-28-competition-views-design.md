# Competition Module Views — Frontend Design Document

**Date:** 2026-02-28
**Branch:** `competition-module`
**Status:** Design complete, awaiting implementation (Phase 7 of competition module)
**Prerequisite:** Phases 1–6 (backend entities, services, repositories) must be complete first.

---

## Aesthetic Direction

**Tone:** Warm, refined, utilitarian — a craft-forward tool that respects its subject matter.

Mead is an ancient, artisanal craft. The UI should feel like a well-made tool — not
flashy, not generic. Think: a beautifully organized tasting notebook, not a SaaS dashboard.

**Palette:** Warm amber/honey tones as accents against a clean, slightly warm background.
Status badges use purposeful color-coding. The default Lumo blue primary color is replaced
with a warm amber that evokes honey.

**Typography:** Lumo defaults are acceptable for a data-heavy tool. The focus is on
clear hierarchy, generous spacing, and purposeful color rather than font novelty.
Vaadin's server-side Java Flow model doesn't support custom font loading as easily as
pure frontend — keep it practical.

**Key principle:** Every visual choice should improve scannability and reduce cognitive
load for competition organizers juggling dozens of judges, hundreds of entries, and
multiple simultaneous competitions.

---

## Theme Configuration

### File: `src/main/frontend/themes/meads/theme.json`

```json
{
  "lumoImports": ["typography", "color", "spacing", "badge", "utility"]
}
```

### File: `src/main/frontend/themes/meads/styles.css`

```css
/* ============================================================
   MEADS Custom Theme — Warm Amber on Lumo
   ============================================================ */

html {
  /* --- Primary: warm amber (honey) --- */
  --lumo-primary-color-50pct: hsla(35, 85%, 45%, 0.5);
  --lumo-primary-color-10pct: hsla(35, 85%, 45%, 0.1);
  --lumo-primary-color: hsl(35, 85%, 45%);
  --lumo-primary-text-color: hsl(35, 85%, 38%);
  --lumo-primary-contrast-color: #fff;

  /* --- Background: warm off-white --- */
  --lumo-base-color: hsl(40, 20%, 98%);

  /* --- Slightly warmer contrast tones --- */
  --lumo-contrast-5pct: hsla(30, 10%, 40%, 0.05);
  --lumo-contrast-10pct: hsla(30, 10%, 40%, 0.1);

  /* --- Success: earthy green --- */
  --lumo-success-color: hsl(145, 45%, 38%);
  --lumo-success-text-color: hsl(145, 45%, 32%);

  /* --- Error: muted brick red --- */
  --lumo-error-color: hsl(5, 60%, 48%);
  --lumo-error-text-color: hsl(5, 60%, 42%);

  /* --- Shape: slightly softer --- */
  --lumo-border-radius-m: 6px;
  --lumo-border-radius-l: 10px;

  /* --- Spacing: generous --- */
  --lumo-space-wide-m: 1.5rem;
}

/* --- Navbar --- */
vaadin-app-layout::part(navbar) {
  background: hsl(30, 15%, 18%);
  color: hsl(40, 30%, 92%);
  box-shadow: 0 1px 4px hsla(30, 20%, 10%, 0.15);
}

/* --- Drawer --- */
vaadin-app-layout::part(drawer) {
  background: hsl(40, 15%, 96%);
  border-right: 1px solid var(--lumo-contrast-10pct);
}

/* --- Status badge color mapping --- */
.badge-draft {
  background: var(--lumo-contrast-10pct);
  color: var(--lumo-secondary-text-color);
}

.badge-registration-open {
  background: hsla(200, 70%, 50%, 0.12);
  color: hsl(200, 70%, 38%);
}

.badge-registration-closed {
  background: hsla(35, 85%, 45%, 0.12);
  color: hsl(35, 85%, 38%);
}

.badge-judging {
  background: hsla(280, 55%, 50%, 0.12);
  color: hsl(280, 55%, 40%);
}

.badge-deliberation {
  background: hsla(25, 80%, 50%, 0.12);
  color: hsl(25, 80%, 40%);
}

.badge-results-published {
  background: hsla(145, 45%, 38%, 0.12);
  color: hsl(145, 45%, 32%);
}

/* --- Page header pattern --- */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--lumo-space-m);
  border-bottom: 1px solid var(--lumo-contrast-10pct);
  margin-bottom: var(--lumo-space-l);
}

.page-header h2 {
  margin: 0;
  font-weight: 600;
}

/* --- Breadcrumb styling --- */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--lumo-space-xs);
  font-size: var(--lumo-font-size-s);
  color: var(--lumo-secondary-text-color);
  margin-bottom: var(--lumo-space-s);
}

.breadcrumb a {
  color: var(--lumo-primary-text-color);
  text-decoration: none;
}

.breadcrumb a:hover {
  text-decoration: underline;
}

.breadcrumb .separator {
  color: var(--lumo-tertiary-text-color);
}

/* --- Event logo --- */
.event-logo {
  width: 64px;
  height: 64px;
  border-radius: var(--lumo-border-radius-m);
  object-fit: contain;
  border: 1px solid var(--lumo-contrast-10pct);
  background: white;
  flex-shrink: 0;
}

.event-logo-large {
  width: 80px;
  height: 80px;
  border-radius: var(--lumo-border-radius-m);
  object-fit: contain;
  border: 1px solid var(--lumo-contrast-10pct);
  background: white;
  flex-shrink: 0;
}

.event-logo-placeholder {
  width: 64px;
  height: 64px;
  border-radius: var(--lumo-border-radius-m);
  border: 1px dashed var(--lumo-contrast-20pct);
  background: var(--lumo-contrast-5pct);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lumo-tertiary-text-color);
  font-size: var(--lumo-font-size-xl);
  flex-shrink: 0;
}

/* --- Summary cards (stats row) --- */
.summary-cards {
  display: flex;
  gap: var(--lumo-space-m);
  margin-bottom: var(--lumo-space-l);
}

.summary-card {
  flex: 1;
  background: var(--lumo-base-color);
  border: 1px solid var(--lumo-contrast-10pct);
  border-radius: var(--lumo-border-radius-l);
  padding: var(--lumo-space-m) var(--lumo-space-l);
}

.summary-card .label {
  font-size: var(--lumo-font-size-s);
  color: var(--lumo-secondary-text-color);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.summary-card .value {
  font-size: var(--lumo-font-size-xxl);
  font-weight: 700;
  color: var(--lumo-body-text-color);
  margin-top: var(--lumo-space-xs);
}
```

### Activating the Theme

Add `@Theme("meads")` annotation to `MeadsApplication.java`:

```java
@SpringBootApplication
@Theme("meads")
public class MeadsApplication { ... }
```

---

## MainLayout Upgrade — Drawer Navigation

The current `MainLayout` uses a flat navbar with buttons. As the app grows beyond the
identity module, it needs a proper navigation structure.

### Layout Structure

```
+--[ MEADS ]--[ hamburger ]------------------------------------------+
|  dark warm navbar                                         [ Logout ]|
+----+---------------------------------------------------------------+
|    |                                                               |
| D  |   Content Area                                                |
| R  |                                                               |
| A  |                                                               |
| W  |                                                               |
| E  |                                                               |
| R  |                                                               |
|    |                                                               |
| -- |                                                               |
| Nav|                                                               |
|    |                                                               |
+----+---------------------------------------------------------------+
```

### Drawer Content (SideNav)

Navigation items are role-dependent. The SideNav uses `VaadinIcon` prefixes and
badge counters where useful.

```
+---------------------------+
| NAVIGATION                |
|                           |
| > Home          (always)  |
| > Events        (admin)   |
|   > [nested: competitions]|
| > Users         (admin)   |
|                           |
+---------------------------+
```

### Java Implementation Sketch

```java
public class MainLayout extends AppLayout {

    private final transient AuthenticationContext authenticationContext;

    public MainLayout(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;

        // --- Navbar ---
        var toggle = new DrawerToggle();
        var title = new H1("MEADS");
        title.getStyle().set("font-size", "1.125rem").set("margin", "0");

        var logoutButton = new Button("Logout", e -> authenticationContext.logout());
        logoutButton.getElement().getThemeList().add("tertiary small");

        var navbar = new HorizontalLayout(toggle, title);
        navbar.setFlexGrow(1, title);
        navbar.setWidthFull();
        navbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navbar.add(logoutButton);

        addToNavbar(navbar);

        // --- Drawer ---
        var nav = new SideNav();

        nav.addItem(new SideNavItem("Home", RootView.class,
                VaadinIcon.HOME.create()));

        if (authenticationContext.hasRole("SYSTEM_ADMIN")) {
            var eventsItem = new SideNavItem("Events", EventListView.class,
                    VaadinIcon.CALENDAR.create());
            nav.addItem(eventsItem);

            nav.addItem(new SideNavItem("Users", UserListView.class,
                    VaadinIcon.USERS.create()));
        }

        var scroller = new Scroller(nav);
        scroller.getStyle().set("padding", "var(--lumo-space-s)");
        addToDrawer(scroller);

        setPrimarySection(Section.DRAWER);
    }
}
```

**Key changes from current MainLayout:**
- `DrawerToggle` replaces manual button navigation
- `SideNav` with icons replaces plain `Button` elements
- `setPrimarySection(Section.DRAWER)` makes drawer the primary nav
- Responsive: drawer collapses to hamburger on small screens
- Logout button stays in the navbar (top-right)

---

## View 1: EventListView

**Route:** `/events`
**Access:** `@RolesAllowed("SYSTEM_ADMIN")`
**Purpose:** CRUD for events. Entry point to competition management.

### Wireframe

```
Breadcrumb: Home > Events

+-- Page Header --------------------------------------------------+
|  Events                                        [ + Create Event ]|
+-----------------------------------------------------------------+

+-- Grid ---------------------------------------------------------+
|      | Name            | Start Date  | End Date    | Location  | Comp. | Actions      |
|------|-----------------|-------------|-------------|-----------|-------|--------------|
| [lg] | Regional 2026   | 2026-06-15  | 2026-06-17  | Porto     |     2 | [Edit] [Del] |
| [lg] | National 2026   | 2026-09-20  | 2026-09-22  | Lisbon    |     1 | [Edit] [Del] |
| [--] | Spring Open     | 2026-04-05  | 2026-04-05  | Coimbra   |     0 | [Edit] [Del] |
+-----------------------------------------------------------------+
  [lg] = logo thumbnail, [--] = placeholder
        Click on row → navigates to /events/{id}/competitions
```

### Component Details

**Grid columns:**
- `Logo` — component column: 32x32 thumbnail if logo exists, placeholder icon if not
- `Name` — sortable, clickable (navigates to competitions)
- `Start Date` / `End Date` — `LocalDateRenderer` with `yyyy-MM-dd` format, sortable
- `Location` — plain text, shows "—" if null
- `Competitions` — count of competitions (via service), right-aligned
- `Actions` — component column with Edit and Delete buttons

**Logo in grid (component column):**
```java
grid.addComponentColumn(event -> {
    if (event.hasLogo()) {
        var resource = new StreamResource("logo",
            () -> new ByteArrayInputStream(event.getLogo()));
        resource.setContentType(event.getLogoContentType());
        var image = new Image(resource, event.getName() + " logo");
        image.setWidth("32px");
        image.setHeight("32px");
        image.getStyle().set("object-fit", "contain")
                        .set("border-radius", "4px");
        return image;
    }
    var placeholder = new Icon(VaadinIcon.PICTURE);
    placeholder.setSize("24px");
    placeholder.setColor("var(--lumo-tertiary-text-color)");
    return placeholder;
}).setHeader("").setWidth("60px").setFlexGrow(0);
```

**Create Event dialog:**
```
+-- Dialog: Create Event -----------------------------------------+
|                                                                  |
|  Name         [________________________________]                 |
|                                                                  |
|  Start Date   [____/____/____]   End Date   [____/____/____]     |
|                                                                  |
|  Location     [________________________________]  (optional)     |
|                                                                  |
|  Logo         [ Upload image ]  (optional, PNG/JPEG, ≤512KB)    |
|               [  preview  ]                                      |
|                                                                  |
|                                    [ Cancel ]  [ Create ]        |
+-----------------------------------------------------------------+
```

- Uses `FormLayout` with auto-responsive mode
- `TextField` for name (required)
- `DatePicker` for start/end dates (required)
- `TextField` for location (optional)
- `Upload` for logo (optional) — accepts image/png, image/jpeg; max 512KB
  - Uses `InMemoryUploadHandler` to capture `byte[]`
  - Shows preview `Image` after upload
  - "Remove" button to clear the logo
- Validation: startDate <= endDate (service enforces, but show field error)
- On success: `Notification` with `LUMO_SUCCESS`, refresh grid

**Logo upload in dialog:**
```java
var logoPreview = new Image();
logoPreview.setVisible(false);
logoPreview.setWidth("80px");
logoPreview.setHeight("80px");
logoPreview.getStyle().set("object-fit", "contain");

var upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
    logoBytes = data;
    logoContentType = metadata.contentType();
    var resource = new StreamResource("preview",
        () -> new ByteArrayInputStream(data));
    resource.setContentType(metadata.contentType());
    logoPreview.setSrc(resource);
    logoPreview.setVisible(true);
}));
upload.setAcceptedFileTypes("image/png", "image/jpeg");
upload.setMaxFileSize(512 * 1024); // 512KB
upload.setMaxFiles(1);
```

**Edit dialog:** Same as create, pre-populated with existing values. Shows current logo
preview if one exists, with option to upload a replacement or remove it.

**Delete:** Confirmation dialog. Blocked if event has competitions (service throws
`IllegalArgumentException`, shown as notification).

**Row click navigation:**
```java
grid.addItemClickListener(e ->
    e.getSource().getUI().ifPresent(ui ->
        ui.navigate("events/" + e.getItem().getId() + "/competitions")));
```

---

## View 2: CompetitionListView

**Route:** `/events/{eventId}/competitions`
**Access:** `@RolesAllowed("SYSTEM_ADMIN")` (initially; see role-based section below)
**Purpose:** List and manage competitions within an event.

### Wireframe

```
Breadcrumb: Home > Events > Regional Mead Festival 2026

+-- Event Header -------------------------------------------------+
|  +------+                                                        |
|  | LOGO |  Regional Mead Festival 2026                           |
|  |      |  Jun 15–17, 2026  ·  Porto                             |
|  +------+                                                        |
+-----------------------------------------------------------------+

+-- Summary Cards ------------------------------------------------+
| [  Competitions  ] [   Participants   ] [      Status       ]    |
| [       2        ] [        14        ] [ 1 Draft, 1 Open   ]    |
+-----------------------------------------------------------------+

+-- Grid ---------------------------------------------------------+
| Name          | Status              | Scoring | Participants | Actions         |
|---------------|---------------------|---------|--------------|-----------------|
| Home          | [REGISTRATION_OPEN] | MJP     |           10 | [View] [Advance]|
| Professional  | [DRAFT]             | MJP     |            4 | [View] [Advance]|
+-----------------------------------------------------------------+
                                               [ + Create Competition ]
```

### Component Details

**Event header section:**
- `HorizontalLayout` with logo + text block
- Logo: `Image` component (80x80, CSS class `event-logo-large`) or placeholder `Div`
  with CSS class `event-logo-placeholder` showing a `VaadinIcon.PICTURE` icon
- Text block: `VerticalLayout` with `H2` (event name) + `Span` (date range + location)
- Date range formatted as "Jun 15–17, 2026" (same month) or "Jun 15 – Jul 2, 2026" (different months)

```java
private Component createEventHeader(Event event) {
    var header = new HorizontalLayout();
    header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
    header.setSpacing(true);

    if (event.hasLogo()) {
        var resource = new StreamResource("logo",
            () -> new ByteArrayInputStream(event.getLogo()));
        resource.setContentType(event.getLogoContentType());
        var logo = new Image(resource, event.getName() + " logo");
        logo.addClassName("event-logo-large");
        header.add(logo);
    } else {
        var placeholder = new Div();
        placeholder.addClassName("event-logo-placeholder");
        placeholder.add(new Icon(VaadinIcon.PICTURE));
        header.add(placeholder);
    }

    var textBlock = new VerticalLayout();
    textBlock.setPadding(false);
    textBlock.setSpacing(false);
    textBlock.add(new H2(event.getName()));
    textBlock.add(new Span(formatDateRange(event) + "  ·  " +
        (event.getLocation() != null ? event.getLocation() : "")));

    header.add(textBlock);
    return header;
}
```

**Summary cards:**
- Three `Div` elements with CSS class `summary-card` inside a `summary-cards` container
- Cards: total competitions count, total participants across all competitions, status breakdown
- Built using `Div` + `Span` elements, styled via CSS classes (not Vaadin components)

**Status badges:**
Each `CompetitionStatus` maps to a CSS class and display text:

```java
private Span createStatusBadge(CompetitionStatus status) {
    String displayText = switch (status) {
        case DRAFT -> "Draft";
        case REGISTRATION_OPEN -> "Registration Open";
        case REGISTRATION_CLOSED -> "Registration Closed";
        case JUDGING -> "Judging";
        case DELIBERATION -> "Deliberation";
        case RESULTS_PUBLISHED -> "Results Published";
    };

    String cssClass = switch (status) {
        case DRAFT -> "badge-draft";
        case REGISTRATION_OPEN -> "badge-registration-open";
        case REGISTRATION_CLOSED -> "badge-registration-closed";
        case JUDGING -> "badge-judging";
        case DELIBERATION -> "badge-deliberation";
        case RESULTS_PUBLISHED -> "badge-results-published";
    };

    var badge = new Span(displayText);
    badge.getElement().getThemeList().add("badge pill small");
    badge.addClassName(cssClass);
    return badge;
}
```

**Status color rationale:**
- **Draft** (gray) — neutral, nothing happening yet
- **Registration Open** (blue) — active, inviting
- **Registration Closed** (amber) — caution, locked
- **Judging** (purple) — distinct phase, in-progress
- **Deliberation** (orange) — warm, discussion
- **Results Published** (green) — complete, success

**Grid columns:**
- `Name` — clickable, navigates to competition detail
- `Status` — component column with colored badge
- `Scoring System` — plain text (MJP)
- `Participants` — count, right-aligned
- `Actions` — View button + Advance Status button (enabled only when valid)

**Create Competition dialog:**
```
+-- Dialog: Create Competition -----------------------------------+
|                                                                  |
|  Name           [________________________________]               |
|                                                                  |
|  Scoring System [  MJP  v ]                                      |
|                                                                  |
|                                    [ Cancel ]  [ Create ]        |
+-----------------------------------------------------------------+
```

- `TextField` for name (required)
- `Select<ScoringSystem>` for scoring system (only MJP for now)
- On success: refresh grid, show success notification

**Advance Status button:**
- Shows next status as tooltip/label: "Advance to Registration Open"
- Confirmation dialog before advancing: "Advance competition '{name}' from Draft to Registration Open?"
- Disabled at terminal state (RESULTS_PUBLISHED)

**Back navigation:** Breadcrumb link to `/events`

---

## View 3: CompetitionDetailView

**Route:** `/competitions/{id}`
**Access:** `@RolesAllowed("SYSTEM_ADMIN")` initially; service-layer check for
COMPETITION_ADMIN (see role-based section below)
**Purpose:** Full management of a single competition — participants, status, settings.

### Wireframe

```
Breadcrumb: Home > Events > Regional 2026 > Home Competition

+-- Competition Header -------------------------------------------+
| +------+                                                         |
| | LOGO |  Home Competition                                       |
| |      |  MJP  ·  [REGISTRATION_OPEN]  [ Advance to Reg. Closed]|
| +------+                                                         |
+-----------------------------------------------------------------+

+-- TabSheet -----------------------------------------------------+
| [ Participants ]  [ Categories ]  [ Settings ]                   |
|                                                                  |
| ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~     |
|                                                                  |
|  (tab content below)                                             |
|                                                                  |
+-----------------------------------------------------------------+
```

### Participants Tab (default)

```
+-- Participants -------------------------------------------------+
|                                                                  |
| [ + Add Participant ]  [ Copy from Competition... ]              |
|                                                                  |
| +-- Grid -----------------------------------------------------+ |
| | Name            | Email              | Role      | Code     | |
| |-----------------|--------------------|-----------+----------| |
| | Ana Silva       | ana@example.com    | [JUDGE]   | AB3K9XYZ | |
| | Pedro Costa     | pedro@example.com  | [STEWARD] | HJ7NW2QR | |
| | Maria Lopes     | maria@example.com  | [ENTRANT] | —        | |
| | João Ferreira   | joao@example.com   | [COMP_ADMIN] | —     | |
| +----------------------------------------------------------+   |
|                                                                  |
+-----------------------------------------------------------------+
```

**Grid columns:**
- `Name` — from `UserService.findById()` (resolved at view level)
- `Email` — from User
- `Role` — badge with `CompetitionRole` (color-coded: Judge=purple, Steward=blue, Entrant=gray, Admin=amber)
- `Access Code` — monospace font, only shown for JUDGE/STEWARD; "—" otherwise
- `Actions` — Remove button (disabled if status >= JUDGING), Regenerate Code (JUDGE/STEWARD only)

**Add Participant dialog:**
```
+-- Dialog: Add Participant --------------------------------------+
|                                                                  |
|  User    [ search-by-email dropdown      v ]                     |
|                                                                  |
|  Role    [ JUDGE / STEWARD / ENTRANT / COMPETITION_ADMIN  v ]    |
|                                                                  |
|  Note: Access code will be auto-generated for Judge/Steward      |
|                                                                  |
|                                    [ Cancel ]  [ Add ]           |
+-----------------------------------------------------------------+
```

- User selection: `ComboBox<User>` with email-based filtering (via `UserService`)
- Role selection: `Select<CompetitionRole>`
- Blocked if status >= JUDGING (button disabled, tooltip explains why)

**Copy Participants dialog:**
```
+-- Dialog: Copy Participants ------------------------------------+
|                                                                  |
|  Copy all participants from another competition in this event.   |
|  Existing participants will be skipped. Access codes are          |
|  preserved.                                                      |
|                                                                  |
|  Source Competition  [ Professional  v ]                          |
|                                                                  |
|  [ Cancel ]  [ Copy ]                                            |
+-----------------------------------------------------------------+
```

- `Select<Competition>` showing other competitions in the same event
- Disabled if only one competition in the event
- On success: "Copied N participants" notification

### Categories Tab

```
+-- Categories ---------------------------------------------------+
|                                                                  |
| Read-only list of MJP categories for this competition's          |
| scoring system.                                                  |
|                                                                  |
| +-- Grid (read-only) ----------------------------------------+ |
| | Code  | Name                        | Description           | |
| |-------|-----------------------------|-----------------------| |
| | M1A   | Traditional Mead (Dry)      | Traditional mead...   | |
| | M1B   | Traditional Mead (Medium)   | Traditional mead...   | |
| | M1C   | Traditional Mead (Sweet)    | Traditional mead...   | |
| | M2A   | Pome Fruit Melomel          | Mead with pome...     | |
| | ...   | ...                         | ...                   | |
| +----------------------------------------------------------+   |
|                                                                  |
+-----------------------------------------------------------------+
```

- Simple read-only `Grid<Category>` with sortable code column
- No actions — categories are reference data
- Filtered by `competition.getScoringSystem()`

### Settings Tab

```
+-- Settings -----------------------------------------------------+
|                                                                  |
|  Only editable while competition is in DRAFT status.             |
|                                                                  |
|  Name           [  Home Competition  ]                           |
|                                                                  |
|  Scoring System [  MJP  v ]                                      |
|                                                                  |
|  Status         REGISTRATION_OPEN (read-only)                    |
|                                                                  |
|                                                  [ Save ]        |
|                                                                  |
|  ---- Danger Zone ----                                           |
|                                                                  |
|  Delete this competition.                [ Delete Competition ]  |
|  This action cannot be undone.                                   |
|                                                                  |
+-----------------------------------------------------------------+
```

- Fields disabled when status != DRAFT
- Delete button always visible (service enforces rules)
- Danger zone styled with error border/background

---

## Role-Based View Organization (Future Stage)

This section outlines how views should adapt when non-admin users access the system.
**Implementation deferred** until after the basic views are working for SYSTEM_ADMIN.

### Role Capabilities Matrix

| Capability                  | SYSTEM_ADMIN | COMPETITION_ADMIN | JUDGE | STEWARD | ENTRANT |
|-----------------------------|:---:|:---:|:---:|:---:|:---:|
| See Events nav item         | Yes | No  | No  | No  | No  |
| See all events              | Yes | No  | No  | No  | No  |
| Create/edit/delete events   | Yes | No  | No  | No  | No  |
| See "My Competitions" nav   | No  | Yes | Yes | Yes | Yes |
| Create competitions         | Yes | No  | No  | No  | No  |
| Edit competition settings   | Yes | Yes | No  | No  | No  |
| Advance competition status  | Yes | Yes | No  | No  | No  |
| Manage participants         | Yes | Yes | No  | No  | No  |
| View participant list       | Yes | Yes | Yes | Yes | No  |
| View own access code        | No  | No  | Yes | Yes | No  |
| View categories             | Yes | Yes | Yes | Yes | Yes |

### Navigation Structure by Role

**SYSTEM_ADMIN:**
```
> Home
> Events          ← full CRUD, sees all events
> Users           ← user management (existing)
```

**COMPETITION_ADMIN:**
```
> Home
> My Competitions ← filtered list: competitions where user is COMPETITION_ADMIN
```

**JUDGE / STEWARD:**
```
> Home
> My Competitions ← filtered list: competitions where user is JUDGE/STEWARD
  (read-only competition detail, can see own access code)
```

**ENTRANT:**
```
> Home
> My Competitions ← filtered list: competitions where user is ENTRANT
  (read-only, can see categories and own entries [future])
```

### Implementation Strategy

1. **"My Competitions" view** (`/my-competitions`)
   - New view accessible to all authenticated users
   - Shows only competitions where the current user is a participant
   - Columns: Event Name, Competition Name, Role, Status
   - Click navigates to `CompetitionDetailView`

2. **CompetitionDetailView role adaptation**
   - The view already receives `requestingUserId` for all service calls
   - Add role-aware UI: hide/show tabs, disable actions based on permissions
   - Service layer already enforces authorization; the view just controls visibility

3. **MainLayout SideNav adaptation**
   - Check if user has any `CompetitionParticipant` records
   - If SYSTEM_ADMIN: show Events + Users
   - If has any participant records: show "My Competitions"
   - Both can coexist (admin who is also a judge)

4. **Access code display for judges/stewards**
   - In "My Competitions" detail view, show the current user's access code prominently
   - Card-style display: "Your access code: `AB3K9XYZ`" with copy button

### Key Principle

The **same CompetitionDetailView** is used for all roles. The view queries the user's
role in the competition and adjusts what's visible/editable. This avoids duplicating
views and keeps the codebase lean.

```java
// In CompetitionDetailView.beforeEnter():
UUID currentUserId = getCurrentUserId();
boolean isSystemAdmin = userService.findById(currentUserId).getRole() == Role.SYSTEM_ADMIN;
boolean isCompAdmin = competitionService.isCompetitionAdmin(competitionId, currentUserId);
boolean canManage = isSystemAdmin || isCompAdmin;

// Show/hide based on canManage:
addParticipantButton.setVisible(canManage);
advanceStatusButton.setVisible(canManage);
settingsTab.setVisible(canManage);
```

---

## Shared UI Patterns

### Breadcrumb Helper

A reusable breadcrumb component used across all competition views:

```java
public class Breadcrumb extends HorizontalLayout {

    public Breadcrumb() {
        addClassName("breadcrumb");
        setSpacing(false);
        setPadding(false);
    }

    public Breadcrumb addLink(String label, String route) {
        if (getComponentCount() > 0) {
            add(new Span(" / "));
        }
        var link = new Anchor(route, label);
        add(link);
        return this;
    }

    public Breadcrumb addCurrent(String label) {
        if (getComponentCount() > 0) {
            var separator = new Span(" / ");
            separator.addClassName("separator");
            add(separator);
        }
        var current = new Span(label);
        add(current);
        return this;
    }
}
```

Usage:
```java
var breadcrumb = new Breadcrumb()
    .addLink("Home", "")
    .addLink("Events", "events")
    .addCurrent("Regional Mead Festival 2026");
```

### Page Header Pattern

```java
private HorizontalLayout createPageHeader(String title, Button... actions) {
    var header = new HorizontalLayout();
    header.addClassName("page-header");
    header.setWidthFull();
    header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

    var h2 = new H2(title);
    header.add(h2);
    header.setFlexGrow(1, h2);

    for (Button action : actions) {
        header.add(action);
    }
    return header;
}
```

### Status Badge Factory

Centralized in a utility class or directly in the competition module's `internal/` package:

```java
class StatusBadgeFactory {

    static Span forCompetitionStatus(CompetitionStatus status) {
        // ... (see CompetitionListView section above)
    }

    static Span forCompetitionRole(CompetitionRole role) {
        String theme = switch (role) {
            case JUDGE -> "badge pill small"; // + purple custom class
            case STEWARD -> "badge pill small contrast";
            case ENTRANT -> "badge pill small";
            case COMPETITION_ADMIN -> "badge pill small warning";
        };
        var badge = new Span(role.name().replace("_", " "));
        badge.getElement().getThemeList().add(theme);
        return badge;
    }
}
```

### Confirmation Dialog Pattern

```java
private void showConfirmation(String title, String message,
                               String confirmText, Runnable onConfirm) {
    var dialog = new Dialog();
    dialog.setHeaderTitle(title);

    var content = new VerticalLayout(new Span(message));
    content.setPadding(false);
    dialog.add(content);

    var confirmButton = new Button(confirmText, e -> {
        onConfirm.run();
        dialog.close();
    });
    confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    var cancelButton = new Button("Cancel", e -> dialog.close());
    cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    dialog.getFooter().add(cancelButton, confirmButton);
    dialog.open();
}
```

---

## Implementation Notes

### Route Parameters

`CompetitionListView` and `CompetitionDetailView` need URL parameters:

```java
@Route(value = "events/:eventId/competitions", layout = MainLayout.class)
public class CompetitionListView extends VerticalLayout
        implements HasUrlParameter<String> {

    @Override
    public void setParameter(BeforeEvent event, String eventId) {
        // Load event and competitions
    }
}
```

Or using `BeforeEnterObserver` for more control:

```java
@Route(value = "competitions", layout = MainLayout.class)
public class CompetitionDetailView extends VerticalLayout
        implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String id = event.getRouteParameters().get("id").orElse(null);
        // Load competition
    }
}
```

### Grid Refresh Pattern

After mutations (create, edit, delete, add participant), refresh the grid:

```java
grid.setItems(service.findAll());  // or findByEvent(eventId), etc.
```

### Error Handling

Service throws → catch in view → display as notification or field error:

```java
try {
    competitionService.addParticipant(...);
    refreshParticipantsGrid();
    Notification.show("Participant added")
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
} catch (IllegalArgumentException ex) {
    Notification.show(ex.getMessage())
        .addThemeVariants(NotificationVariant.LUMO_ERROR);
} catch (IllegalStateException ex) {
    Notification.show(ex.getMessage())
        .addThemeVariants(NotificationVariant.LUMO_ERROR);
}
```

---

## Implementation Sequence (within Phase 7)

These are the TDD cycles from the competition module design doc, with additional
detail on what each view test should cover:

**22. UI test: EventListView**
- Grid renders events with correct columns (including logo thumbnail)
- Create Event dialog opens, validates, saves, refreshes grid
- Logo upload in create/edit dialog (Upload component, preview, remove)
- Edit dialog pre-populates, saves, refreshes grid
- Delete shows confirmation, calls service, refreshes grid
- Row click navigates to competitions view

**23. UI test: CompetitionListView**
- Event header displays event details
- Grid renders competitions with status badges
- Create Competition dialog works
- Advance Status shows confirmation, calls service, updates badge

**24. UI test: CompetitionDetailView**
- Tabs render correctly (Participants, Categories, Settings)
- Add Participant dialog with ComboBox user search
- Remove Participant with confirmation
- Copy Participants dialog
- Categories grid shows MJP categories (read-only)
- Settings tab fields disabled when not in DRAFT

**25. MainLayout nav update (fast cycle)**
- Add SideNav with drawer
- Events nav item visible for SYSTEM_ADMIN
- Covered by existing `MainLayoutTest`

---

## Out of Scope

- "My Competitions" view (deferred to role-based stage)
- Judge/steward/entrant-specific UI adaptations
- Dashboard/home page redesign
- Mobile-optimized layouts beyond Vaadin's built-in responsiveness
- Dark mode theme variant
- Custom fonts or icon sets beyond Vaadin's built-in icons
- Print stylesheets for access code badges
