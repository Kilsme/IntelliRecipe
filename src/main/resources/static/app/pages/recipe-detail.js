import { getErrorMessage } from "../core/http.js";
import { requireSessionOrRedirect } from "../core/session.js";
import { getRecipeDetail, submitRecipeFeedback } from "../api/recipeApi.js";
import { createRecipeUseService } from "../services/recipeUseService.js";
import {
  getQueryParam,
  goBackOr,
  goCartWithReturnRecipeId,
  goHome,
  goRecipe,
} from "../utils/navigationHelper.js";

function required(id) {
  const el = document.getElementById(id);
  if (!el) {
    throw new Error(`recipe-detail.html 缺少必需节点 #${id}`);
  }
  return el;
}

const els = {
  msg: required("msg"),
  title: required("title"),
  desc: required("desc"),
  recipeImage: required("recipeImage"),
  douyin: required("douyin"),
  ingredients: required("ingredients"),
  steps: required("steps"),
  rating: required("rating"),
  review: required("review"),
  submitBtn: required("submitBtn"),
  useBtn: required("useBtn"),
  addMissingBtn: required("addMissingBtn"),
  retryUseBtn: required("retryUseBtn"),
  goCartBtn: required("goCartBtn"),
  backPrevBtn: required("backPrevBtn"),
  backInlineBtn: required("backInlineBtn"),
  closeMissingBtn: required("closeMissingBtn"),
  backBtn: required("backBtn"),
  backHomeBtn: required("backHomeBtn"),
  missingModalMask: required("missingModalMask"),
  missingText: required("missingText"),
  missingList: required("missingList"),
};

const recipeId = getQueryParam("id");
const actionLocks = new Set();
const actionLastTs = new Map();

async function runGuarded(actionKey, handler, minIntervalMs = 800) {
  const now = Date.now();
  const lastTs = actionLastTs.get(actionKey) || 0;

  if (now - lastTs < minIntervalMs) {
    showMsg("操作过于频繁，请稍后再试", false);
    return;
  }

  if (actionLocks.has(actionKey)) {
    showMsg("请求处理中，请勿重复点击", false);
    return;
  }

  actionLocks.add(actionKey);
  actionLastTs.set(actionKey, now);
  try {
    await handler();
  } finally {
    actionLocks.delete(actionKey);
  }
}

function showMsg(text, ok) {
  els.msg.textContent = text || "";
  els.msg.classList.toggle("ok", !!ok);
  els.msg.classList.toggle("err", !ok && !!text);
}

function openMissingModal(message, missingIngredients, replacements = []) {
  const baseMessage = message || "当前缺少以下食材：";
  const replacementSummary = (replacements || [])
    .map((item) => `${item.originalIngredientName || "未知食材"}->${item.replacementIngredientName || "未知食材"}`)
    .join("、");
  els.missingText.textContent = replacementSummary
    ? `${baseMessage}（已自动替代：${replacementSummary}）`
    : baseMessage;
  els.missingList.innerHTML = "";

  (missingIngredients || []).forEach((item) => {
    const li = document.createElement("li");
    li.textContent = `${item.ingredientName} ${item.quantity}${item.unit}`;
    els.missingList.appendChild(li);
  });

  els.missingModalMask.style.display = "flex";
}

function closeMissingModal() {
  els.missingModalMask.style.display = "none";
}

const recipeUseService = createRecipeUseService({
  onNeedMissing: async ({ data }) => {
    openMissingModal(data.message, data.missingIngredients || [], data.replacements || []);
  },
  onSuccess: async ({ data }) => {
    showMsg(data.message || "使用成功，已扣减冰箱库存", true);
  },
  onError: async ({ message }) => {
    showMsg(message || "操作失败", false);
  },
});

async function loadDetail() {
  if (!recipeId) {
    showMsg("缺少 recipeId 参数", false);
    return;
  }

  const result = await getRecipeDetail(recipeId);
  if (!result.ok || !result.data) {
    showMsg(getErrorMessage(result, "加载详情失败"), false);
    return;
  }

  const data = result.data;
  els.title.textContent = data.title || "-";
  els.desc.textContent = data.description || "";
  els.recipeImage.src = data.imageUrl || "/recipe-images/default-dish.svg";
  els.douyin.href = data.douyinUrl || "#";

  els.ingredients.innerHTML = "";
  (data.ingredients || []).forEach((item) => {
    const div = document.createElement("div");
    div.className = "ingredient";
    div.textContent = `${item.ingredientName} - ${item.quantity} ${item.unit}`;
    els.ingredients.appendChild(div);
  });

  els.steps.innerHTML = "";
  (data.steps || []).forEach((step) => {
    const li = document.createElement("li");
    li.textContent = step;
    els.steps.appendChild(li);
  });
}

async function submitFeedback() {
  if (!recipeId) {
    showMsg("缺少 recipeId 参数", false);
    return;
  }

  const payload = {
    recipeId: Number(recipeId),
    rating: Number(els.rating.value),
    reviewText: els.review.value.trim(),
  };

  const result = await submitRecipeFeedback(payload);
  showMsg(result.ok ? "评价提交成功" : getErrorMessage(result, "评价提交失败"), result.ok);
}

async function startUseFlow() {
  if (!recipeId) {
    showMsg("缺少 recipeId 参数", false);
    return;
  }
  await recipeUseService.startUse(Number(recipeId));
}

async function addMissingToCartForDetail() {
  if (!recipeId) return;

  const result = await recipeUseService.addMissing(Number(recipeId));
  if (!result.ok) return;

  showMsg((result.data && result.data.message) || "已添加到购物车", true);
  closeMissingModal();
}

els.submitBtn.addEventListener("click", () => runGuarded("submitFeedback", submitFeedback));
els.useBtn.addEventListener("click", () => runGuarded("startUseFlow", startUseFlow));
els.addMissingBtn.addEventListener("click", () => runGuarded("addMissingToCart", addMissingToCartForDetail));
els.retryUseBtn.addEventListener("click", async () => {
  closeMissingModal();
  await runGuarded("startUseFlow", startUseFlow);
});
els.goCartBtn.addEventListener("click", () => {
  goCartWithReturnRecipeId(recipeId);
});
els.backPrevBtn.addEventListener("click", () => goBackOr("recipe.html"));
els.backInlineBtn.addEventListener("click", () => goBackOr("recipe.html"));
els.closeMissingBtn.addEventListener("click", closeMissingModal);
els.backBtn.addEventListener("click", goRecipe);
els.backHomeBtn.addEventListener("click", goHome);

(async () => {
  const session = await requireSessionOrRedirect({
    onFail: (message) => showMsg(message, false),
  });

  if (!session.ok) return;
  await loadDetail();
})();
