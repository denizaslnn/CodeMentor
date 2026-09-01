# web-ui (Login + Java Kod Review Arayuzu) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tarayicidan giris yapip Java kodu gonderebilecegimiz, review sonucunu yeni bir sekmede kod blogu olarak gorebilecegimiz statik bir web arayuzu eklemek.

**Architecture:** `nginx:alpine` uzerinde statik dosya servis eden yeni bir `web-ui` container'i (host portu 3000). Tarayici dogrudan `http://localhost:8080/api/v1/**` adresindeki gateway'e konusur; gateway'in CORS ayari zaten `http://localhost:3000`'e izin verdigi icin Java tarafinda hicbir degisiklik yapilmaz. JS, ES modulleri halinde ayrilir: `api.js` (HTTP istemcisi, DOM bilmez), `store.js` (localStorage), `app.js` (ana ekran akisi), `review.js` (sonuc sekmesi).

**Tech Stack:** Vanilla HTML/CSS/JS (ES modules), nginx:alpine, Docker Compose, highlight.js (cdnjs, opsiyonel).

## Global Constraints

- Yalnizca **Java** kod parcalari hedeflenir. Backend `CodeRequestDto`'da `language` alani yok; dil bilgisi `prompt` uzerinden tasinir: `"Dil: Java. " + (kullanici sorusu | "Bu kodu incele.")`.
- Access token **yalnizca bellekte** tutulur, `localStorage`/`sessionStorage`'a yazilmaz. Oturum sureklililigi `POST /api/v1/auth/refresh` (httpOnly cookie) ile saglanir.
- Tum API isteklerinde `credentials: 'include'` ve `Accept-Language: tr` gonderilir.
- Status polling araligi **2 saniye**, ust sinir **60 saniye**. Gateway rate limiter'i 2 token/sn (burst 5) — daha sik yoklamak 429 uretir.
- Kaynak kod ust siniri **10000 karakter** (backend `@Size` ile ayni).
- Kullanicidan gelen hicbir metin `innerHTML` ile basilmaz; yalnizca `textContent`.
- `localStorage` erisimleri try/catch icinde olmali (private mode'da erisim exception atabilir).
- Java tarafinda (code-service / ai-service / api-gateway) hicbir dosya degistirilmez.

---

### Task 1: Container iskeleti ve statik kabuk

`web-ui` container'i ayaga kalkar ve login formunu gosterir. Henuz JS yok.

**Files:**
- Create: `web-ui/index.html`
- Create: `web-ui/styles.css`
- Create: `web-ui/nginx.conf`
- Create: `web-ui/Dockerfile`
- Create: `web-ui/.dockerignore`
- Modify: `docker-compose.yml`

**Interfaces:**
- Consumes: yok (ilk task).
- Produces: DOM element id'leri — sonraki task'larin JS'i bunlara baglanir:
  `session`, `who`, `logout`, `auth-screen`, `tab-login`, `tab-register`,
  `auth-form`, `username`, `password`, `auth-submit`, `auth-error`,
  `editor-screen`, `code`, `counter`, `prompt`, `analyze`, `status`,
  `analyze-error`, `result-card`, `open-review`.
  Servis adresi: `http://localhost:3000`.

- [ ] **Step 1: index.html'i yaz**

`web-ui/index.html`:

```html
<!doctype html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>CodeMentor</title>
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <header class="topbar">
    <span class="brand">CodeMentor</span>
    <div class="session" id="session" hidden>
      <span id="who" class="muted"></span>
      <button id="logout" class="ghost" type="button">Çıkış</button>
    </div>
  </header>

  <main>
    <section id="auth-screen" class="card narrow">
      <div class="tabs">
        <button id="tab-login" class="tab active" type="button">Giriş</button>
        <button id="tab-register" class="tab" type="button">Kayıt</button>
      </div>
      <form id="auth-form">
        <label for="username">Kullanıcı adı</label>
        <input id="username" name="username" autocomplete="username" required>
        <label for="password">Şifre</label>
        <input id="password" name="password" type="password" autocomplete="current-password" required>
        <button id="auth-submit" type="submit">Giriş yap</button>
      </form>
      <p class="hint">Şifre en az 8 karakter olmalı; rakam, küçük harf, büyük harf ve özel karakter içermeli.</p>
      <p id="auth-error" class="error" hidden></p>
    </section>

    <section id="editor-screen" hidden>
      <div class="card">
        <label for="code">Java kodu <span class="muted">(en fazla 10.000 karakter)</span></label>
        <textarea id="code" spellcheck="false" placeholder="public class Ornek {&#10;    public static void main(String[] args) {&#10;    }&#10;}"></textarea>
        <div class="row">
          <span id="counter" class="muted">0 / 10000</span>
        </div>

        <label for="prompt">Sorun <span class="muted">(opsiyonel)</span></label>
        <input id="prompt" placeholder="Örn. güvenlik açığı var mı?">

        <div class="row">
          <button id="analyze" type="button">Review et</button>
          <span id="status" class="muted"></span>
        </div>
        <p id="analyze-error" class="error" hidden></p>
      </div>

      <div id="result-card" class="card" hidden>
        <p>Review hazır.</p>
        <button id="open-review" type="button">Review'i yeni sekmede aç</button>
      </div>
    </section>
  </main>
</body>
</html>
```

- [ ] **Step 2: styles.css'i yaz**

`web-ui/styles.css`:

```css
:root {
  --bg: #f6f7f9;
  --card: #ffffff;
  --text: #1c1f24;
  --muted: #6b7280;
  --border: #d9dde3;
  --accent: #2563eb;
  --error: #b91c1c;
}

* { box-sizing: border-box; }

body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font: 15px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--card);
  border-bottom: 1px solid var(--border);
}

.brand { font-weight: 600; }
.session { display: flex; align-items: center; gap: 12px; }

main { max-width: 900px; margin: 24px auto; padding: 0 16px; display: grid; gap: 16px; }

.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 20px;
  display: grid;
  gap: 10px;
}

.narrow { max-width: 420px; margin: 40px auto; }

.tabs { display: flex; gap: 8px; margin-bottom: 8px; }

.tab {
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
}

.tab.active { background: var(--accent); border-color: var(--accent); color: #fff; }

label { font-weight: 500; }

input, textarea {
  width: 100%;
  padding: 9px 11px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font: inherit;
  background: #fff;
  color: inherit;
}

textarea {
  min-height: 260px;
  resize: vertical;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  white-space: pre;
  overflow-wrap: normal;
  overflow-x: auto;
}

button {
  font: inherit;
  padding: 9px 16px;
  border: none;
  border-radius: 6px;
  background: var(--accent);
  color: #fff;
  cursor: pointer;
}

button:disabled { opacity: .55; cursor: default; }

.ghost { background: transparent; color: var(--muted); border: 1px solid var(--border); }

.row { display: flex; align-items: center; gap: 12px; }
.muted { color: var(--muted); font-size: 13px; }
.hint { color: var(--muted); font-size: 12px; margin: 0; }
.error { color: var(--error); margin: 0; }

/* review.html */
.review-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 800px) {
  .review-grid { grid-template-columns: 1fr; }
}

.review-grid h2 { font-size: 14px; margin: 0 0 8px; }

pre {
  margin: 0;
  padding: 14px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
}

pre code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
pre.plain { background: var(--card); color: var(--text); border: 1px solid var(--border); white-space: pre-wrap; }
```

- [ ] **Step 3: nginx yapilandirmasi ve Dockerfile**

`web-ui/nginx.conf`:

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # Gelistirme kolayligi: tarayici eski dosyayi cache'leyip kafa karistirmasin.
    add_header Cache-Control "no-store" always;

    location / {
        try_files $uri $uri/ =404;
    }
}
```

`web-ui/Dockerfile`:

```dockerfile
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY . /usr/share/nginx/html/

