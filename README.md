# IntelliRecipe 后端技术与架构说明
<img width="2510" height="1250" alt="屏幕截图 2026-03-27 161834" src="https://github.com/user-attachments/assets/dc31678d-6af7-451f-9886-0875796b19a2" />

## 1. 后端使用的主要技术

### 1.1 基础框架
- Spring Boot 3.3.x：应用启动、配置管理、Web 容器。
- Spring MVC：REST 接口实现。
- Spring Session（HttpSession 语义）：登录态维护。
- MyBatis：数据访问层（Mapper + SQL）。

### 1.2 数据与存储
- MySQL：业务主数据（用户、菜谱、收藏、冰箱、购物车等）。
- Redis：向量检索与缓存支撑（结合 LangChain4j 社区组件）。

### 1.3 AI 与检索增强
- LangChain4j（open-ai starter / spring-boot starter / easy-rag）：
  - 大模型调用封装。
  - Embedding 与 RAG 检索链路。
- DashScope compatible-mode（Qwen 系列）：
  - 文本模型、Embedding 模型接入。
  - 图片功能已禁用，不作为当前业务链路。
- PDFBox：菜谱知识 PDF 文本提取。

### 1.4 工程与质量
- Lombok：减少样板代码。
- JUnit：基础序列化/VO 测试。
- 统一返回体 `Result`：前后端联调成本更低。

## 2. 后端分层与模块
- `controller`：接收请求、参数校验、会话校验、返回 `Result`。
- `service`：业务编排（智能推荐、库存扣减、缺料替换、收藏管理）。
- `mappper`：MyBatis Mapper，直接访问数据库。
- `model`：数据库实体。
- `vo/dto`：前端展示对象与通用响应结构。
- `config`：鉴权拦截器、限流拦截器、静态资源映射。
- `aiService`：向量检索、推荐草案生成、用户历史向量刷新。

## 3. 核心流程（后端视角）

### 3.1 菜谱生成主链路
1. `RecipeController.generate` 接收 `timeNode + demand`。
2. `SmartRecipeService` 基于用户画像构造查询。
3. `RedisPdfVectorService` 检索知识库片段；`LocalUserVectorService` 检索历史行为。
4. `RecipeRecommendationAiService` 生成菜谱草案（标题/步骤/风味）。
5. 结合食材字典、过敏与饮食偏好落地成结构化菜谱。
6. 保存到 MySQL，返回 `RecipeGenerateVo`。

### 3.2 菜谱使用链路
1. `/use/check` 判断冰箱库存是否满足。
2. 若缺料：返回缺料列表，支持 `/use/add-missing` 加入购物车。
3. `/use` 再次校验后扣减库存，记录已使用行为。

### 3.3 收藏与回流链路
1. 收藏行为写入 `UserCollection`。
2. 支持单条和批量删除收藏。
3. 用户行为可回流到本地向量服务，影响后续推荐。

## 4. 项目突出点

### 4.1 推荐不是“纯大模型直出”
- 先检索（知识 + 历史）再生成，减少幻觉、增强可控性。

### 4.2 食材侧约束强
- 候选食材严格来自字典库。
- 叠加过敏、饮食类型、库存状态，输出可执行菜谱。

### 4.3 闭环能力完整
- 从“生成菜谱”到“检查缺料-加购物车-购买入冰箱-去使用-评价”形成完整闭环。

### 4.4 工程化细节落地
- 会话鉴权 + 频控拦截。
- 统一返回结构，便于前端稳定处理。
- 关键流程模块化清晰，便于扩展与交接。

## 5. 当前约束与后续建议
- 图片生成链路已关闭：前端不展示、后端不返回 `imageUrl`。
- HomeController 存在部分 GET 写操作，建议逐步改为 POST/PUT/DELETE。
- 可增加接口分层文档（OpenAPI）与端到端测试，提升回归效率。
- 可引入指标监控（接口耗时、命中率、推荐转化）做持续优化。

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
