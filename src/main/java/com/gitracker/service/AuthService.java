package com.gitracker.service;

import com.gitracker.dto.LoginRequest;
import com.gitracker.dto.SignUpRequest;
import com.gitracker.dto.UserAuthResponse;
import com.gitracker.entity.UserEntity;
import com.gitracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    public static final String SESSION_USER_ID_KEY = "GITRACKER_USER_ID";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAuthResponse signUp(SignUpRequest request, HttpServletRequest httpRequest) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (request.email() == null || request.email().isBlank() || !request.email().contains("@")) {
            throw new IllegalArgumentException("Valid email address is required");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        UserEntity user = UserEntity.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .build();

        UserEntity savedUser = userRepository.save(user);

        establishAuthenticatedSession(httpRequest, savedUser.getId());

        return mapToUserAuthResponse(savedUser);
    }

    public UserAuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<UserEntity> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty() || !passwordEncoder.matches(request.password(), userOpt.get().getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        UserEntity user = userOpt.get();

        establishAuthenticatedSession(httpRequest, user.getId());

        return mapToUserAuthResponse(user);
    }

    private void establishAuthenticatedSession(HttpServletRequest httpRequest, Long userId) {
        if (httpRequest == null) {
            return;
        }
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            httpRequest.changeSessionId();
        } else {
            session = httpRequest.getSession(true);
        }
        session.setAttribute(SESSION_USER_ID_KEY, userId);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public UserAuthResponse getCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute(SESSION_USER_ID_KEY);
        if (userId == null) {
            return null;
        }

        return userRepository.findById(userId)
                .map(this::mapToUserAuthResponse)
                .orElse(null);
    }

    @Transactional
    public UserAuthResponse linkGithubUsername(String rawUsername, HttpSession session) {
        Long userId = (Long) session.getAttribute(SESSION_USER_ID_KEY);
        if (userId == null) {
            throw new IllegalStateException("User is not authenticated");
        }

        if (rawUsername == null || rawUsername.isBlank()) {
            throw new IllegalArgumentException("GitHub username cannot be empty");
        }

        String cleanedUsername = rawUsername.trim().replace("@", "");
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        user.setGithubUsername(cleanedUsername);
        UserEntity savedUser = userRepository.save(user);

        session.setAttribute("CONNECTED_GITHUB_USERNAME", cleanedUsername);

        return mapToUserAuthResponse(savedUser);
    }

    public UserAuthResponse mapToUserAuthResponse(UserEntity user) {
        boolean hasConnection = user.getGithubConnection() != null && user.getGithubConnection().getAccessToken() != null;
        boolean hasUsername = user.getGithubUsername() != null && !user.getGithubUsername().isBlank();
        boolean connected = hasConnection || hasUsername;

        String githubUsername = null;
        if (hasUsername) {
            githubUsername = user.getGithubUsername();
        } else if (hasConnection) {
            githubUsername = user.getGithubConnection().getUsername();
        }

        String githubAvatarUrl = null;
        if (hasConnection && user.getGithubConnection().getAvatarUrl() != null) {
            githubAvatarUrl = user.getGithubConnection().getAvatarUrl();
        } else if (githubUsername != null) {
            githubAvatarUrl = "https://github.com/" + githubUsername + ".png";
        }

        return new UserAuthResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                connected,
                githubUsername,
                githubAvatarUrl,
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );
    }
}
