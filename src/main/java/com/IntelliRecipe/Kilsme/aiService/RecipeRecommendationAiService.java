package com.IntelliRecipe.Kilsme.aiService;

import com.IntelliRecipe.Kilsme.model.IngredientDict;
import com.IntelliRecipe.Kilsme.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RecipeRecommendationAiService {

    @Autowired
    private ChatModel chatModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, Deque<String>> userConversationMemory = new ConcurrentHashMap<>();
    private String systemPrompt = "你是智能菜谱助手，请返回严格 JSON。";

    @PostConstruct
    public void initSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("system.txt");
            byte[] bytes = resource.getInputStream().readAllBytes();
            String loaded = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!loaded.isEmpty()) {
                this.systemPrompt = loaded;
            }
        } catch (Exception ignored) {
            // 使用默认提示词兜底
        }
    }

    public RecipeDraft recommend(Long userId,
                                 List<IngredientDict> ingredients,
                                 User user,
                                 String timeNode,
                                 String demand,
                                 String targetCuisine,
                                 String retrievalContext) {
        String ingredientText = ingredients.stream().map(IngredientDict::getName).collect(Collectors.joining("、"));
        String prompt = systemPrompt + "\n"
                + "你是专业智能菜谱规划师（不限中餐，支持全球菜系）。请结合动态用户画像输出单道最匹配菜谱。"
                + "\n【硬约束】"
                + "\n1) 仅返回 JSON，禁止任何解释文本。"
                + "\n2) title 为中文自然菜名，不加前后缀，不使用“推荐/菜谱/今日”等营销词。"
                + "\n3) 必须输出 cuisine（如 川菜/湘菜/粤菜/日式/韩式/意式/法式/泰式/墨西哥等）。"
                + "\n4) 若目标菜系不为空，则 cuisine 必须等于目标菜系，步骤和风味不得跑偏到其他菜系。"
                + "\n5) flavorTags 返回 3-5 个标签，需覆盖：口味特征+烹饪方式+营养取向（如减脂/高蛋白）。"
                + "\n6) steps 返回 5-7 步，步骤清晰且可执行，包含关键火候/时间。"
                + "\n7) 严格规避过敏源与用户禁忌。"
                + "\n8) 优先使用可用食材并尽量减少浪费。"
                + "\n【动态决策优先级】"
                + "\n目标菜系/用户目标 > 过敏与饮食类型安全约束 > 时段适配 > 收藏与历史偏好 > 地区与口味习惯。"
                + "\n【用户信息】"
                + "\n饮食类型: " + nullToEmpty(user.getDietType())
                + "\n地区: " + nullToEmpty(user.getRegion())
                + "\n偏好: " + nullToEmpty(user.getPreferences())
                + "\n过敏: " + nullToEmpty(user.getAllergies())
                + "\n用户目标: " + nullToEmpty(demand)
                + "\n目标菜系(高优先级): " + nullToEmpty(targetCuisine)
                + "\n时段: " + timeNode
                + "\n可用食材: " + ingredientText
                + "\n最近会话: " + formatRecentMemory(userId)
                + "\n检索上下文(收藏/历史/RAG): " + trimContext(retrievalContext)
                + "\nJSON Schema: {\"title\":\"\",\"cuisine\":\"\",\"flavorTags\":[\"\"],\"steps\":[\"\"]}";

        try {
            String raw = chatModel.chat(prompt);
            String cleaned = extractJsonObject(cleanJson(raw));
            Map<String, Object> map = objectMapper.readValue(cleaned, new TypeReference<>() {});
            String title = sanitizeTitle(Objects.toString(map.get("title"), ""));
            String cuisine = Objects.toString(map.get("cuisine"), "").trim();
            List<String> flavorTags = parseStringList(map.get("flavorTags"));
            List<String> steps = parseStringList(map.get("steps"));

            // 强约束校验：用户显式菜系优先，避免“川菜需求生成湘菜”
            if (targetCuisine != null && !targetCuisine.isBlank()) {
                cuisine = targetCuisine;
            }
            if (cuisine.isBlank()) {
                cuisine = targetCuisine == null ? "" : targetCuisine;
            }

            RecipeDraft draft = postProcessDraft(title, cuisine, flavorTags, steps, ingredients, timeNode, demand, targetCuisine);

            saveConversation(userId, "Q:" + prompt);
            saveConversation(userId, "A:" + raw);
            return draft;
        } catch (Exception e) {
            return new RecipeDraft("", targetCuisine == null ? "" : targetCuisine, List.of(), List.of());
        }
    }

    public Map<Long, Double> recommendIngredientQuantities(Long userId,
                                                           List<IngredientDict> ingredients,
                                                           User user,
                                                           String timeNode,
                                                           String demand,
                                                           String targetCuisine,
                                                           List<String> steps) {
        if (ingredients == null || ingredients.isEmpty()) {
            return Map.of();
        }

        String ingredientSchema = ingredients.stream()
                .map(item -> "{\"ingredientId\":" + item.getId()
                        + ",\"name\":\"" + nullToEmpty(item.getName()) + "\""
                        + ",\"categoryId\":" + (item.getCategoryId() == null ? 9 : item.getCategoryId()) + "}")
                .collect(Collectors.joining(","));

        String stepsText = steps == null ? "" : String.join("；", steps);
        String prompt = "你是专业营养与烹饪助手。请仅输出 JSON，不要任何解释。"
                + "\n目标：为每个食材给出合理克数（grams），用于单道菜。"
                + "\n约束："
                + "\n1) 所有 ingredientId 必须来自输入清单，不能新增或漏掉。"
                + "\n2) 调味类(categoryId=6) 通常 3-20g；肉/海鲜通常 80-260g；蔬菜通常 80-320g。"
                + "\n3) 结合用户目标与时段：" + nullToEmpty(demand) + " / " + nullToEmpty(timeNode)
                + "\n4) 结果单位固定 grams。"
                + "\n用户偏好：" + nullToEmpty(user == null ? "" : user.getPreferences())
                + "\n目标菜系：" + nullToEmpty(targetCuisine)
                + "\n最近会话：" + formatRecentMemory(userId)
                + "\n步骤参考：" + stepsText
                + "\n食材列表：[" + ingredientSchema + "]"
                + "\nJSON Schema: {\"quantities\":[{\"ingredientId\":1,\"grams\":120.0}]}";

        try {
            String raw = chatModel.chat(prompt);
            String cleaned = extractJsonObject(cleanJson(raw));
            Map<String, Object> root = objectMapper.readValue(cleaned, new TypeReference<>() {});
            Object quantities = root.get("quantities");
            if (!(quantities instanceof List<?> list)) {
                return Map.of();
            }

            Map<Long, Double> result = new LinkedHashMap<>();
            for (Object obj : list) {
                if (!(obj instanceof Map<?, ?> map)) {
                    continue;
                }
                Long ingredientId = toLong(map.get("ingredientId"));
                Double grams = toDouble(map.get("grams"));
                if (ingredientId == null || grams == null || grams <= 0) {
                    continue;
                }
                result.put(ingredientId, grams);
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private RecipeDraft postProcessDraft(String title,
                                         String cuisine,
                                         List<String> flavorTags,
                                         List<String> steps,
                                         List<IngredientDict> ingredients,
                                         String timeNode,
                                         String demand,
                                         String targetCuisine) {
        String safeTitle = sanitizeTitle(title);
        if (safeTitle.isBlank()) {
            safeTitle = buildFallbackTitle(ingredients, targetCuisine);
        }

        String safeCuisine = cuisine == null ? "" : cuisine.trim();
        if (targetCuisine != null && !targetCuisine.isBlank()) {
            safeCuisine = targetCuisine.trim();
        }
        if (safeCuisine.isBlank()) {
            safeCuisine = "家常";
        }

        List<String> safeTags = flavorTags == null ? new ArrayList<>() : new ArrayList<>(flavorTags);
        safeTags.removeIf(tag -> tag == null || tag.isBlank());
        if (!safeTags.contains(safeCuisine)) {
            safeTags.add(0, safeCuisine);
        }
        if (!demandOrDietTag(demand).isBlank() && !safeTags.contains(demandOrDietTag(demand))) {
            safeTags.add(demandOrDietTag(demand));
        }
        while (safeTags.size() > 5) {
            safeTags.remove(safeTags.size() - 1);
        }

        List<String> safeSteps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        safeSteps.removeIf(step -> step == null || step.isBlank());
        if (safeSteps.size() < 4) {
            safeSteps = buildFallbackSteps(ingredients, timeNode, safeCuisine, demand);
        }

        return new RecipeDraft(safeTitle, safeCuisine, safeTags, safeSteps);
    }

    private String buildFallbackTitle(List<IngredientDict> ingredients, String cuisine) {
        String prefix = (cuisine == null || cuisine.isBlank()) ? "家常" : cuisine;
        if (ingredients == null || ingredients.isEmpty()) {
            return prefix + "风味料理";
        }
        String a = ingredients.get(0).getName();
        if (ingredients.size() == 1) {
            return a + "特色做法";
        }
        String b = ingredients.get(1).getName();
        return a + b + "风味菜";
    }

    private List<String> buildFallbackSteps(List<IngredientDict> ingredients, String timeNode, String cuisine, String demand) {
        String names = ingredients == null || ingredients.isEmpty()
                ? "可用食材"
                : ingredients.stream().limit(4).map(IngredientDict::getName).collect(Collectors.joining("、"));
        List<String> fallback = new ArrayList<>();
        fallback.add("准备食材：" + names + "，清洗后按菜系习惯切配。");
        fallback.add("按" + cuisine + "风格调配基础腌料或酱汁，先处理主食材 5-10 分钟。");
        fallback.add("热锅少油，先下耐炒食材，再加入其余食材，按中火翻炒或焖煮至熟。");
        fallback.add("根据" + timeNode + "场景和“" + (demand == null ? "均衡" : demand) + "”目标调整盐糖油比例。");
        fallback.add("出锅前试味并微调，装盘即可。");
        return fallback;
    }

    private String demandOrDietTag(String demand) {
        if (demand == null || demand.isBlank()) {
            return "";
        }
        String d = demand.trim();
        if (d.contains("减脂") || d.contains("减肥")) {
            return "减脂";
        }
        if (d.contains("增肌") || d.contains("高蛋白")) {
            return "高蛋白";
        }
        if (d.contains("低糖")) {
            return "低糖";
        }
        if (d.contains("控盐")) {
            return "控盐";
        }
        return "";
    }

    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String formatRecentMemory(Long userId) {
        if (userId == null) {
            return "无";
        }
        Deque<String> memory = userConversationMemory.get(userId);
        if (memory == null || memory.isEmpty()) {
            return "无";
        }
        return String.join("\n", memory);
    }

    private void saveConversation(Long userId, String text) {
        if (userId == null || text == null || text.isBlank()) {
            return;
        }
        Deque<String> memory = userConversationMemory.computeIfAbsent(userId, key -> new ArrayDeque<>());
        memory.addLast(text.length() > 260 ? text.substring(0, 260) : text);
        while (memory.size() > 8) {
            memory.removeFirst();
        }
    }

    private String cleanJson(String text) {
        if (text == null) {
            return "{}";
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace("```json", "").replace("```", "").trim();
        }
        return cleaned;
    }

    private List<String> parseStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = Objects.toString(item, "").trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    private String sanitizeTitle(String title) {
        if (title == null) {
            return "";
        }
        String t = title.trim();
        if (t.contains("-")) {
            t = t.substring(0, t.indexOf('-')).trim();
        }
        t = t.replace("推荐", "").replace("菜谱", "").trim();
        return t;
    }

    private String trimContext(String context) {
        if (context == null) {
            return "";
        }
        String normalized = context.replaceAll("\\s+", " ");
        return normalized.length() > 1200 ? normalized.substring(0, 1200) : normalized;
    }

    private String nullToEmpty(String value) {
        return Objects.toString(value, "");
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    public record RecipeDraft(String title, String cuisine, List<String> flavorTags, List<String> steps) {
    }
}

