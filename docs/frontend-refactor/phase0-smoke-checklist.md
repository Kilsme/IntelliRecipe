# Phase 0 - 接口冒烟清单

> 目标：在迁移开始前验证关键链路可用，迁移后按同样清单回归。

## 预置

- 启动后端服务。
- 使用浏览器或工具保持 Cookie Session（`credentials: include`）。

## 冒烟项

1. 登录：`POST /api/auth/login`
2. 会话：`GET /api/auth/session`
3. 食材字典：`GET /AllFoodView`（raw数组）
4. 使用前检查：`POST /api/recipe/use/check`
5. 缺料加购：`POST /api/recipe/use/add-missing`
6. 去使用：`POST /api/recipe/use`
7. 购物车完成：`GET /finishFoodlist?listItemId=...`

## 验收要点

- 401 时前端可识别并跳转登录。
- 429 时前端可识别并提示请求频繁。
- `AllFoodView` 解析为原始数组，不走 `Result.success` 分支。
- 历史 GET+query 接口行为不变。
