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
    public QuizHistory saveHistory(QuizHistoryRequest request) {
        log.info("Saving quiz history for deck: {}, score: {}%", request.getDeckTitle(), request.getScorePercentage());

        QuizHistory entity = QuizHistory.builder()
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
    public List<QuizHistory> getAllHistory() {
        return historyRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<QuizHistory> getHistoryById(Long id) {
        return historyRepository.findById(id);
    }

    @Transactional
    public boolean deleteHistory(Long id) {
        if (historyRepository.existsById(id)) {
            historyRepository.deleteById(id);
            log.info("Deleted quiz history with id: {}", id);
            return true;
        }
        return false;
    }

    @Transactional
    public void clearAllHistory() {
        historyRepository.deleteAll();
        log.info("Cleared all quiz history records.");
    }
}
