package app.meads.entry;

import app.meads.competition.CompetitionService;
import app.meads.competition.Division;
import app.meads.competition.ScoringSystem;
import app.meads.entry.internal.EntryCreditRepository;
import app.meads.entry.internal.JumpsellerOrderLineItemRepository;
import app.meads.entry.internal.JumpsellerOrderRepository;
import app.meads.entry.internal.ProductMappingRepository;
import app.meads.identity.Role;
import app.meads.identity.User;
import app.meads.identity.UserService;
import app.meads.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    JumpsellerOrderRepository orderRepository;

    @Mock
    JumpsellerOrderLineItemRepository lineItemRepository;

    @Mock
    ProductMappingRepository productMappingRepository;

    @Mock
    EntryCreditRepository creditRepository;

    @Mock
    CompetitionService competitionService;

    @Mock
    UserService userService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    private static final String HOOKS_TOKEN = "test-secret-token";

    private WebhookService createService() {
        return new WebhookService(orderRepository, lineItemRepository,
                productMappingRepository, creditRepository,
                competitionService, userService, eventPublisher, HOOKS_TOKEN);
    }

    private String computeHmac(String payload, String secret) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
    }

    private String buildPayload(String orderId, String email, String name,
                                 String... products) {
        var productList = new StringBuilder("[");
        for (int i = 0; i < products.length; i++) {
            if (i > 0) productList.append(",");
            productList.append(products[i]);
        }
        productList.append("]");
        return """
                {"id": "%s", "customer": {"email": "%s", "full_name": "%s"}, "products": %s}
                """.formatted(orderId, email, name, productList).trim();
    }

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

    private String buildProduct(String productId, String sku, String name, int qty) {
        return """
                {"product_id": "%s", "sku": "%s", "name": "%s", "qty": %d}
                """.formatted(productId, sku != null ? sku : "", name, qty).trim();
    }

    // --- Signature verification tests ---

    @Test
    void shouldVerifyValidSignature() throws Exception {
        var service = createService();
        var payload = "{\"order\": {\"id\": 1}}";
        var signature = computeHmac(payload, HOOKS_TOKEN);

        assertThat(service.verifySignature(payload, signature)).isTrue();
    }

    @Test
    void shouldRejectInvalidSignature() {
        var service = createService();
        var payload = "{\"order\": {\"id\": 1}}";

        assertThat(service.verifySignature(payload, "invalid-signature")).isFalse();
    }

    @Test
    void shouldRejectNullSignature() {
        var service = createService();

        assertThat(service.verifySignature("{}", null)).isFalse();
    }

    // --- processOrderPaid tests ---

    @Test
    void shouldProcessValidOrderWithSingleDivision() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);
        var mapping = new ProductMapping(divisionId, "101", "SKU-001", "Entry Pack", 1);

        var payload = buildPayload("ORDER-001", "entrant@test.com", "Test Entrant",
                buildProduct("101", "SKU-001", "Entry Pack", 2));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-001")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("101"))
                .willReturn(List.of(mapping));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.findDistinctDivisionIdsByUserId(user.getId()))
                .willReturn(List.of());
        given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        // Should save the order — last save has PROCESSED status
        var orderCaptor = ArgumentCaptor.forClass(JumpsellerOrder.class);
        then(orderRepository).should(org.mockito.Mockito.atLeast(1))
                .save(orderCaptor.capture());
        assertThat(orderCaptor.getAllValues().getLast().getStatus())
                .isEqualTo(OrderStatus.PROCESSED);

        // Should create credits (2 qty * 1 credit/unit = 2 credits)
        var creditCaptor = ArgumentCaptor.forClass(EntryCredit.class);
        then(creditRepository).should().save(creditCaptor.capture());
        assertThat(creditCaptor.getValue().getAmount()).isEqualTo(2);
        assertThat(creditCaptor.getValue().getDivisionId()).isEqualTo(divisionId);
        assertThat(creditCaptor.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(creditCaptor.getValue().getSourceType()).isEqualTo("WEBHOOK");

        // Should mark line item as PROCESSED
        then(lineItemRepository).should().save(argThat(item ->
                item.getStatus() == LineItemStatus.PROCESSED
                && item.getCreditsAwarded() == 2));
    }

    @Test
    void shouldIgnoreNonMappedProducts() {
        var service = createService();
        var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);

        var payload = buildPayload("ORDER-002", "entrant@test.com", "Test Entrant",
                buildProduct("999", null, "Conference T-Shirt", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-002")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("999"))
                .willReturn(List.of());
        given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        // Should NOT create any credits
        then(creditRepository).should(never()).save(any());

        // Should mark line item as IGNORED
        then(lineItemRepository).should().save(argThat(item ->
                item.getStatus() == LineItemStatus.IGNORED));
    }

    @Test
    void shouldFlagMutualExclusivityConflict() {
        var service = createService();
        var competitionId = UUID.randomUUID();
        var divisionA = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var divisionB = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);
        var mappingA = new ProductMapping(divisionA.getId(), "101", "SKU-A",
                "Home Entry", 1);
        var mappingB = new ProductMapping(divisionB.getId(), "102", "SKU-B",
                "Pro Entry", 1);

        var payload = buildPayload("ORDER-003", "entrant@test.com", "Test Entrant",
                buildProduct("101", "SKU-A", "Home Entry", 1),
                buildProduct("102", "SKU-B", "Pro Entry", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-003")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("101"))
                .willReturn(List.of(mappingA));
        given(productMappingRepository.findByJumpsellerProductId("102"))
                .willReturn(List.of(mappingB));
        given(competitionService.findDivisionById(divisionA.getId())).willReturn(divisionA);
        given(competitionService.findDivisionById(divisionB.getId())).willReturn(divisionB);
        // User already has credits in divisionA (from first product processing)
        given(creditRepository.findDistinctDivisionIdsByUserId(user.getId()))
                .willReturn(List.of())
                .willReturn(List.of(divisionA.getId()));
        given(competitionService.findDivisionsByCompetition(competitionId))
                .willReturn(List.of(divisionA, divisionB));
        given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        // Should save order — last save has PARTIALLY_PROCESSED status
        var orderCaptor = ArgumentCaptor.forClass(JumpsellerOrder.class);
        then(orderRepository).should(org.mockito.Mockito.atLeast(1))
                .save(orderCaptor.capture());
        assertThat(orderCaptor.getAllValues().getLast().getStatus())
                .isEqualTo(OrderStatus.PARTIALLY_PROCESSED);

        // Should create credits for first valid item only
        then(creditRepository).should().save(any(EntryCredit.class));

        // Should have two line items saved — one PROCESSED, one NEEDS_REVIEW
        var lineItemCaptor = ArgumentCaptor.forClass(JumpsellerOrderLineItem.class);
        then(lineItemRepository).should(org.mockito.Mockito.times(2))
                .save(lineItemCaptor.capture());
        var items = lineItemCaptor.getAllValues();
        assertThat(items).extracting(JumpsellerOrderLineItem::getStatus)
                .containsExactlyInAnyOrder(LineItemStatus.PROCESSED, LineItemStatus.NEEDS_REVIEW);
    }

    @Test
    void shouldCreateUserForUnknownEmail() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var newUser = new User("new@test.com", "New Entrant", UserStatus.PENDING, Role.USER);
        var mapping = new ProductMapping(divisionId, "101", "SKU-001", "Entry Pack", 1);

        var payload = buildPayload("ORDER-004", "new@test.com", "New Entrant",
                buildProduct("101", "SKU-001", "Entry Pack", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-004")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("101"))
                .willReturn(List.of(mapping));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.findDistinctDivisionIdsByUserId(newUser.getId()))
                .willReturn(List.of());
        given(userService.findOrCreateByEmail("new@test.com", "New Entrant")).willReturn(newUser);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        // findOrCreateByEmail is called — creates PENDING user if not exists
        then(userService).should().findOrCreateByEmail("new@test.com", "New Entrant");
        then(creditRepository).should().save(any(EntryCredit.class));
    }

    @Test
    void shouldMarkOrderNeedsReviewWhenAllItemsInvalid() {
        var service = createService();
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);
        var mapping = new ProductMapping(divisionId, "101", "SKU-001", "Entry Pack", 1);

        // User already has credits in a different division of same competition
        var otherDivisionId = UUID.randomUUID();
        var otherDivision = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");

        var payload = buildPayload("ORDER-005", "entrant@test.com", "Test Entrant",
                buildProduct("101", "SKU-001", "Entry Pack", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-005")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("101"))
                .willReturn(List.of(mapping));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.findDistinctDivisionIdsByUserId(user.getId()))
                .willReturn(List.of(otherDivision.getId()));
        given(competitionService.findDivisionsByCompetition(competitionId))
                .willReturn(List.of(division, otherDivision));
        given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        // All items flagged → last save has NEEDS_REVIEW status
        var orderCaptor = ArgumentCaptor.forClass(JumpsellerOrder.class);
        then(orderRepository).should(org.mockito.Mockito.atLeast(1))
                .save(orderCaptor.capture());
        assertThat(orderCaptor.getAllValues().getLast().getStatus())
                .isEqualTo(OrderStatus.NEEDS_REVIEW);
        then(creditRepository).should(never()).save(any());
    }

    @Test
    void shouldExtractCountryCodeFromShippingAddress() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);
        var mapping = new ProductMapping(divisionId, "101", "SKU-001", "Entry Pack", 1);

        var payload = buildPayloadWithAddress("ORDER-010", "entrant@test.com", "Test Entrant",
                "PT", buildProduct("101", "SKU-001", "Entry Pack", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-010")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("101"))
                .willReturn(List.of(mapping));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.findDistinctDivisionIdsByUserId(user.getId()))
                .willReturn(List.of());
        given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        var orderCaptor = ArgumentCaptor.forClass(JumpsellerOrder.class);
        then(orderRepository).should(org.mockito.Mockito.atLeast(1)).save(orderCaptor.capture());
        assertThat(orderCaptor.getAllValues().getLast().getCustomerCountry()).isEqualTo("PT");
    }

    @Test
    void shouldEnrichUserCountryWhenNull() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);
        var mapping = new ProductMapping(divisionId, "101", "SKU-001", "Entry Pack", 1);

        var payload = buildPayloadWithAddress("ORDER-011", "entrant@test.com", "Test Entrant",
                "PT", buildProduct("101", "SKU-001", "Entry Pack", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-011")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("101"))
                .willReturn(List.of(mapping));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.findDistinctDivisionIdsByUserId(user.getId()))
                .willReturn(List.of());
        given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        // User country should be enriched (was null, now PT)
        assertThat(user.getCountry()).isEqualTo("PT");
    }

    @Test
    void shouldNotOverwriteExistingUserCountry() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP, LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);
        user.updateCountry("BR"); // Already has country
        var mapping = new ProductMapping(divisionId, "101", "SKU-001", "Entry Pack", 1);

        var payload = buildPayloadWithAddress("ORDER-012", "entrant@test.com", "Test Entrant",
                "PT", buildProduct("101", "SKU-001", "Entry Pack", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-012")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("101"))
                .willReturn(List.of(mapping));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.findDistinctDivisionIdsByUserId(user.getId()))
                .willReturn(List.of());
        given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        // Should NOT overwrite existing country
        assertThat(user.getCountry()).isEqualTo("BR");
    }

    @Test
    void shouldSkipDuplicateOrder() {
        var service = createService();

        var payload = buildPayload("ORDER-DUP", "entrant@test.com", "Test Entrant",
                buildProduct("101", "SKU-001", "Entry Pack", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-DUP")).willReturn(true);

        service.processOrderPaid(payload);

        // Should NOT save anything
        then(orderRepository).should(never()).save(any());
        then(lineItemRepository).should(never()).save(any());
        then(creditRepository).should(never()).save(any());
    }

    // --- OrderRequiresReviewEvent tests ---

    @Test
    void shouldPublishOrderRequiresReviewEventWhenNeedsReview() {
        var service = createService();
        var competitionId = UUID.randomUUID();
        var divisionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);
        var mapping = new ProductMapping(divisionId, "101", "SKU-001", "Entry Pack", 1);

        var otherDivision = new Division(competitionId, "Pro", "pro", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");

        var payload = buildPayload("ORDER-EVENT", "entrant@test.com", "Test Entrant",
                buildProduct("101", "SKU-001", "Entry Pack", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-EVENT")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("101"))
                .willReturn(List.of(mapping));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.findDistinctDivisionIdsByUserId(user.getId()))
                .willReturn(List.of(otherDivision.getId()));
        given(competitionService.findDivisionsByCompetition(competitionId))
                .willReturn(List.of(division, otherDivision));
        given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        var eventCaptor = ArgumentCaptor.forClass(OrderRequiresReviewEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        var event = eventCaptor.getValue();
        assertThat(event.jumpsellerOrderId()).isEqualTo("ORDER-EVENT");
        assertThat(event.status()).isEqualTo(OrderStatus.NEEDS_REVIEW);
        assertThat(event.affectedCompetitionIds()).contains(competitionId);
        assertThat(event.customerName()).isEqualTo("Test Entrant");
    }

    @Test
    void shouldNotPublishReviewEventWhenFullyProcessed() {
        var service = createService();
        var divisionId = UUID.randomUUID();
        var competitionId = UUID.randomUUID();
        var division = new Division(competitionId, "Home", "home", ScoringSystem.MJP,
                LocalDateTime.of(2026, 12, 31, 23, 59), "UTC");
        var user = new User("entrant@test.com", "Test Entrant", UserStatus.ACTIVE, Role.USER);
        var mapping = new ProductMapping(divisionId, "101", "SKU-001", "Entry Pack", 1);

        var payload = buildPayload("ORDER-OK", "entrant@test.com", "Test Entrant",
                buildProduct("101", "SKU-001", "Entry Pack", 1));

        given(orderRepository.existsByJumpsellerOrderId("ORDER-OK")).willReturn(false);
        given(orderRepository.save(any(JumpsellerOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(productMappingRepository.findByJumpsellerProductId("101"))
                .willReturn(List.of(mapping));
        given(competitionService.findDivisionById(divisionId)).willReturn(division);
        given(creditRepository.findDistinctDivisionIdsByUserId(user.getId()))
                .willReturn(List.of());
        given(userService.findOrCreateByEmail("entrant@test.com", "Test Entrant")).willReturn(user);
        given(lineItemRepository.save(any(JumpsellerOrderLineItem.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(creditRepository.save(any(EntryCredit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.processOrderPaid(payload);

        then(eventPublisher).should(never()).publishEvent(any(OrderRequiresReviewEvent.class));
    }
}
