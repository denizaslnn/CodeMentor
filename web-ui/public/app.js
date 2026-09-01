import { api, setToken, clearToken, ApiError } from './api.js';

const el = (id) => document.getElementById(id);

let mode = 'login';
let currentUser = null;

function showAuth() {
  el('auth-screen').hidden = false;
  el('editor-screen').hidden = true;
  el('session').hidden = true;
  currentUser = null;
}

function showEditor(username) {
  currentUser = username;
  el('auth-screen').hidden = true;
  el('editor-screen').hidden = false;
  el('session').hidden = false;
  el('who').textContent = username;
}

function showAuthError(message) {
  const node = el('auth-error');
  node.textContent = message;
  node.hidden = !message;
}

/** 401 ise oturumu dusurup login ekranina doner. Ele alindiysa true. */
export function handleUnauthorized(error) {
  if (error instanceof ApiError && error.status === 401) {
    clearToken();
    showAuth();
    showAuthError('Oturumun sona erdi, tekrar giriş yap.');
    return true;
  }
  return false;
}

function setMode(next) {
  mode = next;
  el('tab-login').classList.toggle('active', mode === 'login');
  el('tab-register').classList.toggle('active', mode === 'register');
  el('auth-submit').textContent = mode === 'login' ? 'Giriş yap' : 'Kayıt ol';
  el('password').autocomplete = mode === 'login' ? 'current-password' : 'new-password';
  showAuthError('');
}

async function onAuthSubmit(event) {
  event.preventDefault();
  const username = el('username').value.trim();
  const password = el('password').value;
  if (!username || !password) {
    showAuthError('Kullanıcı adı ve şifre zorunlu.');
    return;
  }

  el('auth-submit').disabled = true;
  showAuthError('');
  try {
    if (mode === 'register') {
      await api.register(username, password);
    }
    const session = await api.login(username, password);
    setToken(session.accessToken);
    el('password').value = '';
    showEditor(username);
  } catch (error) {
    showAuthError(error instanceof ApiError ? error.message : 'Beklenmeyen bir hata oluştu.');
  } finally {
    el('auth-submit').disabled = false;
  }
}

async function onLogout() {
  try {
    await api.logout();
  } catch {
    // Cikis her halukarda yerelde tamamlanir; sunucu hatasi kullaniciyi kilitlemesin.
  }
  clearToken();
  showAuth();
}

/**
 * Access token'in payload'indan kullanici adini okur (yalnizca gosterim icin;
 * imza dogrulamasi gateway'in isi). Cozulemezse null doner.
 */
function usernameFromToken(token) {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decodeURIComponent(escape(json))).username ?? null;
  } catch {
    return null;
  }
}

/**
 * Acilista sessiz oturum kurtarma: refresh token httpOnly cookie'de durur,
 * access token bellekte oldugu icin sayfa yenilenince kaybolmustur.
 */
async function restoreSession() {
  try {
    const session = await api.refresh();
    setToken(session.accessToken);
    showEditor(usernameFromToken(session.accessToken) ?? 'oturum açık');
  } catch {
    showAuth();
  }
}

el('tab-login').addEventListener('click', () => setMode('login'));
el('tab-register').addEventListener('click', () => setMode('register'));
el('auth-form').addEventListener('submit', onAuthSubmit);
el('logout').addEventListener('click', onLogout);

restoreSession();
