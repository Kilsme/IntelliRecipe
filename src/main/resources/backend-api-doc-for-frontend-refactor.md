# IntelliRecipe 后端接口文档（给前端重构）

> 适用场景：前端团队重构页面与状态管理。
> 
> 版本说明：基于当前代码实际接口整理（含 `login/home/recipe/recipe-detail/profile/购物车/冰箱/收藏/去使用流程`）。

---

## 1. 全局约定

### 1.1 鉴权机制
- 使用 `HttpSession`（`credentials: 'include'` 必须带上）。
- 除 `/api/auth/**` 与静态资源外，其它接口均被拦截器鉴权。
- 未登录统一返回：

```json
{
  "success": false,
  "errorMsg": "请先登录",
  "data": null,
  "total": null
}
```

### 1.2 通用返回结构（绝大部分接口）

```json
{
  "success": true,
  "errorMsg": null,
  "data": {},
  "total": null
}
```

字段说明：
- `success`: `true/false`
- `errorMsg`: 失败文案
- `data`: 业务数据
- `total`: 列表总数（当前大部分接口未使用）

### 1.3 频率限制（认证接口）
- `/api/auth/login`、`/api/auth/register`、`/api/auth/session`、`/api/auth/logout` 有限流。
- 超限返回 HTTP 429 + `Result.fail("请求过于频繁，请稍后再试")`。

### 1.4 单位约定
- 冰箱/购物车食材单位仅支持 `g` 或 `kg`。
- 后端入库时会统一换算为 `g`（冰箱库存）。

---

## 2. 数据对象（前端最常用）

### 2.1 User（返回给前端时无密码）

```json
{
  "id": 1,
  "username": "alice",
  "phone": "13800000000",
  "passwordHash": null,
  "heightCm": 165,
  "age": 24,
  "weightKg": 52.5,
  "gender": 0,
  "preferences": "{\"preferred_cuisines\":[\"川菜\",\"粤菜\"]}",
  "allergies": "[\"花生\",\"海鲜\"]",
  "region": "湖南",
  "dietType": "NORMAL",
  "createdAt": "2026-03-22T10:00:00",
  "updatedAt": "2026-03-22T10:30:00"
}
```

### 2.2 FoodVo（冰箱列表）

```json
{
  "userId": 1,
  "ingredientName": "白菜",
  "quantity": 500.0,
  "unit": "g",
  "categoryId": 1,
  "ingredientId": 12,
  "nutrition_info": "{\"fat\":0.2,\"carbs\":3.2,\"protein\":1.5,\"calories\":17}"
}
```

> 注意：历史兼容字段可能出现首字母大写（`IngredientName` / `IngredientId`），前端建议同时兼容。

### 2.3 ListFoodVo（购物车列表）

```json
{
  "listItemId": 101,
  "userId": 1,
  "ingredientName": "牛肉",
  "quantity": 300.0,
  "unit": "g",
  "categoryId": 2,
  "ingredientId": 33
}
```

### 2.4 RecipeGenerateVo（菜谱生成/详情）

```json
{
  "recipeId": 37,
  "title": "黑椒牛肉蔬菜碗",
  "description": "高蛋白低脂晚餐",
  "timeNode": "晚餐",
  "douyinUrl": "https://www.douyin.com/search/黑椒牛肉蔬菜碗",
  "flavorTags": ["黑椒", "鲜香", "低脂"],
  "canUseNow": false,
  "ingredients": [
    {
      "ingredientId": 33,
      "ingredientName": "牛肉",
      "categoryId": 2,
      "quantity": 260,
      "unit": "g"
    }
  ],
  "missingIngredients": [
    {
      "ingredientId": 33,
      "ingredientName": "牛肉",
      "categoryId": 2,
      "quantity": 260,
      "unit": "g"
    }
  ],
  "replacements": [
    {
      "originalIngredientName": "西兰花",
      "replacementIngredientName": "白菜",
      "reason": "同属蔬菜，可替代纤维来源"
    }
  ],
  "steps": ["步骤1", "步骤2"]
}
```

### 2.5 RecipeUseResultVo（去使用流程）

```json
{
  "canUse": false,
  "detailUrl": "recipe-detail.html?id=37",
  "message": "缺少食材，已加入购物车",
  "missingIngredients": [
    {
      "ingredientId": 33,
      "ingredientName": "牛肉",
      "categoryId": 2,
      "quantity": 260,
      "unit": "g"
    }
  ]
}
```

### 2.6 CollectedRecipeVo（收藏列表）

```json
{
  "recipeId": 37,
  "title": "黑椒牛肉蔬菜碗",
  "description": "高蛋白低脂晚餐",
  "collectedAt": "2026-03-22 14:10:23"
}
```

