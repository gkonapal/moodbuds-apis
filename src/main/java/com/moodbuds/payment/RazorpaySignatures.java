package com.moodbuds.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RazorpaySignatures {
    private RazorpaySignatures() {}

    public static boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId,
                                        String receivedSignature, String keySecret) {
        byte[] expected = HexFormat.of().parseHex(sign(razorpayOrderId + "|" + razorpayPaymentId, keySecret));
        try {
            byte[] received = HexFormat.of().parseHex(receivedSignature);
            return MessageDigest.isEqual(expected, received);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }
}
