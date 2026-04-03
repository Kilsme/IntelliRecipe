/**
 * @param {any} item
 */
export function getIngredientName(item) {
  return item?.ingredientName || item?.IngredientName || "未知食材";
}

/**
 * @param {any} item
 */
export function getIngredientId(item) {
  return item?.ingredientId ?? item?.IngredientId ?? item?.id ?? null;
}

/**
 * @param {any} item
 */
export function getListItemId(item) {
  return item?.listItemId ?? item?.id ?? null;
}

/**
 * @param {any} item
 */
export function parseNutritionInfo(item) {
  const raw = item?.nutrition_info ?? item?.nutritionInfo;
  if (!raw) return null;
  if (typeof raw === "object") return raw;
  if (typeof raw !== "string") return null;
  try {
    return JSON.parse(raw);
  } catch (_error) {
    return null;
  }
}
