import { requireSessionOrRedirect } from "../core/session.js";
import { logout as logoutApi } from "../api/authApi.js";
import {
  goCart,
  goFridge,
  goLogin,
  goProfile,
  goRecipe,
} from "../utils/navigationHelper.js";

const THEME_STORAGE_KEY = "home.theme";

function getEl(id) {
  return document.getElementById(id);
}

function bindClick(el, handler) {
  if (el) {
    el.addEventListener("click", handler);
  }
}

const els = {
  status: getEl("status"),
  goFridgeBtn: getEl("goFridgeBtn"),
  goCartBtn: getEl("goCartBtn"),
  goRecipeBtn: getEl("goRecipeBtn"),
  themeToggleBtn: getEl("themeToggleBtn"),
  moreBtn: getEl("moreBtn"),
  moreMenu: getEl("moreMenu"),
  profileBtn: getEl("profileBtn"),
  logoutBtn: getEl("logoutBtn"),
};

function showStatus(text, ok = false) {
  if (!els.status) return;
  els.status.textContent = text || "";
  els.status.classList.toggle("error", !!text && !ok);
  els.status.classList.toggle("ok", !!text && ok);
}

function toggleMoreMenu(forceVisible) {
  if (!els.moreMenu) return;
  const shouldShow =
    typeof forceVisible === "boolean"
      ? forceVisible
      : els.moreMenu.classList.contains("hidden");
  els.moreMenu.classList.toggle("hidden", !shouldShow);
}

function applyTheme(theme) {
  const nextTheme = theme === "night" ? "night" : "day";
  document.body.classList.toggle("theme-day", nextTheme === "day");
  document.body.classList.toggle("theme-night", nextTheme === "night");
  if (els.themeToggleBtn) {
    els.themeToggleBtn.textContent = nextTheme === "day" ? "切换夜景" : "切换日景";
  }
  localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
}

function toggleTheme() {
  const isNight = document.body.classList.contains("theme-night");
  applyTheme(isNight ? "day" : "night");
}

async function onLogout() {
  await logoutApi();
  goLogin();
}

bindClick(els.goFridgeBtn, goFridge);
bindClick(els.goCartBtn, goCart);
bindClick(els.goRecipeBtn, goRecipe);
bindClick(els.profileBtn, goProfile);
bindClick(els.logoutBtn, onLogout);
bindClick(els.themeToggleBtn, toggleTheme);

bindClick(els.moreBtn, (event) => {
  event.stopPropagation();
  toggleMoreMenu();
});

if (els.moreMenu) {
  els.moreMenu.addEventListener("click", (event) => {
    event.stopPropagation();
  });
}

document.addEventListener("click", () => {
  toggleMoreMenu(false);
});

(async () => {
  const savedTheme = localStorage.getItem(THEME_STORAGE_KEY) || "day";
  applyTheme(savedTheme);

  const session = await requireSessionOrRedirect({
    onFail: (message) => showStatus(message, false),
  });
  if (!session.ok) return;

  showStatus("", true);
})();