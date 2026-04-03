import { getErrorMessage } from "../core/http.js";
import { addMissingToCart, checkRecipeUse, useRecipe } from "../api/recipeApi.js";

/**
 * @typedef {{
 *   onNeedMissing?: (ctx: {recipeId: number|string, stage: 'check'|'use', data: any}) => Promise<void>|void;
 *   onSuccess?: (ctx: {recipeId: number|string, data: any}) => Promise<void>|void;
 *   onError?: (ctx: {recipeId: number|string, stage: 'check'|'use'|'addMissing', message: string, result: any}) => Promise<void>|void;
 * }} RecipeUseHandlers
 */

/**
 * 纯业务流程：check -> use；不绑定 UI
 * @param {RecipeUseHandlers} handlers
 */
export function createRecipeUseService(handlers = {}) {
  const { onNeedMissing, onSuccess, onError } = handlers;

  /**
   * @param {number|string} recipeId
   */
  async function startUse(recipeId) {
    const checkResult = await checkRecipeUse(recipeId);
    if (!checkResult.ok) {
      const message = getErrorMessage(checkResult, "使用前检查失败");
      if (onError) await onError({ recipeId, stage: "check", message, result: checkResult });
      return { ok: false, stage: "check", result: checkResult };
    }

    const checkData = checkResult.data || {};
    if (!checkData.canUse) {
      if (onNeedMissing) {
        await onNeedMissing({ recipeId, stage: "check", data: checkData });
      }
      return { ok: false, stage: "check", needMissing: true, data: checkData, result: checkResult };
    }

    const useResult = await useRecipe(recipeId);
    if (!useResult.ok) {
      const message = getErrorMessage(useResult, "使用失败");
      if (onError) await onError({ recipeId, stage: "use", message, result: useResult });
      return { ok: false, stage: "use", result: useResult };
    }

    const useData = useResult.data || {};
    if (!useData.canUse) {
      if (onNeedMissing) {
        await onNeedMissing({ recipeId, stage: "use", data: useData });
      }
      return { ok: false, stage: "use", needMissing: true, data: useData, result: useResult };
    }

    if (onSuccess) {
      await onSuccess({ recipeId, data: useData });
    }
    return { ok: true, stage: "use", data: useData, result: useResult };
  }

  /**
   * @param {number|string} recipeId
   */
  async function addMissing(recipeId) {
    const result = await addMissingToCart(recipeId);
    if (!result.ok) {
      const message = getErrorMessage(result, "添加缺料失败");
      if (onError) await onError({ recipeId, stage: "addMissing", message, result });
      return { ok: false, result };
    }
    return { ok: true, data: result.data, result };
  }

  return {
    startUse,
    addMissing,
  };
}

