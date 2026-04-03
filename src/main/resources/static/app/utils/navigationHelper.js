export function getPageParams() {
  return new URLSearchParams(window.location.search);
}

export function getQueryParam(key) {
  return getPageParams().get(key);
}

export function getReturnRecipeId() {
  return getQueryParam("returnRecipeId");
}

export function goTo(url) {
  window.location.href = url;
}

export function goLogin() {
  goTo("login.html");
}

export function goHome() {
  goTo("home.html");
}

export function goFridge() {
  goTo("fridge.html");
}

export function goCart() {
  goTo("cart.html");
}

export function goProfile() {
  goTo("profile.html");
}

export function goRecipe() {
  goTo("recipe.html");
}

/**
 * @param {number|string} recipeId
 */
export function goRecipeDetail(recipeId) {
  goTo(`recipe-detail.html?id=${encodeURIComponent(String(recipeId))}`);
}

/**
 * @param {number|string|null|undefined} recipeId
 */
export function goHomeWithReturnRecipeId(recipeId) {
  if (recipeId === null || recipeId === undefined || recipeId === "") {
    goHome();
    return;
  }
  goTo(`home.html?returnRecipeId=${encodeURIComponent(String(recipeId))}`);
}

/**
 * @param {number|string|null|undefined} recipeId
 */
export function goCartWithReturnRecipeId(recipeId) {
  if (recipeId === null || recipeId === undefined || recipeId === "") {
    goCart();
    return;
  }
  goTo(`cart.html?returnRecipeId=${encodeURIComponent(String(recipeId))}`);
}

/**
 * @param {string} fallbackUrl
 */
export function goBackOr(fallbackUrl) {
  if (window.history.length > 1) {
    window.history.back();
    return;
  }
  goTo(fallbackUrl);
}
