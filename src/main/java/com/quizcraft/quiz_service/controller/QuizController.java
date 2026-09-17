package com.quizcraft.quiz_service.controller;

import com.quizcraft.quiz_service.dto.QuizGenerateRequest;
import com.quizcraft.quiz_service.dto.QuizResponse;
import com.quizcraft.quiz_service.service.GeminiService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/quiz", produces = "application/json;charset=UTF-8")
public class QuizController {

    private static final Logger log = LoggerFactory.getLogger(QuizController.class);

    private final GeminiService geminiService;

    public QuizController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateQuiz(@Valid @RequestBody QuizGenerateRequest request) {
        log.info("Received quiz generate request: questionCount={}, difficulty={}, noteLength={}",
                request.getQuestionCount(),
                request.getDifficulty(),
                (request.getNote() != null ? request.getNote().length() : 0));

        try {
            QuizResponse response = geminiService.generateQuiz(request);
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
}
