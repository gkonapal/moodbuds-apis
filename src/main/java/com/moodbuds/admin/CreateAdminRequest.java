package com.moodbuds.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(
        @NotBlank @Size(max=100) String username,
        @NotBlank @Size(min=10,max=200) String password,
        @NotBlank @Size(max=200) String fullName,
        @NotNull Long roleId,
        @Size(max=255) String email,
        @Size(max=15) String mobile) {}
