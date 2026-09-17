package com.quizcraft.quiz_service.service;

import com.quizcraft.quiz_service.dto.QuestionItem;
import com.quizcraft.quiz_service.dto.QuizGenerateRequest;
import com.quizcraft.quiz_service.dto.QuizResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${gemini.api-key:}")
    private String configuredApiKey;

    @Value("${gemini.model:gemini-3.6-flash}")
    private String modelName;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public QuizResponse generateQuiz(QuizGenerateRequest request) {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("API Key chưa được cấu hình. Vui lòng kiểm tra file .env hoặc biến môi trường API_KEY.");
        }

        int count = (request.getQuestionCount() != null && request.getQuestionCount() > 0)
                ? request.getQuestionCount()
                : 5;
        String difficulty = (request.getDifficulty() != null && !request.getDifficulty().trim().isEmpty())
                ? request.getDifficulty()
                : "Hiểu sâu";

        String systemPrompt = String.format(
                """
                Bạn là một người gia sư đồng hành truyền cảm hứng, chuyên gia phương pháp Active Recall & Spaced Repetition.
                Nhiệm vụ: Trích xuất các ý tưởng quan trọng từ ghi chép của người học và tạo đúng %d câu hỏi trắc nghiệm chất lượng cao.
                Độ khó: %s.

                YÊU CẦU ĐẶC BIỆT VỀ CHẤT LƯỢNG SƯ PHẠM:
                - Câu hỏi cần gợi mở tư duy, kiểm tra xem người học có hiểu nguyên lý bản chất hay không (không chỉ hỏi vẹt câu chữ).
                - Có đúng 4 phương án trắc nghiệm ngắn gọn, rõ ràng.
                - Phần "explanation": Giải thích cặn kẽ vì sao phương án được chọn là đúng và các phương án kia chưa chuẩn.
                - Phần "memoryHook": Một mẹo ghi nhớ ngắn hoặc câu ẩn dụ dân dã (1-2 câu) giúp nhớ mãi kiến thức này.
                - Phần "topic": Đặt tên tiêu đề bài học trang nhã, dưới 6 từ.

                Định dạng đầu ra là JSON Schema chuẩn.
                """,
                count,
                difficulty
        );

        String userPrompt = "Nội dung ghi chép của học viên:\n\"\"\"\n" + request.getNote().trim() + "\n\"\"\"";

        Map<String, Object> payload = buildGeminiPayload(userPrompt, systemPrompt);

        List<String> modelsToTry = new ArrayList<>();
        if (modelName != null && !modelName.trim().isEmpty()) {
            modelsToTry.add(modelName.trim());
        }
        for (String fallbackModel : List.of("gemini-3.5-flash", "gemini-flash-latest", "gemini-3.1-flash-lite")) {
            if (!modelsToTry.contains(fallbackModel)) {
                modelsToTry.add(fallbackModel);
            }
        }

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tuần tự hóa dữ liệu gửi tới Gemini: " + e.getMessage(), e);
        }

        Exception lastException = null;
        for (String currentModel : modelsToTry) {
            int maxRetries = 2;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    String url = String.format(
                            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                            currentModel,
                            apiKey
                    );

                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(60))
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                            .build();

                    log.info("Sending request to Gemini model: {} (attempt {}/{}) for quiz generation", currentModel, attempt, maxRetries);
                    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    int status = response.statusCode();
                    if (status == 200) {
                        JsonNode root = objectMapper.readTree(response.body());
                        JsonNode candidates = root.path("candidates");
                        if (candidates.isEmpty()) {
                            throw new RuntimeException("Gemini không trả về kết quả khả dụng.");
                        }

                        JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                        if (textNode.isMissingNode() || textNode.asText().trim().isEmpty()) {
                            throw new RuntimeException("Phản hồi từ Gemini không có nội dung văn bản hợp lệ.");
                        }

                        String jsonText = textNode.asText().trim();
                        log.info("Successfully received response from Gemini model: {}", currentModel);
                        return parseQuizResponse(jsonText);
                    }

                    log.warn("Gemini API model {} returned HTTP {}: {}", currentModel, status, response.body());

                    // Retry on 503 (High demand / Service Unavailable) or 429 (Rate limit)
                    if ((status == 503 || status == 429) && attempt < maxRetries) {
                        long backoffMs = 1200L * attempt;
                        log.info("Temporary spike ({}), retrying model {} in {} ms...", status, currentModel, backoffMs);
                        Thread.sleep(backoffMs);
                        continue;
                    }

                    // Otherwise fall through to try next model
                    lastException = new RuntimeException("Gemini API error (" + currentModel + " HTTP " + status + "): " + response.body());
                    break;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Tiến trình bị gián đoạn khi gọi Gemini API", e);
                } catch (Exception e) {
                    log.warn("Attempt {} with model {} failed: {}", attempt, currentModel, e.getMessage());
                    lastException = e;
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Tiến trình bị gián đoạn", ie);
                        }
                    }
                }
            }
            log.info("Model {} unavailable or overloaded, attempting fallback to next model in pool...", currentModel);
        }

        throw new RuntimeException("Tất cả các model Gemini đều đang quá tải hoặc gặp sự cố: " +
                (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }

    private Map<String, Object> buildGeminiPayload(String userPrompt, String systemPrompt) {
        Map<String, Object> payload = new LinkedHashMap<>();

        // contents
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> userContent = new LinkedHashMap<>();
        userContent.put("role", "user");
        userContent.put("parts", List.of(Map.of("text", userPrompt)));
        contents.add(userContent);
        payload.put("contents", contents);

        // systemInstruction
        payload.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
        ));

        // generationConfig with strict responseSchema
        Map<String, Object> questionItemProps = new LinkedHashMap<>();
        questionItemProps.put("question", Map.of("type", "STRING"));
        questionItemProps.put("options", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
        questionItemProps.put("correctIndex", Map.of("type", "INTEGER"));
        questionItemProps.put("explanation", Map.of("type", "STRING"));
        questionItemProps.put("memoryHook", Map.of("type", "STRING"));

        Map<String, Object> questionsSchema = new LinkedHashMap<>();
        questionsSchema.put("type", "ARRAY");
        questionsSchema.put("items", Map.of(
                "type", "OBJECT",
                "properties", questionItemProps,
                "required", List.of("question", "options", "correctIndex", "explanation", "memoryHook")
        ));

        Map<String, Object> schemaProps = new LinkedHashMap<>();
        schemaProps.put("topic", Map.of("type", "STRING"));
        schemaProps.put("questions", questionsSchema);

        Map<String, Object> responseSchema = new LinkedHashMap<>();
        responseSchema.put("type", "OBJECT");
        responseSchema.put("properties", schemaProps);
        responseSchema.put("required", List.of("topic", "questions"));

        payload.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", responseSchema
        ));

        return payload;
    }

    private QuizResponse parseQuizResponse(String jsonText) throws IOException {
        JsonNode parsed = objectMapper.readTree(jsonText);
        String topic = parsed.path("topic").asText("Bộ câu hỏi ôn tập");

        List<QuestionItem> questionList = new ArrayList<>();
        JsonNode questionsNode = parsed.path("questions");
        if (questionsNode.isArray()) {
            for (JsonNode qNode : questionsNode) {
                List<String> options = new ArrayList<>();
                JsonNode optsNode = qNode.path("options");
                if (optsNode.isArray()) {
                    for (JsonNode opt : optsNode) {
                        options.add(opt.asText());
                    }
                }

                QuestionItem item = QuestionItem.builder()
                        .question(qNode.path("question").asText(""))
                        .options(options)
                        .correctIndex(qNode.path("correctIndex").asInt(0))
                        .explanation(qNode.path("explanation").asText(""))
                        .memoryHook(qNode.path("memoryHook").asText(""))
                        .build();

                questionList.add(item);
            }
        }

        return QuizResponse.builder()
                .topic(topic)
                .questions(questionList)
                .build();
    }

    private String resolveApiKey() {
        if (configuredApiKey != null && !configuredApiKey.trim().isEmpty()) {
            return configuredApiKey.trim();
        }

        String sysProp = System.getProperty("API_KEY");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }

        String envVar = System.getenv("API_KEY");
        if (envVar != null && !envVar.trim().isEmpty()) {
            return envVar.trim();
        }

        // Direct fallback scan of .env file
        File[] candidateFiles = new File[] {
                new File(".env"),
                new File("quiz-service/.env"),
                new File("../.env")
        };
        for (File file : candidateFiles) {
            if (file.exists() && file.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.startsWith("API_KEY=")) {
                            String val = line.substring("API_KEY=".length()).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) ||
                                (val.startsWith("'") && val.endsWith("'"))) {
                                if (val.length() >= 2) {
                                    val = val.substring(1, val.length() - 1);
                                }
                            }
                            return val;
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }

        return null;
    }
}
