package com.moodbuds.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ShiprocketGatewayTest {
    private final ShippingProperties properties = new ShippingProperties("MOCK", "FREE", null, null, null,
            "Primary", "560001", "test-token", null, Duration.ofMinutes(15), 0L,
            500, new BigDecimal("25"), new BigDecimal("20"), new BigDecimal("5"),
            null, null, null, null, null, null, null);
    private final ShiprocketGateway gateway = new ShiprocketGateway(properties, RestClient.builder());

    @Test
    void mockQuoteReturnsDeterministicServiceabilityAndEta() {
        var quote = gateway.quote("560001", "110001", 750, new BigDecimal("25"),
                new BigDecimal("20"), new BigDecimal("5"), false);

        assertThat(quote.serviceable()).isTrue();
        assertThat(quote.courierName()).isEqualTo("MoodBuds Mock Express");
        assertThat(quote.estimatedDeliveryDate()).isNotNull();
        assertThat(quote.charge()).isPositive();
    }

    @Test
    void mockQuoteRejectsReservedFailurePincode() {
        assertThat(gateway.quote("560001", "999001", 500, new BigDecimal("25"),
                new BigDecimal("20"), new BigDecimal("5"), false).serviceable()).isFalse();
    }

    @Test
    void normalizesProviderStatuses() {
        assertThat(ShiprocketGateway.normalize("Out For Delivery")).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(ShiprocketGateway.normalize("Delivered")).isEqualTo("DELIVERED");
        assertThat(ShiprocketGateway.normalize("Return Picked Up")).isEqualTo("IN_TRANSIT");
    }
}
