# CodeMentor - Sistem Mimarisi ve Teknik Dokümantasyon

## 1. Proje Özeti (Executive Summary)
**CodeMentor**, yazılım geliştiricilerin kod parçacıklarını asenkron ve yüksek erişilebilir (highly available) bir altyapı üzerinden yapay zekaya (LLM) analiz ettirebildiği, mikroservis (microservices) mimarisiyle tasarlanmış bir platformdur.

Sistem; kullanıcıyı bekletmeyen (non-blocking) yapısı, mesaj kuyruğu (Message Broker) destekli asenkron işleme modeli ve çok katmanlı önbellekleme (Caching) mekanizmalarıyla ölçeklenebilir (scalable) bir kurumsal ürün standartlarında geliştirilmiştir.

## 2. Teknoloji Yığını (Technology Stack)
Projemizde modern yazılım mühendisliği standartlarına uygun olarak aşağıdaki teknolojiler konumlandırılmıştır:

* **Backend Core:** Java, Spring Boot (Microservices)
* **API Gateway & Güvenlik:** Spring Cloud Gateway, JWT (JSON Web Token) tabanlı yetkilendirme
* **Asenkron Mesajlaşma:** RabbitMQ (Event-Driven Architecture)
* **Önbellek & Yüksek Performans:** Redis
* **İlişkisel Veritabanı:** PostgreSQL
* **Konteynerizasyon & DevOps:** Docker, Docker Compose

---

## 3. Sistem Mimarisi (System Architecture)
Sistem birbirinden tamamen izole, kendi veritabanı veya önbellek erişimleri olan üç temel mikroservisten oluşmaktadır:

### 3.1. API Gateway Service (Giriş Katmanı & Güvenlik)
* Sistemin tek giriş noktasıdır (Single Point of Entry).
* **Kimlik Doğrulama (Auth):** Gelen tüm istekler `JwtGlobalFilter` üzerinden geçirilir. JWT imzası ve süresi doğrulanır; geçersiz durumlarda anında **401 Unauthorized** döndürülerek arka servislere yük binmesi engellenir.
* **Header Aktarımı:** Başarılı doğrulama sonrası, Token içerisindeki kullanıcı bilgisi `X-User-Id` başlığına eklenerek güvenli servislere iletilir.

### 3.2. Code Service (İş Mantığı Katmanı)
* Sistemin orkestrasyon merkezidir. İstemciden gelen analiz taleplerini karşılar.
* Kayıtları kalıcılık için PostgreSQL'e kaydederken, analizin asenkron başlatılması için RabbitMQ'ya bir `CodeTaskMessage` fırlatır.
* Durum sorgulama (Polling) yükünü kaldırmak için PostgreSQL yerine doğrudan Redis ile haberleşir.

### 3.3. AI Service (İşçi Katmanı)
* Arka planda izole çalışan işçi (worker) servisidir.
* RabbitMQ kuyruğunu dinler, gelen mesajları alır ve LLM entegrasyonu ile analiz sürecini işletir.
* Görev durumlarını "PROCESSING" ve tamamlandığında "COMPLETED" olarak hem dağıtık önbelleğe (Redis) hem de ana veritabanına (PostgreSQL) kaydeder.

---

## 4. Kullanıcı Akışı ve Sistem Davranışı (User Activity & Flow)
Sistem, AI analiz işlemlerinde kullanıcı tarafında zaman aşımını (timeout) ve arayüz donmalarını engellemek için **Olay Yönelimli Asenkron Akış (Event-Driven Asynchronous Flow)** kullanır.

**Aşama 1: İstek Gönderimi (Submission & Acknowledgment)**
1. Kullanıcı, Bearer Token (JWT) ile kodunu ve prompt bilgisini Gateway üzerinden `/api/v1/analyze` uç noktasına gönderir.
2. Gateway doğrulamayı yapar ve isteği Code Service'e iletir.
3. Code Service bu görevi PostgreSQL'e `PENDING` statüsü ile yazar ve kullanıcıya anında bir `taskId` döner.
4. Eşzamanlı olarak arka planda RabbitMQ'ya işlemin başlatılması için bir mesaj fırlatılır.

**Aşama 2: Asenkron İşleme (Background Processing)**
1. AI Service, RabbitMQ'dan mesajı tüketir.
2. Görevin durumunu Redis üzerinde `PROCESSING` olarak günceller.
3. Yapay Zeka (LLM) analizini tamamladıktan sonra sonucu ve `COMPLETED` statüsünü hem Redis'e hem de kalıcılık için PostgreSQL'e kaydeder.

