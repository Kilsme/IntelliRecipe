import { request } from "../core/http.js";

export function getFridgeView() {
  return request({
    url: "/fridgeView",
    method: "GET",
    responseMode: "result",
  });
}

export function addFoodToFridge({ unit, quantity, ingredientId }) {
  const query = new URLSearchParams({
    unit: String(unit || ""),
    quantity: String(quantity || ""),
    ingredientId: String(ingredientId || ""),
  });
  return request({
    url: `/addFood?${query.toString()}`,
    method: "GET",
    responseMode: "result",
  });
}

export function deleteFoodFromFridge(ingredientId) {
  const query = new URLSearchParams({ ingredientId: String(ingredientId || "") });
  return request({
    url: `/deleteFoodFromFridge?${query.toString()}`,
    method: "GET",
    responseMode: "result",
  });
}

