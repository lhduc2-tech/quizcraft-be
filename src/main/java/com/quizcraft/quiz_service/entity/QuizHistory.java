package com.quizcraft.quiz_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "deck_title", nullable = false)
    private String deckTitle;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "play_mode")
    private String playMode;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "wrong_count")
    private Integer wrongCount;

    @Column(name = "score_percentage")
    private Integer scorePercentage;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(name = "note_snippet", columnDefinition = "TEXT")
    private String noteSnippet;

    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
