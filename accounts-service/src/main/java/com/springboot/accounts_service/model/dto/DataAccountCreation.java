package com.springboot.accounts_service.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DataAccountCreation(

        @NotBlank
        @Size(max = 150)
        String ownerName,

        @NotBlank
        @Pattern(regexp = "^\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}$")
        String document) {
}
