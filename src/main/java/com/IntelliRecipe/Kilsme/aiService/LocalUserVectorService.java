package com.IntelliRecipe.Kilsme.aiService;

import com.IntelliRecipe.Kilsme.model.Recipes;
import com.IntelliRecipe.Kilsme.model.UserCollection;
import com.IntelliRecipe.Kilsme.service.RecipeService;
import com.IntelliRecipe.Kilsme.service.UserCollectionService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocalUserVectorService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private UserCollectionService userCollectionService;

    @Autowired
    private RecipeService recipeService;

    private final ConcurrentHashMap<Long, List<VectorTextChunk>> userLocalVectors = new ConcurrentHashMap<>();

    public List<VectorTextChunk> search(Long userId, String query, int topK) {
        if (userId == null || query == null || query.isBlank()) {
            return List.of();
        }
        List<VectorTextChunk> vectors = userLocalVectors.computeIfAbsent(userId, this::buildUserVectors);
        if (vectors.isEmpty()) {
            return List.of();
        }

        double[] q = embed(query);
        if (q.length == 0) {
            return List.of();
        }
        List<VectorTextChunk> scored = new ArrayList<>();
        for (VectorTextChunk chunk : vectors) {
            double score = cosine(q, chunk.getVector());
            scored.add(new VectorTextChunk(chunk.getId(), chunk.getSource(), chunk.getTitle(), chunk.getText(), chunk.getVector(), score));
        }
        scored.sort(Comparator.comparing(VectorTextChunk::getScore).reversed());
        return scored.stream().limit(Math.max(topK, 1)).toList();
    }

    public void refresh(Long userId) {
        if (userId == null) {
            return;
        }
        userLocalVectors.put(userId, buildUserVectors(userId));
    }

    private List<VectorTextChunk> buildUserVectors(Long userId) {
        List<UserCollection> histories = userCollectionService.getRecentByUserId(userId, 80);
        List<VectorTextChunk> chunks = new ArrayList<>();
        for (UserCollection history : histories) {
            if (history.getRecipeId() == null) {
                continue;
            }
            Recipes recipe = recipeService.getById(history.getRecipeId());
            if (recipe == null) {
                continue;
            }
            String behavior = switch (history.getActionType() == null ? -1 : history.getActionType()) {
                case 0 -> "浏览";
                case 1 -> "做过";
                case 2 -> "收藏";
                default -> "交互";
            };
            String text = behavior + "菜谱: " + nullToEmpty(recipe.getTitle()) + "。描述: " + nullToEmpty(recipe.getDescription())
                    + "。食材: " + nullToEmpty(recipe.getIngredientsRequired()) + "。步骤: " + nullToEmpty(recipe.getSteps());
            double[] vector = embed(text);
            if (vector.length == 0) {
                continue;
            }
            chunks.add(new VectorTextChunk(
                    "user-" + userId + "-" + history.getId(),
                    "local-history",
                    recipe.getTitle(),
                    text,
                    vector,
                    0
            ));
        }
        return chunks;
    }

    private double[] embed(String text) {
        try {
            float[] arr = embeddingModel.embed(text).content().vector();
            double[] vector = new double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                vector[i] = arr[i];
            }
            return vector;
        } catch (Exception e) {
            return new double[0];
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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

