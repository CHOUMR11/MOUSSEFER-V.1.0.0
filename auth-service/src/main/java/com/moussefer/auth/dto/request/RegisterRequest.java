package com.moussefer.auth.dto.request;

import com.moussefer.auth.entity.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Self-service registration request.
 *
 * Accepted roles: PASSENGER, DRIVER only.
 * ORGANIZER and ADMIN accounts must be created by a SUPER_ADMIN through the
 * internal admin endpoint — they are not self-service.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit"
    )
    private String password;

    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Invalid phone number format")
    private String phoneNumber;

    /**
     * Only PASSENGER or DRIVER can self-register.
     * Server-side validation is enforced in AuthService.register().
     */
    @NotNull(message = "Role is required")
    private Role role;

    /**
     * Validates whether the requested role can be self-registered.
     * Returns true only for PASSENGER and DRIVER.
     */
    public boolean isRoleSelfRegistrable() {
        return role == Role.PASSENGER || role == Role.DRIVER;
    }
}
