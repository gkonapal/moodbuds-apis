package com.moodbuds.customer.api;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CustomerDtos {
    private CustomerDtos() {}

    public enum Gender { M, F, OTHER, UNSPECIFIED }
    public enum AddressType { HOME, WORK, OTHER }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must contain 10 to 15 digits, optionally prefixed by +") String mobile) {}

    public record CustomerLoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 72) String password) {}

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}
    public record LogoutRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    public record AuthResponse(String accessToken, String refreshToken, String tokenType,
                               Instant accessTokenExpiresAt, Instant refreshTokenExpiresAt,
                               CustomerProfile customer) {}

    public record CustomerProfile(long id, String email, boolean emailVerified, String mobile,
                                  boolean mobileVerified, String firstName, String lastName,
                                  LocalDate dateOfBirth, Gender gender, boolean active,
                                  Instant createdAt, Instant updatedAt) {}

    public record UpdateProfileRequest(
            @Size(min = 1, max = 100) String firstName,
            @Size(min = 1, max = 100) String lastName,
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must contain 10 to 15 digits, optionally prefixed by +") String mobile,
            @Past LocalDate dateOfBirth,
            Gender gender) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 10, max = 72) String newPassword) {}

    public record AddressRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must contain 10 to 15 digits, optionally prefixed by +") String mobile,
            @NotBlank @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Size(max = 100) String country,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{3,10}$", message = "must be a valid postal code") String pincode,
            AddressType addressType,
            boolean defaultAddress) {
        public AddressRequest {
            country = country == null || country.isBlank() ? "India" : country;
            addressType = addressType == null ? AddressType.HOME : addressType;
        }
    }

    public record AddressResponse(long id, String fullName, String mobile, String addressLine1,
                                  String addressLine2, String city, String state, String country,
                                  String pincode, AddressType addressType, boolean defaultAddress,
                                  Instant createdAt, Instant updatedAt) {}

    public record MessageResponse(String message) {}
}
