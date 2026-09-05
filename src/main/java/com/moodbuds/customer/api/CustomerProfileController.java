package com.moodbuds.customer.api;

import static com.moodbuds.customer.api.CustomerDtos.*;

import java.util.List;

import com.moodbuds.customer.CurrentCustomer;
import com.moodbuds.customer.CustomerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer")
@Tag(name = "Customer Profile")
public class CustomerProfileController {
    private final CustomerProfileService profiles;

    public CustomerProfileController(CustomerProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get the authenticated customer profile")
    CustomerProfile profile(@AuthenticationPrincipal Jwt jwt) {
        return profiles.profile(CurrentCustomer.id(jwt));
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update editable customer profile fields")
    CustomerProfile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(CurrentCustomer.id(jwt), request);
    }

    @PutMapping("/profile/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change password and revoke other customer sessions")
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        profiles.changePassword(CurrentCustomer.id(jwt), CurrentCustomer.sessionId(jwt), request);
    }

    @DeleteMapping("/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate the customer account and revoke all sessions")
    void deactivate(@AuthenticationPrincipal Jwt jwt) {
        profiles.deactivate(CurrentCustomer.id(jwt));
    }

    @GetMapping("/addresses")
    @Operation(summary = "List active customer addresses")
    List<AddressResponse> addresses(@AuthenticationPrincipal Jwt jwt) {
        return profiles.addresses(CurrentCustomer.id(jwt));
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a customer address")
    AddressResponse createAddress(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        return profiles.createAddress(CurrentCustomer.id(jwt), request);
    }

    @GetMapping("/addresses/{addressId}")
    @Operation(summary = "Get one customer-owned address")
    AddressResponse address(@AuthenticationPrincipal Jwt jwt, @PathVariable long addressId) {
        return profiles.address(CurrentCustomer.id(jwt), addressId);
    }

    @PutMapping("/addresses/{addressId}")
    @Operation(summary = "Replace a customer-owned address")
    AddressResponse updateAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable long addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return profiles.updateAddress(CurrentCustomer.id(jwt), addressId, request);
    }

    @PutMapping("/addresses/{addressId}/default")
    @Operation(summary = "Make a customer-owned address the default")
    AddressResponse makeDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable long addressId) {
        return profiles.makeDefault(CurrentCustomer.id(jwt), addressId);
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a customer-owned address")
    void deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable long addressId) {
        profiles.deleteAddress(CurrentCustomer.id(jwt), addressId);
    }
}
