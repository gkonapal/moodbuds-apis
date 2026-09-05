package com.moodbuds.payment;

import static com.moodbuds.payment.api.PaymentDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodbuds.common.ApiException;
import com.moodbuds.customer.CustomerProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RazorpayPaymentService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;
    private final RazorpayGateway gateway;
    private final RazorpayProperties properties;
    private final CustomerProfileService customers;

    public RazorpayPaymentService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc,
                                  ObjectMapper objectMapper, RazorpayGateway gateway,
                                  RazorpayProperties properties, CustomerProfileService customers) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.objectMapper = objectMapper;
        this.gateway = gateway;
        this.properties = properties;
        this.customers = customers;
    }

    @Transactional
    public RazorpayInitiationResponse initiate(long customerId, String orderNumber, String idempotencyKey) {
        customers.requireActive(customerId);
        OrderPaymentRow order = lockOrder(customerId, orderNumber);
        requirePayable(order);
        String key = idempotencyKey.trim();
        PaymentRow existingForKey = paymentByKey(order.id(), key);
        if (existingForKey != null) return initiation(order, existingForKey);
        PaymentRow active = activePayment(order.id());
        if (active != null) return initiation(order, active);

        var providerOrder = gateway.createOrder(order.totalAmount(), order.orderNumber(), order.id());
        if (providerOrder.amount() != order.totalAmount() || !"INR".equals(providerOrder.currency())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RAZORPAY_ORDER_MISMATCH",
                    "Razorpay returned an order with unexpected payment details");
        }
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO payments(order_id,idempotency_key,gateway,gateway_order_id,gateway_payment_id,
                    method,status,amount,currency,gateway_fee,gateway_response,initiated_at,completed_at)
                VALUES(:orderId,:key,'RAZORPAY',:gatewayOrderId,NULL,:method,'INITIATED',:amount,'INR',0,
                    :response,UTC_TIMESTAMP(),NULL)
                """, new MapSqlParameterSource().addValue("orderId", order.id()).addValue("key", key)
                .addValue("gatewayOrderId", providerOrder.id())
                .addValue("method", order.paymentMethod(), Types.VARCHAR)
                .addValue("amount", order.totalAmount()).addValue("response", json(providerOrder.raw())),
                keys, new String[]{"id"});
        return initiation(order, payment(keys.getKey().longValue()));
    }

    @Transactional
    public PaymentStatusResponse verify(long customerId, String orderNumber, RazorpayVerifyRequest request) {
        customers.requireActive(customerId);
        OrderPaymentRow order = lockOrder(customerId, orderNumber);
        PaymentRow payment = paymentByGatewayOrder(order.id(), request.razorpayOrderId());
        if (!RazorpaySignatures.verifyPayment(payment.gatewayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature(), requireSecret())) {
            throw ApiException.badRequest("INVALID_PAYMENT_SIGNATURE", "Razorpay payment signature verification failed");
        }
        return applyProviderPayment(order, payment, gateway.fetchPayment(request.razorpayPaymentId()));
    }

    @Transactional
    public PaymentStatusResponse reconcile(long customerId, String orderNumber) {
        customers.requireActive(customerId);
        return reconcileSystem(customerId, orderNumber);
    }

    @Transactional
    public PaymentStatusResponse reconcileSystem(long customerId, String orderNumber) {
        OrderPaymentRow order = lockOrder(customerId, orderNumber);
        PaymentRow payment = latestPayment(order.id());
        if (payment == null || payment.gatewayOrderId() == null) return status(order, payment);
        List<RazorpayGateway.ProviderPayment> providerPayments = gateway.fetchPaymentsForOrder(payment.gatewayOrderId());
        var selected = providerPayments.stream().filter(item -> "captured".equals(item.status())).findFirst()
                .orElseGet(() -> providerPayments.isEmpty() ? null : providerPayments.get(0));
        return selected == null ? status(order, payment) : applyProviderPayment(order, payment, selected);
    }

    public PaymentStatusResponse status(long customerId, String orderNumber) {
        customers.requireActive(customerId);
        OrderPaymentRow order = order(customerId, orderNumber, false);
        return status(order, latestPayment(order.id()));
    }

    private PaymentStatusResponse applyProviderPayment(OrderPaymentRow order, PaymentRow local,
                                                        RazorpayGateway.ProviderPayment provider) {
        if (!local.gatewayOrderId().equals(provider.orderId()) || provider.amount() != order.totalAmount()
                || !"INR".equals(provider.currency())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_DETAILS_MISMATCH",
                    "The Razorpay payment does not match this MoodBuds order");
        }
        String localStatus = switch (provider.status()) {
            case "captured" -> "SUCCESS";
            case "failed" -> "FAILED";
            default -> "PENDING";
        };
        String method = paymentMethod(provider.method(), order.paymentMethod());
        jdbc.sql("""
                UPDATE payments SET gateway_payment_id=:paymentId,method=:method,status=:status,
                    gateway_response=:response,completed_at=CASE WHEN :status='SUCCESS' THEN UTC_TIMESTAMP() ELSE NULL END
                WHERE id=:id
                """).param("paymentId", provider.id()).param("method", method, Types.VARCHAR)
                .param("status", localStatus).param("response", json(provider.raw()))
                .param("id", local.id()).update();
        if ("SUCCESS".equals(localStatus) && !"CONFIRMED".equals(order.status())) {
            if (!List.of("PENDING_PAYMENT", "PAYMENT_FAILED").contains(order.status())) {
                throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE",
                        "This order can no longer accept a payment");
            }
            jdbc.sql("""
                    UPDATE orders SET status='CONFIRMED',payment_method=:method,updated_at=UTC_TIMESTAMP() WHERE id=:id
                    """).param("method", method, Types.VARCHAR).param("id", order.id()).update();
            jdbc.sql("""
                    INSERT INTO order_status_history(order_id,from_status,to_status,notes,changed_by_id,created_at)
                    VALUES(:orderId,:fromStatus,'CONFIRMED','Razorpay captured payment verified',NULL,UTC_TIMESTAMP())
                    """).param("orderId", order.id()).param("fromStatus", order.status()).update();
            order = order.withStatus("CONFIRMED", method);
        }
        return status(order, payment(local.id()));
    }

    private void requirePayable(OrderPaymentRow order) {
        if (!"FINALIZED".equals(order.shippingPricingStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIPPING_PRICE_NOT_FINALIZED",
                    "Shipping price must be finalized before payment initiation");
        }
        if (!List.of("PENDING_PAYMENT", "PAYMENT_FAILED").contains(order.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "This order cannot accept a payment");
        }
        if (!order.paymentExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_WINDOW_EXPIRED",
                    "The payment window for this order has expired");
        }
        if ("COD".equals(order.paymentMethod())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COD_NOT_AVAILABLE",
                    "Cash on delivery does not use Razorpay");
        }
    }

    private RazorpayInitiationResponse initiation(OrderPaymentRow order, PaymentRow payment) {
        return new RazorpayInitiationResponse(order.orderNumber(), payment.id(), payment.gatewayOrderId(),
                gateway.publicKeyId(), payment.amount(), payment.currency(), payment.status(),
                properties.checkoutName(), properties.checkoutDescription() + " " + order.orderNumber(),
                new CheckoutPrefill(order.customerName(), order.email(), order.mobile()),
                new CheckoutTheme(properties.themeColor()), order.paymentExpiresAt());
    }

    private PaymentStatusResponse status(OrderPaymentRow order, PaymentRow payment) {
        if (payment == null) {
            return new PaymentStatusResponse(order.orderNumber(), order.status(), null, null, null,
                    order.paymentMethod(), "NOT_INITIATED", order.totalAmount(), "INR",
                    payable(order), null, null, order.paymentExpiresAt());
        }
        return new PaymentStatusResponse(order.orderNumber(), order.status(), payment.gateway(),
                payment.gatewayOrderId(), payment.gatewayPaymentId(), payment.method(), payment.status(),
                payment.amount(), payment.currency(), payable(order) && !"SUCCESS".equals(payment.status()),
                payment.initiatedAt(), payment.completedAt(), order.paymentExpiresAt());
    }

    private OrderPaymentRow lockOrder(long customerId, String orderNumber) {
        return order(customerId, orderNumber, true);
    }

    private OrderPaymentRow order(long customerId, String orderNumber, boolean lock) {
        return jdbc.sql("""
                SELECT o.id,o.order_number,o.status,o.payment_method,o.total_amount,o.payment_expires_at,
                       o.shipping_pricing_status,CONCAT(u.first_name,' ',u.last_name) customer_name,
                       u.email,u.mobile
                FROM orders o JOIN users u ON u.id=o.user_id
                WHERE o.user_id=:userId AND o.order_number=:orderNumber
                """ + (lock ? " FOR UPDATE" : "")).param("userId", customerId).param("orderNumber", orderNumber)
                .query((rs, rowNum) -> new OrderPaymentRow(rs.getLong("id"), rs.getString("order_number"),
                        rs.getString("status"), rs.getString("payment_method"), rs.getLong("total_amount"),
                        rs.getString("shipping_pricing_status"), rs.getString("customer_name"),
                        rs.getString("email"), rs.getString("mobile"),
                        rs.getTimestamp("payment_expires_at").toInstant())).optional()
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    private PaymentRow paymentByKey(long orderId, String key) {
        return jdbc.sql(paymentSql() + " WHERE order_id=:orderId AND idempotency_key=:key")
                .param("orderId", orderId).param("key", key).query((rs, rowNum) -> payment(rs)).optional().orElse(null);
    }

    private PaymentRow paymentByGatewayOrder(long orderId, String gatewayOrderId) {
        return jdbc.sql(paymentSql() + " WHERE order_id=:orderId AND gateway_order_id=:gatewayOrderId FOR UPDATE")
                .param("orderId", orderId).param("gatewayOrderId", gatewayOrderId)
                .query((rs, rowNum) -> payment(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Razorpay payment attempt"));
    }

    private PaymentRow activePayment(long orderId) {
        return jdbc.sql(paymentSql() + " WHERE order_id=:orderId AND status IN ('INITIATED','PENDING') ORDER BY id DESC LIMIT 1")
                .param("orderId", orderId).query((rs, rowNum) -> payment(rs)).optional().orElse(null);
    }

    private PaymentRow latestPayment(long orderId) {
        return jdbc.sql(paymentSql() + " WHERE order_id=:orderId ORDER BY id DESC LIMIT 1")
                .param("orderId", orderId).query((rs, rowNum) -> payment(rs)).optional().orElse(null);
    }

    private PaymentRow payment(long id) {
        return jdbc.sql(paymentSql() + " WHERE id=:id").param("id", id)
                .query((rs, rowNum) -> payment(rs)).single();
    }

    private String paymentSql() {
        return """
                SELECT id,order_id,idempotency_key,gateway,gateway_order_id,gateway_payment_id,method,
                       status,amount,currency,initiated_at,completed_at FROM payments
                """;
    }

    private PaymentRow payment(ResultSet rs) throws SQLException {
        return new PaymentRow(rs.getLong("id"), rs.getLong("order_id"), rs.getString("idempotency_key"),
                rs.getString("gateway"), rs.getString("gateway_order_id"), rs.getString("gateway_payment_id"),
                rs.getString("method"), rs.getString("status"), rs.getLong("amount"), rs.getString("currency"),
                rs.getTimestamp("initiated_at").toInstant(), nullableInstant(rs, "completed_at"));
    }

    private String requireSecret() {
        if (!properties.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RAZORPAY_NOT_CONFIGURED",
                    "Razorpay test credentials are not configured");
        }
        return properties.keySecret();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not store Razorpay response", exception); }
    }

    private static String paymentMethod(String providerMethod, String requestedMethod) {
        if (providerMethod == null) return requestedMethod;
        return switch (providerMethod.toLowerCase(Locale.ROOT)) {
            case "upi" -> "UPI";
            case "netbanking" -> "NET_BANKING";
            case "wallet" -> "WALLET";
            case "card" -> List.of("CREDIT_CARD", "DEBIT_CARD").contains(requestedMethod) ? requestedMethod : "CREDIT_CARD";
            default -> requestedMethod;
        };
    }

    private static boolean payableStatus(String status) {
        return List.of("PENDING_PAYMENT", "PAYMENT_FAILED").contains(status);
    }

    private static boolean payable(OrderPaymentRow order) {
        return payableStatus(order.status()) && order.paymentExpiresAt().isAfter(Instant.now());
    }

    private static Instant nullableInstant(ResultSet rs, String field) throws SQLException {
        var timestamp = rs.getTimestamp(field); return timestamp == null ? null : timestamp.toInstant();
    }

    private record PaymentRow(long id, long orderId, String idempotencyKey, String gateway,
                              String gatewayOrderId, String gatewayPaymentId, String method,
                              String status, long amount, String currency,
                              Instant initiatedAt, Instant completedAt) {}

    private record OrderPaymentRow(long id, String orderNumber, String status, String paymentMethod,
                                   long totalAmount, String shippingPricingStatus, String customerName,
                                   String email, String mobile, Instant paymentExpiresAt) {
        OrderPaymentRow withStatus(String newStatus, String newMethod) {
            return new OrderPaymentRow(id, orderNumber, newStatus, newMethod, totalAmount,
                    shippingPricingStatus, customerName, email, mobile, paymentExpiresAt);
        }
    }
}
