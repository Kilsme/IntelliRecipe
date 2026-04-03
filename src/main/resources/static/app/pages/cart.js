import { getErrorMessage } from "../core/http.js";
import { requireSessionOrRedirect } from "../core/session.js";
import { getAllFoodView } from "../api/dictApi.js";
import {
  addFoodToCart,
  deleteCartItem as deleteCartItemApi,
  finishCartItem as finishCartItemApi,
  getCartView,
} from "../api/cartApi.js";
import {
  CATEGORY_NAMES,
  getCategoryNameById,
  normalizeCategoryIndex,
  toBackendCategoryId,
} from "../adapters/categoryAdapter.js";
import { getIngredientId, getIngredientName, getListItemId } from "../adapters/voAdapter.js";
import {
  getReturnRecipeId,
  goHome,
  goRecipeDetail,
} from "../utils/navigationHelper.js";

function required(id) {
  const el = document.getElementById(id);
  if (!el) {
    throw new Error(`cart.html 缺少必需节点 #${id}`);
  }
  return el;
}

const els = {
  username: required("username"),
  status: required("status"),
  cartAddMsg: required("cartAddMsg"),
  cartCategoryTabs: required("cartCategoryTabs"),
  cartIngredientSelect: required("cartIngredientSelect"),
  cartQuantityInput: required("cartQuantityInput"),
  cartUnitInput: required("cartUnitInput"),
  addFoodToCartBtn: required("addFoodToCartBtn"),
  viewCartBtn: required("viewCartBtn"),
  cartContainer: required("cartContainer"),
  cartList: required("cartList"),
  backHomeBtn: required("backHomeBtn"),
  backToRecipeBtn: required("backToRecipeBtn"),
};

const state = {
  allFoodData: [],
  cartSelectedCategoryIndex: 0,
  returnRecipeId: getReturnRecipeId(),
  ingredientCategoryMap: new Map(),
};

function showStatus(text, type = "") {
  els.status.textContent = text || "";
  els.status.classList.remove("success", "error");
  if (type) {
    els.status.classList.add(type);
  }
}

