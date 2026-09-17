package com.quizcraft.quiz_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DotenvLoader {

    private static final Logger log = LoggerFactory.getLogger(DotenvLoader.class);

    public static void load() {
        File[] possibleFiles = new File[] {
                new File(".env"),
                new File("quiz-service/.env"),
                new File("../.env")
        };

        File envFile = null;
        for (File file : possibleFiles) {
            if (file.exists() && file.isFile()) {
                envFile = file;
                break;
            }
        }

        if (envFile == null) {
            log.info("No .env file found in candidate locations, relying on system environment variables.");
            return;
        }

        log.info("Loading environment variables from: {}", envFile.getAbsolutePath());
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String key = line.substring(0, equalIndex).trim();
                    String value = line.substring(equalIndex + 1).trim();

                    // Strip surrounding single or double quotes
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        if (value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                    }

                    // Only set if not already present in System properties or System env
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read .env file at {}: {}", envFile.getAbsolutePath(), e.getMessage());
        }
    }
}
