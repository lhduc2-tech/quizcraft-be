package com.quizcraft.quiz_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizcraft.quiz_service.dto.*;
import com.quizcraft.quiz_service.entity.User;
import com.quizcraft.quiz_service.repository.UserRepository;
import com.quizcraft.quiz_service.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisBlacklistService redisBlacklistService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập '" + username + "' đã tồn tại. Vui lòng chọn tên khác!");
        }

        String email = request.getEmail() != null && !request.getEmail().trim().isEmpty()
                ? request.getEmail().trim().toLowerCase()
                : null;

        if (email != null && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email '" + email + "' đã được đăng ký!");
        }

        String fullName = request.getFullName() != null && !request.getFullName().trim().isEmpty()
                ? request.getFullName().trim()
                : username;

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(fullName)
                .authProvider("LOCAL")
                .build();

        user = userRepository.save(user);
        String token = tokenProvider.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(UserResponse.fromEntity(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String usernameOrEmail = request.getUsername().trim();

        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail.toLowerCase()))
                .orElseThrow(() -> new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác!"));

        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BadCredentialsException("Tài khoản này đã bị xóa hoặc vô hiệu hóa!");
        }

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác!");
        }

        String token = tokenProvider.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(UserResponse.fromEntity(user))
                .build();
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        String idToken = request.getIdToken().trim();

        try {
            // Verify token with Google TokenInfo API
            String verifyUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(verifyUrl))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Google token verification failed with HTTP {}: {}", response.statusCode(), response.body());
                throw new IllegalArgumentException("Mã xác thực Google không hợp lệ hoặc đã hết hạn!");
            }

            JsonNode payload = objectMapper.readTree(response.body());

            String googleId = payload.path("sub").asText();
            String email = payload.path("email").asText();
            String name = payload.path("name").asText(email);
            String picture = payload.path("picture").asText(null);

            if (email == null || email.trim().isEmpty()) {
                throw new IllegalArgumentException("Không thể lấy email từ tài khoản Google!");
            }

            // Find existing user by googleId or email
            User user = userRepository.findByGoogleId(googleId)
                    .or(() -> userRepository.findByEmail(email.toLowerCase()))
                    .orElse(null);

            if (user != null && user.getIsDeleted() != null && user.getIsDeleted() == 1) {
                throw new IllegalArgumentException("Tài khoản này đã bị xóa hoặc vô hiệu hóa!");
            }

            if (user == null) {
                // Create new Google User
                String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
                if (baseUsername.length() < 3) baseUsername = "user_" + baseUsername;
                String finalUsername = baseUsername;
                int suffix = 1;
                while (userRepository.existsByUsername(finalUsername)) {
                    finalUsername = baseUsername + "_" + suffix++;
                }

                user = User.builder()
                        .username(finalUsername)
                        .email(email.toLowerCase())
                        .fullName(name)
                        .avatarUrl(picture)
                        .authProvider("GOOGLE")
                        .googleId(googleId)
                        .isDeleted(0)
                        .build();

                user = userRepository.save(user);
                log.info("Created new user via Google OAuth: username={}, email={}", user.getUsername(), user.getEmail());
            } else {
                // Update profile details
                boolean updated = false;
                if (user.getGoogleId() == null) {
                    user.setGoogleId(googleId);
                    updated = true;
                }
                if (picture != null && !picture.equals(user.getAvatarUrl())) {
                    user.setAvatarUrl(picture);
                    updated = true;
                }
                if (name != null && !name.equals(user.getFullName())) {
                    user.setFullName(name);
                    updated = true;
                }
                if (updated) {
                    user = userRepository.save(user);
                }
            }

            String token = tokenProvider.generateToken(user);

            return AuthResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .user(UserResponse.fromEntity(user))
                    .build();

        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Exception e) {
            log.error("Google authentication error: {}", e.getMessage(), e);
            throw new RuntimeException("Xác thực qua Google thất bại: " + e.getMessage());
        }
    }

    public void logout(String token) {
        if (token != null && !token.trim().isEmpty()) {
            long remainingSeconds = tokenProvider.getRemainingSeconds(token);
            redisBlacklistService.blacklistToken(token, remainingSeconds);
        }
    }

    @Transactional
    public void softDeleteUser(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + userId));

        user.setIsDeleted(1);
        userRepository.save(user);
        log.info("Soft deleted user: id={}, username={}", user.getId(), user.getUsername());

        if (token != null && !token.trim().isEmpty()) {
            logout(token);
        }
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + userId));
        return UserResponse.fromEntity(user);
    }
}