EXPOSE 80
```

`web-ui/.dockerignore`:

```
Dockerfile
.dockerignore
nginx.conf
```

- [ ] **Step 4: Compose'a web-ui servisini ekle**

`docker-compose.yml` icinde `api-gateway:` servisinin TAMAMINDAN SONRA, `volumes:` satirindan ONCE ekle:

```yaml
  web-ui:
    build:
      context: ./web-ui
      dockerfile: Dockerfile
    container_name: web-ui
    depends_on:
      api-gateway:
        condition: service_started
    ports:
      - "3000:80"
    networks:
      - codementor-net
```

- [ ] **Step 5: Derle, kaldir ve dogrula**

Run:
```bash
docker compose build web-ui && docker compose up -d web-ui && sleep 8
curl -s -o /dev/null -w "index: %{http_code}\n" http://localhost:3000/
curl -s -o /dev/null -w "styles: %{http_code}\n" http://localhost:3000/styles.css
curl -s http://localhost:3000/ | grep -c 'id="auth-form"'
curl -s -I http://localhost:3000/ | grep -i "cache-control"
```
Expected: `index: 200`, `styles: 200`, grep sonucu `1`, `Cache-Control: no-store`.

- [ ] **Step 6: Commit**

```bash
git add web-ui/ docker-compose.yml
git commit -m "feat(web-ui): nginx container'i ve statik kabuk"
```

---

### Task 2: Kimlik dogrulama akisi

Login/kayit calisir, sayfa yenilendiginde oturum cookie ile geri gelir, cikis yapilir.

**Files:**
- Create: `web-ui/api.js`
- Create: `web-ui/app.js`
- Modify: `web-ui/index.html` (sadece `</body>` oncesine script etiketi)

**Interfaces:**
- Consumes: Task 1'in DOM id'leri.
- Produces (sonraki task `api.js`'ten bunlari kullanir):
  - `setToken(token)`, `clearToken()`, `getToken()`
  - `ApiError` — alanlari: `message`, `status` (number, ag hatasinda `0`), `errorCode`
  - `api.register(username, password)`, `api.login(username, password)` -> `{accessToken, expiresIn}`, `api.refresh()` -> `{accessToken, expiresIn}`, `api.logout()`, `api.analyze(sourceCode, prompt)` -> `{taskId, status}`, `api.status(taskId)` -> `{taskId, status, result}`
  - `app.js` icinde: `showAuth()`, `showEditor(username)`, `handleUnauthorized(error)` -> `boolean`

- [ ] **Step 1: api.js'i yaz**

`web-ui/api.js`:

```js
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
```

- [ ] **Step 2: app.js'in auth kismini yaz**

`web-ui/app.js`:

```js
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
```

- [ ] **Step 3: index.html'e script etiketini ekle**

`web-ui/index.html` icinde `</main>` ile `</body>` arasina ekle:

```html
  <script type="module" src="app.js"></script>
