import { request } from "../core/http.js";

export function login(payload) {
  return request({
    url: "/api/auth/login",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {}),
    responseMode: "result",
  });
}

export function register(payload) {
  return request({
    url: "/api/auth/register",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload || {}),
    responseMode: "result",
  });
}

export function getSession() {
  return request({
    url: "/api/auth/session",
    method: "GET",
    responseMode: "result",
  });
}

export function logout() {
  return request({
    url: "/api/auth/logout",
    method: "POST",
    responseMode: "result",
  });
}

