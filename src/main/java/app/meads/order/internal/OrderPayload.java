package app.meads.order.internal;

import java.time.Instant;
import java.util.UUID;

public record OrderPayload(
    String externalOrderId,
    String externalSource,
    UUID competitionId,
    CustomerInfo customer,
    int quantity,
    Instant purchasedAt
) {
    public record CustomerInfo(
        String email,
        String name,
        String phone,
        AddressInfo address
    ) {}

    public record AddressInfo(
        String line1,
        String line2,
        String city,
        String stateProvince,
        String postalCode,
        String country
    ) {}
}