```

- [ ] **Step 4: Derle ve servis edildigini dogrula**

Run:
```bash
docker compose build web-ui && docker compose up -d web-ui && sleep 8
curl -s -o /dev/null -w "app.js: %{http_code}\n" http://localhost:3000/app.js
curl -s -o /dev/null -w "api.js: %{http_code}\n" http://localhost:3000/api.js
curl -s -I http://localhost:3000/app.js | grep -i "content-type"
curl -s http://localhost:3000/ | grep -c 'type="module"'
```
Expected: ikisi de `200`, content-type `application/javascript` (veya `text/javascript`), grep sonucu `1`.

- [ ] **Step 5: Tarayicida manuel dogrula**

`http://localhost:3000` adresini ac ve sirasiyla kontrol et:

1. Login formu geliyor, konsol hatasi yok (DevTools > Console).
2. "Kayıt" sekmesi -> yeni bir kullanici adi + `Test1234!x` -> kayit + otomatik giris oluyor, kod ekrani aciliyor.
3. Sayfayi yenile (Cmd+R) -> login formu DEGIL, dogrudan kod ekrani geliyor (sessiz refresh calisti).
4. "Çıkış" -> login formuna donuyor. Tekrar yenile -> login formu kaliyor.
5. Yanlis sifreyle giris -> "Geçersiz kullanıcı adı veya şifre" mesaji Turkce cikiyor.

- [ ] **Step 6: Commit**

```bash
git add web-ui/api.js web-ui/app.js web-ui/index.html
git commit -m "feat(web-ui): login/kayit akisi ve sessiz oturum kurtarma"
```

---

### Task 3: Analiz akisi, sonuc sekmesi ve dokumantasyon

Java kodu gonderilir, sonuc yeni sekmede kod blogu olarak gosterilir.

**Files:**
- Create: `web-ui/store.js`
- Create: `web-ui/review.html`
- Create: `web-ui/review.js`
- Modify: `web-ui/app.js` (analiz bolumu eklenir)
- Modify: `TEKNIK_DOKUMANTASYON.md`

