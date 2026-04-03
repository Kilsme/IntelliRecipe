import { request } from "../core/http.js";

export function getAllFoodView() {
  return request({
    url: "/AllFoodView",
    method: "GET",
    responseMode: "raw",
  });
}

