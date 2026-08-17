package com.gitracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "User login credentials")
public record LoginRequest(

        @Schema(description = "User email address", example = "ashen@example.com")
        String email,

        @Schema(description = "Password")
        String password

) implements Serializable {
}
