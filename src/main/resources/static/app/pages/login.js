import { getErrorMessage } from "../core/http.js";
import { login, register } from "../api/authApi.js";
import { goHome } from "../utils/navigationHelper.js";

const modeButtons = Array.from(document.querySelectorAll(".mode-btn"));
const form = document.getElementById("authForm");
const formMsg = document.getElementById("formMsg");
const registerOnlyFields = Array.from(document.querySelectorAll(".register-only"));
const submitBtn = document.getElementById("submitBtn");
const usernameInput = document.getElementById("username");
const passwordInput = document.getElementById("password");
const phoneInput = document.getElementById("phone");

if (!form || !formMsg || !submitBtn || !usernameInput || !passwordInput || modeButtons.length === 0) {
  throw new Error("登录页 DOM 结构不完整，无法初始化登录脚本");
}

let mode = "login";

function showMessage(message, success = false) {
  formMsg.textContent = message || "";
  formMsg.classList.toggle("success", success);
  formMsg.classList.toggle("error", !!message && !success);
}

function switchMode(nextMode) {
  if (mode === nextMode) return;
  mode = nextMode;

  modeButtons.forEach((btn) => btn.classList.toggle("active", btn.dataset.mode === mode));
  registerOnlyFields.forEach((field) => field.classList.toggle("hidden", mode === "login"));
  submitBtn.textContent = mode === "login" ? "登录" : "注册";
  showMessage("");
  form.reset();
}

modeButtons.forEach((btn) => {
  btn.addEventListener("click", () => switchMode(btn.dataset.mode || "login"));
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  showMessage("");

  const payload = {
    username: usernameInput.value.trim(),
    password: passwordInput.value,
  };

  if (mode === "register") {
    payload.phone = (phoneInput && phoneInput.value ? phoneInput.value : "").trim();
  }

  const result = mode === "login" ? await login(payload) : await register(payload);
  if (result.ok) {
    if (mode === "login") {
      showMessage("登录成功，正在跳转...", true);
      window.setTimeout(() => goHome(), 600);
    } else {
      showMessage("注册成功，请切换到登录", true);
      switchMode("login");
    }
    return;
  }

  showMessage(getErrorMessage(result, mode === "login" ? "登录失败" : "注册失败"));
});
