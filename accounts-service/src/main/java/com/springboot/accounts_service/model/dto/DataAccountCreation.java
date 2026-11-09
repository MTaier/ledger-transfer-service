package com.springboot.accounts_service.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DataAccountCreation(

        @NotBlank
        String ownerName,

        @NotBlank
        @Pattern(regexp = "^\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}$")
        String document) {
}
