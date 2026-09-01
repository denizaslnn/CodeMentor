# web-ui: login + Java kod review arayuzu

Tarih: 2026-09-01
Durum: onaylandi, implementasyon bekliyor

## Problem

Sistem su an yalnizca curl / Swagger uzerinden kullanilabiliyor. Akisi (login ->
kod gonder -> review oku) uctan uca gorebilecegimiz basit bir web arayuzu yok.

## Cozum ozeti

`nginx:alpine` uzerinde statik dosya servis eden yeni bir `web-ui` container'i.
Tarayici dogrudan gateway'e (`http://localhost:8080/api/v1/**`) konusur. Gateway'in
`CORS_ALLOWED_ORIGINS` degeri zaten `http://localhost:3000` oldugu icin **Java
tarafinda hicbir degisiklik yapilmaz**.

Bu asamada yalnizca **Java** kod parcalari hedeflenir.

## Mimari

```
tarayici (http://localhost:3000)
  |  fetch, credentials: include
  v
api-gateway (http://localhost:8080)  -- JWT dogrulama, rate limit, CORS
  v
code-service -> RabbitMQ -> ai-service -> mock-vllm
```

Statik dosyalar:

```
web-ui/
  index.html     login/kayit + kod gonderme ekrani
  review.html    yeni sekmede sonuc gorunumu
  app.js         API istemcisi + akis (auth, analyze, polling)
  review.js      sonuc sekmesinin render'i
  styles.css     iki sayfanin paylastigi stil
  Dockerfile
```

Compose'da `web-ui` servisi `3000:80` ile yayinlanir ve `api-gateway`'e
`depends_on: service_started` ile baglanir.

## Kullandigi API kontrati (mevcut, degismiyor)

| Endpoint | Govde | Cevap (`data`) |
|---|---|---|
| `POST /api/v1/auth/register` | `{username, password}` | `{username, role}` |
| `POST /api/v1/auth/login` | `{username, password}` | `{accessToken, expiresIn}` + refresh cookie |
| `POST /api/v1/auth/refresh` | — (cookie) | `{accessToken, expiresIn}` |
| `POST /api/v1/auth/logout` | — (cookie) | `null` |
| `POST /api/v1/analyze` | `{sourceCode, prompt}` | `{taskId, status}` |
| `GET /api/v1/status/{taskId}` | — | `{taskId, status, result}` |

Tum cevaplar `{success, message, data, httpStatusCode, errorCode}` sarmalindadir.
Hata durumunda `message` alani zaten yerelleştirilmis metni tasir; arayuz bu metni
oldugu gibi gosterir ve isteklere `Accept-Language: tr` ekler.

## Dogrulanmis varsayimlar

Tasarim iki kritik varsayima dayaniyordu; ikisi de calisan stack uzerinde
olculdu (2026-09-01):

1. **CORS preflight JWT filtresine takilmiyor.** Authorization header'i olmayan
   `OPTIONS /api/v1/analyze` istegi `200` doner ve
   `Access-Control-Allow-Origin: http://localhost:3000` +
   `Access-Control-Allow-Credentials: true` header'larini tasir. Gateway'in CORS
   WebFilter'i preflight'i `JwtGlobalFilter`'dan once yanitliyor.
2. **Cookie ile sessiz refresh calisiyor.** Login'den sonra `refresh_token`
   cookie'si set ediliyor ve Authorization header'i olmadan yapilan
   `POST /api/v1/auth/refresh` yeni bir access token donduruyor.

## Oturum yonetimi