---

## 3. 接口清单（按页面分组）

## 3.1 登录页 `login.html`

### 3.1.1 登录
- **接口**: `POST /api/auth/login`
- **鉴权**: 否
- **请求体**:

```json
{
  "username": "alice",
  "password": "123456"
}
```

- **成功返回**: `Result.ok(User)`
- **失败返回**: `用户名和密码不能为空` / `用户名或者密码错误`
- **前端展示建议**:
  - 成功：提示后跳 `home.html`
  - 失败：展示 `errorMsg`

### 3.1.2 注册
- **接口**: `POST /api/auth/register`
- **鉴权**: 否
- **请求体**:

```json
{
  "username": "alice",
  "phone": "13800000000",
  "password": "123456"
}
```

- **成功返回**: `Result.ok("注册成功")`
- **失败返回**: 参数校验失败或业务异常文案
- **前端展示建议**:
  - 成功：提示后切换到登录态
  - 失败：显示 `errorMsg`

### 3.1.3 会话检查
- **接口**: `GET /api/auth/session`
- **鉴权**: 否（但用于判断登录状态）
- **成功返回**: `Result.ok(User)`
- **失败返回**: `未登录`
- **前端展示建议**: 用于首屏判断是否跳转登录

### 3.1.4 退出
- **接口**: `POST /api/auth/logout`
- **鉴权**: 否
- **成功返回**: `Result.ok("已退出登录")`
- **前端展示建议**: 退出后跳 `login.html`

---

## 3.2 首页 `home.html`（冰箱 + 购物车 + 收藏快捷入口）

### 3.2.1 查看冰箱
- **接口**: `GET /fridgeView`
- **鉴权**: 是
- **请求参数**: 无
- **成功返回**: `Result.ok(FoodVo[])`
- **前端展示字段**:
  - `ingredientName`
  - `quantity + unit`
  - `categoryId`（映射为中文大类）
  - `nutrition_info`（JSON 转中文：热量/蛋白质/脂肪/碳水/膳食纤维）

### 3.2.2 食材字典（9大类）
- **接口**: `GET /AllFoodView`
- **鉴权**: 是
- **返回结构（非 Result 包装）**: `IngredientDict[][]`
  - 外层数组长度 9（分类顺序）
  - 每项包含 `id/name/categoryId/nutritionInfo/seasonTags`
- **前端展示建议**:
  - 左右或顶部分类 Tab + 下拉食材选择
  - 分类索引兼容 0-8（前端）与 1-9（数据库）

### 3.2.3 添加食材到冰箱
- **接口**: `GET /addFood`
- **鉴权**: 是
- **Query 参数**:
  - `ingredientId` (Long)
  - `quantity` (double)
  - `unit` (`g` 或 `kg`)
- **成功返回**: `Result.ok("添加成功")`
- **失败返回**:
  - `食材不存在`
  - `单位仅支持 g 或 kg，且数量必须大于 0`
  - `历史单位异常，请先清理该食材后重试`
- **前端展示建议**:
  - 成功后刷新冰箱列表
  - 失败提示错误文案

### 3.2.4 删除冰箱食材
- **接口**: `GET /deleteFoodFromFridge`
- **鉴权**: 是
- **Query 参数**: `ingredientId`
- **成功返回**: `Result.ok("删除成功")`
- **失败返回**: `参数错误` / `食材不存在或已删除`
- **前端展示建议**:
  - 每行提供删除按钮
  - 删除后局部刷新

### 3.2.5 查看购物车
- **接口**: `GET /foodListView`
- **鉴权**: 是
- **成功返回**: `Result.ok(ListFoodVo[])`
- **前端展示字段**:
  - `listItemId`（勾选/删除关键键）
  - `ingredientName`
  - `quantity + unit`
  - `categoryId`（分组展示）

### 3.2.6 添加食材到购物车
- **接口**: `GET /addFoodToList`
- **鉴权**: 是
- **Query 参数**:
  - `ingredientId`
  - `quantity`
  - `unit`
  - `categoryId`（支持 0-8 或 1-9，后端会归一化）
- **成功返回**: `Result.ok("添加成功")`
- **失败返回**: `食材不存在` / `创建购物车失败,请重新创建` / `添加食品失败请重新添加`
- **说明**: 购物车允许重复添加同一食材。

### 3.2.7 勾选购物车单条（入冰箱并移除）
- **接口**: `GET /finishFoodlist`
- **鉴权**: 是
- **Query 参数**: `listItemId`
- **成功返回**: `Result.ok("已加入冰箱并移出购物车")`
- **失败返回**:
  - `参数错误`
  - `购物车不存在`
  - `购物车条目不存在或已删除`
  - `购物车条目单位异常，仅支持 g 或 kg`
