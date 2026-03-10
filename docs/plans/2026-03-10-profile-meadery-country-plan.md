# User Profile Self-Edit, Meadery Name & Country — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add user profile self-edit (name, meadery name, country), per-division meadery name
enforcement, and Jumpseller webhook country enrichment.

**Architecture:** Three modules touched — identity (User + profile view), competition
(Division meaderyNameRequired flag), entry (webhook enrichment + UI enforcement). Changes
follow the existing patterns: entity domain methods, service validation, Vaadin views,
Flyway migrations modified in-place (pre-deployment). TDD full cycle for all new behavior.

**Tech Stack:** Spring Boot 4, Vaadin 25 (Java Flow), PostgreSQL 18, Flyway, Karibu Testing,
Mockito, Testcontainers.

**Design doc:** `docs/plans/2026-03-10-profile-meadery-country-design.md`

---

## Task 1: Add `country` field to User entity + migration

**Files:**
- Modify: `src/main/resources/db/migration/V2__create_users_table.sql`
- Modify: `src/main/java/app/meads/identity/User.java`

**Step 1: Write a failing unit test**

Create `src/test/java/app/meads/identity/UserTest.java` (if it doesn't exist) or add to it:

```java
@Test
void shouldUpdateCountry() {
    var user = new User("test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
    user.updateCountry("PT");
    assertThat(user.getCountry()).isEqualTo("PT");
}

@Test
void shouldAllowNullCountry() {
    var user = new User("test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
    user.updateCountry("PT");
    user.updateCountry(null);
    assertThat(user.getCountry()).isNull();
}
```

Run: `mvn test -Dtest=UserTest -Dsurefire.useFile=false`
Expected: FAIL — `updateCountry` method and `country` field don't exist yet.

**Step 2: Implement minimal code**

Add to `User.java` (after `meaderyName` field, ~line 40):

```java
@Column(length = 2)
private String country;
```

Add domain method (after `updateMeaderyName`, ~line 75):

```java
public void updateCountry(String country) {
    this.country = country;
}
```

Add to V2 migration (after `meadery_name` column):

```sql
country                 VARCHAR(2),
```

Run: `mvn test -Dtest=UserTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass (existing tests unaffected — new column is nullable).

Commit: `Add country field to User entity`

---

## Task 2: Add `UserService.updateProfile()` method

**Files:**
- Modify: `src/main/java/app/meads/identity/UserService.java`
- Modify: `src/test/java/app/meads/identity/UserServiceTest.java`

**Step 1: Write a failing unit test**

Add to `UserServiceTest.java`:

```java
@Test
void shouldUpdateProfile() {
    var user = new User("test@example.com", "Old Name", UserStatus.ACTIVE, Role.USER);
    given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
    given(userRepository.save(any(User.class))).willAnswer(i -> i.getArgument(0));

    var result = userService.updateProfile(user.getId(), "New Name", "My Meadery", "PT");

    assertThat(result.getName()).isEqualTo("New Name");
    assertThat(result.getMeaderyName()).isEqualTo("My Meadery");
    assertThat(result.getCountry()).isEqualTo("PT");
}

@Test
void shouldRejectInvalidCountryCode() {
    var user = new User("test@example.com", "Name", UserStatus.ACTIVE, Role.USER);
    given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

    assertThatThrownBy(() -> userService.updateProfile(user.getId(), "Name", null, "XX"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("country code");
}

@Test
void shouldAllowNullCountryInProfile() {
    var user = new User("test@example.com", "Name", UserStatus.ACTIVE, Role.USER);
    given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
    given(userRepository.save(any(User.class))).willAnswer(i -> i.getArgument(0));

    var result = userService.updateProfile(user.getId(), "Name", null, null);

    assertThat(result.getCountry()).isNull();
    assertThat(result.getMeaderyName()).isNull();
}
```

Run: `mvn test -Dtest=UserServiceTest#shouldUpdateProfile -Dsurefire.useFile=false`
Expected: FAIL — `updateProfile` method doesn't exist.

**Step 2: Implement minimal code**

Add to `UserService.java`:

```java
private static final Set<String> VALID_COUNTRY_CODES = Set.of(Locale.getISOCountries());

public User updateProfile(@NotNull UUID userId, @NotBlank String name,
                           String meaderyName, String country) {
    if (country != null && !VALID_COUNTRY_CODES.contains(country)) {
        throw new IllegalArgumentException("Invalid ISO 3166-1 alpha-2 country code: " + country);
    }
    var user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    user.updateDetails(name, user.getRole(), user.getStatus());
    user.updateMeaderyName(meaderyName);
    user.updateCountry(country);
    log.info("Profile updated for user {} ({})", user.getEmail(), userId);
    return userRepository.save(user);
}
```

Import: `java.util.Locale`, `java.util.Set`.

Run: `mvn test -Dtest=UserServiceTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`

Commit: `Add UserService.updateProfile() with ISO country validation`

---

## Task 3: Add `meaderyNameRequired` to Division entity + migration

**Files:**
- Modify: `src/main/resources/db/migration/V4__create_divisions_table.sql`
- Modify: `src/main/java/app/meads/competition/Division.java`

**Step 1: Write a failing unit test**

Create or add to `src/test/java/app/meads/competition/DivisionTest.java`:

```java
@Test
void shouldDefaultMeaderyNameRequiredToFalse() {
    var division = new Division(UUID.randomUUID(), "Test", "TST", ScoringSystem.MJP);
    assertThat(division.isMeaderyNameRequired()).isFalse();
}

@Test
void shouldUpdateMeaderyNameRequired() {
    var division = new Division(UUID.randomUUID(), "Test", "TST", ScoringSystem.MJP);
    // Division starts in DRAFT, so this should work
    division.updateMeaderyNameRequired(true);
    assertThat(division.isMeaderyNameRequired()).isTrue();
}

@Test
void shouldRejectMeaderyNameRequiredChangeOutsideDraft() {
    var division = new Division(UUID.randomUUID(), "Test", "TST", ScoringSystem.MJP);
    division.advanceStatus(); // DRAFT → REGISTRATION_OPEN
    assertThatThrownBy(() -> division.updateMeaderyNameRequired(true))
            .isInstanceOf(IllegalStateException.class);
}
```

Run: `mvn test -Dtest=DivisionTest -Dsurefire.useFile=false`
Expected: FAIL — `isMeaderyNameRequired` and `updateMeaderyNameRequired` don't exist.

**Step 2: Implement minimal code**

Add field to `Division.java` (after `entryPrefix`, ~line 47):

```java
@Column(name = "meadery_name_required", nullable = false)
private boolean meaderyNameRequired;
```

Initialize in constructor (add to existing constructor body):

```java
this.meaderyNameRequired = false;
```

Add getter (Lombok `@Getter` handles it) and domain method:

```java
public void updateMeaderyNameRequired(boolean meaderyNameRequired) {
    if (status != DivisionStatus.DRAFT) {
        throw new IllegalStateException("Meadery name requirement can only be changed in DRAFT status");
    }
    this.meaderyNameRequired = meaderyNameRequired;
}
```

Add to V4 migration (after `entry_prefix` column):

```sql
meadery_name_required           BOOLEAN NOT NULL DEFAULT FALSE,
```

Run: `mvn test -Dtest=DivisionTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All pass — existing tests unaffected (boolean defaults to false).

Commit: `Add meaderyNameRequired flag to Division entity`

---

## Task 4: Expose `meaderyNameRequired` in CompetitionService

**Files:**
- Modify: `src/main/java/app/meads/competition/CompetitionService.java`
- Modify: `src/test/java/app/meads/competition/CompetitionServiceTest.java`

**Step 1: Write a failing unit test**

Add to `CompetitionServiceTest.java`:

```java
@Test
void shouldUpdateDivisionMeaderyNameRequired() {
    // Setup: mock division in DRAFT status
    var division = new Division(competitionId, "Test", "TST", ScoringSystem.MJP);
    given(divisionRepository.findById(division.getId())).willReturn(Optional.of(division));
    given(divisionRepository.save(any(Division.class))).willAnswer(i -> i.getArgument(0));
    mockAuthorization(division);

    var result = competitionService.updateDivisionMeaderyNameRequired(
            division.getId(), true, adminUserId);

    assertThat(result.isMeaderyNameRequired()).isTrue();
}
```

Note: Follow the existing mock/authorization pattern already in `CompetitionServiceTest.java`
for methods like `shouldUpdateDivisionEntryLimits`. The test may need `mockAuthorization`
helper or inline mock setup matching the file's existing pattern.

Run: `mvn test -Dtest=CompetitionServiceTest#shouldUpdateDivisionMeaderyNameRequired -Dsurefire.useFile=false`
Expected: FAIL — `updateDivisionMeaderyNameRequired` doesn't exist.

**Step 2: Implement minimal code**

Add to `CompetitionService.java` (near `updateDivisionEntryLimits`):

```java
public Division updateDivisionMeaderyNameRequired(@NotNull UUID divisionId,
                                                    boolean meaderyNameRequired,
                                                    @NotNull UUID requestingUserId) {
    var division = divisionRepository.findById(divisionId)
            .orElseThrow(() -> new IllegalArgumentException("Division not found: " + divisionId));
    requireAuthorized(division.getCompetitionId(), requestingUserId);
    division.updateMeaderyNameRequired(meaderyNameRequired);
    log.debug("Division {} meaderyNameRequired set to {}", divisionId, meaderyNameRequired);
    return divisionRepository.save(division);
}
```

Run: `mvn test -Dtest=CompetitionServiceTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`

Commit: `Add CompetitionService.updateDivisionMeaderyNameRequired()`

---

## Task 5: Add `customerCountry` to JumpsellerOrder entity + migration

**Files:**
- Modify: `src/main/resources/db/migration/V10__create_jumpseller_orders_table.sql`
- Modify: `src/main/java/app/meads/entry/JumpsellerOrder.java`

**Step 1: Write a failing unit test**

Add to `src/test/java/app/meads/entry/JumpsellerOrderTest.java`:

```java
@Test
void shouldStoreCustomerCountry() {
    var order = new JumpsellerOrder("ORD-1", "test@example.com", "Test", "{}");
    order.setCustomerCountry("PT");
    assertThat(order.getCustomerCountry()).isEqualTo("PT");
}
```

Note: Check the JumpsellerOrder constructor signature — it takes
`(jumpsellerOrderId, customerEmail, customerName, rawPayload)`. The `customerCountry` field
will need a setter or domain method since it's set after construction during webhook processing.

Run: `mvn test -Dtest=JumpsellerOrderTest#shouldStoreCustomerCountry -Dsurefire.useFile=false`
Expected: FAIL — `customerCountry` and its accessor don't exist.

**Step 2: Implement minimal code**

Add field to `JumpsellerOrder.java` (after `customerName`):

```java
@Column(name = "customer_country", length = 2)
private String customerCountry;
```

Add setter (this is a write-once-at-processing-time field, similar to `processedAt`):

```java
public void setCustomerCountry(String customerCountry) {
    this.customerCountry = customerCountry;
}
```

Note: Lombok `@Getter` handles the getter. Add `@Setter` to just this field, or write
a manual setter — match the pattern used for `passwordHash` on User (which uses `@Setter`).

Add to V10 migration (after `customer_name`):

```sql
customer_country        VARCHAR(2),
```

Run: `mvn test -Dtest=JumpsellerOrderTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`

Commit: `Add customerCountry field to JumpsellerOrder entity`

---

## Task 6: Webhook enrichment — extract country + enrich User

**Files:**
- Modify: `src/main/java/app/meads/entry/WebhookService.java`
- Modify: `src/test/java/app/meads/entry/WebhookServiceTest.java`

**Step 1: Write failing unit tests**

Add to `WebhookServiceTest.java`:

First, update `buildPayload` helper to support an optional shipping address:

```java
private String buildPayloadWithAddress(String orderId, String email, String name,
                                        String countryCode, String... products) {
    var productList = new StringBuilder("[");
    for (int i = 0; i < products.length; i++) {
        if (i > 0) productList.append(",");
        productList.append(products[i]);
    }
    productList.append("]");
    var addressBlock = countryCode != null
            ? ", \"shipping_address\": {\"country_code\": \"%s\"}".formatted(countryCode)
            : "";
    return """
            {"id": "%s", "customer": {"email": "%s", "full_name": "%s"}%s, "products": %s}
            """.formatted(orderId, email, name, addressBlock, productList).trim();
}
```

Then add tests:

```java
@Test
void shouldExtractCountryCodeFromShippingAddress() {
    var product = buildProduct("101", "SKU-1", "Entry Pack", 1);
    var payload = buildPayloadWithAddress("ORD-1", "test@example.com", "Test", "PT", product);

    // Setup mocks for normal processing flow (copy from existing test)
    // ...

    service.processOrderPaid(payload);

    var orderCaptor = ArgumentCaptor.forClass(JumpsellerOrder.class);
    then(orderRepository).should(atLeast(1)).save(orderCaptor.capture());
    assertThat(orderCaptor.getAllValues().getLast().getCustomerCountry()).isEqualTo("PT");
}

@Test
void shouldEnrichUserCountryWhenNull() {
    var product = buildProduct("101", "SKU-1", "Entry Pack", 1);
    var payload = buildPayloadWithAddress("ORD-1", "test@example.com", "Test", "PT", product);

    var user = new User("test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
    // user.getCountry() is null
    given(userService.findOrCreateByEmail("test@example.com", "Test")).willReturn(user);
    // Setup remaining mocks...

    service.processOrderPaid(payload);

    assertThat(user.getCountry()).isEqualTo("PT");
    then(userService).should().updateProfile(eq(user.getId()), eq("Test"), isNull(), eq("PT"));
}

@Test
void shouldNotOverwriteExistingUserCountry() {
    var product = buildProduct("101", "SKU-1", "Entry Pack", 1);
    var payload = buildPayloadWithAddress("ORD-1", "test@example.com", "Test", "BR", product);

    var user = new User("test@example.com", "Test", UserStatus.ACTIVE, Role.USER);
    user.updateCountry("PT"); // Already has a country
    given(userService.findOrCreateByEmail("test@example.com", "Test")).willReturn(user);
    // Setup remaining mocks...

    service.processOrderPaid(payload);

    assertThat(user.getCountry()).isEqualTo("PT"); // Not overwritten
}
```

Note: The exact mock setup will depend on the existing test patterns in `WebhookServiceTest.java`.
Copy the mock setup from `shouldProcessValidOrderWithSingleDivision` and adapt. The key point
is testing country extraction and user enrichment behavior.

Run: `mvn test -Dtest=WebhookServiceTest#shouldExtractCountryCodeFromShippingAddress -Dsurefire.useFile=false`
Expected: FAIL — WebhookService doesn't extract country yet.

**Step 2: Implement minimal code**

Modify `WebhookService.processOrderPaid()`. After parsing customer email/name (~line 84),
add country extraction:

```java
// Extract country code from shipping address (fallback to billing)
String customerCountry = null;
var shippingAddress = root.get("shipping_address");
if (shippingAddress != null && shippingAddress.has("country_code")) {
    customerCountry = shippingAddress.get("country_code").asText();
}
if (customerCountry == null) {
    var billingAddress = root.get("billing_address");
    if (billingAddress != null && billingAddress.has("country_code")) {
        customerCountry = billingAddress.get("country_code").asText();
    }
}
```

After creating the JumpsellerOrder (~line 98), set the country:

```java
order.setCustomerCountry(customerCountry);
```

After finding/creating the user (~line 93), enrich country if null:

```java
if (customerCountry != null && user.getCountry() == null) {
    user.updateCountry(customerCountry);
    log.info("Enriched user {} country to {} from webhook", user.getEmail(), customerCountry);
}
```

Note: The user enrichment may need to call `userService.updateProfile()` or a dedicated
method, depending on whether the user entity is managed in this transaction. Check if
`findOrCreateByEmail` returns a managed entity or detached. If detached, call a service
method. If managed (same transaction), calling `user.updateCountry()` directly and
relying on JPA dirty checking may work — but verify with the existing transaction
boundaries. The safest approach is to add a `UserService.updateCountryIfNull(UUID, String)`
method, or reuse the existing `updateMeaderyName` pattern.

Run: `mvn test -Dtest=WebhookServiceTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`

Commit: `Extract country from Jumpseller webhook and enrich User`

---

## Task 7: ProfileView — new profile self-edit view

**Files:**
- Create: `src/main/java/app/meads/identity/internal/ProfileView.java`
- Modify: `src/main/java/app/meads/MainLayout.java`

**Step 1: Write a failing UI test**

Create `src/test/java/app/meads/identity/ProfileViewTest.java`:

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class ProfileViewTest {

    @Autowired private ApplicationContext ctx;
    @Autowired private UserRepository userRepository;

    // Follow exact setup pattern from UserListViewTest.java:
    // Routes, MockSpringServlet, MockVaadin, resolveAuthentication, propagateSecurityContext

    @Test
    @WithMockUser(username = "entrant@test.com", roles = "USER")
    void shouldDisplayProfileFields(TestInfo testInfo) {
        // Create user with known data
        var user = userRepository.findByEmail("entrant@test.com")
                .orElseGet(() -> userRepository.save(
                        new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER)));

        UI.getCurrent().navigate("profile");

        var emailField = _get(TextField.class, spec -> spec.withCaption("Email"));
        assertThat(emailField.isReadOnly()).isTrue();
        assertThat(emailField.getValue()).isEqualTo("entrant@test.com");

        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        assertThat(nameField.getValue()).isEqualTo("Test Entrant");
    }

    @Test
    @WithMockUser(username = "entrant@test.com", roles = "USER")
    void shouldSaveProfileChanges(TestInfo testInfo) {
        var user = userRepository.findByEmail("entrant@test.com")
                .orElseGet(() -> userRepository.save(
                        new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER)));

        UI.getCurrent().navigate("profile");

        var nameField = _get(TextField.class, spec -> spec.withCaption("Name"));
        nameField.setValue("Updated Name");

        // Set meadery name
        var meaderyField = _get(TextField.class, spec -> spec.withCaption("Meadery Name"));
        meaderyField.setValue("My Meadery");

        _click(_get(Button.class, spec -> spec.withCaption("Save")));

        var updated = userRepository.findByEmail("entrant@test.com").orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getMeaderyName()).isEqualTo("My Meadery");
    }
}
```

Note: Karibu uses `spec.withCaption()` for label matching — verify this matches the
existing test patterns in `UserListViewTest.java` (some versions use `spec.withLabel()`).

Run: `mvn test -Dtest=ProfileViewTest -Dsurefire.useFile=false`
Expected: FAIL — ProfileView doesn't exist.

**Step 2: Implement minimal code**

Create `ProfileView.java`:

```java
package app.meads.identity.internal;

