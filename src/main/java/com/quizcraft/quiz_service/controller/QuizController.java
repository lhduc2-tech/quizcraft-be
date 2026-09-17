package com.quizcraft.quiz_service.controller;

import com.quizcraft.quiz_service.dto.QuizGenerateRequest;
import com.quizcraft.quiz_service.dto.QuizResponse;
import com.quizcraft.quiz_service.security.UserPrincipal;
import com.quizcraft.quiz_service.service.GeminiService;
import com.quizcraft.quiz_service.service.GuestRateLimitService;
import com.quizcraft.quiz_service.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/quiz", produces = "application/json;charset=UTF-8")
public class QuizController {

    private static final Logger log = LoggerFactory.getLogger(QuizController.class);

    private final GeminiService geminiService;
    private final GuestRateLimitService guestRateLimitService;

    public QuizController(GeminiService geminiService, GuestRateLimitService guestRateLimitService) {
        this.geminiService = geminiService;
        this.guestRateLimitService = guestRateLimitService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateQuiz(
            @Valid @RequestBody QuizGenerateRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        String clientIp = ClientIpUtil.getClientIp(httpRequest);
        boolean isGuest = (principal == null);

        log.info("Received quiz generate request: questionCount={}, difficulty={}, isGuest={}, ip={}, noteLength={}",
                request.getQuestionCount(),
                request.getDifficulty(),
                isGuest,
                clientIp,
                (request.getNote() != null ? request.getNote().length() : 0));

        // Rate limit for guest users by IP (Max 30 questions)
        int requestedCount = (request.getQuestionCount() != null && request.getQuestionCount() > 0)
                ? request.getQuestionCount()
                : 3;

        if (isGuest) {
            int currentCount = guestRateLimitService.getQuestionCount(clientIp);

            if (currentCount >= GuestRateLimitService.MAX_GUEST_QUESTIONS) {
                log.warn("Guest IP {} exceeded maximum limit (current={}, max=30)", clientIp, currentCount);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                        "error", "Đã đạt giới hạn câu hỏi",
                        "message", "Khách vãng lai tối đa chỉ được tạo 30 câu hỏi (bạn đã dùng 30/30 câu). Vui lòng đăng nhập để tạo không giới hạn!",
                        "requiresLogin", true,
                        "currentCount", currentCount,
                        "maxAllowed", GuestRateLimitService.MAX_GUEST_QUESTIONS,
                        "remaining", 0
                ));
            }

            if (currentCount + requestedCount > GuestRateLimitService.MAX_GUEST_QUESTIONS) {
                int remaining = Math.max(0, GuestRateLimitService.MAX_GUEST_QUESTIONS - currentCount);
                log.warn("Guest IP {} requested {} questions but only {} remaining", clientIp, requestedCount, remaining);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "error", "Vượt quá giới hạn câu hỏi",
                        "message", "Bạn chỉ còn tạo được " + remaining + " câu hỏi cho khách vãng lai (đã tạo " + currentCount + "/30 câu). Vui lòng chọn số câu ít hơn hoặc đăng nhập để tạo không giới hạn!",
                        "requiresLogin", true,
                        "currentCount", currentCount,
                        "maxAllowed", GuestRateLimitService.MAX_GUEST_QUESTIONS,
                        "remaining", remaining
                ));
            }
        }

        try {
            QuizResponse response = geminiService.generateQuiz(request);

            // Record question count for guest
            if (isGuest) {
                int actualCount = (response != null && response.getQuestions() != null && !response.getQuestions().isEmpty())
                        ? response.getQuestions().size()
                        : requestedCount;
                guestRateLimitService.recordQuestions(clientIp, actualCount);
                log.info("Recorded {} questions for guest IP {}", actualCount, clientIp);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Client error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Yêu cầu không hợp lệ",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Internal server error during quiz generation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Lỗi tạo câu hỏi",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/guest-limit")
    public ResponseEntity<Map<String, Object>> getGuestLimitStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        if (principal != null) {
            return ResponseEntity.ok(Map.of(
                    "isGuest", false,
                    "unlimited", true,
                    "message", "Người dùng đã đăng nhập có quyền tạo câu hỏi không giới hạn."
            ));
        }

        String clientIp = ClientIpUtil.getClientIp(httpRequest);
        int currentCount = guestRateLimitService.getQuestionCount(clientIp);
        int remaining = guestRateLimitService.getRemainingQuestions(clientIp);

        return ResponseEntity.ok(Map.of(
                "isGuest", true,
                "unlimited", false,
                "currentCount", currentCount,
                "maxAllowed", GuestRateLimitService.MAX_GUEST_QUESTIONS,
                "remaining", remaining
        ));
    }
}
