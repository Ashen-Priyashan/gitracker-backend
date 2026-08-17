package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "User registration request data")
public record SignUpRequest(

        @Schema(description = "Full name or username", example = "Ashen Priyashan")
        String name,

        @Schema(description = "User email address", example = "ashen@example.com")
        String email,

        @Schema(description = "Password")
        String password,

        @Schema(description = "Password confirmation")
        String confirmPassword

) implements Serializable {
}
