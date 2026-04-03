# 前端自动化测试说明

## 1) API 冒烟自动化

脚本位置：

- `scripts/smoke-api.mjs`

覆盖链路：

- 登录（失败时自动注册后重试登录）
- `GET /AllFoodView`（raw 数组校验）
- `POST /api/recipe/use/check`
- `POST /api/recipe/use/add-missing`
- `POST /api/recipe/use`
- `GET /finishFoodlist`（必要时先补 `addFoodToList`）

运行方式：

```bash
npm run test:smoke:api
```

可选环境变量：

- `BASE_URL`（默认 `http://localhost:8082`）
- `SMOKE_USERNAME` / `SMOKE_PASSWORD` / `SMOKE_PHONE`

## 2) Playwright 页面 E2E

配置与用例：

- 配置：`playwright.config.js`
- 用例：`tests/e2e/frontend-flow.spec.js`

覆盖链路：

- 登录（失败时自动注册）
- 生成菜谱
- 触发缺料加购（先用 API 删除该菜谱所需食材以稳定进入缺料分支）
- 跳转购物车并完成一项采购入冰箱
- 通过 `returnRecipeId` 回跳菜谱详情页

运行方式：

```bash
npm run test:e2e
```

首次安装浏览器：

```bash
npx playwright install
```

可选环境变量：

- `BASE_URL`
- `E2E_USERNAME` / `E2E_PASSWORD` / `E2E_PHONE`
