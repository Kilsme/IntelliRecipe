# Phase 0 - API兼容约定表

## 1. 返回模式约定

| API模块 | 接口 | 方法 | 返回模式 |
|---|---|---|---|
| `authApi` | `/api/auth/login` | POST | `result` |
| `authApi` | `/api/auth/register` | POST | `result` |
| `authApi` | `/api/auth/session` | GET | `result` |
| `authApi` | `/api/auth/logout` | POST | `result` |
| `userApi` | `/api/user/info` | GET | `result` |
| `userApi` | `/api/user/updateUser` | POST | `result` |
| `recipeApi` | `/api/recipe/*` | GET/POST | `result` |
| `fridgeApi` | `/fridgeView` | GET | `result` |
| `fridgeApi` | `/addFood` | GET + query | `result` |
| `fridgeApi` | `/deleteFoodFromFridge` | GET + query | `result` |
| `cartApi` | `/foodListView` | GET | `result` |
| `cartApi` | `/addFoodToList` | GET + query | `result` |
| `cartApi` | `/finishFoodlist` | GET + query | `result` |
| `cartApi` | `/deleteFoodFromList` | GET + query | `result` |
| `dictApi` | `/AllFoodView` | GET | `raw` |

## 2. 错误归一策略

- `401`：识别为 `session` 错误，页面按“登录失效”处理并跳转登录页。
- `429`：识别为 `rate_limit` 错误，页面展示“请求频繁”提示。
- 网络异常：识别为 `network` 错误，页面统一展示网络失败提示。
- 业务失败：`success=false` 识别为 `business` 错误，优先使用 `errorMsg`。

## 3. 历史接口兼容

- 保留旧接口语义，不改 URL、不改 Method、不改 query 参数结构。
- `GET + query` 的 `addFood/finishFoodlist/deleteFoodFromList/addFoodToList` 仅做前端封装，不做协议改造。
