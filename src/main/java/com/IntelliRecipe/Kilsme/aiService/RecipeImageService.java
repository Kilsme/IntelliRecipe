package com.IntelliRecipe.Kilsme.aiService;

import com.IntelliRecipe.Kilsme.model.Recipes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class RecipeImageService {

    @Value("${langchain4j.open-ai.image-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.image-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.image-model.generations-url:}")
    private String generationsUrl;

    @Value("${langchain4j.open-ai.image-model.dashscope-url:https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis}")
    private String dashscopeUrl;

    @Value("${langchain4j.open-ai.image-model.dashscope-task-url-template:https://dashscope.aliyuncs.com/api/v1/tasks/%s}")
    private String dashscopeTaskUrlTemplate;

    @Value("${image.generation.max-polls:10}")
    private int maxPolls;

    @Value("${image.generation.poll-interval-ms:1500}")
    private long pollIntervalMs;

    @Value("${langchain4j.open-ai.image-model.model-name:qwen-image-2.0}")
    private String modelName;

    @Value("${image.generation.dashscope-model:wanx2.1-t2i-turbo}")
    private String dashscopeModel;

    @Value("${image.generation.enable-openai-compatible:false}")
    private boolean enableOpenAiCompatible;

    @Value("${image.storage-dir:${user.dir}/data/recipe-images}")
    private String storageDir;

    @Value("${image.fallback.pollinations-enabled:true}")
    private boolean pollinationsEnabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateAndSave(Recipes recipe, String prompt) {
        String promptText = (prompt == null || prompt.isBlank())
                ? "一道美味的菜肴，实拍图：" + recipe.getTitle() + "。盘子干净，美食摄影，高清光影绝佳。"
                : prompt;

        try {
            return generateViaDashScopeNative(promptText, recipe.getId());
        } catch (Exception e) {
            System.err.println("[RecipeImageService] Error generating image via DashScope native API: " + e.getMessage());
        }

        if (enableOpenAiCompatible) {
            try {
                String apiUrl = resolveImageGenerationUrl();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);

                String requestJson = String.format(
                    "{\"model\": \"%s\", \"prompt\": \"%s\", \"n\": 1, \"size\": \"1024x1024\", \"response_format\": \"url\"}",
                    modelName,
                    promptText.replace("\"", "\\\"").replace("\n", " ")
                );

                HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
                return extractAndSaveImageFromJson(response.getBody(), recipe.getId());

            } catch (Exception e) {
                System.err.println("[RecipeImageService] Error generating image via OpenAI-compatible API: " + e.getMessage());
            }
        }

        if (pollinationsEnabled) {
            return fallbackGenerateAndSave(promptText, recipe.getId());
        }

        return "/recipe-images/default-dish.svg";
    }

    private String resolveImageGenerationUrl() {
        if (generationsUrl != null && !generationsUrl.isBlank()) {
            return generationsUrl.trim();
        }

        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.endsWith("/images/generations")) {
            return normalized;
        }
        if (normalized.endsWith("/")) {
            return normalized + "images/generations";
        }
        return normalized + "/images/generations";
    }

    private String generateViaDashScopeNative(String promptText, Long recipeId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.add("X-DashScope-Async", "enable");

        String cleanPrompt = promptText.replaceAll("\\s+", " ").trim();
        if (cleanPrompt.length() > 220) {
            cleanPrompt = cleanPrompt.substring(0, 220);
        }
        Map<String, Object> payload = Map.of(
                "model", dashscopeModel,
                "input", Map.of("prompt", cleanPrompt),
                "parameters", Map.of("size", "1024*1024", "n", 1)
        );
        String requestJson = objectMapper.writeValueAsString(payload);

        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(dashscopeUrl, entity, String.class);
        } catch (HttpStatusCodeException ex) {
            throw new RuntimeException("DashScope native HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
        }

        try {
            return extractAndSaveImageFromJson(response.getBody(), recipeId);
        } catch (Exception ignored) {
        }


        JsonNode root = objectMapper.readTree(response.getBody());
        String taskId = root.path("output").path("task_id").asText("");
        if (taskId.isBlank()) {
            throw new RuntimeException("DashScope native API returned no task_id and no direct image payload.");
        }
        return pollDashScopeTaskAndSave(taskId, recipeId);
    }

    private String pollDashScopeTaskAndSave(String taskId, Long recipeId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);

        for (int i = 0; i < Math.max(1, maxPolls); i++) {
            String taskUrl = String.format(dashscopeTaskUrlTemplate, taskId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response;
            try {
                response = restTemplate.exchange(taskUrl, HttpMethod.GET, entity, String.class);
            } catch (HttpStatusCodeException ex) {
                throw new RuntimeException("DashScope task poll HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("output").path("task_status").asText("");
            if ("SUCCEEDED".equalsIgnoreCase(status)) {
                return extractAndSaveImageFromJson(response.getBody(), recipeId);
            }
            if ("FAILED".equalsIgnoreCase(status)) {
                throw new RuntimeException("DashScope task failed: " + response.getBody());
            }

            Thread.sleep(Math.max(200, pollIntervalMs));
        }

        throw new RuntimeException("DashScope task timeout: " + taskId);
    }

    private String extractAndSaveImageFromJson(String json, Long recipeId) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        String imageUrl = findFirstText(root,
                "/data/0/url",
                "/output/results/0/url",
                "/output/url",
                "/images/0/url");
        if (imageUrl != null && !imageUrl.isBlank()) {
            return downloadAndSaveImage(imageUrl, recipeId);
        }

        String b64 = findFirstText(root,
                "/data/0/b64_json",
                "/output/results/0/b64_json",
                "/output/results/0/b64_image",
                "/images/0/b64_json");
        if (b64 != null && !b64.isBlank()) {
            return saveBytesAsImage(Base64.getDecoder().decode(b64), recipeId);
        }

        throw new RuntimeException("No usable image payload found from provider.");
    }

    private String findFirstText(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = root.at(pointer);
            if (!node.isMissingNode() && !node.isNull()) {
                String value = node.asText("");
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String saveBytesAsImage(byte[] imageBytes, Long recipeId) throws Exception {
        Path dir = ensureStorageDir();
        String fileName = buildFileName(recipeId);
        Path imagePath = dir.resolve(fileName);
        Files.write(imagePath, imageBytes);
        return "/recipe-images/" + fileName;
    }

    private String downloadAndSaveImage(String imageUrl, Long recipeId) throws Exception {
        Path dir = ensureStorageDir();
        String fileName = buildFileName(recipeId);
        Path imagePath = dir.resolve(fileName);

        HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "image/*,*/*;q=0.8");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException("Image download failed, status=" + status + ", url=" + imageUrl);
        }
        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, imagePath, StandardCopyOption.REPLACE_EXISTING);
        }

        return "/recipe-images/" + fileName;
    }

    private Path ensureStorageDir() throws Exception {
        Path dir = Paths.get(storageDir).toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    private String buildFileName(Long recipeId) {
        return "recipe-" + recipeId + "-" + System.currentTimeMillis() + ".png";
    }

    private String fallbackGenerateAndSave(String promptText, Long recipeId) {
        // Keep the prompt simple and URL-safe to reduce provider 400 responses.
        String safePrompt = promptText.replaceAll("\\s+", " ").trim();
        if (safePrompt.isBlank()) {
            safePrompt = "家常菜 实拍 美食摄影";

        }
        String encodedPrompt = java.net.URLEncoder.encode(safePrompt, StandardCharsets.UTF_8).replace("+", "%20");
        String seed = String.valueOf(System.currentTimeMillis());

        List<String> candidates = new ArrayList<>();
        candidates.add("https://image.pollinations.ai/prompt/" + encodedPrompt + "?width=1024&height=1024&nologo=true&seed=" + seed);
        candidates.add("https://image.pollinations.ai/prompt/" + encodedPrompt + "?model=flux&width=1024&height=1024&seed=" + seed);
        candidates.add("https://image.pollinations.ai/prompt/" + encodedPrompt);

        for (String imageUrl : candidates) {
            try {
                return downloadAndSaveImage(imageUrl, recipeId);
            } catch (Exception ex) {
                System.err.println("[RecipeImageService] Fallback candidate failed: " + imageUrl + " ; reason=" + ex.getMessage());
            }
        }

        System.err.println("[RecipeImageService] Fallback image generation also failed: all candidates failed");
        return "/recipe-images/default-dish.svg";
    }
}
