import { getErrorMessage } from "../core/http.js";
import { requireSessionOrRedirect } from "../core/session.js";
import {
  batchDeleteCollections,
  collectRecipe,
  deleteCollection,
  generateRecipe,
  getCollections,
} from "../api/recipeApi.js";
import { createRecipeUseService } from "../services/recipeUseService.js";
import {
  goHome,
  goCartWithReturnRecipeId,
  goRecipeDetail,
} from "../utils/navigationHelper.js";

function required(id) {
  const el = document.getElementById(id);
  if (!el) {
    throw new Error(`recipe.html 缺少必需节点 #${id}`);
  }
  return el;
}

function optional(id) {
  return document.getElementById(id);
}

const els = {
  msg: required("msg"),
  demandInput: required("demandInput"),
  recipeTitle: required("recipeTitle"),
  recipeDesc: required("recipeDesc"),
  recipeImage: required("recipeImage"),
  flavorTags: required("flavorTags"),
  replaceInfo: required("replaceInfo"),
  missingInfo: required("missingInfo"),
  ingredients: required("ingredients"),
  steps: required("steps"),
  douyinLink: required("douyinLink"),
  resultCard: required("resultCard"),
  collectionList: required("collectionList"),
  missingModalMask: required("missingModalMask"),
  missingModalText: required("missingModalText"),
  missingModalList: required("missingModalList"),
  generateBtn: required("generateBtn"),
  collectBtn: required("collectBtn"),
  useBtn: required("useBtn"),
  viewCollectionsBtn: required("viewCollectionsBtn"),
  batchDeleteBtn: required("batchDeleteBtn"),
  backBtn: required("backBtn"),
  addMissingBtn: required("addMissingBtn"),
  goCartBtn: required("goCartBtn"),
  retryUseBtn: required("retryUseBtn"),
  modalJumpDetailBtn: required("modalJumpDetailBtn"),
  closeModalBtn: required("closeModalBtn"),
  jumpToDetailBtn: optional("jumpToDetailBtn"),
};

const state = {
  selectedTime: "早餐",
  currentRecipeId: null,
  pendingUseRecipeId: null,
  latestRecipeIdForManualJump: null,
};

function showMsg(text, ok) {
  els.msg.textContent = text || "";
  els.msg.classList.toggle("ok", !!ok);
  els.msg.classList.toggle("err", !ok && !!text);
}

function bindTimeTabs() {
  const timeButtons = Array.from(document.querySelectorAll(".time-btn"));
  timeButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      state.selectedTime = btn.dataset.time || "早餐";
      timeButtons.forEach((item) => item.classList.remove("active"));
      btn.classList.add("active");
    });
  });
}

function renderRecipe(data) {
  state.currentRecipeId = data.recipeId;
  state.latestRecipeIdForManualJump = data.recipeId;

  els.recipeTitle.textContent = data.title || "智能推荐菜谱";
  els.recipeDesc.textContent = data.description || "";
  els.recipeImage.src = data.imageUrl || "/recipe-images/default-dish.svg";

  els.flavorTags.innerHTML = "";
  (data.flavorTags || []).forEach((tag) => {
    const span = document.createElement("span");
    span.className = "tag";
    span.textContent = tag;
    els.flavorTags.appendChild(span);
  });

  els.ingredients.innerHTML = "";
  (data.ingredients || []).forEach((item) => {
    const div = document.createElement("div");
    div.className = "ingredient-item";
    div.textContent = `${item.ingredientName} - ${item.quantity} ${item.unit}`;
    els.ingredients.appendChild(div);
  });

  if (data.replacements && data.replacements.length > 0) {
    els.replaceInfo.style.display = "block";
    els.replaceInfo.innerHTML =
      "<b>冰箱替换说明：</b><br>" +
      data.replacements
        .map((item) => {
          const reason = item.reason || "同类替换";
          return `${item.originalIngredientName} -> ${item.replacementIngredientName}（${reason}）`;
        })
        .join("<br>");
  } else {
    els.replaceInfo.style.display = "none";
    els.replaceInfo.innerHTML = "";
  }

  if (data.missingIngredients && data.missingIngredients.length > 0) {
    els.missingInfo.style.display = "block";
    els.missingInfo.innerHTML =
      "<b>当前缺少食材：</b><br>" +
      data.missingIngredients
        .map((item) => `${item.ingredientName} ${item.quantity} ${item.unit}`)
        .join("<br>");
  } else {
    els.missingInfo.style.display = "none";
    els.missingInfo.innerHTML = "";
  }

  els.steps.innerHTML = "";
  (data.steps || []).forEach((step) => {
    const li = document.createElement("li");
    li.textContent = step;
    els.steps.appendChild(li);
  });

  els.douyinLink.href = data.douyinUrl || "#";
  els.resultCard.style.display = "block";
}

