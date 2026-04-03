import { getErrorMessage } from "../core/http.js";
import { requireSessionOrRedirect } from "../core/session.js";
import { getAllFoodView } from "../api/dictApi.js";
import {
  addFoodToFridge,
  deleteFoodFromFridge,
  getFridgeView,
} from "../api/fridgeApi.js";
import {
  CATEGORY_NAMES,
  getCategoryNameById,
  normalizeCategoryIndex,
  toBackendCategoryId,
} from "../adapters/categoryAdapter.js";
import {
  getIngredientId,
  getIngredientName,
  parseNutritionInfo,
} from "../adapters/voAdapter.js";
import { goHome } from "../utils/navigationHelper.js";

function required(id) {
  const el = document.getElementById(id);
  if (!el) {
    throw new Error(`fridge.html 缺少必需节点 #${id}`);
  }
  return el;
}

const els = {
  username: required("username"),
  status: required("status"),
  addMsg: required("addMsg"),
  categoryTabs: required("categoryTabs"),
  ingredientSelect: required("ingredientSelect"),
  quantityInput: required("quantityInput"),
  unitInput: required("unitInput"),
  addFoodBtn: required("addFoodBtn"),
  viewFridgeBtn: required("viewFridgeBtn"),
  fridgeContainer: required("fridgeContainer"),
  fridgeList: required("fridgeList"),
  backHomeBtn: required("backHomeBtn"),
};

const state = {
  allFoodData: [],
  selectedCategoryIndex: 0,
  ingredientCategoryMap: new Map(),
};

const nutritionLabelMap = {
  calories: "热量",
  protein: "蛋白质",
  fat: "脂肪",
  carbs: "碳水",
  fiber: "纤维",
};

function showStatus(text, type = "") {
  els.status.textContent = text || "";
  els.status.classList.remove("success", "error");
  if (type) {
    els.status.classList.add(type);
  }
}

function showAddMsg(message, success) {
  els.addMsg.textContent = message || "";
  els.addMsg.classList.remove("success", "error");
  if (message) {
    els.addMsg.classList.add(success ? "success" : "error");
  }
}

function getCategoryItems(index) {
  const list = state.allFoodData[index];
  return Array.isArray(list) ? list : [];
}

function normalizeBackendCategoryId(rawCategoryId) {
  return toBackendCategoryId(normalizeCategoryIndex(rawCategoryId));
}

function rebuildIngredientCategoryMap() {
  const map = new Map();
  state.allFoodData.forEach((group, index) => {
    const backendCategoryId = toBackendCategoryId(index);
    (group || []).forEach((item) => {
      if (item && item.id !== null && item.id !== undefined) {
        map.set(Number(item.id), backendCategoryId);
      }
    });
  });
  state.ingredientCategoryMap = map;
}

function resolveDisplayCategoryId(item) {
  const ingredientId = Number(getIngredientId(item));
  if (!Number.isNaN(ingredientId) && state.ingredientCategoryMap.has(ingredientId)) {
    return state.ingredientCategoryMap.get(ingredientId);
  }
  return normalizeBackendCategoryId(item.categoryId ?? item.category_id);
}

function formatWeight(quantity, unit) {
  const numeric = Number(quantity);
  const lowerUnit = (unit || "").toLowerCase();
  if (Number.isNaN(numeric)) {
    return `${quantity} ${unit || ""}`.trim();
  }
  if (lowerUnit === "g" && numeric >= 1000) {
    const kgValue = (numeric / 1000).toFixed(2).replace(/\.?0+$/, "");
    return `${kgValue} kg`;
  }
  if (lowerUnit === "kg") {
    const kgValue = numeric.toFixed(2).replace(/\.?0+$/, "");
    return `${kgValue} kg`;
  }
  return `${numeric.toFixed(2).replace(/\.?0+$/, "")} g`;
}

function renderCategoryTabs() {
  els.categoryTabs.innerHTML = "";
  CATEGORY_NAMES.forEach((name, index) => {
    const count = getCategoryItems(index).length;
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `category-tab${index === state.selectedCategoryIndex ? " active" : ""}`;
    btn.textContent = `${name} (${count})`;
    btn.addEventListener("click", () => {
      state.selectedCategoryIndex = index;
      renderCategoryTabs();
      renderIngredientSelect();
    });
    els.categoryTabs.appendChild(btn);
  });
}

function renderIngredientSelect() {
  const list = getCategoryItems(state.selectedCategoryIndex);
  els.ingredientSelect.innerHTML = "";

  if (list.length === 0) {
    const option = document.createElement("option");
    option.value = "";
    option.textContent = "该分类暂无食材";
    els.ingredientSelect.appendChild(option);
    return;
  }

  list.forEach((item) => {
    const option = document.createElement("option");
    option.value = item.id;
    option.textContent = item.name;
    els.ingredientSelect.appendChild(option);
  });
}

async function loadAllFood() {
  const result = await getAllFoodView();
  if (!result.ok) {
    showStatus(getErrorMessage(result, "加载食材字典失败"), "error");
    return false;
  }

  state.allFoodData = Array.isArray(result.data) ? result.data : [];
  rebuildIngredientCategoryMap();
  renderCategoryTabs();
  renderIngredientSelect();
  return true;
}

