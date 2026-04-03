/**
 * @typedef {Object} AppError
 * @property {'network'|'http'|'session'|'rate_limit'|'business'|'parse'} type
 * @property {string} message
 * @property {number} [status]
 * @property {unknown} [detail]
 */

/**
 * @template T
 * @typedef {Object} HttpResult
 * @property {boolean} ok
 * @property {number} status
 * @property {T | null} data
 * @property {unknown} [raw]
 * @property {AppError | null} error
 * @property {unknown} [payload]
 */

const RESPONSE_MODE_RESULT = "result";
const RESPONSE_MODE_RAW = "raw";

function toAppError(type, message, extra = {}) {
  return { type, message, ...extra };
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (error) {
    return { __rawText: text, __parseError: error instanceof Error ? error.message : String(error) };
  }
}

/**
 * @template T
 * @param {{
 *   url: string;
 *   method?: string;
 *   headers?: Record<string, string>;
 *   body?: BodyInit | null;
 *   credentials?: RequestCredentials;
 *   responseMode?: 'result'|'raw';
 * }} options
 * @returns {Promise<HttpResult<T>>}
 */
export async function request(options) {
  const {
    url,
    method = "GET",
    headers = {},
    body = null,
    credentials = "include",
    responseMode = RESPONSE_MODE_RESULT,
  } = options;

  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      credentials,
      body,
    });
  } catch (error) {
    return {
      ok: false,
      status: 0,
      data: null,
      error: toAppError("network", "网络请求失败", { detail: error }),
    };
  }

  const payload = await parseBody(response);
  const status = response.status;

  if (!response.ok) {
    const serverMessage =
      payload && typeof payload === "object" && "errorMsg" in payload
        ? String(payload.errorMsg || "")
        : "";
    const fallback = `HTTP ${status}`;
    const message = serverMessage || fallback;
    const type = status === 401 ? "session" : status === 429 ? "rate_limit" : "http";

    return {
      ok: false,
      status,
      data: null,
      payload,
      error: toAppError(type, message, { status, detail: payload }),
    };
  }

  if (responseMode === RESPONSE_MODE_RAW) {
    return {
      ok: true,
      status,
      data: /** @type {T} */ (payload),
      raw: payload,
      payload,
      error: null,
    };
  }

  if (payload && typeof payload === "object" && "success" in payload) {
    if (payload.success) {
      return {
        ok: true,
        status,
        data: /** @type {T} */ (payload.data ?? null),
        payload,
        error: null,
      };
    }

    return {
      ok: false,
      status,
      data: /** @type {T} */ (payload.data ?? null),
      payload,
      error: toAppError("business", String(payload.errorMsg || "业务处理失败"), {
        status,
        detail: payload,
      }),
    };
  }

  if (payload && typeof payload === "object" && "__rawText" in payload) {
    return {
      ok: false,
      status,
      data: null,
      payload,
      error: toAppError("parse", "响应解析失败", { status, detail: payload }),
    };
  }

  return {
    ok: true,
    status,
    data: /** @type {T} */ (payload),
    raw: payload,
    payload,
    error: null,
  };
}

/**
 * @param {unknown} result
 * @param {string} fallback
 */
export function getErrorMessage(result, fallback = "请求失败") {
  if (!result || typeof result !== "object") return fallback;
  if ("error" in result && result.error && typeof result.error === "object" && "message" in result.error) {
    return String(result.error.message || fallback);
  }
  return fallback;
}

/**
 * @param {unknown} result
 */
export function isSessionError(result) {
  if (!result || typeof result !== "object" || !("error" in result)) return false;
  const error = result.error;
  if (!error || typeof error !== "object") return false;
  return error.type === "session" || error.status === 401;
}

/**
 * @param {unknown} result
 */
export function isRateLimitError(result) {
  if (!result || typeof result !== "object" || !("error" in result)) return false;
  const error = result.error;
  if (!error || typeof error !== "object") return false;
  return error.type === "rate_limit" || error.status === 429;
}