import app.meads.MainLayout;
import app.meads.identity.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Locale;

@Route(value = "profile", layout = MainLayout.class)
@PermitAll
@PageTitle("My Profile")
class ProfileView extends VerticalLayout {

    ProfileView(UserService userService) {
        var email = SecurityContextHolder.getContext().getAuthentication().getName();
        var user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        var emailField = new TextField("Email");
        emailField.setValue(user.getEmail());
        emailField.setReadOnly(true);
        emailField.setWidthFull();

        var nameField = new TextField("Name");
        nameField.setValue(user.getName());
        nameField.setRequired(true);
        nameField.setWidthFull();

        var meaderyField = new TextField("Meadery Name");
        meaderyField.setValue(user.getMeaderyName() != null ? user.getMeaderyName() : "");
        meaderyField.setWidthFull();

        var countryCombo = new ComboBox<String>("Country");
        var countries = Arrays.stream(Locale.getISOCountries())
                .sorted((a, b) -> new Locale("", a).getDisplayCountry(Locale.ENGLISH)
                        .compareTo(new Locale("", b).getDisplayCountry(Locale.ENGLISH)))
                .toList();
        countryCombo.setItems(countries);
        countryCombo.setItemLabelGenerator(code ->
                new Locale("", code).getDisplayCountry(Locale.ENGLISH));
        countryCombo.setClearButtonVisible(true);
        countryCombo.setWidthFull();
        if (user.getCountry() != null) {
            countryCombo.setValue(user.getCountry());
        }

        var saveButton = new Button("Save", e -> {
            var name = nameField.getValue();
            if (name == null || name.isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            try {
                var meadery = meaderyField.getValue();
                userService.updateProfile(user.getId(), name.trim(),
                        meadery != null && !meadery.isBlank() ? meadery.trim() : null,
                        countryCombo.getValue());
                Notification.show("Profile updated").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show(ex.getMessage());
            }
        });

        add(emailField, nameField, meaderyField, countryCombo, saveButton);
        setMaxWidth("600px");
    }
}
```

Note: Check if `UserService.findByEmail()` exists. If not, it may be called differently
(e.g., the repository method is in `internal/`). You may need to add a public
`findByEmail(String email)` method to `UserService` if one doesn't exist — check the
existing service API.

Add "My Profile" to `MainLayout.java` — in the nav section (around line 70), add for all
authenticated users:

```java
if (authenticationContext.isAuthenticated()) {
    nav.addItem(new SideNavItem("My Profile", "profile", VaadinIcon.USER.create()));
}
```

Place this so it appears for all authenticated users (including SYSTEM_ADMIN).

Run: `mvn test -Dtest=ProfileViewTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`

Commit: `Add ProfileView for user self-edit (name, meadery, country)`

---

## Task 8: Add meadery name + country to admin UserListView dialog

**Files:**
- Modify: `src/main/java/app/meads/identity/internal/UserListView.java`
- Modify: `src/test/java/app/meads/identity/UserListViewTest.java`

**Step 1: Write a failing UI test**

Add to `UserListViewTest.java`:

```java
@Test
@WithMockUser(roles = "SYSTEM_ADMIN")
void shouldDisplayMeaderyNameAndCountryInEditDialog(TestInfo testInfo) {
    var user = new User("meadery@test.com", "Meadery User", UserStatus.ACTIVE, Role.USER);
    user.updateMeaderyName("Golden Mead");
    user.updateCountry("PT");
    userRepository.save(user);

    UI.getCurrent().navigate("users");
    // Open edit dialog — follow existing pattern in the test file

    var meaderyField = _get(TextField.class, spec -> spec.withCaption("Meadery Name"));
    assertThat(meaderyField.getValue()).isEqualTo("Golden Mead");

    var countryCombo = _get(ComboBox.class, spec -> spec.withCaption("Country"));
    assertThat(countryCombo.getValue()).isEqualTo("PT");
}
```

Run: `mvn test -Dtest=UserListViewTest#shouldDisplayMeaderyNameAndCountryInEditDialog -Dsurefire.useFile=false`
Expected: FAIL — meadery and country fields not in dialog.

**Step 2: Implement minimal code**

In `UserListView.openUserDialog()` (~line 242), add meadery and country fields after the
name field:

```java
var meaderyField = new TextField("Meadery Name");
meaderyField.setWidthFull();
if (existingUser != null && existingUser.getMeaderyName() != null) {
    meaderyField.setValue(existingUser.getMeaderyName());
}

var countryCombo = new ComboBox<String>("Country");
var countries = Arrays.stream(Locale.getISOCountries())
        .sorted((a, b) -> new Locale("", a).getDisplayCountry(Locale.ENGLISH)
                .compareTo(new Locale("", b).getDisplayCountry(Locale.ENGLISH)))
        .toList();
countryCombo.setItems(countries);
countryCombo.setItemLabelGenerator(code ->
        new Locale("", code).getDisplayCountry(Locale.ENGLISH));
countryCombo.setClearButtonVisible(true);
countryCombo.setWidthFull();
if (existingUser != null && existingUser.getCountry() != null) {
    countryCombo.setValue(existingUser.getCountry());
}
```

Add to the form layout and save handler. In the save handler for edit mode, call:

```java
userService.updateProfile(existingUser.getId(), nameField.getValue().trim(),
        meaderyField.getValue() != null && !meaderyField.getValue().isBlank()
                ? meaderyField.getValue().trim() : null,
        countryCombo.getValue());
```

Note: The existing save handler uses `userService.updateUser()` for name/role/status.
You'll need to also call `updateProfile()` or extend `updateUser()`. The simplest approach
is to call both: `updateUser()` for role/status + `updateProfile()` for name/meadery/country.
Or refactor to use a single method. Match the existing pattern — check what `updateUser`
already does and avoid duplicating the name update.

Run: `mvn test -Dtest=UserListViewTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`

Commit: `Add meadery name and country to admin user edit dialog`

---

## Task 9: Add `meaderyNameRequired` checkbox to DivisionDetailView Settings tab

**Files:**
- Modify: `src/main/java/app/meads/competition/internal/DivisionDetailView.java`
- Modify or create: `src/test/java/app/meads/competition/DivisionDetailViewTest.java`

**Step 1: Write a failing UI test**

Add test:

```java
@Test
@WithMockUser(username = ADMIN_EMAIL, roles = "USER")
void shouldDisplayMeaderyNameRequiredCheckboxInSettings(TestInfo testInfo) {
    // Setup: create competition + division in DRAFT
    // Navigate to division detail view
    // Switch to Settings tab

    var checkbox = _get(Checkbox.class, spec -> spec.withCaption("Meadery Name Required"));
    assertThat(checkbox.getValue()).isFalse(); // default
    assertThat(checkbox.isEnabled()).isTrue(); // DRAFT allows editing
}
```

Run: `mvn test -Dtest=DivisionDetailViewTest#shouldDisplayMeaderyNameRequiredCheckboxInSettings -Dsurefire.useFile=false`
Expected: FAIL — checkbox doesn't exist in the view.

**Step 2: Implement minimal code**

In `DivisionDetailView.createSettingsTab()` (~line 400), add a `Checkbox` after the entry
limits fields:

```java
var meaderyRequiredCheckbox = new Checkbox("Meadery Name Required");
meaderyRequiredCheckbox.setValue(division.isMeaderyNameRequired());
meaderyRequiredCheckbox.setEnabled(isDraft);
meaderyRequiredCheckbox.setTooltipText(
        "When enabled, entrants must have a meadery name in their profile to submit entries");
```

Add it to the form layout. In the save handler, add:

```java
competitionService.updateDivisionMeaderyNameRequired(
        divisionId, meaderyRequiredCheckbox.getValue(), getCurrentUserId());
```

Run: `mvn test -Dtest=DivisionDetailViewTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`

Commit: `Add meaderyNameRequired checkbox to Division Settings tab`

---

## Task 10: MyEntriesView — meadery name warning + submit blocking

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/MyEntriesView.java`
- Modify: `src/test/java/app/meads/entry/MyEntriesViewTest.java`

**Step 1: Write failing UI tests**

Add to `MyEntriesViewTest.java`:

```java
@Test
@WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
void shouldShowWarningWhenMeaderyNameRequiredButMissing(TestInfo testInfo) {
    // Setup: division with meaderyNameRequired = true
    // User has no meaderyName set
    // Navigate to my-entries for that division

    var warning = _get(Div.class, spec -> spec.withText("This division requires a meadery name"));
    assertThat(warning).isNotNull();

    // Submit All button should be disabled
    var submitAllBtn = _get(Button.class, spec -> spec.withCaption("Submit All"));
    assertThat(submitAllBtn.isEnabled()).isFalse();
}

@Test
@WithMockUser(username = ENTRANT_EMAIL, roles = "USER")
void shouldNotShowWarningWhenMeaderyNameIsSet(TestInfo testInfo) {
    // Setup: division with meaderyNameRequired = true
    // User HAS meaderyName set
    // Navigate to my-entries

    // No warning banner
    var warnings = _find(Div.class, spec -> spec.withText("This division requires a meadery name"));
    assertThat(warnings).isEmpty();
}
```

Note: Adapt the Karibu assertions to match the actual API (`_find` returns a list,
`_get` throws if not found). The warning might be a `Span` inside a styled `Div` —
match whatever component you use. Also test the per-entry submit button disabling.

Run: `mvn test -Dtest=MyEntriesViewTest#shouldShowWarningWhenMeaderyNameRequiredButMissing -Dsurefire.useFile=false`
Expected: FAIL — no warning logic exists.

**Step 2: Implement minimal code**

In `MyEntriesView`, add a field to track the state:

```java
private boolean meaderyNameMissing;
```

In `beforeEnter()`, after fetching the division, check:

```java
var user = userService.findByEmail(email).orElseThrow();
this.meaderyNameMissing = division.isMeaderyNameRequired()
        && (user.getMeaderyName() == null || user.getMeaderyName().isBlank());
```

Note: `MyEntriesView` currently calls `competitionService` to get the division. The Division
object is available. The user can be fetched via the identity module's `UserService` —
check if it's already injected or needs to be added as a constructor parameter.
Cross-module access: `UserService` is in `identity` module's public API, but `entry`
module's `allowedDependencies` includes `identity`, so this is allowed.

Add warning banner in the view construction (after `createCreditInfo()`):

```java
if (meaderyNameMissing) {
    var warning = new Div();
    warning.getStyle()
            .set("background-color", "var(--lumo-warning-color-10pct)")
            .set("color", "var(--lumo-warning-text-color)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("margin-bottom", "var(--lumo-space-m)");
    var anchor = new Anchor("profile", "update your profile");
    warning.add(new Span("This division requires a meadery name. Please "), anchor,
            new Span(" before submitting entries."));
    add(warning);
}
```

Disable submit buttons — modify `createActionButtons()`:

```java
// Submit All — also disabled if meadery name missing
submitButton.setEnabled(hasDrafts && !meaderyNameMissing);
if (meaderyNameMissing) {
    submitButton.setTooltipText("Meadery name required — update your profile");
}
```

For per-entry submit buttons in the grid (around line 324):

```java
submitBtn.setEnabled(isDraft && isOpen && !meaderyNameMissing);
if (meaderyNameMissing) {
    submitBtn.setTooltipText("Meadery name required — update your profile");
}
```

Run: `mvn test -Dtest=MyEntriesViewTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`

Commit: `Block entry submission when meadery name required but missing`

---

## Task 11: DivisionEntryAdminView — meadery name + country columns

**Files:**
- Modify: `src/main/java/app/meads/entry/internal/DivisionEntryAdminView.java`
- Modify: `src/test/java/app/meads/entry/DivisionEntryAdminViewTest.java`

**Step 1: Write a failing UI test**

Add to `DivisionEntryAdminViewTest.java`:

```java
@Test
@WithMockUser(username = ADMIN_EMAIL, roles = "USER")
void shouldShowMeaderyAndCountryColumnsInEntriesTab(TestInfo testInfo) {
    // Setup: user with meadery name + country, create entry
    // Navigate to entry admin view, select Entries tab

    var grid = _get(Grid.class);
    var columns = grid.getColumns();
    var headers = columns.stream()
            .map(col -> col.getHeaderText())
            .toList();
    assertThat(headers).contains("Meadery", "Country");
}
```

Run: `mvn test -Dtest=DivisionEntryAdminViewTest#shouldShowMeaderyAndCountryColumnsInEntriesTab -Dsurefire.useFile=false`
Expected: FAIL — columns don't exist.

**Step 2: Implement minimal code**

The admin entries grid currently shows entrant email. To show meadery name and country,
you need to look up the User for each entry. Options:

**Option A:** Add a method to `EntryService` that returns entry data enriched with user
info (e.g., a DTO or map). Example:

```java
// In EntryService (public API)
public Map<UUID, User> findUsersForEntries(List<Entry> entries) {
    var userIds = entries.stream().map(Entry::getUserId).distinct().toList();
    return userService.findAllByIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
}
```

This requires adding `findAllByIds` to `UserService` (public API).

**Option B:** Add the user lookup inline in the view using `UserService` directly.
Since `entry` module depends on `identity`, this is allowed.

Choose the simpler option. In the grid, add columns:

```java
grid.addColumn(entry -> {
    var user = userMap.get(entry.getUserId());
    return user != null ? user.getMeaderyName() : "";
}).setHeader("Meadery").setAutoWidth(true);

grid.addColumn(entry -> {
    var user = userMap.get(entry.getUserId());
    return user != null && user.getCountry() != null
            ? new Locale("", user.getCountry()).getDisplayCountry(Locale.ENGLISH)
            : "";
}).setHeader("Country").setAutoWidth(true);
```

Run: `mvn test -Dtest=DivisionEntryAdminViewTest -Dsurefire.useFile=false`
Expected: PASS

**Step 3: Run full suite + commit**

Run: `mvn test -Dsurefire.useFile=false`

Commit: `Add meadery name and country columns to admin entries view`

---

## Task 12: Final integration + documentation

**Step 1: Run full test suite**

Run: `mvn test -Dsurefire.useFile=false`
Expected: All tests pass.

**Step 2: Run modulith structure test**

Run: `mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false`
Expected: PASS — no module boundary violations.

**Step 3: Update documentation**

Update the following files:

1. **`docs/SESSION_CONTEXT.md`:**
   - Update test count
   - Add profile self-edit to identity module status
   - Add meaderyNameRequired to competition module status
   - Add webhook country enrichment to entry module status
   - Update "What's Next" — mark Priority 1 as done, promote remaining priorities

2. **`CLAUDE.md`:**
   - Update User.java field list in package layout (add `country`)
   - Update Division.java description (add `meaderyNameRequired`)
   - Update JumpsellerOrder.java description (add `customerCountry`)
   - Add ProfileView to identity internal package layout
   - Update MainLayout description (mention "My Profile" nav item)
   - Update migration version notes if needed

3. **`docs/walkthrough/manual-test.md`:**
   - Add profile self-edit test steps (navigate to My Profile, edit fields, save)
   - Add meaderyNameRequired checkbox in Division Settings
   - Add test for warning banner in MyEntriesView when meadery required
   - Add test for submit blocking behavior

4. **Delete this plan file** (`docs/plans/2026-03-10-profile-meadery-country-plan.md`)
   — implementation is complete, code is the source of truth. Keep the design doc
   as reference.

**Step 4: Commit**

Commit: `Update docs for profile self-edit, meadery name & country feature`

---

## Summary

| Task | Module | Description |
|------|--------|-------------|
| 1 | identity | Add `country` field to User entity + V2 migration |
| 2 | identity | Add `UserService.updateProfile()` with ISO validation |
| 3 | competition | Add `meaderyNameRequired` to Division entity + V4 migration |
| 4 | competition | Expose `meaderyNameRequired` in CompetitionService |
| 5 | entry | Add `customerCountry` to JumpsellerOrder + V10 migration |
| 6 | entry | Webhook country extraction + User enrichment |
| 7 | identity | New ProfileView + MainLayout nav item |
| 8 | identity | Meadery + country fields in admin UserListView dialog |
| 9 | competition | MeaderyNameRequired checkbox in DivisionDetailView Settings |
| 10 | entry | MyEntriesView warning banner + submit blocking |
| 11 | entry | Meadery + country columns in DivisionEntryAdminView |
| 12 | all | Final integration test + documentation updates |
