package com.moodbuds.admin;

import jakarta.validation.constraints.Size;

public record UpdateAdminRequest(@Size(max=200) String fullName, Long roleId,
                                 @Size(max=255) String email, @Size(max=15) String mobile, Boolean active) {}