function openMissingModal(recipeId, missingIngredients, message, replacements = []) {
  state.pendingUseRecipeId = recipeId;
  const baseMessage = message || "当前库存不足，请先采购以下食材：";
  const replacementSummary = (replacements || [])
    .map((item) => `${item.originalIngredientName || "未知食材"}->${item.replacementIngredientName || "未知食材"}`)
    .join("、");
  els.missingModalText.textContent = replacementSummary
    ? `${baseMessage}（已自动替代：${replacementSummary}）`
    : baseMessage;
  els.missingModalList.innerHTML = "";

  (missingIngredients || []).forEach((item) => {
    const li = document.createElement("li");
    li.textContent = `${item.ingredientName} ${item.quantity}${item.unit}`;
    els.missingModalList.appendChild(li);
  });

  els.missingModalMask.style.display = "flex";
}

function closeMissingModal() {
  els.missingModalMask.style.display = "none";
  state.pendingUseRecipeId = null;
}

function jumpToRecipeDetailForUse() {
  const id = state.pendingUseRecipeId || state.latestRecipeIdForManualJump || state.currentRecipeId;
  if (!id) {
    showMsg("当前没有可跳转的菜谱", false);
    return;
  }
  goRecipeDetail(id);
}

const recipeUseService = createRecipeUseService({
  onNeedMissing: async ({ recipeId, data }) => {
    openMissingModal(
      recipeId,
      data.missingIngredients || [],
      data.message || "缺少食材，请先添加到购物车",
      data.replacements || [],
    );
  },
  onSuccess: async ({ recipeId, data }) => {
    showMsg(data.message || "已满足使用条件，正在跳转详情页...", true);
    const detailUrl = data.detailUrl || `recipe-detail.html?id=${recipeId}`;
    window.setTimeout(() => {
      window.location.href = detailUrl;
    }, 600);
  },
  onError: async ({ message }) => {
    showMsg(message || "操作失败", false);
  },
});

async function onGenerateRecipe() {
  showMsg("生成中，请稍候...", true);

  const demand = els.demandInput.value.trim();
  const result = await generateRecipe(state.selectedTime, demand);
  if (!result.ok || !result.data) {
    showMsg(getErrorMessage(result, "生成失败"), false);
    return;
  }

  renderRecipe(result.data);
  showMsg("生成完成，可收藏或去使用", true);
}

async function onCollectRecipe() {
  if (!state.currentRecipeId) {
    showMsg("请先生成菜谱", false);
    return;
  }

  const result = await collectRecipe(state.currentRecipeId);
  showMsg(result.ok ? "收藏成功" : getErrorMessage(result, "收藏失败"), result.ok);
  if (result.ok) {
    await loadCollections();
  }
}

async function useRecipe() {
  if (!state.currentRecipeId) {
    showMsg("请先生成菜谱", false);
    return;
  }
  await recipeUseService.startUse(state.currentRecipeId);
}

async function addMissingToCartForPending() {
  if (!state.pendingUseRecipeId) {
    return;
  }

  const result = await recipeUseService.addMissing(state.pendingUseRecipeId);
  if (!result.ok) {
    return;
  }

  showMsg((result.data && result.data.message) || "已添加到购物车", true);
  state.latestRecipeIdForManualJump = state.pendingUseRecipeId;

  if (els.jumpToDetailBtn) {
    els.jumpToDetailBtn.classList.remove("hidden");
  }

  closeMissingModal();
}

