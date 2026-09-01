import { api, setToken, clearToken, ApiError } from './api.js';
import { saveReview } from './store.js';

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

// --- Analiz akisi ---

const MAX_CODE_LENGTH = 10000;
const POLL_INTERVAL_MS = 2000; // Gateway limiti 2 token/sn; daha sik yoklamak 429 uretir.
const POLL_TIMEOUT_MS = 60000;

let lastTaskId = null;

function showAnalyzeError(message) {
  const node = el('analyze-error');
  node.textContent = message;
  node.hidden = !message;
}

function setStatus(text) {
  el('status').textContent = text;
}

function updateCounter() {
  const length = el('code').value.length;
  el('counter').textContent = `${length} / ${MAX_CODE_LENGTH}`;
}

function openReviewTab(taskId) {
  return window.open(`review.html?taskId=${encodeURIComponent(taskId)}`, '_blank');
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** COMPLETED/FAILED olana veya zaman asimina kadar yoklar. */
async function pollUntilDone(taskId) {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  while (Date.now() < deadline) {
    await sleep(POLL_INTERVAL_MS);
    const task = await api.status(taskId);
    if (task.status === 'COMPLETED' || task.status === 'FAILED') {
      return task;
    }
    setStatus(`Durum: ${task.status}...`);
  }
  return null;
}

async function onAnalyze() {
  const code = el('code').value;
  if (!code.trim()) {
    showAnalyzeError('Önce bir Java kodu yapıştır.');
    return;
  }
  if (code.length > MAX_CODE_LENGTH) {
    showAnalyzeError(`Kod çok uzun: ${code.length} karakter (en fazla ${MAX_CODE_LENGTH}).`);
    return;
  }

  const question = el('prompt').value.trim();
  // Backend'de language alani yok; dil bilgisi prompt uzerinden tasiniyor.
  const prompt = `Dil: Java. ${question || 'Bu kodu incele.'}`;

  el('analyze').disabled = true;
  showAnalyzeError('');
  el('result-card').hidden = true;
  setStatus('Kuyruğa alınıyor...');

  try {
    const task = await api.analyze(code, prompt);
    setStatus('Analiz ediliyor...');

    const finished = await pollUntilDone(task.taskId);

    if (finished === null) {
      setStatus('');
      showAnalyzeError('Analiz zaman aşımına uğradı. Tekrar deneyebilirsin.');
      return;
    }
    if (finished.status === 'FAILED') {
      setStatus('');
      showAnalyzeError('Analiz başarısız oldu. Sunucu loglarına bakman gerekebilir.');
      return;
    }

    lastTaskId = finished.taskId;
    saveReview({
      taskId: finished.taskId,
      code,
      prompt,
      result: finished.result,
      createdAt: Date.now(),
    });

    setStatus('Review hazır.');
    el('result-card').hidden = false;

    // Popup blocker async acilislari engelleyebilir; engellenirse butona dusuyoruz.
    if (!openReviewTab(finished.taskId)) {
      setStatus('Review hazır. Yeni sekme engellendi, aşağıdaki butonu kullan.');
    }
  } catch (error) {
    setStatus('');
    if (handleUnauthorized(error)) {
      return;
    }
    showAnalyzeError(error instanceof ApiError ? error.message : 'Beklenmeyen bir hata oluştu.');
  } finally {
    el('analyze').disabled = false;
  }
}

el('code').addEventListener('input', updateCounter);
el('analyze').addEventListener('click', onAnalyze);
el('open-review').addEventListener('click', () => {
  if (lastTaskId) {
    openReviewTab(lastTaskId);
  }
});
updateCounter();

el('tab-login').addEventListener('click', () => setMode('login'));
el('tab-register').addEventListener('click', () => setMode('register'));
el('auth-form').addEventListener('submit', onAuthSubmit);
el('logout').addEventListener('click', onLogout);

restoreSession();