**Aşama 3: Durum Sorgulama ve Sonuç Alma (Polling Mechanism)**
1. Kullanıcı, elindeki `taskId` ile belirli aralıklarla `/api/v1/status/{taskId}` uç noktasına istek atar.
2. Code Service, veritabanı maliyetini düşürmek için sorguyu milisaniyeler içinde **Redis Cache** üzerinden yanıtlar.
3. Sonuç `COMPLETED` ise, analiz sonucu temiz bir JSON yapısında kullanıcıya iletilir.

---

## 5. API Spesifikasyonları (API Endpoints)

| HTTP Metodu | Uç Nokta (Endpoint) | Yetki (Auth) | Açıklama | Yanıt Modeli (Response) |
| :--- | :--- | :---: | :--- | :--- |
| **POST** | `/api/v1/analyze` | Evet (JWT) | Kaynak kod ve prompt analiz kuyruğuna eklenir. | `{"taskId": "uuid"}` |
| **GET** | `/api/v1/status/{taskId}`| Evet (JWT) | Asenkron devam eden görevin güncel statüsü veya sonucunu döner. | `{"status": "COMPLETED", "result": "..."}` |

---

## 6. Hata Yönetimi ve Kalite Standartları (Quality Assurance)
* **Cache Miss & Fallback Mekanizması:** Sistem okuma operasyonları için Redis'e güvenir. Ancak Redis üzerinde bir veri kaybı yaşanırsa, sistem otomatik olarak PostgreSQL üzerinden (Fallback) sorgulama yaparak veri tutarlılığını garanti altına alır.
* **Global Exception Handling:** Projedeki tüm servislerde hata yönetimi standartlaştırılmıştır. Dağınık `try-catch` blokları yerine, Spring `@RestControllerAdvice` kullanılarak hatalar (401, 404, 500 vb.) istemciye standart bir JSON sözleşmesiyle dönülür.
* **Konteynerizasyon:** Altyapının tüm bağımlılıkları Dockerize edilmiştir. `docker-compose up` komutuyla platformdan bağımsız olarak tek tuşla ayağa kalkabilir. Tüm loglamalar performanslı izleme için Lombok (`@Slf4j`) ile asenkron olarak yazılmaktadır.# CodeMentor - Sistem Mimarisi ve Teknik Dokümantasyon

## 1. Proje Özeti (Executive Summary)
**CodeMentor**, yazılım geliştiricilerin kod parçacıklarını asenkron ve yüksek erişilebilir (highly available) bir altyapı üzerinden yapay zekaya (LLM) analiz ettirebildiği, mikroservis (microservices) mimarisiyle tasarlanmış bir platformdur.

Sistem; kullanıcıyı bekletmeyen (non-blocking) yapısı, mesaj kuyruğu (Message Broker) destekli asenkron işleme modeli ve çok katmanlı önbellekleme (Caching) mekanizmalarıyla ölçeklenebilir (scalable) bir kurumsal ürün standartlarında geliştirilmiştir.

## 2. Teknoloji Yığını (Technology Stack)
Projemizde modern yazılım mühendisliği standartlarına uygun olarak aşağıdaki teknolojiler konumlandırılmıştır:

* **Backend Core:** Java, Spring Boot (Microservices)
* **API Gateway & Güvenlik:** Spring Cloud Gateway, JWT (JSON Web Token) tabanlı yetkilendirme
* **Asenkron Mesajlaşma:** RabbitMQ (Event-Driven Architecture)
* **Önbellek & Yüksek Performans:** Redis
* **İlişkisel Veritabanı:** PostgreSQL
* **Konteynerizasyon & DevOps:** Docker, Docker Compose

---

## 3. Sistem Mimarisi (System Architecture)
Sistem birbirinden tamamen izole, kendi veritabanı veya önbellek erişimleri olan üç temel mikroservisten oluşmaktadır:

### 3.1. API Gateway Service (Giriş Katmanı & Güvenlik)
* Sistemin tek giriş noktasıdır (Single Point of Entry).
* **Kimlik Doğrulama (Auth):** Gelen tüm istekler `JwtGlobalFilter` üzerinden geçirilir. JWT imzası ve süresi doğrulanır; geçersiz durumlarda anında **401 Unauthorized** döndürülerek arka servislere yük binmesi engellenir.
* **Header Aktarımı:** Başarılı doğrulama sonrası, Token içerisindeki kullanıcı bilgisi `X-User-Id` başlığına eklenerek güvenli servislere iletilir.

