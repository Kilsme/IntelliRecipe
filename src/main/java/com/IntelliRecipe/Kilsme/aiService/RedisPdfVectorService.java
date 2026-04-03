package com.IntelliRecipe.Kilsme.aiService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RedisPdfVectorService {

    private static final String REDIS_KEYS_SET = "recipe:pdf:chunk:keys";
    private static final String REDIS_CHUNK_PREFIX = "recipe:pdf:chunk:";
    private static final String REDIS_INDEX_FINGERPRINT = "recipe:pdf:index:fingerprint";

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<VectorTextChunk> cache = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        try {
            String fingerprint = buildClasspathFingerprint();
            String oldFingerprint = stringRedisTemplate.opsForValue().get(REDIS_INDEX_FINGERPRINT);

            if (fingerprint.equals(oldFingerprint)) {
                loadFromRedisToCache();
                if (!cache.isEmpty()) {
                    return;
                }
            }

            clearRedisIndex();
            ingestClasspathContent();
            stringRedisTemplate.opsForValue().set(REDIS_INDEX_FINGERPRINT, fingerprint);
            loadFromRedisToCache();
        } catch (Exception ignored) {
            // 向量索引失败时允许降级，不影响核心业务接口可用
        }
    }

    public List<VectorTextChunk> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (cache.isEmpty()) {
            return List.of();
        }
        double[] queryVector = embedToDouble(query);
        if (queryVector.length == 0) {
            return List.of();
        }

        List<VectorTextChunk> scored = new ArrayList<>();
        for (VectorTextChunk chunk : cache) {
            double score = cosine(queryVector, chunk.getVector());
            VectorTextChunk copy = new VectorTextChunk(chunk.getId(), chunk.getSource(), chunk.getTitle(), chunk.getText(), chunk.getVector(), score);
            scored.add(copy);
        }
        scored.sort(Comparator.comparing(VectorTextChunk::getScore).reversed());
        return scored.stream().limit(Math.max(topK, 1)).toList();
    }

    private void ingestClasspathContent() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] pdfResources = resolver.getResources("classpath:content/**/*.pdf");
        Resource[] txtResources = resolver.getResources("classpath:content/**/*.txt");

        for (Resource resource : pdfResources) {
            String text = readPdf(resource);
            storeResourceChunks(resource.getFilename(), text, "pdf");
        }
        for (Resource resource : txtResources) {
            String text = readText(resource);
            storeResourceChunks(resource.getFilename(), text, "txt");
        }
    }

    private void storeResourceChunks(String title, String text, String source) throws Exception {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String chunk : splitText(text, 700, 120)) {
            String id = digest(source + "|" + title + "|" + chunk);
            String key = REDIS_CHUNK_PREFIX + id;
            if (stringRedisTemplate.hasKey(key)) {
                stringRedisTemplate.opsForSet().add(REDIS_KEYS_SET, id);
                continue;
            }
            double[] vector = embedToDouble(chunk);
            if (vector.length == 0) {
                continue;
            }
            Map<String, String> map = new HashMap<>();
            map.put("source", source);
            map.put("title", title == null ? "未命名菜谱" : title);
            map.put("text", chunk);
            map.put("vector", objectMapper.writeValueAsString(vector));
            stringRedisTemplate.opsForHash().putAll(key, map);
            stringRedisTemplate.opsForSet().add(REDIS_KEYS_SET, id);
        }
    }

    private void clearRedisIndex() {
        var ids = stringRedisTemplate.opsForSet().members(REDIS_KEYS_SET);
        if (ids != null && !ids.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (String id : ids) {
                keys.add(REDIS_CHUNK_PREFIX + id);
            }
            if (!keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
        stringRedisTemplate.delete(REDIS_KEYS_SET);
    }

    private String buildClasspathFingerprint() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] pdfResources = resolver.getResources("classpath:content/**/*.pdf");
            Resource[] txtResources = resolver.getResources("classpath:content/**/*.txt");

            List<String> refs = new ArrayList<>();
            for (Resource resource : pdfResources) {
                refs.add(resource.getFilename() + "#" + safeDigest(resource));
            }
            for (Resource resource : txtResources) {
                refs.add(resource.getFilename() + "#" + safeDigest(resource));
            }
            refs.sort(String::compareTo);
            return digest(String.join("|", refs));
        } catch (Exception e) {
            return "fingerprint-fallback";
        }
    }

    private String safeDigest(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            return digest(Base64.getEncoder().encodeToString(inputStream.readAllBytes()));
        } catch (Exception e) {
            return "na";
        }
    }

    private void loadFromRedisToCache() {
        cache.clear();
        var ids = stringRedisTemplate.opsForSet().members(REDIS_KEYS_SET);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            String key = REDIS_CHUNK_PREFIX + id;
            Map<Object, Object> data = stringRedisTemplate.opsForHash().entries(key);
            if (data.isEmpty()) {
                continue;
            }
            String vectorJson = Objects.toString(data.get("vector"), "[]");
            try {
                double[] vector = objectMapper.readValue(vectorJson, new TypeReference<double[]>() {});
                cache.add(new VectorTextChunk(
                        id,
                        Objects.toString(data.get("source"), "pdf"),
                        Objects.toString(data.get("title"), "未命名菜谱"),
                        Objects.toString(data.get("text"), ""),
                        vector,
                        0
                ));
            } catch (Exception ignored) {
            }
        }
    }

    private String readPdf(Resource resource) {
        try (InputStream inputStream = resource.getInputStream(); PDDocument doc = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException e) {
            return "";
        }
    }

    private String readText(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String normalized = text.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return chunks;
        }
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private double[] embedToDouble(String text) {
        try {
            float[] vector = embeddingModel.embed(text).content().vector();
            double[] arr = new double[vector.length];
            for (int i = 0; i < vector.length; i++) {
                arr[i] = vector[i];
            }
            return arr;
        } catch (Exception e) {
            return new double[0];
        }
    }

    private String digest(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }

    private double cosine(double[] a, double[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}