- **前端展示建议**:
  - 仅允许单个 checkbox 同时勾选
  - 成功后同时刷新购物车和冰箱

### 3.2.8 购物车删除（不入冰箱）
- **接口**: `GET /deleteFoodFromList`
- **鉴权**: 是
- **Query 参数**: `listItemId`
- **成功返回**: `Result.ok("删除成功")`
- **失败返回**: `条目不存在或已删除`
- **前端展示建议**:
  - 三点菜单触发删除

---

## 3.3 智能菜谱页 `recipe.html`

### 3.3.1 生成菜谱
- **接口**: `GET /api/recipe/generate`
- **鉴权**: 是
- **Query 参数**:
  - `timeNode`（可空，如：早餐/午餐/晚餐/夜宵）
  - `demand`（可空，用户目标：减脂/增肌/川菜等）
- **成功返回**: `Result.ok(RecipeGenerateVo)`
- **前端展示字段**:
  - 标题 `title`
  - 描述 `description`
  - 风味标签 `flavorTags`
  - 食材 `ingredients`
  - 缺料 `missingIngredients`
  - 替换说明 `replacements`
  - 步骤 `steps`
  - 抖音链接 `douyinUrl`
  - 即时可用标记 `canUseNow`

### 3.3.2 收藏菜谱
- **接口**: `POST /api/recipe/collect`
- **鉴权**: 是
- **请求体**:

```json
{ "recipeId": 37 }
```

- **成功返回**: `Result.ok("收藏成功")`
- **失败返回**: `参数错误` / `收藏失败`

### 3.3.3 去使用前检查
- **接口**: `POST /api/recipe/use/check`
- **鉴权**: 是
- **请求体**:

```json
{ "recipeId": 37 }
```

- **成功返回**: `Result.ok(RecipeUseResultVo)`
- **前端展示建议**:
  - `canUse=true`：允许直接调用 `/use`
  - `canUse=false`：弹窗展示 `message + missingIngredients`

### 3.3.4 缺料加入购物车
- **接口**: `POST /api/recipe/use/add-missing`
- **鉴权**: 是
- **请求体**:

```json
{ "recipeId": 37 }
```

- **成功返回**: `Result.ok(RecipeUseResultVo)`（通常 `message` 用于提示）
- **前端展示建议**:
  - 提示“已加入购物车”
  - 给“去购物车处理”与“手动跳详情页”入口

### 3.3.5 真正去使用（扣减冰箱）
- **接口**: `POST /api/recipe/use`
- **鉴权**: 是
- **请求体**:

```json
{ "recipeId": 37 }
```

- **成功返回**: `Result.ok(RecipeUseResultVo)`
  - `canUse=true`：已扣减库存，可跳详情
  - `canUse=false`：仍缺料，继续引导补货

### 3.3.6 查看收藏列表
- **接口**: `GET /api/recipe/collections`
- **鉴权**: 是
- **成功返回**: `Result.ok(CollectedRecipeVo[])`
- **前端展示字段**:
  - `title`
  - `description`
  - `collectedAt`
  - 操作按钮：查看详情 / 去使用 / 删除

### 3.3.7 删除单个收藏
- **接口**: `POST /api/recipe/collection/delete`
- **鉴权**: 是
- **请求体**:

```json
{ "recipeId": 37 }
```

- **成功返回**: `Result.ok("删除成功")`
- **失败返回**: `未找到可删除收藏`

### 3.3.8 批量删除收藏
- **接口**: `POST /api/recipe/collection/delete-batch`
- **鉴权**: 是
- **请求体**:

```json
{ "recipeIds": [37, 38, 39] }
```

- **成功返回**: `Result.ok("已删除 X 条收藏记录")`
- **失败返回**: `参数错误`

### 3.3.9 图片重生成（已关闭）
- **接口**: `POST /api/recipe/image/regenerate`
- **鉴权**: 是
- **返回**: `Result.fail("图片生成功能已关闭")`
- **前端处理**: 不展示图片入口，不调用该接口。

---

## 3.4 菜谱详情页 `recipe-detail.html`

### 3.4.1 菜谱详情
- **接口**: `GET /api/recipe/detail`
- **鉴权**: 是
- **Query 参数**: `recipeId`
- **成功返回**: `Result.ok(RecipeGenerateVo)`
- **前端展示**:
  - 基本信息：`title/description/douyinUrl`
  - `ingredients` 列表
  - `steps` 步骤列表

### 3.4.2 提交评价
- **接口**: `POST /api/recipe/feedback`
- **鉴权**: 是
- **请求体**:

