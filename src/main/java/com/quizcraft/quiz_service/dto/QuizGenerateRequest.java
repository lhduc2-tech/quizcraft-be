package com.quizcraft.quiz_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizGenerateRequest {

    @NotBlank(message = "Nội dung ghi chú không được để trống")
    @Size(min = 10, message = "Nội dung ghi chú tối thiểu 10 ký tự")
    private String note;

    @Builder.Default
    private Integer questionCount = 5;

    @Builder.Default
    private String difficulty = "Hiểu sâu";

    @Builder.Default
    private String playMode = "flashcard";
}