**Interfaces:**
- Consumes: Task 2'nin `api`, `ApiError`, `handleUnauthorized`; Task 1'in DOM id'leri (`code`, `counter`, `prompt`, `analyze`, `status`, `analyze-error`, `result-card`, `open-review`).
- Produces: `store.js` -> `saveReview(review)`, `loadReview(taskId)`; review nesnesi `{ taskId, code, prompt, result, createdAt }` (createdAt: `Date.now()`).

- [ ] **Step 1: store.js'i yaz**

`web-ui/store.js`:

```js
/**
 * Review sonuclarinin tarayici tarafi deposu.
 *
 * Ana ekran ile sonuc sekmesi ayri window'lardir; veri URL ile tasinamaz
 * (kod 10.000 karaktere kadar cikabilir). Ayni origin'de olduklari icin
 * localStorage ortak alan olarak kullanilir.
 */
const KEY_PREFIX = 'review:';
const MAX_ENTRIES = 20;

export function saveReview(review) {
  try {
    localStorage.setItem(KEY_PREFIX + review.taskId, JSON.stringify(review));
    prune();
  } catch {
    // Private mode / kota dolu: sonuc yine de ana ekranda gorunur, sekme bos durum gosterir.
  }
}

export function loadReview(taskId) {
  try {
    const raw = localStorage.getItem(KEY_PREFIX + taskId);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function prune() {
  const keys = Object.keys(localStorage).filter((key) => key.startsWith(KEY_PREFIX));
  if (keys.length <= MAX_ENTRIES) {
    return;
  }
  keys
    .map((key) => {
      let createdAt = 0;
      try {
        createdAt = JSON.parse(localStorage.getItem(key))?.createdAt ?? 0;
      } catch {
        createdAt = 0;
      }
      return { key, createdAt };
    })
    .sort((a, b) => a.createdAt - b.createdAt)
    .slice(0, keys.length - MAX_ENTRIES)
    .forEach(({ key }) => localStorage.removeItem(key));
}
```

- [ ] **Step 2: app.js'e analiz akisini ekle**

`web-ui/app.js` dosyasinda ilk satirdaki import'u su sekilde degistir:

```js
import { api, setToken, clearToken, ApiError } from './api.js';
import { saveReview } from './store.js';
```

Ve dosyanin SONUNDAKI olay baglamalarindan (`el('tab-login').addEventListener...`) ONCE su bolumu ekle:

```js
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
```

- [ ] **Step 3: review.html'i yaz**

`web-ui/review.html`:

```html
<!doctype html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Review — CodeMentor</title>
  <link rel="stylesheet" href="styles.css">
  <!-- Syntax highlight opsiyoneldir: CDN'e ulasilamazsa sayfa duz monospace calisir. -->
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css">
  <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/java.min.js"></script>
</head>
<body>
  <header class="topbar">
    <span class="brand">CodeMentor · Review</span>
    <span id="meta" class="muted"></span>
  </header>

  <main>
    <p id="empty" class="card" hidden>Bu review bulunamadı. Sekmeyi ana ekrandan yeniden aç.</p>

    <div id="content" class="review-grid" hidden>
      <section>
        <h2>Gönderilen Java kodu</h2>
        <pre><code id="code-block" class="language-java"></code></pre>
      </section>
      <section>
        <h2>Review</h2>
        <pre class="plain"><code id="result-block"></code></pre>
      </section>
    </div>
  </main>

  <script type="module" src="review.js"></script>
</body>
</html>
```

- [ ] **Step 4: review.js'i yaz**

`web-ui/review.js`:

```js
import { loadReview } from './store.js';

const el = (id) => document.getElementById(id);

const taskId = new URLSearchParams(window.location.search).get('taskId');
const review = taskId ? loadReview(taskId) : null;

if (!review) {
  el('empty').hidden = false;
} else {
  el('content').hidden = false;
  // textContent: gelen metin HTML olarak yorumlanmaz.
  el('code-block').textContent = review.code;
  el('result-block').textContent = review.result;
  el('meta').textContent = `${review.taskId} · ${new Date(review.createdAt).toLocaleString('tr-TR')}`;
  document.title = `Review ${review.taskId.slice(0, 8)} — CodeMentor`;

  // highlight.js yuklenmediyse (CDN kapali) sayfa duz monospace olarak kalir.
  if (window.hljs) {
    window.hljs.highlightElement(el('code-block'));
  }
}
```