async function retryUseAfterShopping() {
  if (!state.pendingUseRecipeId) {
    return;
  }
  const recipeId = state.pendingUseRecipeId;
  closeMissingModal();
  await recipeUseService.startUse(recipeId);
}

async function onDeleteCollection(recipeId) {
  const result = await deleteCollection(recipeId);
  showMsg(result.ok ? "删除收藏成功" : getErrorMessage(result, "删除失败"), result.ok);
  if (result.ok) {
    await loadCollections();
  }
}

async function onBatchDeleteCollections() {
  const checked = Array.from(document.querySelectorAll(".collection-check:checked"));
  if (checked.length === 0) {
    showMsg("请先勾选要删除的收藏菜谱", false);
    return;
  }

  const recipeIds = checked
    .map((checkbox) => Number(checkbox.value))
    .filter((id) => !Number.isNaN(id));

  const result = await batchDeleteCollections(recipeIds);
  showMsg(
    result.ok ? result.data || "批量删除成功" : getErrorMessage(result, "批量删除失败"),
    result.ok,
  );
  if (result.ok) {
    await loadCollections();
  }
}

function renderCollections(items) {
  els.collectionList.innerHTML = "";

  if (!items || items.length === 0) {
    els.collectionList.innerHTML = '<div class="muted">还没有收藏菜谱，先生成并收藏一个吧。</div>';
    return;
  }

  items.forEach((item) => {
    const div = document.createElement("div");
    div.className = "collection-item";

    const title = item.title || "未命名菜谱";
    const desc = item.description || "";
    const time = item.collectedAt || "";

    div.innerHTML = `
      <div style="display:flex; justify-content:space-between; gap:10px; align-items:center;">
          <div class="collection-title">${title}</div>
          <label style="font-size:12px; color:#64748b;">
              <input class="collection-check" type="checkbox" value="${item.recipeId}"> 选择
          </label>
      </div>
      <div class="muted">${desc}</div>
      <div class="collection-time">收藏时间：${time}</div>
      <div class="collection-actions">
          <button class="small-btn js-detail-btn" type="button">查看详情</button>
          <button class="small-btn js-use-btn" type="button">去使用</button>
          <button class="small-btn danger js-delete-btn" type="button">删除收藏</button>
      </div>
    `;

    div.querySelector(".js-detail-btn").addEventListener("click", (event) => {
      event.stopPropagation();
      goRecipeDetail(item.recipeId);
    });

    div.querySelector(".js-use-btn").addEventListener("click", async (event) => {
      event.stopPropagation();
      await recipeUseService.startUse(item.recipeId);
    });

    div.querySelector(".js-delete-btn").addEventListener("click", async (event) => {
      event.stopPropagation();
      await onDeleteCollection(item.recipeId);
    });

    els.collectionList.appendChild(div);
  });
}

async function loadCollections() {
  const result = await getCollections();
  if (!result.ok) {
    showMsg(getErrorMessage(result, "获取收藏失败"), false);
    return;
  }
  renderCollections(result.data);
}

els.generateBtn.addEventListener("click", onGenerateRecipe);
els.collectBtn.addEventListener("click", onCollectRecipe);
els.useBtn.addEventListener("click", useRecipe);
els.viewCollectionsBtn.addEventListener("click", loadCollections);
els.batchDeleteBtn.addEventListener("click", onBatchDeleteCollections);
els.backBtn.addEventListener("click", goHome);
els.addMissingBtn.addEventListener("click", addMissingToCartForPending);
els.goCartBtn.addEventListener("click", () => {
  const id = state.pendingUseRecipeId || state.latestRecipeIdForManualJump || state.currentRecipeId;
  goCartWithReturnRecipeId(id);
});
els.retryUseBtn.addEventListener("click", retryUseAfterShopping);
els.modalJumpDetailBtn.addEventListener("click", jumpToRecipeDetailForUse);
els.closeModalBtn.addEventListener("click", closeMissingModal);

if (els.jumpToDetailBtn) {
  els.jumpToDetailBtn.addEventListener("click", jumpToRecipeDetailForUse);
}

bindTimeTabs();

(async () => {
  const session = await requireSessionOrRedirect({
    onFail: (message) => showMsg(message, false),
  });
  if (!session.ok) return;
})();
