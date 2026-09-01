/**
 * Gateway ile konusan ince HTTP istemcisi. DOM bilmez.
 *
 * Access token BILEREK yalnizca bellekte tutulur (localStorage'a yazilmaz):
 * XSS durumunda kalici olarak sizmasin. Sayfa yenilenince token kaybolur,
 * oturum httpOnly refresh cookie'si ile /auth/refresh uzerinden geri alinir.
 */
const BASE_URL = window.CODEMENTOR_API ?? 'http://localhost:8080';

let accessToken = null;

export function setToken(token) {
  accessToken = token;
}

export function clearToken() {
  accessToken = null;
}

export function getToken() {
  return accessToken;
}

export class ApiError extends Error {
  constructor(message, status, errorCode) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.errorCode = errorCode;
  }
}

async function request(path, { method = 'GET', body, auth = false } = {}) {
  const headers = { 'Accept-Language': 'tr' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (auth && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError('Sunucuya ulaşılamadı. Stack ayakta mı?', 0, 'NETWORK');
  }

  let payload = null;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }

  if (!response.ok) {
    const fallback = response.status === 429
      ? 'Çok fazla istek gönderildi, biraz bekleyip tekrar dene.'
      : `İstek başarısız (HTTP ${response.status}).`;
    throw new ApiError(payload?.message || fallback, response.status, payload?.errorCode);
  }

  return payload?.data ?? null;
}

export const api = {
  register: (username, password) =>
    request('/api/v1/auth/register', { method: 'POST', body: { username, password } }),
  login: (username, password) =>
    request('/api/v1/auth/login', { method: 'POST', body: { username, password } }),
  refresh: () => request('/api/v1/auth/refresh', { method: 'POST' }),
  logout: () => request('/api/v1/auth/logout', { method: 'POST' }),
  analyze: (sourceCode, prompt) =>
    request('/api/v1/analyze', { method: 'POST', auth: true, body: { sourceCode, prompt } }),
  status: (taskId) =>
    request(`/api/v1/status/${encodeURIComponent(taskId)}`, { auth: true }),
};
