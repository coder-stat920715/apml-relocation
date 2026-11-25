package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.CoordinatorRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;

public final class CoordinatorDto {
    private CoordinatorDto() {}

    public record Request(
            @NotNull CoordinatorRole role,
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter branch code") String branchCode,
            @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "must be a valid 10-digit mobile number") String phoneNumber) {}

    /** The phone number is always masked: no caller is authenticated as hub staff. */
    public record Response(CoordinatorRole role, String name, String branchCode, String maskedPhoneNumber,
                           LocalDateTime assignedAt) {}
}
