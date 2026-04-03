import { request } from "../core/http.js";

export function getCartView() {
  return request({
    url: "/foodListView",
    method: "GET",
    responseMode: "result",
  });
}

export function addFoodToCart({ unit, quantity, ingredientId, categoryId }) {
  const query = new URLSearchParams({
    unit: String(unit || ""),
    quantity: String(quantity || ""),
    ingredientId: String(ingredientId || ""),
    categoryId: String(categoryId || ""),
  });
  return request({
    url: `/addFoodToList?${query.toString()}`,
    method: "GET",
    responseMode: "result",
  });
}

export function finishCartItem(listItemId) {
  const query = new URLSearchParams({ listItemId: String(listItemId || "") });
  return request({
    url: `/finishFoodlist?${query.toString()}`,
    method: "GET",
    responseMode: "result",
  });
}

export function deleteCartItem(listItemId) {
  const query = new URLSearchParams({ listItemId: String(listItemId || "") });
  return request({
    url: `/deleteFoodFromList?${query.toString()}`,
    method: "GET",
    responseMode: "result",
  });
}