function buildNutritionHtml(item) {
  const nutritionInfo = parseNutritionInfo(item);
  if (!nutritionInfo || typeof nutritionInfo !== "object") {
    return "";
  }

  const parts = [];
  ["calories", "protein", "fat"].forEach((key) => {
    if (nutritionInfo[key] === undefined) return;
    const unit = key === "calories" ? "kcal" : "g";
    parts.push(`${nutritionLabelMap[key] || key}: ${nutritionInfo[key]}${unit}`);
  });

  if (parts.length === 0) {
    return '<div class="nutrition-tags">暂无详细营养数据</div>';
  }
  return `<div class="nutrition-tags">${parts.join("<br>")}</div>`;
}

function renderFridgeItems(items) {
  els.fridgeList.innerHTML = "";

  if (!items || items.length === 0) {
    els.fridgeList.innerHTML =
      '<p class="status" style="grid-column: 1/-1; text-align: center;">你的冰箱还是空的，快去添加食材吧。</p>';
    return;
  }

  const grouped = new Map();
  items.forEach((item) => {
    const categoryId = resolveDisplayCategoryId(item);
    if (!grouped.has(categoryId)) grouped.set(categoryId, []);
    grouped.get(categoryId).push(item);
  });

  for (let i = 1; i <= 9; i += 1) {
    const group = grouped.get(i) || [];
    if (group.length === 0) continue;

    const section = document.createElement("div");
    section.className = "category-section";
    section.innerHTML = `
      <div class="category-title">${getCategoryNameById(i)} <span style="font-size:0.8em; color:#9ca3af; margin-left:8px; font-weight:400">(${group.length})</span></div>
      <div class="fridge-list" id="fridge-cat-${i}"></div>
    `;

    const groupListEl = section.querySelector(`#fridge-cat-${i}`);
    group.forEach((item) => {
      const ingredientId = getIngredientId(item);
      const card = document.createElement("div");
      card.className = "fridge-item";
      card.innerHTML = `
        <div class="fridge-top">
          <div class="fridge-name">${getIngredientName(item)}</div>
          <div class="fridge-actions">
            <button class="fridge-more" type="button" aria-label="更多操作">...</button>
            <div class="fridge-menu hidden">
              <button class="fridge-delete-btn" type="button">删除</button>
            </div>
          </div>
        </div>
        <div class="fridge-quantity">余量：${formatWeight(item.quantity, item.unit || "g")}</div>
        ${buildNutritionHtml(item)}
      `;

      const menu = card.querySelector(".fridge-menu");
      const moreBtn = card.querySelector(".fridge-more");
      const deleteBtn = card.querySelector(".fridge-delete-btn");

      moreBtn.addEventListener("click", (event) => {
        event.stopPropagation();
        document.querySelectorAll(".fridge-menu").forEach((el) => {
          if (el !== menu) el.classList.add("hidden");
        });
        menu.classList.toggle("hidden");
      });

      deleteBtn.addEventListener("click", async (event) => {
        event.stopPropagation();
        await onDeleteFridgeItem(ingredientId);
      });

      groupListEl.appendChild(card);
    });

    els.fridgeList.appendChild(section);
  }
}

async function loadFridge() {
  els.viewFridgeBtn.disabled = true;
  els.viewFridgeBtn.textContent = "加载中...";
  const result = await getFridgeView();
  els.viewFridgeBtn.disabled = false;
  els.viewFridgeBtn.textContent = "刷新库存";

  if (!result.ok) {
    showAddMsg(getErrorMessage(result, "获取冰箱数据失败"), false);
    return false;
  }

  renderFridgeItems(result.data);
  els.fridgeContainer.classList.remove("hidden");
  return true;
}

async function onAddFood() {
  const ingredientId = els.ingredientSelect.value;
  const quantity = els.quantityInput.value;
  const unit = els.unitInput.value;

  if (!ingredientId) {
    showAddMsg("请先选择食材", false);
    return;
  }
  if (!quantity || Number(quantity) <= 0) {
    showAddMsg("请输入正确的数量", false);
    return;
  }
  if (!unit) {
    showAddMsg("请输入单位", false);
    return;
  }

  const result = await addFoodToFridge({ ingredientId, quantity, unit });
  if (!result.ok) {
    showAddMsg(getErrorMessage(result, "添加失败"), false);
    return;
  }

  showAddMsg("已加入冰箱", true);
  await loadFridge();
}

async function onDeleteFridgeItem(ingredientId) {
  if (!ingredientId) {
    showAddMsg("食材参数异常，无法删除", false);
    return;
  }

  const result = await deleteFoodFromFridge(ingredientId);
  if (!result.ok) {
    showAddMsg(getErrorMessage(result, "删除失败"), false);
    return;
  }

  showAddMsg("已从冰箱删除该食材", true);
  await loadFridge();
}

document.addEventListener("click", () => {
  document.querySelectorAll(".fridge-menu").forEach((menu) => menu.classList.add("hidden"));
});

els.backHomeBtn.addEventListener("click", goHome);
els.viewFridgeBtn.addEventListener("click", loadFridge);
els.addFoodBtn.addEventListener("click", onAddFood);

(async () => {
  const session = await requireSessionOrRedirect({
    onFail: (message) => showStatus(message, "error"),
  });
  if (!session.ok) return;

  els.username.textContent = session.user.username || "";
  showStatus("", "");
  const loaded = await loadAllFood();
  if (loaded) {
    await loadFridge();
  }
})();