```json
{
  "recipeId": 37,
  "rating": 5,
  "reviewText": "口味不错，步骤清晰"
}
```

- **成功返回**: `Result.ok("评价成功")`
- **失败返回**: `参数错误` / `评价失败`
- **前端展示建议**:
  - 评分范围 1~5
  - 成功后提示即可

### 3.4.3 详情页去使用链路
- 与 `recipe.html` 相同，复用 3 个接口：
  - `POST /api/recipe/use/check`
  - `POST /api/recipe/use/add-missing`
  - `POST /api/recipe/use`
- **前端行为建议**:
  - 缺料弹窗内提供：加入购物车、去购物车、购买后再试

---

## 3.5 个人中心页 `profile.html`

### 3.5.1 获取用户信息
- **接口**: `GET /api/user/info`
- **鉴权**: 是
- **成功返回**: `Result.ok(User)`
- **前端展示建议**:
  - 只读：`username`、`phone`
  - 可编辑：`gender/age/heightCm/weightKg/region/dietType/preferences/allergies`
  - `gender`: `0=女`, `1=男`
  - 不展示密码字段

### 3.5.2 更新用户信息
- **接口**: `POST /api/user/updateUser`
- **鉴权**: 是
- **请求体（建议）**:

```json
{
  "username": "alice",
  "phone": "13800000000",
  "gender": 0,
  "age": 24,
  "heightCm": 165,
  "weightKg": 52.5,
  "region": "湖南",
  "dietType": "LOSE_WEIGHT",
  "preferences": "{\"preferred_cuisines\":[\"川菜\",\"湘菜\"]}",
  "allergies": "[\"花生\"]"
}
```

- **后端实际生效字段**:
  - `heightCm` `age` `weightKg` `gender` `preferences` `allergies` `region` `dietType`
  - `username` `phone` 在后端保持只读（不更新）
- **成功返回**: `Result.ok(更新后的User)`

---

## 4. 页面级“前端传什么、展示什么”速查

## 4.1 `login.html`
- 传给后端：
  - 登录：`username/password`
  - 注册：`username/phone/password`
- 展示后端：
  - 成功/失败提示文案

## 4.2 `home.html`
- 传给后端：
  - 冰箱添加：`ingredientId/quantity/unit`
  - 购物车添加：`ingredientId/quantity/unit/categoryId`
  - 完成购买：`listItemId`
  - 删除：`ingredientId` 或 `listItemId`
- 展示后端：
  - 冰箱：食材名、数量单位、分类、营养信息
  - 购物车：分组、数量、三点菜单删除、单选勾选处理
  - 收藏快捷卡片：标题、描述、收藏时间

## 4.3 `recipe.html`
- 传给后端：
  - 生成：`timeNode/demand`
  - 收藏：`recipeId`
  - 去使用链路：`recipeId`
  - 批量删除：`recipeIds[]`
- 展示后端：
  - 菜谱主信息、步骤、缺料、替换说明、抖音链接、收藏列表

## 4.4 `recipe-detail.html`
- 传给后端：
  - 详情：`recipeId`
  - 评价：`recipeId/rating/reviewText`
  - 去使用链路：`recipeId`
- 展示后端：
  - 详情、食材、步骤、评价提交状态、缺料弹窗

## 4.5 `profile.html`
- 传给后端：
  - `gender/age/heightCm/weightKg/region/dietType/preferences/allergies`
- 展示后端：
  - 用户基础信息与健康饮食偏好

---

## 5. 关键联调注意事项（必须看）
- `AllFoodView` 不是 `Result` 包装，直接返回二维数组。
- 鉴权依赖 Cookie Session，前端所有请求都要 `credentials: 'include'`。
- 购物车与冰箱接口当前使用 `GET + Query`，重构时可做 API 适配层统一封装。
- 分类 ID 兼容两种索引：前端可能用 `0-8`，数据库是 `1-9`。
- 字段名历史兼容：`ingredientName` 与 `IngredientName` 可能并存，前端建议兜底处理。
- 图片生成功能已关闭：不要展示图片区域，不要调用 `/api/recipe/image/regenerate`。

---

## 6. 推荐前端 API 封装清单（便于重构）
- `authApi.login/register/session/logout`
- `userApi.getInfo/update`
- `fridgeApi.view/add/delete`
- `dictApi.allFoodView`
- `cartApi.view/add/finish/delete`
- `recipeApi.generate/detail/collect/collections/delete/deleteBatch/checkUse/addMissing/use/feedback`

这样可以在重构时把“页面逻辑”和“接口细节”分离，后续后端改 REST 风格时只需要改这一层。

