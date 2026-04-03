# 智能菜谱功能说明

## 已实现能力

- Redis 向量检索：启动后自动扫描 `src/main/resources/content` 下 `pdf/txt` 内容并向量化缓存。
- 本地向量检索：基于用户收藏/浏览/做过历史构建本地向量。
- 生成推荐：综合用户画像 + 时段 + 双路检索结果，生成文本步骤与抖音搜索链接。
- 食材约束：生成食材严格从 `ingredients_dict` 匹配。
- 交互闭环：支持收藏、去使用（自动加入购物车并跳转详情）、使用后评价。

## 新增页面

- `src/main/resources/static/recipe.html`
- `src/main/resources/static/recipe-detail.html`

首页入口：`home.html` 中“智能生成菜谱”。

## 新增接口

- `GET /api/recipe/generate?timeNode=早餐|午餐|晚餐|夜宵`
- `GET /api/recipe/detail?recipeId=xxx`
- `POST /api/recipe/collect`
- `POST /api/recipe/use`
- `POST /api/recipe/feedback`

## 数据准备

请先执行：

- `src/main/resources/sql/smart_recipe_feature.sql`

并确保：

- `ingredients_dict` 已初始化（你提供的 INSERT IGNORE 数据）
- Redis 可访问（默认 `localhost:6379`）
- `application.yml` 中 OpenAI 兼容 API Key 已配置

## 快速联调（示例）

```bash
curl -X GET "http://localhost:8082/api/recipe/generate?timeNode=晚餐" -b cookie.txt -c cookie.txt

curl -X POST "http://localhost:8082/api/recipe/collect" \
  -H "Content-Type: application/json" \
  -d '{"recipeId": 1}' -b cookie.txt -c cookie.txt

curl -X POST "http://localhost:8082/api/recipe/use" \
  -H "Content-Type: application/json" \
  -d '{"recipeId": 1}' -b cookie.txt -c cookie.txt

curl -X POST "http://localhost:8082/api/recipe/feedback" \
  -H "Content-Type: application/json" \
  -d '{"recipeId": 1, "rating": 5, "reviewText": "很好做"}' -b cookie.txt -c cookie.txt
```

> 注意：项目当前环境若无 `mvn`/`mvnw`，请在 IDE 内直接运行 Spring Boot 启动类。

