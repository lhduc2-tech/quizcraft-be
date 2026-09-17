package com.quizcraft.quiz_service;

import com.quizcraft.quiz_service.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QuizServiceApplication {

	public static void main(String[] args) {
		DotenvLoader.load();
		SpringApplication.run(QuizServiceApplication.class, args);
	}

}
