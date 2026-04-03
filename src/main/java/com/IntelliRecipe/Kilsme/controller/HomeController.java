package com.IntelliRecipe.Kilsme.controller;

import com.IntelliRecipe.Kilsme.dto.Result;
import com.IntelliRecipe.Kilsme.model.*;
import com.IntelliRecipe.Kilsme.service.FridgeService;
import com.IntelliRecipe.Kilsme.service.IngredientService;
import com.IntelliRecipe.Kilsme.service.ListService;
import com.IntelliRecipe.Kilsme.vo.FoodVo;
import com.IntelliRecipe.Kilsme.vo.ListFoodVo;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@RestController
public class HomeController {
    @Autowired
    private FridgeService fridgeService;
    @Autowired
    private IngredientService ingredientService;
    @Autowired
    private ListService listService;

    @GetMapping("/fridgeView")
    public Result fridge(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        //向前端传递冰箱中有的食材
        Long userId = user.getId();
        List<UserFridge> fridgeByUserId = fridgeService.getFridgeByUserId(userId);
        List<FoodVo> foodVos = new ArrayList<>();
        for (UserFridge temp : fridgeByUserId) {
            Long ingredientId = temp.getIngredientId();
            IngredientDict ingredientById = ingredientService.getIngredientById(ingredientId);
            FoodVo foodVo = new FoodVo();
            foodVo.setCategoryId(ingredientById.getCategoryId());
            foodVo.setUnit(temp.getUnit());
            foodVo.setQuantity(temp.getQuantity());
            foodVo.setIngredientName(ingredientById.getName());
            foodVo.setUserId(userId);
            foodVo.setIngredientId(ingredientId);
            foodVo.setNutrition_info(ingredientById.getNutritionInfo());
            foodVos.add(foodVo);
        }

        return Result.ok(foodVos);
    }

