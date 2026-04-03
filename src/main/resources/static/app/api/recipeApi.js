import { request } from "../core/http.js";

export function generateRecipe(timeNode, demand) {
  const query = new URLSearchParams({
    timeNode: String(timeNode || ""),
    demand: String(demand || ""),
  });
  return request({
    url: `/api/recipe/generate?${query.toString()}`,
    method: "GET",
    responseMode: "result",
  });
}

export function getRecipeDetail(recipeId) {
  const query = new URLSearchParams({ recipeId: String(recipeId || "") });
  return request({
    url: `/api/recipe/detail?${query.toString()}`,
    method: "GET",
    responseMode: "result",
  });
}

export function collectRecipe(recipeId) {
  return request({
    url: "/api/recipe/collect",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recipeId }),
    responseMode: "result",
  });
}

export function checkRecipeUse(recipeId) {
  return request({
    url: "/api/recipe/use/check",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recipeId }),
    responseMode: "result",
  });
}

export function addMissingToCart(recipeId) {
  return request({
    url: "/api/recipe/use/add-missing",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recipeId }),
    responseMode: "result",
  });
}

export function useRecipe(recipeId) {
  return request({
    url: "/api/recipe/use",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recipeId }),
    responseMode: "result",
  });
}

export function submitRecipeFeedback(payload) {
  return request({
    url: "/api/recipe/feedback",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {}),
    responseMode: "result",
  });
}

export function getCollections() {
  return request({
    url: "/api/recipe/collections",
    method: "GET",
    responseMode: "result",
  });
}

export function deleteCollection(recipeId) {
  return request({
    url: "/api/recipe/collection/delete",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recipeId }),
    responseMode: "result",
  });
}

export function batchDeleteCollections(recipeIds) {
  return request({
    url: "/api/recipe/collection/delete-batch",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recipeIds: recipeIds || [] }),
    responseMode: "result",
  });
}

