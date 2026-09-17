package com.quizcraft.quiz_service.repository;

import com.quizcraft.quiz_service.entity.QuizHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizHistoryRepository extends JpaRepository<QuizHistory, Long> {

    List<QuizHistory> findAllByOrderByCreatedAtDesc();

    List<QuizHistory> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    java.util.Optional<QuizHistory> findByIdAndUserId(Long id, Long userId);

    void deleteAllByUserId(Long userId);

    long countByUserId(Long userId);
}
