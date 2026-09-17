package com.quizcraft.quiz_service.service;

import com.quizcraft.quiz_service.dto.QuizHistoryRequest;
import com.quizcraft.quiz_service.entity.QuizHistory;
import com.quizcraft.quiz_service.repository.QuizHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class QuizHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QuizHistoryService.class);

    private final QuizHistoryRepository historyRepository;

    public QuizHistoryService(QuizHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Transactional
    public QuizHistory saveHistory(QuizHistoryRequest request, Long userId) {
        log.info("Saving quiz history for userId: {}, deck: {}, score: {}%", userId, request.getDeckTitle(), request.getScorePercentage());

        QuizHistory entity = QuizHistory.builder()
                .userId(userId)
                .deckTitle(request.getDeckTitle())
                .difficulty(request.getDifficulty())
                .playMode(request.getPlayMode())
                .totalQuestions(request.getTotalQuestions())
                .correctCount(request.getCorrectCount())
                .wrongCount(request.getWrongCount())
                .scorePercentage(request.getScorePercentage())
                .timeSpentSeconds(request.getTimeSpentSeconds())
                .noteSnippet(request.getNoteSnippet())
                .detailJson(request.getDetailJson())
                .createdAt(LocalDateTime.now())
                .build();

        return historyRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<QuizHistory> getAllHistory(Long userId) {
        if (userId != null) {
            return historyRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        }
        return historyRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<QuizHistory> getHistoryById(Long id, Long userId) {
        if (userId != null) {
            return historyRepository.findByIdAndUserId(id, userId);
        }
        return historyRepository.findById(id);
    }

    @Transactional
    public boolean deleteHistory(Long id, Long userId) {
        if (userId != null) {
            Optional<QuizHistory> record = historyRepository.findByIdAndUserId(id, userId);
            if (record.isPresent()) {
                historyRepository.delete(record.get());
                log.info("Deleted quiz history id {} for userId {}", id, userId);
                return true;
            }
            return false;
        }

        if (historyRepository.existsById(id)) {
            historyRepository.deleteById(id);
            log.info("Deleted quiz history with id: {}", id);
            return true;
        }
        return false;
    }

    @Transactional
    public void clearAllHistory(Long userId) {
        if (userId != null) {
            historyRepository.deleteAllByUserId(userId);
            log.info("Cleared all quiz history records for userId: {}", userId);
        } else {
            historyRepository.deleteAll();
            log.info("Cleared all quiz history records.");
        }
    }
}