### 3.2. Code Service (İş Mantığı Katmanı)
* Sistemin orkestrasyon merkezidir. İstemciden gelen analiz taleplerini karşılar.
* Kayıtları kalıcılık için PostgreSQL'e kaydederken, analizin asenkron başlatılması için RabbitMQ'ya bir `CodeTaskMessage` fırlatır.
* Durum sorgulama (Polling) yükünü kaldırmak için PostgreSQL yerine doğrudan Redis ile haberleşir.

### 3.3. AI Service (İşçi Katmanı)
* Arka planda izole çalışan işçi (worker) servisidir.
* RabbitMQ kuyruğunu dinler, gelen mesajları alır ve LLM entegrasyonu ile analiz sürecini işletir.
* Görev durumlarını "PROCESSING" ve tamamlandığında "COMPLETED" olarak hem dağıtık önbelleğe (Redis) hem de ana veritabanına (PostgreSQL) kaydeder.

---

## 4. Kullanıcı Akışı ve Sistem Davranışı (User Activity & Flow)
Sistem, AI analiz işlemlerinde kullanıcı tarafında zaman aşımını (timeout) ve arayüz donmalarını engellemek için **Olay Yönelimli Asenkron Akış (Event-Driven Asynchronous Flow)** kullanır.

**Aşama 1: İstek Gönderimi (Submission & Acknowledgment)**
1. Kullanıcı, Bearer Token (JWT) ile kodunu ve prompt bilgisini Gateway üzerinden `/api/v1/analyze` uç noktasına gönderir.
2. Gateway doğrulamayı yapar ve isteği Code Service'e iletir.
3. Code Service bu görevi PostgreSQL'e `PENDING` statüsü ile yazar ve kullanıcıya anında bir `taskId` döner.
4. Eşzamanlı olarak arka planda RabbitMQ'ya işlemin başlatılması için bir mesaj fırlatılır.

**Aşama 2: Asenkron İşleme (Background Processing)**
1. AI Service, RabbitMQ'dan mesajı tüketir.
2. Görevin durumunu Redis üzerinde `PROCESSING` olarak günceller.
3. Yapay Zeka (LLM) analizini tamamladıktan sonra sonucu ve `COMPLETED` statüsünü hem Redis'e hem de kalıcılık için PostgreSQL'e kaydeder.

**Aşama 3: Durum Sorgulama ve Sonuç Alma (Polling Mechanism)**
1. Kullanıcı, elindeki `taskId` ile belirli aralıklarla `/api/v1/status/{taskId}` uç noktasına istek atar.
2. Code Service, veritabanı maliyetini düşürmek için sorguyu milisaniyeler içinde **Redis Cache** üzerinden yanıtlar.
3. Sonuç `COMPLETED` ise, analiz sonucu temiz bir JSON yapısında kullanıcıya iletilir.

---

## 5. API Spesifikasyonları (API Endpoints)

| HTTP Metodu | Uç Nokta (Endpoint) | Yetki (Auth) | Açıklama | Yanıt Modeli (Response) |
| :--- | :--- | :---: | :--- | :--- |
| **POST** | `/api/v1/analyze` | Evet (JWT) | Kaynak kod ve prompt analiz kuyruğuna eklenir. | `{"taskId": "uuid"}` |
| **GET** | `/api/v1/status/{taskId}`| Evet (JWT) | Asenkron devam eden görevin güncel statüsü veya sonucunu döner. | `{"status": "COMPLETED", "result": "..."}` |

---

## 6. Hata Yönetimi ve Kalite Standartları (Quality Assurance)
* **Cache Miss & Fallback Mekanizması:** Sistem okuma operasyonları için Redis'e güvenir. Ancak Redis üzerinde bir veri kaybı yaşanırsa, sistem otomatik olarak PostgreSQL üzerinden (Fallback) sorgulama yaparak veri tutarlılığını garanti altına alır.
* **Global Exception Handling:** Projedeki tüm servislerde hata yönetimi standartlaştırılmıştır. Dağınık `try-catch` blokları yerine, Spring `@RestControllerAdvice` kullanılarak hatalar (401, 404, 500 vb.) istemciye standart bir JSON sözleşmesiyle dönülür.
* **Konteynerizasyon:** Altyapının tüm bağımlılıkları Dockerize edilmiştir. `docker-compose up` komutuyla platformdan bağımsız olarak tek tuşla ayağa kalkabilir. Tüm loglamalar performanslı izleme için Lombok (`@Slf4j`) ile asenkron olarak yazılmaktadır.
---

