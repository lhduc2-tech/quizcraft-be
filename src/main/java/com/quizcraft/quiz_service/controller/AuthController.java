package com.quizcraft.quiz_service.controller;

import com.quizcraft.quiz_service.dto.*;
import com.quizcraft.quiz_service.security.JwtAuthenticationFilter;
import com.quizcraft.quiz_service.security.UserPrincipal;
import com.quizcraft.quiz_service.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String token = JwtAuthenticationFilter.getJwtFromRequest(request);
        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Đã đăng xuất và vô hiệu hóa token thành công"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserResponse response = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getPublicAuthConfig() {
        String googleClientId = System.getProperty("GOOGLE_CLIENT_ID");
        if (googleClientId == null || googleClientId.trim().isEmpty()) {
            googleClientId = System.getenv("GOOGLE_CLIENT_ID");
        }
        return ResponseEntity.ok(Map.of(
                "googleClientId", googleClientId != null ? googleClientId.trim() : ""
        ));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, Object>> deleteCurrentAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = JwtAuthenticationFilter.getJwtFromRequest(request);
        authService.softDeleteUser(principal.getId(), token);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã xóa mềm tài khoản thành công. Tài khoản này sẽ không thể đăng nhập được nữa."
        ));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> softDeleteUserById(
            @PathVariable("id") Long id,
            HttpServletRequest request
    ) {
        String token = JwtAuthenticationFilter.getJwtFromRequest(request);
        authService.softDeleteUser(id, token);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã xóa mềm tài khoản ID " + id + " thành công."
        ));
    }
}
