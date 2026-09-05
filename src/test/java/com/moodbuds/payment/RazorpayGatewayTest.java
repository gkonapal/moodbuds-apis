package com.moodbuds.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RazorpayGatewayTest {
    @Test
    void createsProviderOrderUsingServerAmountAndReceipt() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new RazorpayGateway(properties(), builder);
        server.expect(requestTo("https://razorpay.test/v1/orders")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"amount":16240,"currency":"INR","receipt":"MB-2026-TEST","partial_payment":false}
                        """, false))
                .andRespond(withSuccess("""
                        {"id":"order_test","amount":16240,"currency":"INR","status":"created"}
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.createOrder(16_240, "MB-2026-TEST", 7);

        assertThat(result.id()).isEqualTo("order_test");
        assertThat(result.amount()).isEqualTo(16_240);
        server.verify();
    }

    @Test
    void fetchesCapturedPaymentDetails() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new RazorpayGateway(properties(), builder);
        server.expect(requestTo("https://razorpay.test/v1/payments/pay_test"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
                        {"id":"pay_test","order_id":"order_test","amount":16240,
                         "currency":"INR","status":"captured","method":"upi"}
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.fetchPayment("pay_test");

        assertThat(result.status()).isEqualTo("captured");
        assertThat(result.method()).isEqualTo("upi");
        server.verify();
    }

    @Test
    void createsIdempotentRefundInPaise() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new RazorpayGateway(properties(), builder);
        server.expect(requestTo("https://razorpay.test/v1/payments/pay_test/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Refund-Idempotency", "refund-key-123"))
                .andExpect(content().json("""
                        {"amount":5250,"speed":"optimum","receipt":"MB-RF-9"}
                        """, false))
                .andRespond(withSuccess("""
                        {"id":"rfnd_test","payment_id":"pay_test","amount":5250,"currency":"INR",
                         "status":"pending","speed_requested":"optimum","speed_processed":"normal"}
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.createRefund("pay_test", 5_250, "MB-RF-9", "Returned item",
                "refund-key-123", "OPTIMUM");

        assertThat(result.id()).isEqualTo("rfnd_test");
        assertThat(result.status()).isEqualTo("pending");
        assertThat(result.speedProcessed()).isEqualTo("normal");
        server.verify();
    }

    @Test
    void fetchesProcessedRefundAndProviderReference() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new RazorpayGateway(properties(), builder);
        server.expect(requestTo("https://razorpay.test/v1/refunds/rfnd_test"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
                        {"id":"rfnd_test","payment_id":"pay_test","amount":5250,"currency":"INR",
                         "status":"processed","speed_requested":"optimum","speed_processed":"instant",
                         "acquirer_data":{"arn":"arn_123"}}
                        """, MediaType.APPLICATION_JSON));

        var result = gateway.fetchRefund("rfnd_test");

        assertThat(result.status()).isEqualTo("processed");
        assertThat(result.providerReference()).isEqualTo("arn_123");
        server.verify();
    }

    private RazorpayProperties properties() {
        return new RazorpayProperties("rzp_test_key", "test-secret", "https://razorpay.test",
                "MoodBuds", "MoodBuds order", "#111827");
    }
}