## 7. Yerel Çalıştırma ve Debug

### 7.1. Tek seferlik kurulum: `.env`

Servisler `JWT_SECRET` olmadan **bilerek açılmaz** (`JwtUtil.init` / `JwtTokenProvider.init`
fail-fast eder). Varsayılan bir secret'a düşmek güvenlik açığı olurdu. Secret repoda
tutulmaz, `.env` gitignore'dadır.

```bash
cp .env.example .env
```

Sonra `.env` içindeki `JWT_SECRET` satırını doldur:

```bash
# Linux / macOS
openssl rand -base64 48
```
```powershell
# Windows (PowerShell)
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

> **Önemli:** `api-gateway` ile `code-service` **aynı** secret'ı kullanmak zorundadır.
> Farklı olursa uygulamalar sorunsuz açılır ama gateway imzayı doğrulayamadığı için
> her istek `401` döner. Aynı `.env` dosyası her iki servise de gittiği için bu
> kurulumda kendiliğinden sağlanır.

Bu **tek** dosya hem Docker Compose hem de IDE tarafından okunur:

| Nereden çalışıyor | `.env` nasıl okunuyor |
|---|---|
| Docker Compose | Compose kök dizindeki `.env`'i okuyup değerleri container'a environment variable olarak geçirir |
| IDE (run/debug) | Servislerin `application.yml`'indeki `spring.config.import: optional:file:../.env[.properties]` |

IDE run configuration'ına elle environment variable girmeye gerek yoktur.
Container içinde `.env` dosyası bulunmaz; oradaki değerler environment variable
olarak gelir ve environment variable'lar config import'unu ezer.

> `.env` Spring tarafından `.properties` olarak parse edilir: satır **sonuna**
> yorum yazma (`ACCESS_TOKEN_EXPIRATION=900000  # 15 dk`), yorum değerin parçası olur.
> Yorumlar kendi satırında, `#` ile başlamalıdır.

### 7.2. Senaryo A — Her şey Docker'da

```bash
docker compose up -d --build
```

| Adres | Ne |
|---|---|
| http://localhost:8080 | api-gateway (tek public giriş) |
| http://localhost:8080/swagger-ui/index.html | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/actuator/health | Gateway health |
| http://localhost:15672 | RabbitMQ yönetim arayüzü (guest/guest) |

`code-service` (8084) bilerek dışa kapalıdır: gateway'in bastığı `X-User-Id`
header'ına güvenir, doğrudan erişilebilir olsaydı bu header spoof edilebilirdi.

### 7.3. Senaryo B — Altyapı Docker'da, servisler IDE'de (önerilen debug yolu)

Sadece altyapıyı kaldır, servisleri IDE'den normal şekilde debug modda başlat:

```bash
docker compose up -d postgres redis rabbitmq
```

`application.yml` varsayılanları `localhost`'u gösterdiği için ek ayar gerekmez;
`.env` de otomatik okunur. Aynı servis hem Docker'da hem IDE'de çalışırsa port
çakışır — IDE'den çalıştıracağın servisi Docker'da durdur:

```bash
docker compose stop api-gateway code-service
```

### 7.4. Senaryo C — Container içindeki servise remote debug

Servisleri Docker'da bırakıp IDE'den JDWP ile bağlanmak için debug override'ı kullan:

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d --build
```

IntelliJ: **Run > Edit Configurations > + > Remote JVM Debug**, Host `localhost`,
Port aşağıdaki tablodan, mod "Attach to remote JVM".

| Servis | Debug portu |
|---|---|
| code-service | 5005 |
| ai-service | 5006 |
| api-gateway | 5007 |

`suspend=n` olduğu için uygulamalar debugger'ı beklemeden açılır; breakpoint'in
uygulama açılışında dursun istiyorsan `docker-compose.debug.yml` içinde `suspend=y` yap.

> Debug override'ı yalnızca yerel geliştirme içindir. Açık bir JDWP portu, JVM'de
> kod çalıştırılmasına izin verir; production'da asla kullanılmaz.

---

## 8. Kod Analiz Motoru (AI Tarafı)

`ai-service` analizi `CodeAnalysisEngine` arayüzü üzerinden yapar. İki implementasyon
vardır ve seçim `ai.engine` (env: `AI_ENGINE`) ile yapılır:

| `AI_ENGINE` | Motor | Davranış |
|---|---|---|
| `mock` (veya boş) | `MockCodeAnalysisEngine` | HTTP çağrısı yok, sabit metin döner |
| `openai` | `OpenAiCompatibleCodeAnalysisEngine` | `{base-url}/v1/chat/completions` çağrılır |

vLLM ve OpenAI aynı API şemasını konuşur, bu yüzden tek istemci hepsine yeter.
Hangi sağlayıcıya gidileceği tamamen config'tir:

| Hedef | `AI_OPENAI_BASE_URL` | `AI_OPENAI_MODEL` | `OPENAI_API_KEY` |
|---|---|---|---|
| Yerel mock (varsayılan) | `http://mock-vllm:8000` | `mock-code-analyzer` | boş |
| Kendi vLLM sunucun | `http://<host>:8000` | sunucudaki model id | boş veya key |
| Gerçek ChatGPT | `https://api.openai.com/v1` | `gpt-4o-mini` | `sk-...` |

