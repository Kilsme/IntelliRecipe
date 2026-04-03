import { request } from "../core/http.js";

export function getUserInfo() {
  return request({
    url: "/api/user/info",
    method: "GET",
    responseMode: "result",
  });
}

export function updateUser(payload) {
  return request({
    url: "/api/user/updateUser",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {}),
    responseMode: "result",
  });
}

