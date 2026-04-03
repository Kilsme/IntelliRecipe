const BASE_URL = process.env.BASE_URL || "http://localhost:8082";
const USERNAME = process.env.SMOKE_USERNAME || "autotest_user";
const PASSWORD = process.env.SMOKE_PASSWORD || "autotest_password_123";
const PHONE = process.env.SMOKE_PHONE || "13800000000";

const cookieJar = new Map();

function logStep(message) {
  console.log(`[SMOKE] ${message}`);
}

function getCookieHeader() {
  if (cookieJar.size === 0) return "";
  return Array.from(cookieJar.entries())
    .map(([k, v]) => `${k}=${v}`)
    .join("; ");
}

function saveSetCookie(headers) {
  const setCookies =
    typeof headers.getSetCookie === "function"
      ? headers.getSetCookie()
      : headers.get("set-cookie")
      ? [headers.get("set-cookie")]
      : [];

  for (const line of setCookies) {
    if (!line) continue;
    const first = line.split(";")[0];
    const eqIndex = first.indexOf("=");
    if (eqIndex <= 0) continue;
    const key = first.slice(0, eqIndex).trim();
    const value = first.slice(eqIndex + 1).trim();
    if (key && value) cookieJar.set(key, value);
  }
}

async function api(path, options = {}) {
  const {
    method = "GET",
    body = undefined,
    expectedStatus,
    expectJson = true,
  } = options;

  const headers = {};
  const cookie = getCookieHeader();
  if (cookie) headers.Cookie = cookie;
  let payload;
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: payload,
  });
  saveSetCookie(res.headers);

  if (expectedStatus && res.status !== expectedStatus) {
    throw new Error(`Unexpected status for ${path}: ${res.status} (expected ${expectedStatus})`);
  }

  if (!expectJson) return res;
  const text = await res.text();
  let json = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch (error) {
      throw new Error(`Invalid JSON for ${path}: ${text.slice(0, 200)}`);
    }
  }
  return { status: res.status, json };
}

async function ensureLogin() {
  logStep("Login");
  let loginRes = await api("/api/auth/login", {
    method: "POST",
    body: { username: USERNAME, password: PASSWORD },
  });

  if (loginRes.json?.success) return;

  logStep("Register fallback");
  await api("/api/auth/register", {
    method: "POST",
    body: { username: USERNAME, password: PASSWORD, phone: PHONE },
  });

  loginRes = await api("/api/auth/login", {
    method: "POST",
    body: { username: USERNAME, password: PASSWORD },
  });

  if (!loginRes.json?.success) {
    throw new Error(`Login failed: ${loginRes.json?.errorMsg || "unknown error"}`);
  }
}

function assertResultOk(result, stepName) {
  if (!result?.json || typeof result.json !== "object" || !("success" in result.json)) {
    throw new Error(`${stepName}: response is not Result wrapper`);
  }
  if (!result.json.success) {
    throw new Error(`${stepName}: ${result.json.errorMsg || "business failed"}`);
  }
}

async function main() {
  logStep(`Base URL: ${BASE_URL}`);
  await ensureLogin();

  logStep("AllFoodView(raw)");
  const allFood = await api("/AllFoodView");
  if (!Array.isArray(allFood.json)) {
    throw new Error("AllFoodView did not return raw array");
  }

  logStep("Generate recipe");
  const generated = await api(
    `/api/recipe/generate?timeNode=${encodeURIComponent("晚餐")}&demand=${encodeURIComponent("自动化冒烟")}`,
  );
  assertResultOk(generated, "generate");
  const recipeId = generated.json?.data?.recipeId;
  if (!recipeId) throw new Error("generate: recipeId missing");

  logStep("use/check");
  const useCheck = await api("/api/recipe/use/check", {
    method: "POST",
    body: { recipeId },
  });
  assertResultOk(useCheck, "use/check");

  logStep("use/add-missing");
  const addMissing = await api("/api/recipe/use/add-missing", {
    method: "POST",
    body: { recipeId },
  });
  assertResultOk(addMissing, "use/add-missing");

  logStep("use");
  const useRes = await api("/api/recipe/use", {
    method: "POST",
    body: { recipeId },
  });
  assertResultOk(useRes, "use");

  logStep("finishFoodlist");
  let listView = await api("/foodListView");
  assertResultOk(listView, "foodListView");
  let listItems = Array.isArray(listView.json?.data) ? listView.json.data : [];

  if (listItems.length === 0) {
    const firstCategory = allFood.json.find((g) => Array.isArray(g) && g.length > 0);
    const firstIngredient = firstCategory?.[0];
    if (!firstIngredient?.id) {
      throw new Error("No ingredient available for addFoodToList fallback");
    }
    const fallbackCategoryId = Number(firstIngredient.categoryId || 1);
    const addList = await api(
      `/addFoodToList?unit=${encodeURIComponent("g")}&quantity=${encodeURIComponent("100")}&ingredientId=${encodeURIComponent(String(firstIngredient.id))}&categoryId=${encodeURIComponent(String(fallbackCategoryId))}`,
    );
    assertResultOk(addList, "addFoodToList fallback");

    listView = await api("/foodListView");
    assertResultOk(listView, "foodListView(second)");
    listItems = Array.isArray(listView.json?.data) ? listView.json.data : [];
  }

  const listItemId = listItems[0]?.listItemId || listItems[0]?.id;
  if (!listItemId) throw new Error("finishFoodlist: no listItemId available");

  const finish = await api(`/finishFoodlist?listItemId=${encodeURIComponent(String(listItemId))}`);
  assertResultOk(finish, "finishFoodlist");

  console.log("\n✅ API smoke passed");
  console.log(`- recipeId: ${recipeId}`);
  console.log(`- listItemId: ${listItemId}`);
}

main().catch((error) => {
  console.error(`\n❌ API smoke failed: ${error.message}`);
  process.exit(1);
});

