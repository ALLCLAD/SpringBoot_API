package com.eglise.secretariat.auth;

import com.eglise.secretariat.auth.dto.AuthResponseDto;
import com.eglise.secretariat.auth.dto.LoginRequestDto;
import com.eglise.secretariat.auth.dto.UpdateProfileDto;
import com.eglise.secretariat.shared.security.JwtUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service gérant la logique d'authentification et d'émission des jetons JWT avec persistance en base.
 */
@Service
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.username:secretaire}")
    private String defaultUsername = "secretaire";

    @Value("${app.auth.password:secretaire123}")
    private String defaultPassword = "secretaire123";

    public AuthService(AdminUserRepository adminUserRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Initialisation au premier démarrage :
     * Si la table des utilisateurs est vide (première installation), on crée le compte par défaut.
     * Si des utilisateurs existent déjà (après modification par l'utilisateur), on ne touche à rien.
     */
    @PostConstruct
    @Transactional
    public void initDefaultUserIfEmpty() {
        if (adminUserRepository.count() == 0) {
            AdminUser defaultUser = new AdminUser(
                    defaultUsername,
                    passwordEncoder.encode(defaultPassword),
                    "SECRETAIRE"
            );
            adminUserRepository.save(defaultUser);
        }
    }

    public AuthResponseDto login(LoginRequestDto request) {
        String username = request.username();
        String password = request.password();

        AdminUser user = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Identifiant ou mot de passe incorrect"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Identifiant ou mot de passe incorrect");
        }

        String role = user.getRole();
        String token = jwtUtil.generateToken(user.getUsername(), role);

        return new AuthResponseDto(token, jwtUtil.getExpirationMs(), role);
    }

    @Transactional
    public void updateProfile(String currentUsername, UpdateProfileDto request) {
        // Récupération de l'utilisateur connecté
        AdminUser user = adminUserRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new BadCredentialsException("Utilisateur non trouvé"));

        // 1. Vérification obligatoire du mot de passe actuel
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Le mot de passe actuel est incorrect");
        }

        // 2. Modification du username si renseigné
        if (request.newUsername() != null && !request.newUsername().isBlank()) {
            // Si le nouvel identifiant est différent de l'actuel, vérifier qu'il n'est pas déjà pris
            if (!user.getUsername().equals(request.newUsername())
                    && adminUserRepository.findByUsername(request.newUsername()).isPresent()) {
                throw new IllegalArgumentException("Cet identifiant est déjà utilisé");
            }
            user.setUsername(request.newUsername());
        }

        // 3. Modification du mot de passe si renseigné
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.newPassword()));
        }

        adminUserRepository.save(user);
    }
}