- Access token **yalnizca bellekte** (JS degiskeni) tutulur; `localStorage`'a
  yazilmaz (XSS'te sizmamasi icin).
- Sayfa yenilenince token kaybolur. Bu yuzden acilista sessizce
  `POST /api/v1/auth/refresh` denenir: refresh token httpOnly cookie'dedir,
  `SameSite=Lax` ve `localhost:3000` -> `localhost:8080` ayni site sayildigi icin
  gonderilir. Basarili -> kod ekrani, basarisiz -> login formu.
- Tum isteklerde `credentials: 'include'`.
- `401` alinirsa bellekteki token temizlenir ve login ekranina donulur.
- Login ekraninda "Giris" / "Kayit" sekmeleri bulunur (kayit olmadan kullanici
  olusturulamaz).
- "Cikis" butonu `POST /api/v1/auth/logout` cagirir ve bellegi temizler.

## Analiz akisi

1. Kullanici Java kodunu textarea'ya yazar, istege bagli bir soru girer.
2. `POST /api/v1/analyze` gonderilir. `prompt` alani sabit bir dil onekiyle
   kurulur: `"Dil: Java. " + (kullanici sorusu | "Bu kodu incele.")` — backend'in
   `CodeRequestDto`'sunda `language` alani olmadigi icin dil bilgisi prompt
   uzerinden tasinir.
3. `GET /api/v1/status/{taskId}` **2 saniyede bir** yoklanir, en fazla 60 saniye.
   Gateway rate limiter'i 2 token/sn (burst 5) oldugu icin daha sik yoklamak 429
   uretir.
4. `COMPLETED`: sonuc `localStorage`'a `review:<taskId>` anahtariyla yazilir
   (`{taskId, code, prompt, result, createdAt}`), sonra `review.html?taskId=...`
   yeni sekmede acilir. Kod 10.000 karaktere kadar cikabildigi icin veri URL ile
   tasinmaz.
5. `FAILED` veya zaman asimi: yeni sekme acilmaz, ana ekranda hata karti gosterilir.

`localStorage`'da en fazla son 20 kayit tutulur; daha eskiler silinir.

### Popup blocker

`window.open` async bir islemden (polling) sonra cagrildigi icin tarayici
engelleyebilir. Bu yuzden:

- Otomatik `window.open` denenir.
- Donen deger `null` ise (veya her durumda yedek olarak) ana ekranda belirgin bir
  **"Review'i yeni sekmede ac"** butonu gosterilir. Butona tiklama kullanici
  hareketi oldugu icin engellenmez.

## Sonuc sekmesi (review.html)

- `?taskId=` parametresiyle acilir, veriyi `localStorage`'dan okur.
- Iki kolon: solda gonderilen Java kodu, sagda review metni; ikisi de
  `<pre><code>` icinde monospace.
- Syntax highlighting: `highlight.js` (cdnjs, yalnizca Java dili). CDN'e
  ulasilamazsa sayfa duz monospace olarak calismaya devam eder — highlight
  opsiyoneldir, kirilma nedeni degildir.
- Kayit bulunamazsa ("sekmeyi cok sonra actin, kayit silinmis") anlasilir bir
  bos-durum mesaji gosterilir.

## Hata yonetimi

| Durum | Davranis |
|---|---|
| `401` | Bellek temizlenir, login ekranina donulur |
| `429` | "Cok fazla istek gonderildi, biraz bekleyip tekrar dene" |
| Diger HTTP hatalari | API'nin `message` alani gosterilir |
| Ag hatasi / gateway kapali | "Sunucuya ulasilamadi" mesaji |
| Task `FAILED` | Ana ekranda hata karti (yeni sekme acilmaz) |
| 60 sn zaman asimi | "Analiz zaman asimina ugradi" + tekrar deneme imkani |

## Test

Repoda JS test altyapisi yok; yalnizca bu arayuz icin Playwright/Jest getirmek
orantisiz olur. Bunun yerine:

1. **Otomatik:** `web-ui` container'inin dosyalari servis ettigi `curl` ile
   dogrulanir (`/`, `/review.html`, `/app.js`, `/styles.css` icin HTTP 200 ve
   beklenen icerik parcasi).
2. **Manuel:** dokumana adim adim kontrol listesi yazilir — kayit, giris, Java
   kodu gonderme, yeni sekmenin acilmasi, sayfa yenilendiginde oturumun
   korunmasi, yanlis sifre, cikis sonrasi login'e donus.

Otomatik uctan uca (Playwright) testi istenirse ayri bir is olarak sonra eklenir.

## Kapsam disi

- Java disindaki diller
- Review gecmisi ekrani
- Kod duzenleyici (Monaco / CodeMirror)
- Tema secici, kullanici profili
- Otomatik e2e test altyapisi