- [ ] **Step 5: Derle ve servis edildigini dogrula**

Run:
```bash
docker compose build web-ui && docker compose up -d web-ui && sleep 8
for f in / review.html app.js api.js store.js review.js styles.css; do
  printf "%-14s " "$f"; curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:3000$f"
done
curl -s http://localhost:3000/review.html | grep -c 'id="code-block"'
```
Expected: hepsi `200`, grep sonucu `1`.

- [ ] **Step 6: Tarayicida uctan uca manuel dogrula**

`http://localhost:3000` adresinde:

1. Giris yap (Task 2'de olusturdugun kullanici).
2. Textarea'ya bir Java sinifi yapistir, orn:
   ```java
   public class Hesap {
       private int bakiye;
       public void para(int m) { bakiye += m; }
   }
   ```
   Sayacin `0 / 10000`'den guncellendigini gor.
3. "Review et" -> durum metni "Kuyruğa alınıyor..." -> "Analiz ediliyor..." seklinde ilerliyor.
4. Birkac saniye icinde YENI SEKME aciliyor: solda gonderdigin kod (renklendirilmis), sagda mock-vllm'den gelen review metni. Ust barda taskId ve tarih var.
5. Yeni sekme popup blocker'a takildiysa ana ekranda "Review'i yeni sekmede aç" butonu goruntuleniyor; tiklayinca sekme aciliyor.
6. Bos textarea ile "Review et" -> "Önce bir Java kodu yapıştır." uyarisi.
7. Yeni sekmenin adresindeki `taskId`'yi elle bozup yenile -> "Bu review bulunamadı." bos durumu.

- [ ] **Step 7: Dokumantasyonu guncelle**

`TEKNIK_DOKUMANTASYON.md` dosyasinin SONUNA ekle:

```markdown

---

## 9. Web Arayüzü (web-ui)

`web-ui/` altında, nginx ile servis edilen statik bir arayüz. Tarayıcı doğrudan
gateway'e (`http://localhost:8080`) konuşur; gateway'in `CORS_ALLOWED_ORIGINS`
değeri `http://localhost:3000` olduğu için ek yapılandırma gerekmez.

**Adres:** http://localhost:3000

| Dosya | Sorumluluk |
|---|---|
| `index.html` | Login/kayıt + kod gönderme ekranı |
| `review.html` | Yeni sekmede sonuç görünümü |
| `api.js` | Gateway HTTP istemcisi (DOM bilmez) |
| `app.js` | Ana ekran akışı: auth, analiz, polling |
| `store.js` | Review sonuçlarının localStorage deposu |
| `review.js` | Sonuç sekmesinin render'ı |

### Akış

Giriş → Java kodu yapıştır → "Review et" → `POST /api/v1/analyze` →
`GET /api/v1/status/{taskId}` 2 saniyede bir yoklanır (gateway limiti 2 token/sn) →
`COMPLETED` olunca sonuç `localStorage`'a yazılır ve `review.html?taskId=...`
yeni sekmede açılır: solda gönderilen kod, sağda review.

### Oturum

Access token **yalnızca bellekte** tutulur, `localStorage`'a yazılmaz. Sayfa
yenilendiğinde açılışta sessizce `POST /api/v1/auth/refresh` denenir; refresh token
httpOnly cookie'de olduğu için oturum korunur.

### Bilinen sınırlar

- Yalnızca Java hedeflenir. Backend'de `language` alanı olmadığı için dil bilgisi
  `prompt` üzerinden gider (`"Dil: Java. ..."`).
- Yeni sekme async bir işlemden sonra açıldığı için popup blocker'a takılabilir;
  bu durumda ana ekrandaki "Review'i yeni sekmede aç" butonu kullanılır.
- Syntax highlighting cdnjs'ten yüklenir; internet yoksa sayfa düz monospace olarak
  çalışmaya devam eder.
```

- [ ] **Step 8: Commit**

```bash
git add web-ui/store.js web-ui/review.html web-ui/review.js web-ui/app.js TEKNIK_DOKUMANTASYON.md
git commit -m "feat(web-ui): analiz akisi ve yeni sekmede review gorunumu"
```
