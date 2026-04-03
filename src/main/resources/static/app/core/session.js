import { getErrorMessage, isSessionError } from "./http.js";
import { getSession } from "../api/authApi.js";

/**
 * @param {{
 *   redirectUrl?: string;
 *   delayMs?: number;
 *   onFail?: (message: string) => void;
 * }} [options]
 */
export async function requireSessionOrRedirect(options = {}) {
  const { redirectUrl = "login.html", delayMs = 1200, onFail } = options;
  const sessionResult = await getSession();

  if (sessionResult.ok && sessionResult.data) {
    return {
      ok: true,
      user: sessionResult.data,
      result: sessionResult,
    };
  }

  const message = isSessionError(sessionResult)
    ? "登录状态失效，请重新登录"
    : getErrorMessage(sessionResult, "请先登录");

  if (typeof onFail === "function") {
    onFail(message);
  }

  window.setTimeout(() => {
    window.location.href = redirectUrl;
  }, delayMs);

  return {
    ok: false,
    user: null,
    result: sessionResult,
  };
}
