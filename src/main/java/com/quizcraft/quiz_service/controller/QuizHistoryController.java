package com.quizcraft.quiz_service.controller;

import com.quizcraft.quiz_service.dto.QuizHistoryRequest;
import com.quizcraft.quiz_service.entity.QuizHistory;
import com.quizcraft.quiz_service.service.QuizHistoryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/history", produces = "application/json;charset=UTF-8")
public class QuizHistoryController {

    private static final Logger log = LoggerFactory.getLogger(QuizHistoryController.class);

    private final QuizHistoryService historyService;

    public QuizHistoryController(QuizHistoryService historyService) {
        this.historyService = historyService;
    }

    @PostMapping
    public ResponseEntity<?> saveHistory(@Valid @RequestBody QuizHistoryRequest request) {
        try {
            QuizHistory saved = historyService.saveHistory(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Failed to save quiz history: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Lỗi lưu lịch sử",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping
    public ResponseEntity<List<QuizHistory>> getAllHistory() {
        return ResponseEntity.ok(historyService.getAllHistory());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getHistoryById(@PathVariable Long id) {
        return historyService.getHistoryById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHistory(@PathVariable Long id) {
        boolean deleted = historyService.deleteHistory(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Đã xóa bản ghi lịch sử", "id", id));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy bản ghi"));
    }

    @DeleteMapping
    public ResponseEntity<?> clearAllHistory() {
        historyService.clearAllHistory();
        return ResponseEntity.ok(Map.of("message", "Đã xóa toàn bộ lịch sử bài làm"));
    }
}
