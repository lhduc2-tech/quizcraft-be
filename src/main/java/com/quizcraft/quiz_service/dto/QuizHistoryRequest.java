package com.quizcraft.quiz_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizHistoryRequest {

    @NotBlank(message = "Tiêu đề bài ôn tập không được để trống")
    private String deckTitle;

    private String difficulty;
    private String playMode;
    private Integer totalQuestions;
    private Integer correctCount;
    private Integer wrongCount;
    private Integer scorePercentage;
    private Integer timeSpentSeconds;
    private String noteSnippet;
    private String detailJson;
}
