export const CATEGORY_NAMES = [
  "蔬菜菌菇",
  "肉禽蛋类",
  "水产海鲜",
  "水果鲜切",
  "米面粮油",
  "调味酱料",
  "乳品烘焙",
  "冷饮速食",
  "其他食材",
];

/**
 * @param {unknown} categoryId
 */
export function normalizeCategoryIndex(categoryId) {
  const idNum = Number(categoryId);
  if (Number.isNaN(idNum)) return 8;
  if (idNum >= 1 && idNum <= 9) return idNum - 1;
  if (idNum >= 0 && idNum <= 8) return idNum;
  return 8;
}

/**
 * @param {unknown} categoryId
 */
export function getCategoryNameById(categoryId) {
  return CATEGORY_NAMES[normalizeCategoryIndex(categoryId)] || CATEGORY_NAMES[8];
}

/**
 * @param {number} uiIndex
 */
export function toBackendCategoryId(uiIndex) {
  const index = Number(uiIndex);
  if (Number.isNaN(index)) return 9;
  const normalized = Math.max(0, Math.min(8, index));
  return normalized + 1;
}
