# IntelliRecipe 前后端需求文档

## 1. 文档目标
- 用于统一前端与后端的功能边界、接口约定、异常处理与联调规则。
- 适用对象：产品、前端开发、后端开发、测试。
- 当前版本说明：图片生成与展示功能已关闭，相关入口保留“已关闭”提示。

## 2. 业务范围
- 用户注册、登录、会话保持、登出。
- 用户画像与偏好管理。
- 冰箱库存管理（查看、添加、删除）。
- 购物车管理（查看、添加、删除、批量处理、购买入冰箱）。
- 智能菜谱生成、详情查看、收藏管理、使用流程、评价。

## 3. 角色与权限
- 游客：仅可访问静态页面，调用受保护接口会返回“请先登录”。
- 登录用户：可访问全部业务接口，数据隔离到当前用户。

## 4. 页面需求（前端）

### 4.1 `login.html`
- 支持注册与登录。
- 登录成功后跳转首页。
- 失败时展示后端 `errorMsg`。

### 4.2 `home.html`
- 展示冰箱与购物车数据。
- 支持食材加减、购物车购买入冰箱。
- 从购物车返回菜谱使用场景（带 `returnRecipeId` 参数）。

### 4.3 `recipe.html`
- 支持按时段与目标生成菜谱。
- 展示标题、描述、风味标签、食材、步骤、抖音教程链接。
- 支持收藏、去使用、收藏列表、删除收藏、批量删除。
- 图片功能：不展示图片、不触发图片生成接口。

### 4.4 `recipe-detail.html`
- 展示菜谱详情、食材清单、步骤、评价入口。
- 支持“去使用”前置检查：缺料时弹窗提示并可一键加购物车。
- 支持“返回上一页/返回智能菜谱/返回首页/卡片内返回”多入口返回。

### 4.5 `profile.html`
- 展示与编辑用户基础信息、饮食偏好、过敏信息等。

## 5. 后端接口需求

### 5.1 认证与会话（`/api/auth`）
- `POST /api/auth/login`：登录。
- `POST /api/auth/register`：注册。
- `GET /api/auth/session`：会话状态检查。
- `POST /api/auth/logout`：退出登录。

### 5.2 用户信息（`/api/user`）
- `GET /api/user/info`：获取用户信息。
- `POST /api/user/updateUser`：更新用户信息。

### 5.3 菜谱（`/api/recipe`）
- `GET /api/recipe/generate`：生成菜谱。
- `GET /api/recipe/detail`：菜谱详情。
- `POST /api/recipe/collect`：收藏菜谱。
- `POST /api/recipe/use/check`：使用前检查。
- `POST /api/recipe/use/add-missing`：缺料加购物车。
- `POST /api/recipe/use`：实际使用并扣减库存。
- `POST /api/recipe/feedback`：提交评价。
- `GET /api/recipe/collections`：获取收藏列表。
- `POST /api/recipe/collection/delete`：删除单条收藏。
- `POST /api/recipe/collection/delete-batch`：批量删除收藏。
- `POST /api/recipe/image/regenerate`：返回“图片生成功能已关闭”。

### 5.4 冰箱与购物车（HomeController）
- `GET /fridgeView`、`GET /addFood`、`GET /deleteFoodFromFridge`
- `GET /foodListView`、`GET /addFoodToList` 等
- 说明：历史接口以 GET 为主，参数风格不统一；后续可逐步 REST 化。

## 6. 数据与状态约定
- 通用返回结构：`Result`（`success`、`data`、`errorMsg`）。
- 未登录统一返回失败消息：`请先登录`。
- 菜谱生成结果不再包含 `imageUrl` 字段。
- 食材单位内部统一按 g 管理，支持前端输入 `g`/`kg`。

## 7. 核心业务流程

### 7.1 智能生成流程
1. 前端提交 `timeNode + demand`。
2. 后端拼接用户画像查询，进行 PDF 向量检索 + 用户历史检索。
3. 结合食材字典、过敏与饮食偏好筛选候选食材。
4. AI 生成草案（标题、步骤、风味），落库并返回前端。

### 7.2 去使用流程
1. 前端调用 `/use/check`。
2. 若缺料：弹窗展示缺料，用户可 `/use/add-missing`。
3. 若可用：调用 `/use`，扣减冰箱库存并记录行为。

## 8. 非功能需求
- 安全：基于 Session 鉴权，关键接口限流拦截。
- 性能：检索与推荐接口响应目标 < 3s（依赖模型与向量检索状态）。
- 可维护性：前后端统一错误码与错误文案，便于联调排查。
- 可观测性：关键流程打印结构化日志（生成、使用、收藏、库存变更）。

## 9. 联调与验收清单
- 登录后生成菜谱成功，返回包含标题/食材/步骤。
- 去使用场景能正确区分“可用/缺料”。
- 缺料加购物车后可在购物车页看到对应食材。
- 收藏与批量删除接口可正常生效。
- 详情页返回按钮行为符合预期。
- 图片相关前端区域与后端返回均已移除/禁用。

