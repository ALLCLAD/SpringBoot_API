package com.eglise.secretariat.auth;

import com.eglise.secretariat.auth.dto.AuthResponseDto;
import com.eglise.secretariat.auth.dto.LoginRequestDto;
import com.eglise.secretariat.auth.dto.UpdateProfileDto;
import com.eglise.secretariat.shared.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(adminUserRepository, jwtUtil, passwordEncoder);
    }

    @Test
    void testInitDefaultUserWhenTableIsEmpty() {
        when(adminUserRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("secretaire123")).thenReturn("encodedPassword");

        authService.initDefaultUserIfEmpty();

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserRepository, times(1)).save(captor.capture());
        AdminUser savedUser = captor.getValue();
        assertEquals("secretaire", savedUser.getUsername());
        assertEquals("encodedPassword", savedUser.getPassword());
        assertEquals("SECRETAIRE", savedUser.getRole());
    }

    @Test
    void testInitDefaultUserWhenTableIsNotEmpty() {
        when(adminUserRepository.count()).thenReturn(1L);

        authService.initDefaultUserIfEmpty();

        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void testLoginSuccess() {
        AdminUser user = new AdminUser("secretaire", "encodedHash", "SECRETAIRE");
        when(adminUserRepository.findByUsername("secretaire")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secretaire123", "encodedHash")).thenReturn(true);
        when(jwtUtil.generateToken("secretaire", "SECRETAIRE")).thenReturn("mocked-jwt-token");
        when(jwtUtil.getExpirationMs()).thenReturn(86400000L);

        LoginRequestDto request = new LoginRequestDto("secretaire", "secretaire123");
        AuthResponseDto response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked-jwt-token", response.token());
        assertEquals("SECRETAIRE", response.role());
    }

    @Test
    void testLoginBadCredentialsWrongPassword() {
        AdminUser user = new AdminUser("secretaire", "encodedHash", "SECRETAIRE");
        when(adminUserRepository.findByUsername("secretaire")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mauvais_pwd", "encodedHash")).thenReturn(false);

        LoginRequestDto request = new LoginRequestDto("secretaire", "mauvais_pwd");
        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void testLoginBadCredentialsUserNotFound() {
        when(adminUserRepository.findByUsername("inconnu")).thenReturn(Optional.empty());

        LoginRequestDto request = new LoginRequestDto("inconnu", "any");
        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void testUpdateProfileSuccess() {
        AdminUser user = new AdminUser("secretaire", "oldEncoded", "SECRETAIRE");
        when(adminUserRepository.findByUsername("secretaire")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secretaire123", "oldEncoded")).thenReturn(true);
        when(passwordEncoder.encode("nouveauPass123")).thenReturn("newEncoded");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.empty());

        UpdateProfileDto updateDto = new UpdateProfileDto("secretaire123", "admin", "nouveauPass123");
        authService.updateProfile("secretaire", updateDto);

        assertEquals("admin", user.getUsername());
        assertEquals("newEncoded", user.getPassword());
        verify(adminUserRepository).save(user);
    }
}
