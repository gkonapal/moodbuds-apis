package com.moodbuds.customer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;

@Service
public class CustomerTokenService {
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenPair create() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        return new TokenPair(raw, hash(raw));
    }

    public String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public boolean matches(String raw, String storedHash) {
        if (raw == null || storedHash == null) return false;
        return MessageDigest.isEqual(hash(raw).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    public record TokenPair(String raw, String hash) {}
}
