package com.eglise.secretariat.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileDto(
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String currentPassword,

        String newUsername,

        String newPassword
) {}
