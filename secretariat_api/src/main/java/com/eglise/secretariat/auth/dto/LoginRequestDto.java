package com.eglise.secretariat.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank(message = "L'identifiant est obligatoire")
        String username,

        @NotBlank(message = "Le mot de passe est obligatoire")
        String password
) {}
