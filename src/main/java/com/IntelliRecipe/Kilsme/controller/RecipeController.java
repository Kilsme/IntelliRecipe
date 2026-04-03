package com.IntelliRecipe.Kilsme.controller;

import com.IntelliRecipe.Kilsme.dto.Result;
import com.IntelliRecipe.Kilsme.model.User;
import com.IntelliRecipe.Kilsme.service.SmartRecipeService;
import com.IntelliRecipe.Kilsme.vo.CollectedRecipeVo;
import com.IntelliRecipe.Kilsme.vo.RecipeGenerateVo;
import com.IntelliRecipe.Kilsme.vo.RecipeUseResultVo;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/recipe")
public class RecipeController {

    @Autowired
    private SmartRecipeService smartRecipeService;

    @GetMapping("/generate")
    public Result generate(@RequestParam(required = false) String timeNode,
                           @RequestParam(required = false) String demand,
                           HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        RecipeGenerateVo vo = smartRecipeService.generate(user, timeNode, demand);
        return Result.ok(vo);
    }

    @GetMapping("/detail")
    public Result detail(@RequestParam Long recipeId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        RecipeGenerateVo vo = smartRecipeService.getDetail(user.getId(), recipeId);
        if (vo == null) {
            return Result.fail("菜谱不存在");
        }
        return Result.ok(vo);
    }

    @PostMapping("/collect")
    public Result collect(@RequestBody Map<String, Object> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long recipeId = toLong(payload.get("recipeId"));
        if (recipeId == null) {
            return Result.fail("参数错误");
        }
        boolean ok = smartRecipeService.collectRecipe(user.getId(), recipeId);
        return ok ? Result.ok("收藏成功") : Result.fail("收藏失败");
    }

    @PostMapping("/use")
    public Result use(@RequestBody Map<String, Object> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long recipeId = toLong(payload.get("recipeId"));
        if (recipeId == null) {
            return Result.fail("参数错误");
        }
        RecipeUseResultVo useResult = smartRecipeService.useRecipe(user, recipeId);
        if (useResult == null) {
            return Result.fail("菜谱不存在");
        }
        return Result.ok(useResult);
    }

    @PostMapping("/use/check")
    public Result checkUse(@RequestBody Map<String, Object> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long recipeId = toLong(payload.get("recipeId"));
        if (recipeId == null) {
            return Result.fail("参数错误");
        }
        RecipeUseResultVo useResult = smartRecipeService.checkUse(user, recipeId);
        if (useResult == null) {
            return Result.fail("菜谱不存在");
        }
        return Result.ok(useResult);
    }

    @PostMapping("/use/add-missing")
    public Result addMissingToCart(@RequestBody Map<String, Object> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long recipeId = toLong(payload.get("recipeId"));
        if (recipeId == null) {
            return Result.fail("参数错误");
        }
        RecipeUseResultVo useResult = smartRecipeService.addMissingToCart(user, recipeId);
        if (useResult == null) {
            return Result.fail("菜谱不存在");
        }
        return Result.ok(useResult);
    }

    @PostMapping("/feedback")
    public Result feedback(@RequestBody Map<String, Object> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long recipeId = toLong(payload.get("recipeId"));
        Integer rating = toInteger(payload.get("rating"));
        String reviewText = payload.get("reviewText") == null ? "" : String.valueOf(payload.get("reviewText"));
        if (recipeId == null || rating == null || rating < 1 || rating > 5) {
            return Result.fail("参数错误");
        }
        boolean ok = smartRecipeService.feedback(user.getId(), recipeId, rating, reviewText);
        return ok ? Result.ok("评价成功") : Result.fail("评价失败");
    }

    @GetMapping("/collections")
    public Result collections(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        List<CollectedRecipeVo> list = smartRecipeService.listCollectedRecipes(user.getId());
        return Result.ok(list);
    }

    @PostMapping("/collection/delete")
    public Result deleteCollection(@RequestBody Map<String, Object> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long recipeId = toLong(payload.get("recipeId"));
        if (recipeId == null) {
            return Result.fail("参数错误");
        }
        int rows = smartRecipeService.deleteCollectedRecipe(user.getId(), recipeId);
        return rows > 0 ? Result.ok("删除成功") : Result.fail("未找到可删除收藏");
    }

    @PostMapping("/collection/delete-batch")
    public Result batchDeleteCollection(@RequestBody Map<String, Object> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        Object idsObj = payload.get("recipeIds");
        if (!(idsObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return Result.fail("参数错误");
        }
        List<Long> recipeIds = rawList.stream().map(this::toLong).filter(Objects::nonNull).toList();
        if (recipeIds.isEmpty()) {
            return Result.fail("参数错误");
        }
        int rows = smartRecipeService.batchDeleteCollectedRecipes(user.getId(), recipeIds);
        return Result.ok("已删除 " + rows + " 条收藏记录");
    }

    @PostMapping("/image/regenerate")
    public Result regenerateImage(@RequestBody Map<String, Object> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.fail("图片生成功能已关闭");
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}

