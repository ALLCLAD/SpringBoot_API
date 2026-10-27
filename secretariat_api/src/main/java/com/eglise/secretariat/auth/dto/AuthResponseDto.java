package com.eglise.secretariat.auth.dto;

public record AuthResponseDto(
        String token,
        long expiresIn,
        String role
) {}