    //进行展示的接口
    @GetMapping("AllFoodView")
    public List<List<IngredientDict>> allFoodView() {
        List<List<IngredientDict>> result = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            List<IngredientDict> temp = ingredientService.getIngredientsByCategoryId(i + 1);
            result.add(temp);
        }
        return result;
    }


    //进行添加食材的接口
    @GetMapping("/addFood")
    public Result addFood(String unit, double quantity, Long ingredientId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (ingredientId == null || ingredientService.getIngredientById(ingredientId) == null) {
            return Result.fail("食材不存在");
        }
        Double inputGrams = toGrams(quantity, unit);
        if (inputGrams == null) {
            return Result.fail("单位仅支持 g 或 kg，且数量必须大于 0");
        }
        inputGrams = roundToTwoDecimals(inputGrams);

        Long userId = user.getId();
        //这里需要进行添加食材的操作
        //首先需要查询冰箱中是否已经有这个食材了，如果有的话就进行更新，如果没有的话就进行添加
        List<UserFridge> fridgeByUserId = fridgeService.getFridgeByUserId(userId);
        for (UserFridge temp : fridgeByUserId) {
            if (temp.getIngredientId().equals(ingredientId)) {
                //如果已经有了这个食材了，就进行更新
                Double oldGrams = toGrams(temp.getQuantity(), temp.getUnit());
                if (oldGrams == null) {
                    return Result.fail("历史单位异常，请先清理该食材后重试");
                }
                temp.setQuantity(roundToTwoDecimals(oldGrams + inputGrams));
                temp.setUnit("g");
                //这里需要进行更新操作
                fridgeService.updateFridge(temp);
                return Result.ok("添加成功");
            }
        }
        //如果没有这个食材了，就进行添加
        UserFridge userFridge = new UserFridge();
        userFridge.setUserId(userId);
        userFridge.setIngredientId(ingredientId);
        userFridge.setQuantity(inputGrams);
        userFridge.setUnit("g");
        //这里需要进行添加操作
        fridgeService.addFridge(userFridge);
        return Result.ok("添加成功");

    }

    @GetMapping("/deleteFoodFromFridge")
    public Result deleteFoodFromFridge(Long ingredientId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (ingredientId == null) {
            return Result.fail("参数错误");
        }
        int res = fridgeService.deleteFridgeFood(user.getId(), ingredientId);
        if (res == 0) {
            return Result.fail("食材不存在或已删除");
        }
        return Result.ok("删除成功");
    }

    //进行购物车的展示
    @GetMapping("/foodListView")
    public Result FoodListView(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        //根据id进行查询shoplist
        ShoppingList shoppingList = listService.selectByUserId(user.getId());
        if (shoppingList == null) {
            return Result.ok(new ArrayList<>());
        }
        //根据shopListID进行查询shopListItem
        List<ShoppingListItem> shoppingListItems = listService.selectItemByListId(shoppingList.getId());
        //进行vo展示
        List<ListFoodVo>listFoodVos=new ArrayList<>();
        for(ShoppingListItem temp:shoppingListItems){
            ListFoodVo listFoodVo = new ListFoodVo();
            listFoodVo.setUnit(temp.getUnit());
            listFoodVo.setQuantity(temp.getQuantityNeeded());
            listFoodVo.setIngredientId(temp.getIngredientId());
            listFoodVo.setCategoryId(temp.getCategoryId());
            listFoodVo.setUserId(user.getId());
            listFoodVo.setIngredientName(ingredientService.getIngredientById(temp.getIngredientId()).getName());
            listFoodVo.setListItemId(temp.getId());
            listFoodVos.add(listFoodVo);
        }
        return Result.ok(listFoodVos);
    }


    //进行添加食材到购物车
    @GetMapping("/addFoodToList")
    public Result addFoodToList(String unit, double quantity, Long ingredientId, HttpSession session, int categoryId) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }

        IngredientDict ingredient = ingredientService.getIngredientById(ingredientId);
        if (ingredient == null) {
            return Result.fail("食材不存在");
        }

        // 兼容前端可能传 0-8 或 1-9，最终保证写入 1-9；无效值则以食材字典分类兜底
        int normalizedCategoryId = normalizeCategoryId(categoryId, ingredient.getCategoryId());

        //先查询用户有无购物车的列表
        Long userId = user.getId();
        ShoppingList shoppingList = listService.selectByUserId(userId);
        if (shoppingList == null) {
            //进行插入
            int num = listService.insertShopList(userId);
            if (num == 0) {
                return Result.fail("创建购物车失败,请重新创建");
            }
            shoppingList = listService.selectByUserId(userId);
            if (shoppingList == null) {
                return Result.fail("创建购物车失败,请重新创建");
            }
        }
        //有购物车 进行添加进行
        int res = listService.addFoodToList(unit, quantity, ingredientId, shoppingList.getId(), normalizedCategoryId);

        if (res == 0) {
            return Result.fail("添加食品失败请重新添加");
        }
        return Result.ok("添加成功");

    }

    private int normalizeCategoryId(int rawCategoryId, Integer ingredientCategoryId) {
        if (rawCategoryId >= 1 && rawCategoryId <= 9) {
            return rawCategoryId;
        }
        if (rawCategoryId >= 0 && rawCategoryId <= 8) {
            return rawCategoryId + 1;
        }
        if (ingredientCategoryId != null && ingredientCategoryId >= 1 && ingredientCategoryId <= 9) {
            return ingredientCategoryId;
        }
        return 9;
    }

    private Double toGrams(Double quantity, String unit) {
        if (quantity == null || quantity <= 0 || unit == null) {
            return null;
        }
        String normalized = unit.trim().toLowerCase();
        if ("g".equals(normalized)) {
            return quantity;
        }
        if ("kg".equals(normalized)) {
            return quantity * 1000;
        }
        return null;
    }

    private Double roundToTwoDecimals(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
    // 勾选购物车单条后：将该条数量加入冰箱并从购物车移除（逻辑删除）
    @Transactional
    @GetMapping("/finishFoodlist")
    public Result finishFoodList(Long listItemId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (listItemId == null) {
            return Result.fail("参数错误");
        }

        ShoppingList shoppingList = listService.selectByUserId(user.getId());
        if (shoppingList == null) {
            return Result.fail("购物车不存在");
        }

        ShoppingListItem item = listService.selectItemByIdAndListId(listItemId, shoppingList.getId());
        if (item == null) {
            return Result.fail("购物车条目不存在或已删除");
        }

        String itemUnit = item.getUnit() == null ? "g" : item.getUnit();
        Double addGrams = toGrams(item.getQuantityNeeded(), itemUnit);
        if (addGrams == null) {
            return Result.fail("购物车条目单位异常，仅支持 g 或 kg");
        }
        addGrams = roundToTwoDecimals(addGrams);

        List<UserFridge> fridgeByUserId = fridgeService.getFridgeByUserId(user.getId());
        boolean merged = false;
        for (UserFridge temp : fridgeByUserId) {
            if (temp.getIngredientId().equals(item.getIngredientId())) {
                Double oldGrams = toGrams(temp.getQuantity(), temp.getUnit());
                if (oldGrams == null) {
                    return Result.fail("冰箱历史单位异常，请先清理该食材后重试");
                }
                temp.setQuantity(roundToTwoDecimals(oldGrams + addGrams));
                temp.setUnit("g");
                fridgeService.updateFridge(temp);
                merged = true;
                break;
            }
        }

        if (!merged) {
            UserFridge userFridge = new UserFridge();
            userFridge.setUserId(user.getId());
            userFridge.setIngredientId(item.getIngredientId());
            userFridge.setQuantity(addGrams);
            userFridge.setUnit("g");
            fridgeService.addFridge(userFridge);
        }

        int deleteRes = listService.logicalDeleteItem(listItemId, shoppingList.getId());
        if (deleteRes == 0) {
            throw new IllegalStateException("购物车条目删除失败");
        }
        return Result.ok("已加入冰箱并移出购物车");
    }

    // 三点菜单删除：仅从购物车移除（逻辑删除）
    @GetMapping("/deleteFoodFromList")
    public Result deleteFoodFromList(Long listItemId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (listItemId == null) {
            return Result.fail("参数错误");
        }

        ShoppingList shoppingList = listService.selectByUserId(user.getId());
        if (shoppingList == null) {
            return Result.fail("购物车不存在");
        }

        int res = listService.logicalDeleteItem(listItemId, shoppingList.getId());
        if (res == 0) {
            return Result.fail("条目不存在或已删除");
        }
        return Result.ok("删除成功");
    }




}
