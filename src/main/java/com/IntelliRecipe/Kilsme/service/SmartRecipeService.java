package com.IntelliRecipe.Kilsme.service;

import com.IntelliRecipe.Kilsme.aiService.LocalUserVectorService;
import com.IntelliRecipe.Kilsme.aiService.RecipeImageService;
import com.IntelliRecipe.Kilsme.aiService.RecipeRecommendationAiService;
import com.IntelliRecipe.Kilsme.aiService.RedisPdfVectorService;
import com.IntelliRecipe.Kilsme.aiService.VectorTextChunk;
import com.IntelliRecipe.Kilsme.model.*;
import com.IntelliRecipe.Kilsme.vo.CollectedRecipeVo;
import com.IntelliRecipe.Kilsme.vo.IngredientReplacementVo;
import com.IntelliRecipe.Kilsme.vo.RecipeGenerateVo;
import com.IntelliRecipe.Kilsme.vo.RecipeIngredientVo;
import com.IntelliRecipe.Kilsme.vo.RecipeUseResultVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class SmartRecipeService {

    @Autowired
    private RedisPdfVectorService redisPdfVectorService;

    @Autowired
    private LocalUserVectorService localUserVectorService;

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private UserCollectionService userCollectionService;

    @Autowired
    private ListService listService;

    @Autowired
    private RecipeRecommendationAiService recipeRecommendationAiService;

    @Autowired
    private RecipeImageService recipeImageService;

    @Autowired
    private FridgeService fridgeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeGenerateVo generate(User user, String timeNode, String demand) {
        String effectiveTimeNode = normalizeTimeNode(timeNode);
        String targetCuisine = resolveTargetCuisine(demand, user.getPreferences());
        String query = buildQuery(user, effectiveTimeNode, demand);

        List<VectorTextChunk> pdfHits = redisPdfVectorService.search(query, 4);
        List<VectorTextChunk> historyHits = localUserVectorService.search(user.getId(), query, 3);

        String context = buildContext(pdfHits, historyHits);
        List<IngredientDict> dict = ingredientService.getAllIngredients();
        List<IngredientDict> selected = selectIngredients(context, user, dict);

        RecipeRecommendationAiService.RecipeDraft draft = recipeRecommendationAiService.recommend(
                user.getId(), selected, user, effectiveTimeNode, demand, targetCuisine, context);
        String title = draft.title();
        if (title == null || title.isBlank()) {
            title = buildIngredientTitle(selected);
        }
        List<String> steps = draft.steps();
        if (steps == null || steps.size() < 3) {
            steps = buildSteps(selected, effectiveTimeNode);
        }
        List<String> flavorTags = draft.flavorTags();
        if (flavorTags == null || flavorTags.isEmpty()) {
            flavorTags = inferFlavorTags(selected, user, effectiveTimeNode);
        }
        flavorTags = alignFlavorTagsByCuisine(flavorTags, targetCuisine);
        String douyinUrl = "https://www.douyin.com/search/" + URLEncoder.encode(title + " 教程", StandardCharsets.UTF_8);
        Map<Long, Double> aiQuantityMap = recipeRecommendationAiService.recommendIngredientQuantities(
                user.getId(),
                selected,
                user,
                effectiveTimeNode,
                demand,
                targetCuisine,
                steps
        );

        Recipes recipe = new Recipes();
        recipe.setTitle(title);
        recipe.setDescription("基于时段、个人画像与历史偏好生成，食材严格来自字典库。");
        recipe.setUserId(user.getId());
        recipe.setFlavorTags(toJson(flavorTags));
        recipe.setCuisineTags(resolveCuisineTagsJson(targetCuisine, user.getPreferences()));
        recipe.setTimeMinutes(25);
        recipe.setDifficulty(0);
        recipe.setIngredientsRequired(toIngredientsJson(selected, user, effectiveTimeNode, demand, aiQuantityMap));
        recipe.setSteps(toJson(steps));
        recipe.setNutritionSummary(buildNutritionSummary(selected));
        recipe.setVideoTutorials(toJson(Map.of("douyin", douyinUrl, "imageUrl", "/recipe-images/default-dish.svg")));
        recipe.setIsPublic(true);
        recipeService.create(recipe);

        String imageUrl = recipeImageService.generateAndSave(recipe, title);
        recipe.setVideoTutorials(toJson(Map.of("douyin", douyinUrl, "imageUrl", imageUrl)));
        recipeService.updateVideoTutorials(recipe.getId(), recipe.getVideoTutorials());

        List<RecipeIngredientVo> ingredientVos = toIngredientVoList(selected, user, effectiveTimeNode, demand, aiQuantityMap);
        SubstitutionPlan substitutionPlan = buildSubstitutionPlan(user.getId(), ingredientVos);
        List<RecipeIngredientVo> finalIngredients = substitutionPlan.finalIngredients();
        List<RecipeIngredientVo> missingIngredients = calculateMissingIngredients(user.getId(), finalIngredients);

        RecipeGenerateVo vo = new RecipeGenerateVo();
        vo.setRecipeId(recipe.getId());
        vo.setTitle(title);
        vo.setDescription(recipe.getDescription());
        vo.setImageUrl(imageUrl);
        vo.setTimeNode(effectiveTimeNode);
        vo.setDouyinUrl(douyinUrl);
        vo.setFlavorTags(flavorTags);
        vo.setCanUseNow(missingIngredients.isEmpty());
        vo.setIngredients(finalIngredients);
        vo.setMissingIngredients(missingIngredients);
        vo.setReplacements(substitutionPlan.replacements());
        vo.setSteps(steps);
        return vo;
    }

    public RecipeGenerateVo getDetail(Long userId, Long recipeId) {
        Recipes recipe = recipeService.getById(recipeId);
        if (recipe == null) {
            return null;
        }
        RecipeGenerateVo vo = new RecipeGenerateVo();
        String imageUrl = parseImageFromVideoJson(recipe.getVideoTutorials());
        if ("/recipe-images/default-dish.svg".equals(imageUrl)) {
            try {
                imageUrl = recipeImageService.generateAndSave(recipe, recipe.getTitle());
                String douyinUrl = parseDouyinFromVideoJson(recipe.getVideoTutorials());
                recipe.setVideoTutorials(toJson(Map.of("douyin", douyinUrl, "imageUrl", imageUrl)));
                recipeService.updateVideoTutorials(recipe.getId(), recipe.getVideoTutorials());
            } catch (Exception ignored) {
                imageUrl = "/recipe-images/default-dish.svg";
            }
        }
        vo.setRecipeId(recipe.getId());
        vo.setTitle(recipe.getTitle());
        vo.setDescription(recipe.getDescription());
        vo.setImageUrl(imageUrl);
        vo.setTimeNode("自定义");
        vo.setDouyinUrl(parseDouyinFromVideoJson(recipe.getVideoTutorials()));
        vo.setSteps(parseSteps(recipe.getSteps()));
        vo.setIngredients(parseIngredients(recipe.getIngredientsRequired()));
        vo.setFlavorTags(parseStringArray(recipe.getFlavorTags()));
        List<RecipeIngredientVo> missingIngredients = calculateMissingIngredients(userId, vo.getIngredients());
        vo.setCanUseNow(missingIngredients.isEmpty());
        vo.setMissingIngredients(missingIngredients);
        vo.setReplacements(List.of());

        UserCollection history = new UserCollection();
        history.setUserId(userId);
        history.setRecipeId(recipeId);
        history.setActionType(0);
        userCollectionService.add(history);
        localUserVectorService.refresh(userId);

        return vo;
    }

    public boolean collectRecipe(Long userId, Long recipeId) {
        UserCollection collect = new UserCollection();
        collect.setUserId(userId);
        collect.setRecipeId(recipeId);
        collect.setActionType(2);
        int rows = userCollectionService.add(collect);
        localUserVectorService.refresh(userId);
        return rows > 0;
    }

    public RecipeUseResultVo checkUse(User user, Long recipeId) {
        Recipes recipe = recipeService.getById(recipeId);
        if (recipe == null) {
            return null;
        }
        List<RecipeIngredientVo> ingredients = parseIngredients(recipe.getIngredientsRequired());
        RecipeUseResultVo result = new RecipeUseResultVo();
        if (ingredients.isEmpty()) {
            result.setCanUse(false);
            result.setMessage("菜谱缺少食材配置");
            result.setMissingIngredients(List.of());
            result.setReplacements(List.of());
            return result;
        }

        SubstitutionPlan substitutionPlan = buildSubstitutionPlan(user.getId(), ingredients);
        List<RecipeIngredientVo> finalIngredients = substitutionPlan.finalIngredients();
        List<RecipeIngredientVo> missing = calculateMissingIngredients(user.getId(), finalIngredients);
        if (!missing.isEmpty()) {
            result.setCanUse(false);
            if (substitutionPlan.replacements().isEmpty()) {
                result.setMessage("缺少必需食材，请先加入购物车并完成购买入冰箱");
            } else {
                result.setMessage("已尝试相似食材替代（" + substitutionPlan.replacements().size() + "项），仍缺少部分食材");
            }
            result.setMissingIngredients(missing);
            result.setReplacements(substitutionPlan.replacements());
            return result;
        }

        result.setCanUse(true);
        result.setDetailUrl("recipe-detail.html?id=" + recipeId);
        result.setMessage(substitutionPlan.replacements().isEmpty() ? "食材已满足，可去使用" : "已自动使用相似食材替代，可去使用");
        result.setMissingIngredients(List.of());
        result.setReplacements(substitutionPlan.replacements());
        return result;
    }

    public RecipeUseResultVo addMissingToCart(User user, Long recipeId) {
        Recipes recipe = recipeService.getById(recipeId);
        if (recipe == null) {
            return null;
        }
        List<RecipeIngredientVo> ingredients = parseIngredients(recipe.getIngredientsRequired());
        RecipeUseResultVo result = new RecipeUseResultVo();
        if (ingredients.isEmpty()) {
            result.setCanUse(false);
            result.setMessage("菜谱缺少食材配置");
            result.setMissingIngredients(List.of());
            result.setReplacements(List.of());
            return result;
        }
        SubstitutionPlan substitutionPlan = buildSubstitutionPlan(user.getId(), ingredients);
        List<RecipeIngredientVo> finalIngredients = substitutionPlan.finalIngredients();
        List<RecipeIngredientVo> missing = calculateMissingIngredients(user.getId(), finalIngredients);
        if (!missing.isEmpty()) {
            addMissingIngredientsToShoppingList(user.getId(), missing);
            result.setCanUse(false);
            if (substitutionPlan.replacements().isEmpty()) {
                result.setMessage("已将缺少食材加入购物车，请完成购买并入冰箱");
            } else {
                result.setMessage("已完成相似食材替代，并将剩余缺料加入购物车");
            }
            result.setMissingIngredients(missing);
            result.setReplacements(substitutionPlan.replacements());
            return result;
        }
        result.setCanUse(true);
        result.setDetailUrl("recipe-detail.html?id=" + recipeId);
        result.setMessage(substitutionPlan.replacements().isEmpty() ? "当前已无需采购，可直接使用" : "替代后当前已无需采购，可直接使用");
        result.setMissingIngredients(List.of());
        result.setReplacements(substitutionPlan.replacements());
        return result;
    }

    public RecipeUseResultVo useRecipe(User user, Long recipeId) {
        RecipeUseResultVo check = checkUse(user, recipeId);
        if (check == null) {
            return null;
        }
        if (!check.isCanUse()) {
            return check;
        }

        RecipeUseResultVo result = new RecipeUseResultVo();

        Recipes recipe = recipeService.getById(recipeId);
        List<RecipeIngredientVo> ingredients = parseIngredients(recipe.getIngredientsRequired());
        SubstitutionPlan substitutionPlan = buildSubstitutionPlan(user.getId(), ingredients);
        List<RecipeIngredientVo> finalIngredients = substitutionPlan.finalIngredients();

        consumeIngredientsFromFridge(user.getId(), finalIngredients);

        UserCollection cooked = new UserCollection();
        cooked.setUserId(user.getId());
        cooked.setRecipeId(recipeId);
        cooked.setActionType(1);
        cooked.setCookedAt(LocalDateTime.now());
        userCollectionService.add(cooked);
        localUserVectorService.refresh(user.getId());

        result.setCanUse(true);
        result.setDetailUrl("recipe-detail.html?id=" + recipeId);
        result.setMessage(substitutionPlan.replacements().isEmpty() ? "使用成功，已扣减冰箱库存" : "使用成功，已按相似替代食材扣减库存");
        result.setMissingIngredients(List.of());
        result.setReplacements(substitutionPlan.replacements());
        return result;
    }

    public boolean feedback(Long userId, Long recipeId, Integer rating, String reviewText) {
        UserCollection feedback = new UserCollection();
        feedback.setUserId(userId);
        feedback.setRecipeId(recipeId);
        feedback.setActionType(1);
        feedback.setRating(rating);
        feedback.setReviewText(reviewText);
        feedback.setCookedAt(LocalDateTime.now());
        int rows = userCollectionService.add(feedback);
        localUserVectorService.refresh(userId);
        return rows > 0;
    }

    public List<CollectedRecipeVo> listCollectedRecipes(Long userId) {
        List<UserCollection> collections = userCollectionService.getByUserIdAndActionType(userId, 2);
        if (collections == null || collections.isEmpty()) {
            return List.of();
        }

        Map<Long, CollectedRecipeVo> dedup = new LinkedHashMap<>();
        for (UserCollection collection : collections) {
            Long recipeId = collection.getRecipeId();
            if (recipeId == null || dedup.containsKey(recipeId)) {
                continue;
            }
            Recipes recipe = recipeService.getById(recipeId);
            if (recipe == null) {
                continue;
            }
            CollectedRecipeVo vo = new CollectedRecipeVo();
            vo.setRecipeId(recipeId);
            vo.setTitle(recipe.getTitle());
            vo.setDescription(recipe.getDescription());
            vo.setCollectedAt(collection.getCreatedAt() == null ? "" : collection.getCreatedAt().toString());
            dedup.put(recipeId, vo);
        }
        return new ArrayList<>(dedup.values());
    }

    public int deleteCollectedRecipe(Long userId, Long recipeId) {
        int rows = userCollectionService.deleteCollected(userId, recipeId);
        localUserVectorService.refresh(userId);
        return rows;
    }

    public int batchDeleteCollectedRecipes(Long userId, List<Long> recipeIds) {
        int rows = userCollectionService.batchDeleteCollected(userId, recipeIds);
        localUserVectorService.refresh(userId);
        return rows;
    }


    private List<IngredientDict> selectIngredients(String context, User user, List<IngredientDict> dict) {
        if (dict == null || dict.isEmpty()) {
            return List.of();
        }
        Set<String> allergies = parseJsonStringArray(user.getAllergies());

        List<IngredientDict> matched = dict.stream()
                .filter(ing -> context.contains(ing.getName()))
                .filter(ing -> !allergies.contains(ing.getName()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (matched.isEmpty()) {
            matched = dict.stream()
                    .filter(ing -> !allergies.contains(ing.getName()))
                    .sorted(Comparator.comparing(IngredientDict::getCategoryId))
                    .limit(8)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        String dietType = user.getDietType() == null ? "NORMAL" : user.getDietType().toUpperCase(Locale.ROOT);
        if ("VEGAN".equals(dietType)) {
            matched = matched.stream().filter(ing -> ing.getCategoryId() != 2 && ing.getCategoryId() != 3).collect(Collectors.toCollection(ArrayList::new));
        } else if ("LOSE_WEIGHT".equals(dietType)) {
            matched = matched.stream().filter(ing -> ing.getCategoryId() != 8).collect(Collectors.toCollection(ArrayList::new));
        }

        LinkedHashSet<Long> dedup = new LinkedHashSet<>();
        List<IngredientDict> result = new ArrayList<>();
        for (IngredientDict ingredient : matched) {
            if (dedup.add(ingredient.getId())) {
                result.add(ingredient);
            }
            if (result.size() >= 7) {
                break;
            }
        }
        return result;
    }

    private String buildIngredientTitle(List<IngredientDict> ingredients) {
        List<String> names = ingredients.stream().map(IngredientDict::getName).limit(2).toList();
        if (names.isEmpty()) {
            return "家常炒菜";
        }
        if (names.size() == 1) {
            return names.get(0) + "家常做法";
        }
        return names.get(0) + names.get(1) + "小炒";
    }

    private List<String> buildSteps(List<IngredientDict> ingredients, String timeNode) {
        List<String> names = ingredients.stream().map(IngredientDict::getName).limit(4).toList();
        String ingredientText = names.isEmpty() ? "已选食材" : String.join("、", names);
        List<String> steps = new ArrayList<>();
        steps.add("准备食材：" + ingredientText + "，清洗后按常规切配。");
        steps.add("热锅少油，先下耐炒食材翻炒 2-3 分钟，再加入其余食材。");
        steps.add("按个人口味加入基础调味（盐、酱油等），中火翻炒或炖煮至熟。");
        steps.add("根据" + timeNode + "场景控制分量，出锅前可加葱花提升风味。");
        return steps;
    }

    private String buildQuery(User user, String timeNode, String demand) {
        return "用户画像: 饮食类型=" + nullToEmpty(user.getDietType())
                + ", 地区=" + nullToEmpty(user.getRegion())
                + ", 性别=" + (user.getGender() == null ? "" : user.getGender())
                + ", 年龄=" + (user.getAge() == null ? "" : user.getAge())
                + ", 身高=" + (user.getHeightCm() == null ? "" : user.getHeightCm())
                + ", 体重=" + (user.getWeightKg() == null ? "" : user.getWeightKg())
                + ", 偏好=" + nullToEmpty(user.getPreferences())
                + ", 过敏=" + nullToEmpty(user.getAllergies())
                + ", 时段=" + timeNode
                + ", 用户目标=" + nullToEmpty(demand);
    }

    private String buildContext(List<VectorTextChunk> pdfHits, List<VectorTextChunk> historyHits) {
        StringBuilder sb = new StringBuilder();
        for (VectorTextChunk hit : pdfHits) {
            sb.append(hit.getTitle()).append(" ").append(hit.getText()).append(" ");
        }
        for (VectorTextChunk hit : historyHits) {
            sb.append(hit.getTitle()).append(" ").append(hit.getText()).append(" ");
        }
        return sb.toString();
    }

    private String toIngredientsJson(List<IngredientDict> ingredients, User user, String timeNode, String demand, Map<Long, Double> aiQuantityMap) {
        List<RecipeIngredientVo> items = toIngredientVoList(ingredients, user, timeNode, demand, aiQuantityMap);
        return toJson(items);
    }

    private List<RecipeIngredientVo> toIngredientVoList(List<IngredientDict> ingredients,
                                                        User user,
                                                        String timeNode,
                                                        String demand,
                                                        Map<Long, Double> aiQuantityMap) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        List<RecipeIngredientVo> list = new ArrayList<>();
        int ingredientCount = ingredients.size();
        double dishScale = estimateDishScale(ingredients, user, timeNode, demand);
        for (IngredientDict ingredient : ingredients) {
            RecipeIngredientVo vo = new RecipeIngredientVo();
            vo.setIngredientId(ingredient.getId());
            vo.setIngredientName(ingredient.getName());
            vo.setCategoryId(ingredient.getCategoryId());
            Double aiSuggestedQuantity = aiQuantityMap == null ? null : aiQuantityMap.get(ingredient.getId());
            vo.setQuantity(estimateQuantityByScenario(ingredient, timeNode, demand, ingredientCount, dishScale, aiSuggestedQuantity));
            vo.setUnit("g");
            list.add(vo);
        }
        return list;
    }

    private Double estimateQuantityByScenario(IngredientDict ingredient,
                                              String timeNode,
                                              String demand,
                                              int ingredientCount,
                                              double dishScale,
                                              Double aiSuggestedQuantity) {
        double base = defaultQuantityByIngredient(ingredient);

        double mealFactor = switch (normalizeTimeNode(timeNode)) {
            case "早餐" -> 0.85;
            case "午餐" -> 1.0;
            case "晚餐" -> 1.1;
            case "夜宵" -> 0.75;
            default -> 1.0;
        };

        String text = (demand == null ? "" : demand).toLowerCase(Locale.ROOT);
        double servingFactor = 1.0;
        if (text.contains("单人") || text.contains("一人") || text.contains("1人")) {
            servingFactor = 0.8;
        } else if (text.contains("两人") || text.contains("2人")) {
            servingFactor = 1.3;
        } else if (text.contains("三人") || text.contains("3人")) {
            servingFactor = 1.6;
        } else if (text.contains("四人") || text.contains("4人")) {
            servingFactor = 1.9;
        }

        double targetFactor = 1.0;
        Integer categoryId = ingredient.getCategoryId();
        if (text.contains("减脂") || text.contains("低脂") || text.contains("轻食")) {
            if (categoryId != null && (categoryId == 1 || categoryId == 4)) {
                targetFactor = 1.15;
            } else if (categoryId != null && (categoryId == 2 || categoryId == 5 || categoryId == 8)) {
                targetFactor = 0.85;
            }
        } else if (text.contains("增肌") || text.contains("高蛋白")) {
            if (categoryId != null && (categoryId == 2 || categoryId == 3 || categoryId == 7)) {
                targetFactor = 1.2;
            }
        }

        // Dishes with many ingredients should reduce each single ingredient amount.
        double complexityFactor = ingredientCount >= 7 ? 0.82 : ingredientCount <= 3 ? 1.15 : 1.0;

        double heuristicQuantity = base * mealFactor * servingFactor * targetFactor * complexityFactor * dishScale;
        heuristicQuantity = clampQuantityByCategory(categoryId, heuristicQuantity);

        double quantity = heuristicQuantity;
        if (aiSuggestedQuantity != null && aiSuggestedQuantity > 0) {
            double boundedAiQuantity = clampQuantityByCategory(categoryId, aiSuggestedQuantity);
            // Use AI as primary source, keep a small heuristic anchor to avoid unstable extremes.
            quantity = boundedAiQuantity * 0.75 + heuristicQuantity * 0.25;
        }

        // Keep kitchen-friendly increments, e.g. 85g -> 85, 153g -> 155.
        return Math.round(quantity / 5.0) * 5.0;
    }

    private double estimateDishScale(List<IngredientDict> ingredients, User user, String timeNode, String demand) {
        if (ingredients == null || ingredients.isEmpty()) {
            return 1.0;
        }

        int servings = inferServings(demand, timeNode);
        double calorieTarget = estimateMealCalorieTarget(user, timeNode) * servings;
        double estimatedCalories = estimateCurrentRecipeCalories(ingredients);
        if (estimatedCalories <= 1) {
            return 1.0;
        }

        double factor = calorieTarget / estimatedCalories;
        return Math.max(0.75, Math.min(1.9, factor));
    }

    private int inferServings(String demand, String timeNode) {
        String text = demand == null ? "" : demand;

        // Explicit Arabic number, e.g. "2人份" / "3个人".
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([1-9])\\s*人").matcher(text);
        if (m.find()) {
            try {
                return Math.max(1, Math.min(6, Integer.parseInt(m.group(1))));
            } catch (Exception ignored) {
            }
        }

        if (text.contains("两人") || text.contains("二人")) return 2;
        if (text.contains("三人") || text.contains("家庭") || text.contains("全家")) return 3;
        if (text.contains("聚餐") || text.contains("朋友") || text.contains("多人")) return 4;

        // If not specified, default by meal context: breakfast/snack tends to smaller portions.
        String normalized = normalizeTimeNode(timeNode);
        if ("早餐".equals(normalized) || "夜宵".equals(normalized)) {
            return 1;
        }
        return 2;
    }

    private double estimateMealCalorieTarget(User user, String timeNode) {
        double weight = user != null && user.getWeightKg() != null ? user.getWeightKg().doubleValue() : 60D;
        double height = user != null && user.getHeightCm() != null ? user.getHeightCm() : 165D;
        int age = user != null && user.getAge() != null ? user.getAge() : 25;
        int gender = user != null && user.getGender() != null ? user.getGender() : 1;

        // Mifflin-St Jeor BMR estimation.
        double bmr = (gender == 0)
                ? (10 * weight + 6.25 * height - 5 * age - 161)
                : (10 * weight + 6.25 * height - 5 * age + 5);
        double daily = bmr * 1.35; // light activity baseline

        String dietType = user == null || user.getDietType() == null ? "NORMAL" : user.getDietType().toUpperCase(Locale.ROOT);
        if ("LOSE_WEIGHT".equals(dietType)) {
            daily *= 0.85;
        } else if ("MUSCLE_GAIN".equals(dietType)) {
            daily *= 1.12;
        }

        String normalized = normalizeTimeNode(timeNode);
        double ratio = switch (normalized) {
            case "早餐" -> 0.28;
            case "午餐" -> 0.38;
            case "晚餐" -> 0.34;
            case "夜宵" -> 0.18;
            default -> 0.34;
        };
        return Math.max(260D, daily * ratio);
    }

    private double estimateCurrentRecipeCalories(List<IngredientDict> ingredients) {
        double calories = 0;
        for (IngredientDict ingredient : ingredients) {
            double quantity = defaultQuantityByIngredient(ingredient);
            Map<String, Object> nutrition = parseJsonObject(ingredient.getNutritionInfo());
            double calPer100g = toDouble(nutrition.get("calories"));
            if (calPer100g <= 0) {
                calPer100g = 70; // fallback average
            }
            calories += (quantity / 100.0) * calPer100g;
        }
        return calories;
    }

    private double clampQuantityByCategory(Integer categoryId, double quantity) {
        if (categoryId == null) {
            return Math.max(30D, Math.min(300D, quantity));
        }
        return switch (categoryId) {
            case 1 -> Math.max(80D, Math.min(350D, quantity));
            case 2, 3 -> Math.max(60D, Math.min(280D, quantity));
            case 4 -> Math.max(60D, Math.min(220D, quantity));
            case 5 -> Math.max(40D, Math.min(180D, quantity));
            case 6 -> Math.max(3D, Math.min(25D, quantity));
            case 7 -> Math.max(20D, Math.min(180D, quantity));
            case 8 -> Math.max(60D, Math.min(260D, quantity));
            default -> Math.max(30D, Math.min(260D, quantity));
        };
    }

    private Double defaultQuantityByIngredient(IngredientDict ingredient) {
        if (ingredient == null) {
            return 100D;
        }
        String name = ingredient.getName() == null ? "" : ingredient.getName();

        // Common seasoning items should not use large gram values.
        if (name.contains("盐") || name.contains("糖") || name.contains("酱油") || name.contains("醋") || name.contains("料酒") || name.contains("胡椒") || name.contains("辣椒粉")) {
            return 5D;
        }
        if (name.contains("蒜") || name.contains("姜") || name.contains("葱")) {
            return 15D;
        }

        return defaultQuantityByCategory(ingredient.getCategoryId());
    }

    private Double defaultQuantityByCategory(Integer categoryId) {
        if (categoryId == null) {
            return 100D;
        }
        return switch (categoryId) {
            case 1 -> 200D; // vegetables
            case 2 -> 150D; // meat/egg
            case 3 -> 180D; // seafood
            case 4 -> 120D; // fruits
            case 5 -> 100D; // grains/oils
            case 6 -> 8D;   // seasonings
            case 7 -> 80D;  // dairy/baking
            case 8 -> 160D; // frozen/ready food
            case 9 -> 100D; // others
            default -> 120D;
        };
    }

    private String buildNutritionSummary(List<IngredientDict> ingredients) {
        double calories = 0;
        double protein = 0;
        for (IngredientDict ingredient : ingredients) {
            Map<String, Object> nutrition = parseJsonObject(ingredient.getNutritionInfo());
            calories += toDouble(nutrition.get("calories"));
            protein += toDouble(nutrition.get("protein"));
        }
        Map<String, Object> summary = Map.of(
                "calories_estimate", round(calories),
                "protein_estimate", round(protein)
        );
        return toJson(summary);
    }

    private String parseDouyinFromVideoJson(String json) {
        Map<String, Object> map = parseJsonObject(json);
        Object value = map.get("douyin");
        return value == null ? "" : String.valueOf(value);
    }

    private String parseImageFromVideoJson(String json) {
        Map<String, Object> map = parseJsonObject(json);
        Object value = map.get("imageUrl");
        if (value == null) {
            return "/recipe-images/default-dish.svg";
        }
        String url = String.valueOf(value);
        return url.isBlank() ? "/recipe-images/default-dish.svg" : url;
    }

    private List<String> parseSteps(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of("暂无步骤");
        }
    }

    private List<RecipeIngredientVo> parseIngredients(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<RecipeIngredientVo>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseStringArray(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String parseFirstCuisine(String cuisineTagsJson) {
        List<String> cuisines = parseStringArray(cuisineTagsJson);
        return cuisines.isEmpty() ? "" : cuisines.get(0);
    }

    private String normalizeTimeNode(String timeNode) {
        if (timeNode == null || timeNode.isBlank()) {
            int hour = LocalDateTime.now().getHour();
            if (hour < 10) {
                return "早餐";
            }
            if (hour < 15) {
                return "午餐";
            }
            return "晚餐";
        }
        return timeNode;
    }

    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "家常菜";
        }
        String value = title.replace(".pdf", "").trim();
        return value.length() > 18 ? value.substring(0, 18) : value;
    }

    private List<String> inferFlavorTags(List<IngredientDict> ingredients, User user, String timeNode) {
        List<String> tags = new ArrayList<>();
        tags.add("家常");
        if ("早餐".equals(timeNode)) {
            tags.add("清淡");
        } else if ("晚餐".equals(timeNode)) {
            tags.add("均衡");
        }
        String dietType = user.getDietType() == null ? "" : user.getDietType().toUpperCase(Locale.ROOT);
        if ("LOSE_WEIGHT".equals(dietType)) {
            tags.add("低脂");
        } else if ("MUSCLE_GAIN".equals(dietType)) {
            tags.add("高蛋白");
        } else if ("VEGAN".equals(dietType)) {
            tags.add("植物基");
        }
        if (ingredients.stream().anyMatch(i -> i.getCategoryId() != null && i.getCategoryId() == 6)) {
            tags.add("酱香");
        }
        return tags.stream().distinct().limit(4).toList();
    }

    private String extractCuisineTags(String preferences) {
        if (preferences == null || preferences.isBlank()) {
            return "[]";
        }
        try {
            Map<String, Object> map = parseJsonObject(preferences);
            Object cuisines = map.get("preferred_cuisines");
            return cuisines == null ? "[]" : toJson(cuisines);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String resolveCuisineTagsJson(String targetCuisine, String preferences) {
        if (targetCuisine != null && !targetCuisine.isBlank()) {
            return toJson(List.of(targetCuisine));
        }
        return extractCuisineTags(preferences);
    }

    private String resolveTargetCuisine(String demand, String preferences) {
        String d = demand == null ? "" : demand;
        String fromDemand = detectCuisineKeyword(d);
        if (!fromDemand.isBlank()) {
            return fromDemand;
        }
        if (preferences == null || preferences.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> map = parseJsonObject(preferences);
            Object cuisines = map.get("preferred_cuisines");
            if (cuisines instanceof List<?> list && !list.isEmpty()) {
                String first = String.valueOf(list.get(0)).trim();
                return normalizeCuisine(first);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String detectCuisineKeyword(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace(" ", "").toLowerCase(Locale.ROOT);

        Map<String, String> cuisineMap = new LinkedHashMap<>();
        cuisineMap.put("川菜", "川菜");
        cuisineMap.put("湘菜", "湘菜");
        cuisineMap.put("粤菜", "粤菜");
        cuisineMap.put("鲁菜", "鲁菜");
        cuisineMap.put("苏菜", "苏菜");
        cuisineMap.put("浙菜", "浙菜");
        cuisineMap.put("闽菜", "闽菜");
        cuisineMap.put("徽菜", "徽菜");
        cuisineMap.put("淮扬菜", "淮扬菜");
        cuisineMap.put("东北菜", "东北菜");
        cuisineMap.put("西北菜", "西北菜");
        cuisineMap.put("本帮菜", "本帮菜");
        cuisineMap.put("日料", "日式");
        cuisineMap.put("日式", "日式");
        cuisineMap.put("日本菜", "日式");
        cuisineMap.put("韩餐", "韩式");
        cuisineMap.put("韩式", "韩式");
        cuisineMap.put("韩国菜", "韩式");
        cuisineMap.put("意餐", "意式");
        cuisineMap.put("意式", "意式");
        cuisineMap.put("意大利", "意式");
        cuisineMap.put("法餐", "法式");
        cuisineMap.put("法式", "法式");
        cuisineMap.put("法国菜", "法式");
        cuisineMap.put("泰餐", "泰式");
        cuisineMap.put("泰式", "泰式");
        cuisineMap.put("泰国菜", "泰式");
        cuisineMap.put("墨西哥", "墨西哥");
        cuisineMap.put("mexican", "墨西哥");
        cuisineMap.put("地中海", "地中海");
        cuisineMap.put("mediterranean", "地中海");
        cuisineMap.put("美式", "美式");
        cuisineMap.put("american", "美式");

        for (Map.Entry<String, String> entry : cuisineMap.entrySet()) {
            if (normalized.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return "";
    }

    private String normalizeCuisine(String cuisine) {
        if (cuisine == null) {
            return "";
        }
        String text = cuisine.trim();
        if (text.endsWith("系") && text.length() > 1) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    private List<String> alignFlavorTagsByCuisine(List<String> flavorTags, String targetCuisine) {
        if (targetCuisine == null || targetCuisine.isBlank()) {
            return flavorTags;
        }
        List<String> tags = new ArrayList<>();
        tags.add(targetCuisine);
        if (flavorTags != null) {
            tags.addAll(flavorTags);
        }
        return tags.stream().filter(s -> s != null && !s.isBlank()).distinct().limit(4).toList();
    }


    private Set<String> parseJsonStringArray(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return new HashSet<>(list);
        } catch (Exception e) {
            return Set.of();
        }
    }

    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String nullToEmpty(String value) {
        return Objects.toString(value, "");
    }

    private SubstitutionPlan buildSubstitutionPlan(Long userId, List<RecipeIngredientVo> origin) {
        if (origin == null || origin.isEmpty()) {
            return new SubstitutionPlan(List.of(), List.of());
        }
        List<UserFridge> fridges = fridgeService.getFridgeByUserId(userId);
        Map<Long, UserFridge> fridgeMap = fridges.stream().collect(Collectors.toMap(UserFridge::getIngredientId, item -> item, (a, b) -> a));
        Map<Long, IngredientDict> fridgeIngredientMap = fridges.stream()
                .map(UserFridge::getIngredientId)
                .filter(Objects::nonNull)
                .distinct()
                .map(ingredientService::getIngredientById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(IngredientDict::getId, item -> item, (a, b) -> a));
        List<RecipeIngredientVo> finalList = new ArrayList<>();
        List<IngredientReplacementVo> replacements = new ArrayList<>();
        Set<Long> occupiedReplacementIngredient = new HashSet<>();

        for (RecipeIngredientVo item : origin) {
            double need = toGrams(item.getQuantity(), item.getUnit());
            UserFridge direct = fridgeMap.get(item.getIngredientId());
            if (direct != null && toGrams(direct.getQuantity(), direct.getUnit()) >= need) {
                finalList.add(item);
                continue;
            }

            ReplacementCandidate candidate = findBestReplacementCandidate(
                    item,
                    need,
                    fridges,
                    occupiedReplacementIngredient,
                    fridgeIngredientMap
            );

            if (candidate != null) {
                finalList.add(candidate.replacement());
                occupiedReplacementIngredient.add(candidate.fridgeIngredientId());
                IngredientReplacementVo replacementVo = new IngredientReplacementVo();
                replacementVo.setOriginalIngredientName(item.getIngredientName());
                replacementVo.setReplacementIngredientName(candidate.replacement().getIngredientName());
                replacementVo.setReason(candidate.reason());
                replacements.add(replacementVo);
            } else {
                finalList.add(item);
            }
        }

        return new SubstitutionPlan(finalList, replacements);
    }

    private ReplacementCandidate findBestReplacementCandidate(
            RecipeIngredientVo target,
            double requiredGrams,
            List<UserFridge> fridges,
            Set<Long> occupiedReplacementIngredient,
            Map<Long, IngredientDict> fridgeIngredientMap
    ) {
        double bestScore = 0;
        UserFridge bestFridge = null;
        IngredientDict bestIngredient = null;

        for (UserFridge fridge : fridges) {
            Long candidateId = fridge.getIngredientId();
            if (candidateId == null || occupiedReplacementIngredient.contains(candidateId)) {
                continue;
            }
            if (toGrams(fridge.getQuantity(), fridge.getUnit()) + 1e-9 < requiredGrams) {
                continue;
            }
            IngredientDict candidateIngredient = fridgeIngredientMap.get(candidateId);
            if (candidateIngredient == null) {
                continue;
            }

            double score = scoreReplacementCandidate(target, candidateIngredient);
            if (score > bestScore) {
                bestScore = score;
                bestFridge = fridge;
                bestIngredient = candidateIngredient;
            }
        }

        // Threshold avoids poor cross-category replacement like seasoning -> frozen foods.
        if (bestFridge == null || bestIngredient == null || bestScore < 45) {
            return null;
        }

        RecipeIngredientVo replacement = new RecipeIngredientVo();
        replacement.setIngredientId(bestIngredient.getId());
        replacement.setIngredientName(bestIngredient.getName());
        replacement.setCategoryId(bestIngredient.getCategoryId());
        replacement.setQuantity(target.getQuantity());
        replacement.setUnit(target.getUnit());

        String reason = buildReplacementReason(target, bestIngredient);
        return new ReplacementCandidate(bestFridge.getIngredientId(), replacement, reason);
    }

    private double scoreReplacementCandidate(RecipeIngredientVo target, IngredientDict candidate) {
        if (target == null || candidate == null) {
            return 0;
        }
        if (Objects.equals(target.getIngredientId(), candidate.getId())) {
            return 100;
        }
        Integer targetCategoryId = target.getCategoryId();
        Integer candidateCategoryId = candidate.getCategoryId();
        String targetName = normalizeIngredientName(target.getIngredientName());
        String candidateName = normalizeIngredientName(candidate.getName());
        boolean ruleBasedMatch = isRuleBasedSubstitution(targetName, candidateName);

        if (!isCategoryCompatible(targetCategoryId, candidateCategoryId, ruleBasedMatch)) {
            return 0;
        }

        double score = Objects.equals(targetCategoryId, candidateCategoryId) ? 52 : 30;
        if (targetName.isEmpty() || candidateName.isEmpty()) {
            return score;
        }

        if (targetName.equals(candidateName)) {
            score += 40;
        }
        if (ruleBasedMatch) {
            score += 30;
        }
        score += keywordOverlapScore(targetName, candidateName);
        score += nutritionSimilarityScore(target.getIngredientId(), candidate);
        return score;
    }

    private boolean isCategoryCompatible(Integer targetCategoryId, Integer candidateCategoryId, boolean ruleBasedMatch) {
        if (targetCategoryId == null || candidateCategoryId == null) {
            return false;
        }
        if (Objects.equals(targetCategoryId, candidateCategoryId)) {
            return true;
        }
        if (targetCategoryId == 6 || candidateCategoryId == 6) {
            return false;
        }
        return ruleBasedMatch;
    }

    private String normalizeIngredientName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[\\s·,，。()（）]", "").toLowerCase(Locale.ROOT);
    }

    private boolean isRuleBasedSubstitution(String targetName, String candidateName) {
        return matchesRule(targetName, candidateName) || matchesRule(candidateName, targetName);
    }

    private boolean matchesRule(String sourceName, String targetName) {
        if (sourceName.contains("青椒") && (targetName.contains("尖椒") || targetName.contains("彩椒"))) return true;
        if (sourceName.contains("五花肉") && (targetName.contains("前腿肉") || targetName.contains("后腿肉"))) return true;
        if (sourceName.contains("生抽") && targetName.contains("薄盐生抽")) return true;
        if (sourceName.contains("洋葱") && targetName.contains("大葱")) return true;
        if (sourceName.contains("猪肉") && (targetName.contains("里脊") || targetName.contains("前腿") || targetName.contains("后腿") || targetName.contains("五花"))) return true;
        if (sourceName.contains("牛肉") && (targetName.contains("牛腱") || targetName.contains("牛腩"))) return true;
        if (sourceName.contains("鸡胸") && (targetName.contains("鸡腿") || targetName.contains("鸡柳"))) return true;
        if (sourceName.contains("醋") && (targetName.contains("米醋") || targetName.contains("香醋") || targetName.contains("陈醋"))) return true;
        return false;
    }

    private double keywordOverlapScore(String targetName, String candidateName) {
        if (targetName.isEmpty() || candidateName.isEmpty()) {
            return 0;
        }
        if (targetName.contains(candidateName) || candidateName.contains(targetName)) {
            return 16;
        }
        List<String> keywords = List.of(
                "猪", "牛", "鸡", "鸭", "鱼", "虾", "蟹", "蛋", "豆腐", "豆", "椒", "葱", "姜", "蒜",
                "醋", "酱油", "生抽", "老抽", "白菜", "菠菜", "土豆", "番茄", "西红柿", "香菇", "蘑菇"
        );
        int hitCount = 0;
        for (String keyword : keywords) {
            if (targetName.contains(keyword) && candidateName.contains(keyword)) {
                hitCount += 1;
            }
        }
        return Math.min(16, hitCount * 4.0);
    }

    private double nutritionSimilarityScore(Long targetIngredientId, IngredientDict candidate) {
        if (targetIngredientId == null || candidate == null) {
            return 0;
        }
        IngredientDict target = ingredientService.getIngredientById(targetIngredientId);
        if (target == null) {
            return 0;
        }

        Map<String, Object> a = parseJsonObject(target.getNutritionInfo());
        Map<String, Object> b = parseJsonObject(candidate.getNutritionInfo());
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }

        double calA = toDouble(a.get("calories"));
        double calB = toDouble(b.get("calories"));
        double pA = toDouble(a.get("protein"));
        double pB = toDouble(b.get("protein"));
        double fA = toDouble(a.get("fat"));
        double fB = toDouble(b.get("fat"));

        double distance = 0;
        distance += Math.abs(calA - calB) / Math.max(50D, calA);
        distance += Math.abs(pA - pB) / Math.max(5D, pA);
        distance += Math.abs(fA - fB) / Math.max(3D, fA);

        return Math.max(0, 12 - distance * 4);
    }

    private String buildReplacementReason(RecipeIngredientVo target, IngredientDict replacement) {
        if (target == null || replacement == null) {
            return "优先使用冰箱现有相似食材替换";
        }
        if (Objects.equals(target.getCategoryId(), replacement.getCategoryId())) {
            return "优先使用冰箱现有同类食材替换";
        }
        String targetName = normalizeIngredientName(target.getIngredientName());
        String replacementName = normalizeIngredientName(replacement.getName());
        if (isRuleBasedSubstitution(targetName, replacementName)) {
            return "按常见替代规则匹配冰箱食材";
        }
        return "根据类别与营养相似度匹配替代";
    }

    private List<RecipeIngredientVo> calculateMissingIngredients(Long userId, List<RecipeIngredientVo> ingredients) {
        List<UserFridge> fridges = fridgeService.getFridgeByUserId(userId);
        Map<Long, UserFridge> fridgeMap = fridges.stream().collect(Collectors.toMap(UserFridge::getIngredientId, item -> item, (a, b) -> a));
        List<RecipeIngredientVo> missing = new ArrayList<>();

        for (RecipeIngredientVo item : ingredients) {
            if (item.getIngredientId() == null) {
                continue;
            }
            double need = toGrams(item.getQuantity(), item.getUnit());
            UserFridge have = fridgeMap.get(item.getIngredientId());
            double own = have == null ? 0 : toGrams(have.getQuantity(), have.getUnit());
            if (own + 1e-9 < need) {
                RecipeIngredientVo miss = new RecipeIngredientVo();
                miss.setIngredientId(item.getIngredientId());
                miss.setIngredientName(item.getIngredientName());
                miss.setCategoryId(item.getCategoryId());
                miss.setUnit("g");
                miss.setQuantity(roundToTwoDecimals(Math.max(need - own, 0)));
                missing.add(miss);
            }
        }
        return missing;
    }

    private void addMissingIngredientsToShoppingList(Long userId, List<RecipeIngredientVo> missing) {
        ShoppingList shoppingList = listService.selectByUserId(userId);
        if (shoppingList == null) {
            int created = listService.insertShopList(userId);
            if (created == 0) {
                return;
            }
            shoppingList = listService.selectByUserId(userId);
        }
        if (shoppingList == null) {
            return;
        }
        for (RecipeIngredientVo item : missing) {
            if (item.getIngredientId() == null || item.getCategoryId() == null) {
                continue;
            }
            listService.addFoodToList("g", item.getQuantity() == null ? 0D : item.getQuantity(), item.getIngredientId(), shoppingList.getId(), item.getCategoryId());
        }
    }

    private void consumeIngredientsFromFridge(Long userId, List<RecipeIngredientVo> ingredients) {
        List<UserFridge> fridges = fridgeService.getFridgeByUserId(userId);
        Map<Long, UserFridge> fridgeMap = fridges.stream().collect(Collectors.toMap(UserFridge::getIngredientId, item -> item, (a, b) -> a));

        for (RecipeIngredientVo item : ingredients) {
            UserFridge fridge = fridgeMap.get(item.getIngredientId());
            if (fridge == null) {
                continue;
            }
            double remain = toGrams(fridge.getQuantity(), fridge.getUnit()) - toGrams(item.getQuantity(), item.getUnit());
            if (remain <= 0) {
                fridgeService.deleteFridgeFood(userId, item.getIngredientId());
            } else {
                fridge.setQuantity(roundToTwoDecimals(remain));
                fridge.setUnit("g");
                fridgeService.updateFridge(fridge);
            }
        }
    }

    private double toGrams(Double quantity, String unit) {
        if (quantity == null) {
            return 0;
        }
        if (unit == null) {
            return quantity;
        }
        String u = unit.trim().toLowerCase(Locale.ROOT);
        if ("kg".equals(u)) {
            return quantity * 1000;
        }
        return quantity;
    }

    private double toGrams(double quantity, String unit) {
        if (unit == null) {
            return quantity;
        }
        String u = unit.trim().toLowerCase(Locale.ROOT);
        if ("kg".equals(u)) {
            return quantity * 1000;
        }
        return quantity;
    }

    private double roundToTwoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private record SubstitutionPlan(List<RecipeIngredientVo> finalIngredients, List<IngredientReplacementVo> replacements) {
    }

    private record ReplacementCandidate(Long fridgeIngredientId, RecipeIngredientVo replacement, String reason) {
    }
}