`OPENAI_API_KEY` boş bırakılırsa `Authorization` header'ı hiç gönderilmez. Key yalnızca
`.env`'den okunur (gitignored) ve hiçbir log satırına yazılmaz.

Analiz çağrısı başarısız olursa (upstream hatası, timeout, boş cevap)
`AnalysisEngineException` fırlatılır ve mevcut `CodeTaskProcessingService` task'ı
`FAILED` olarak kaydeder — kuyruk zehirlenmez.

### mock-vllm servisi

`mock-vllm/` altında, FastAPI ile yazılmış küçük bir servistir. Gerçek model
çalıştırmaz; her isteğe aynı dummy metni döner. Amacı AI tarafını gerçek bir
sağlayıcıya ihtiyaç duymadan test edilebilir kılmaktır.

| Adres | Ne |
|---|---|
| http://localhost:8000/docs | Mock servisin kendi Swagger sayfası |
| http://localhost:8000/v1/chat/completions | OpenAI uyumlu chat endpoint'i |
| http://localhost:8000/v1/models | Sahte model listesi |
| http://localhost:8000/health | Healthcheck |

Streaming (`stream: true`) desteklenmez.

Testleri çalıştırmak için:

```bash
cd mock-vllm
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/pytest -q
```

---

## 9. Web Arayüzü (web-ui)

`web-ui/` altında, nginx ile servis edilen statik bir arayüz. Tarayıcı doğrudan
gateway'e (`http://localhost:8080`) konuşur; gateway'in `CORS_ALLOWED_ORIGINS`
değeri `http://localhost:3000` olduğu için ek yapılandırma gerekmez.

**Adres:** http://localhost:3000

| Dosya | Sorumluluk |
|---|---|
| `public/index.html` | Login/kayıt + kod gönderme ekranı |
| `public/review.html` | Yeni sekmede sonuç görünümü |
| `public/api.js` | Gateway HTTP istemcisi (DOM bilmez) |
| `public/app.js` | Ana ekran akışı: auth, analiz, polling |
| `public/store.js` | Review sonuçlarının localStorage deposu |
| `public/review.js` | Sonuç sekmesinin render'ı |
| `nginx.conf`, `Dockerfile` | Servis yapılandırması (imaja `public/` girer) |

### Akış

Giriş → Java kodu yapıştır → "Review et" → `POST /api/v1/analyze` →
`GET /api/v1/status/{taskId}` 2 saniyede bir yoklanır (gateway limiti 2 token/sn) →
`COMPLETED` olunca sonuç `localStorage`'a yazılır ve `review.html?taskId=...`
yeni sekmede açılır: solda gönderilen kod, sağda review.

### Oturum

Access token **yalnızca bellekte** tutulur, `localStorage`'a yazılmaz. Sayfa
yenilendiğinde açılışta sessizce `POST /api/v1/auth/refresh` denenir; refresh token
httpOnly cookie'de olduğu için oturum korunur. Ekranda görünen kullanıcı adı
access token'ın payload'ından okunur.

### Bilinen sınırlar

- Yalnızca Java hedeflenir. Backend'de `language` alanı olmadığı için dil bilgisi
  `prompt` üzerinden gider (`"Dil: Java. ..."`).
- Yeni sekme async bir işlemden sonra açıldığı için popup blocker'a takılabilir;
  bu durumda ana ekrandaki "Review'i yeni sekmede aç" butonu kullanılır.
- Syntax highlighting cdnjs'ten yüklenir; internet yoksa sayfa düz monospace olarak
  çalışmaya devam eder.
- Otomatik e2e (tarayıcı) testi yoktur; doğrulama curl + manuel tıklama ile yapılır.
