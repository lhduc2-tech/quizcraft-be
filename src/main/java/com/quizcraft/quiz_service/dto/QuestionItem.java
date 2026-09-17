package com.quizcraft.quiz_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionItem {
    private String question;
    private List<String> options;
    private Integer correctIndex;
    private String explanation;
    private List<String> optionExplanations;
    private String memoryHook;
}
