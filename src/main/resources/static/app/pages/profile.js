import { getErrorMessage } from "../core/http.js";
import { requireSessionOrRedirect } from "../core/session.js";
import { getUserInfo, updateUser } from "../api/userApi.js";
import { goHome } from "../utils/navigationHelper.js";

function toText(value) {
  return value === null || value === undefined ? "" : String(value);
}

function showMsg(text, ok) {
  const el = document.getElementById("msg");
  el.textContent = text || "";
  el.classList.toggle("ok", !!ok);
  el.classList.toggle("err", !ok);
}

function fillForm(user) {
  document.getElementById("username").value = toText(user.username);
  document.getElementById("phone").value = toText(user.phone);
  document.getElementById("gender").value =
    user.gender === null || user.gender === undefined ? "" : String(user.gender);
  document.getElementById("age").value = toText(user.age);
  document.getElementById("heightCm").value = toText(user.heightCm);
  document.getElementById("weightKg").value = toText(user.weightKg);
  document.getElementById("region").value = toText(user.region);
  document.getElementById("dietType").value = toText(user.dietType || "NORMAL");
  document.getElementById("preferences").value = parseCuisineText(user.preferences);
  document.getElementById("allergies").value = parseAllergiesText(user.allergies);
}

function parseCuisineText(rawPreferences) {
  if (!rawPreferences) {
    return "";
  }
  try {
    const obj = typeof rawPreferences === "string" ? JSON.parse(rawPreferences) : rawPreferences;
    if (obj && Array.isArray(obj.preferred_cuisines)) {
      return obj.preferred_cuisines.join("、");
    }
    return typeof rawPreferences === "string" ? rawPreferences : "";
  } catch (_error) {
    return String(rawPreferences);
  }
}

function buildPreferencesJson(preferencesText) {
  const cuisines = preferencesText
    .split(/[、,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
  return JSON.stringify({ preferred_cuisines: cuisines });
}

function parseAllergiesText(rawAllergies) {
  if (!rawAllergies) {
    return "";
  }
  try {
    const data = typeof rawAllergies === "string" ? JSON.parse(rawAllergies) : rawAllergies;
    if (Array.isArray(data)) {
      return data.join("、");
    }
    return typeof rawAllergies === "string" ? rawAllergies : "";
  } catch (_error) {
    return String(rawAllergies);
  }
}

function buildAllergiesJson(allergiesText) {
  const allergies = allergiesText
    .split(/[、,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
  return JSON.stringify(allergies);
}

function buildUserPayload() {
  const genderValue = document.getElementById("gender").value;
  const preferencesText = document.getElementById("preferences").value.trim();
  const allergiesText = document.getElementById("allergies").value.trim();
  return {
    username: document.getElementById("username").value,
    phone: document.getElementById("phone").value,
    gender: genderValue === "" ? null : Number(genderValue),
    age: document.getElementById("age").value === "" ? null : Number(document.getElementById("age").value),
    heightCm:
      document.getElementById("heightCm").value === ""
        ? null
        : Number(document.getElementById("heightCm").value),
    weightKg:
      document.getElementById("weightKg").value === ""
        ? null
        : Number(document.getElementById("weightKg").value),
    region: document.getElementById("region").value.trim(),
    dietType: document.getElementById("dietType").value,
    preferences: buildPreferencesJson(preferencesText),
    allergies: buildAllergiesJson(allergiesText),
  };
}

async function saveUser() {
  const payload = buildUserPayload();
  const result = await updateUser(payload);
  if (result.ok) {
    showMsg("个人信息已更新", true);
    if (result.data) {
      fillForm(result.data);
    }
    return;
  }
  showMsg(getErrorMessage(result, "更新失败"), false);
}

document.getElementById("backBtn").addEventListener("click", goHome);
document.getElementById("saveBtn").addEventListener("click", saveUser);

(async () => {
  const session = await requireSessionOrRedirect({
    onFail: (message) => showMsg(message, false),
  });
  if (!session.ok) {
    return;
  }

  const result = await getUserInfo();
  if (!result.ok || !result.data) {
    showMsg(getErrorMessage(result, "请先登录"), false);
    window.setTimeout(() => {
      window.location.href = "login.html";
    }, 1200);
    return;
  }
  fillForm(result.data);
  showMsg("", true);
})();