function showCartAddMsg(message, success) {
  els.cartAddMsg.textContent = message || "";
  els.cartAddMsg.classList.remove("success", "error");
  if (message) {
    els.cartAddMsg.classList.add(success ? "success" : "error");
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

function renderCartCategoryTabs() {
  els.cartCategoryTabs.innerHTML = "";
  CATEGORY_NAMES.forEach((name, index) => {
    const count = getCategoryItems(index).length;
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `category-tab${index === state.cartSelectedCategoryIndex ? " active" : ""}`;
    btn.textContent = `${name} (${count})`;
    btn.addEventListener("click", () => {
      state.cartSelectedCategoryIndex = index;
      renderCartCategoryTabs();
      renderCartIngredientSelect();
    });
    els.cartCategoryTabs.appendChild(btn);
  });
}

function renderCartIngredientSelect() {
  const list = getCategoryItems(state.cartSelectedCategoryIndex);
  els.cartIngredientSelect.innerHTML = "";

  if (list.length === 0) {
    const option = document.createElement("option");
    option.value = "";
    option.textContent = "该分类暂无食材";
    els.cartIngredientSelect.appendChild(option);
    return;
  }

  list.forEach((item) => {
    const option = document.createElement("option");
    option.value = item.id;
    option.textContent = item.name;
    els.cartIngredientSelect.appendChild(option);
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
  renderCartCategoryTabs();
  renderCartIngredientSelect();
  return true;
}

function setCartCheckboxDisabled(disabled) {
  document.querySelectorAll(".cart-check").forEach((checkbox) => {
    checkbox.disabled = disabled;
  });
}

async function onFinishCartItem(listItemId, checkboxEl) {
  if (!listItemId) {
    showCartAddMsg("条目参数异常，无法完成勾选", false);
    checkboxEl.checked = false;
    return;
  }

  setCartCheckboxDisabled(true);
  const result = await finishCartItemApi(listItemId);
  setCartCheckboxDisabled(false);

  if (!result.ok) {
    showCartAddMsg(getErrorMessage(result, "勾选处理失败"), false);
    checkboxEl.checked = false;
    return;
  }

  showCartAddMsg(result.data || "已加回冰箱并从购物车移除", true);
  await loadCart();
}

async function onDeleteCartItem(listItemId) {
  if (!listItemId) {
    showCartAddMsg("条目参数异常，无法删除", false);
    return;
  }

  const result = await deleteCartItemApi(listItemId);
  if (!result.ok) {
    showCartAddMsg(getErrorMessage(result, "删除失败"), false);
    return;
  }

  showCartAddMsg("已从购物车删除", true);
  await loadCart();
}

function renderCartItems(items) {
  els.cartList.innerHTML = "";

  if (!items || items.length === 0) {
    els.cartList.innerHTML =
      '<p class="status" style="grid-column: 1/-1; text-align: center;">购物车为空，快去添加食材吧。</p>';
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
      <div class="fridge-list" id="cart-cat-${i}"></div>
    `;

    const groupListEl = section.querySelector(`#cart-cat-${i}`);

    group.forEach((item) => {
      const name = getIngredientName(item);
      const qty = item.quantity ?? item.quantityNeeded ?? 0;
      const unit = item.unit || "g";
      const listItemId = getListItemId(item);

      const row = document.createElement("div");
      row.className = "cart-item";
      row.innerHTML = `
        <div class="cart-top">
          <label style="display:flex; align-items:center; gap:8px; margin:0;">
            <input class="cart-check" type="checkbox" data-list-item-id="${listItemId}">
            <span class="cart-name">${name}</span>
          </label>
          <div class="cart-actions">
            <button class="cart-more" type="button" aria-label="更多操作">...</button>
            <div class="cart-menu hidden">
              <button class="cart-delete-btn" type="button" data-delete-item-id="${listItemId}">删除</button>
            </div>
          </div>
        </div>
        <div class="cart-quantity">采购量：${formatWeight(qty, unit)}</div>
      `;

      const checkbox = row.querySelector(".cart-check");
      checkbox.addEventListener("change", async (event) => {
        if (!event.target.checked) return;
        document.querySelectorAll(".cart-check").forEach((cb) => {
          if (cb !== event.target) cb.checked = false;
        });
        await onFinishCartItem(listItemId, event.target);
      });

      const menu = row.querySelector(".cart-menu");
      const moreBtn = row.querySelector(".cart-more");
      moreBtn.addEventListener("click", (event) => {
        event.stopPropagation();
        document.querySelectorAll(".cart-menu").forEach((el) => {
          if (el !== menu) el.classList.add("hidden");
        });
        menu.classList.toggle("hidden");
      });

      row.querySelector(".cart-delete-btn").addEventListener("click", async (event) => {
        event.stopPropagation();
        await onDeleteCartItem(listItemId);
      });

      groupListEl.appendChild(row);
    });

    els.cartList.appendChild(section);
  }
}

async function loadCart() {
  els.viewCartBtn.disabled = true;
  els.viewCartBtn.textContent = "加载中...";
  const result = await getCartView();
  els.viewCartBtn.disabled = false;
  els.viewCartBtn.textContent = "刷新购物车";

  if (!result.ok) {
    showCartAddMsg(getErrorMessage(result, "获取购物车失败"), false);
    return false;
  }

  renderCartItems(result.data);
  els.cartContainer.classList.remove("hidden");
  return true;
}

async function onAddFoodToCart() {
  const ingredientId = els.cartIngredientSelect.value;
  const quantity = els.cartQuantityInput.value;
  const unit = els.cartUnitInput.value;

  if (!ingredientId) {
    showCartAddMsg("请先选择食材", false);
    return;
  }
  if (!quantity || Number(quantity) <= 0) {
    showCartAddMsg("请输入正确的数量", false);
    return;
  }
  if (!unit) {
    showCartAddMsg("请输入单位", false);
    return;
  }

  const categoryId = toBackendCategoryId(state.cartSelectedCategoryIndex);
  const result = await addFoodToCart({ ingredientId, quantity, unit, categoryId });

  if (!result.ok) {
    showCartAddMsg(getErrorMessage(result, "添加失败"), false);
    return;
  }

  showCartAddMsg("已添加到购物车（可重复添加同一食材）", true);
  await loadCart();
}

function setupReturnRecipeButton() {
  if (!state.returnRecipeId) {
    els.backToRecipeBtn.classList.add("hidden");
    return;
  }

  els.backToRecipeBtn.classList.remove("hidden");
  els.backToRecipeBtn.addEventListener("click", () => {
    goRecipeDetail(state.returnRecipeId);
  });
}

document.addEventListener("click", () => {
  document.querySelectorAll(".cart-menu").forEach((menu) => menu.classList.add("hidden"));
});

els.backHomeBtn.addEventListener("click", goHome);
els.viewCartBtn.addEventListener("click", loadCart);
els.addFoodToCartBtn.addEventListener("click", onAddFoodToCart);

(async () => {
  const session = await requireSessionOrRedirect({
    onFail: (message) => showStatus(message, "error"),
  });
  if (!session.ok) return;

  els.username.textContent = session.user.username || "";
  showStatus("", "");
  setupReturnRecipeButton();

  const loaded = await loadAllFood();
  if (loaded) {
    await loadCart();
  }
})();
