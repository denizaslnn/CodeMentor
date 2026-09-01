# CodeMentor — IntelliJ IDEA ile Yerel Çalıştırma ve Debug Rehberi

Bu doküman projeyi **sıfırdan** bir makinede IntelliJ IDEA ile ayağa kaldırıp
debug edebilmek için gereken her adımı sırayla anlatır: indirilecek araçlar,
kurulum, IDE ayarları, `.env` hazırlığı, servisleri başlatma, breakpoint ile
debug ve doğrulama.

Proje 3 Spring Boot servisi + 1 statik web arayüzü + altyapıdan oluşur:

| Bileşen | Port | Ne yapar |
|---|---|---|
| `api-gateway` | 8080 | Tek public giriş. JWT doğrulama, rate limit, CORS, routing |
| `code-service` | 8084 | Auth (register/login/refresh), analiz isteği alma, RabbitMQ'ya publish |
| `ai-service` | 8083 | Kuyruğu tüketir, LLM'e sorar, sonucu DB + Redis'e yazar |
| `web-ui` | 3000 | Statik HTML/JS arayüz (nginx container'ı) |
| `mock-vllm` | 8000 | OpenAI uyumlu sahte LLM (FastAPI) |
| PostgreSQL | 5432 | `code_analysis_db` |
| Redis | 6379 | Task status/result read-model, rate limit |
| RabbitMQ | 5672 / 15672 | Analiz kuyruğu / yönetim arayüzü |

---

## 1. Gerekli Araçların İndirilmesi

### 1.1. JDK 21

Servislerin hedefleri farklıdır (`code-service` → Java 17, `api-gateway` ve
`ai-service` → Java 21), ama **tek bir JDK 21 hepsini derler**. JDK 17 kurarsan
`api-gateway` ve `ai-service` derlenmez, o yüzden 21 kur.

- İndir: <https://adoptium.net/temurin/releases/?version=21> (Temurin 21 LTS)
- macOS'te alternatif olarak:
  ```bash
  brew install --cask temurin@21
  ```
- Windows: `.msi` kurulumunda "Set JAVA_HOME" seçeneğini işaretle.

Doğrulama:
```bash
java -version     # openjdk version "21.x.x" görmelisin
```

> IntelliJ, JDK'yı kendisi de indirebilir (Adım 3.1). Sistem geneline kurmak
> istemiyorsan bu adımı atlayıp IDE'ye bırakabilirsin.

### 1.2. IntelliJ IDEA

- İndir: <https://www.jetbrains.com/idea/download/>
- **Ultimate** önerilir (Spring Boot run configuration'ları, HTTP Client,
  Database tool window, Docker entegrasyonu hazır gelir).
- **Community Edition** de yeterlidir: bu durumda Spring Boot uygulamalarını
  `Application` (main class) run configuration'ı olarak çalıştırırsın; bu
  rehberdeki her şey yine çalışır.

### 1.3. Docker Desktop

Altyapı (PostgreSQL, Redis, RabbitMQ, mock-vllm) container'da çalışır — bunları
elle kurmana gerek yok.

- İndir: <https://www.docker.com/products/docker-desktop/>
- Kurulumdan sonra Docker Desktop'ı **başlat** ve doğrula:
  ```bash
  docker --version
  docker compose version
  ```

### 1.4. Git

- İndir: <https://git-scm.com/downloads> (macOS'te `xcode-select --install` da yeterli)

### 1.5. Maven (opsiyonel)

Her serviste `mvnw` (Maven Wrapper) vardır, ayrıca Maven kurman gerekmez.
IntelliJ de kendi bundled Maven'ını kullanır.

---

## 2. Projeyi Klonlama

```bash
git clone https://github.com/denizaslnn/CodeMentor.git
cd CodeMentor
```

---

## 3. IntelliJ IDEA Ayarları

### 3.1. Projeyi açma

**File > Open** → klonladığın `CodeMentor` klasörünü seç → **Open as Project**.

> Alt klasörlerden birini (`code-service` gibi) değil, **kök dizini** aç.
> `.env` dosyası kökte durur ve servisler onu `../.env` yolundan okur.

### 3.2. Maven modüllerini tanıtma

Proje bir aggregator (parent) `pom.xml` içermez; üç servis bağımsız Maven
projeleridir. IntelliJ ilk açılışta genelde hepsini bulur. Bulamazsa:

**Maven** tool window (sağ kenar) → **+** (Add Maven Projects) → sırayla ekle:

- `api-gateway/pom.xml`
- `code-service/pom.xml`
- `ai-service/pom.xml`

Sonra Maven panelinde **Reload All Maven Projects** (🔄) — bağımlılıkların
inmesi ilk seferde birkaç dakika sürer.

### 3.3. Project SDK (JDK 21)

**File > Project Structure > Project**

- **SDK**: 21 (yoksa `Add SDK > Download JDK…` → Version 21, Vendor Eclipse Temurin)
- **Language level**: 21

**Project Structure > Modules** altında `code-service` modülünün language
level'ı 17 kalabilir (pom'unda öyle tanımlı), sorun değil.

### 3.4. Lombok

Üç servis de Lombok kullanır.

1. **Settings > Plugins > Marketplace** → "Lombok" ara → kuruluysa dokunma,
   değilse **Install** → IDE'yi yeniden başlat.
2. **Settings > Build, Execution, Deployment > Compiler > Annotation Processors**
   → **Enable annotation processing** işaretli olmalı.

Bu iki adım eksikse `getX()/builder()` çağrıları "cannot resolve method" diye
kırmızı görünür.

### 3.5. Encoding (UTF-8)

Kaynak dosyalar ve `messages*.properties` Türkçe karakter içerir.

**Settings > Editor > File Encodings**:
- Global Encoding: `UTF-8`
- Project Encoding: `UTF-8`
- Default encoding for properties files: `UTF-8`, **Transparent native-to-ascii
  conversion** işaretli.

### 3.6. Projeyi derle

**Build > Build Project** (⌘F9 / Ctrl+F9). Hatasız bitmeli.

---

## 4. `.env` Dosyasını Hazırlama

`.env` git'e **commit edilmez** (`.gitignore`'da), o yüzden şablondan üretilir.
Bu dosya iki yerden okunur:

- **Docker Compose** → kökteki `.env`'i otomatik okur,
- **Servisler** → `application.yml` içindeki
  `spring.config.import: optional:file:../.env[.properties]` sayesinde
  **IDE'den çalıştırdığında da** okur.

Yani IDE run configuration'ına elle secret girmene gerek yoktur.

```bash
cp .env.example .env
```

Sonra `.env` içindeki `JWT_SECRET`'ı gerçek bir değerle değiştir:

```bash
# macOS / Linux
openssl rand -base64 48
```
```powershell
# Windows PowerShell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

Çıkan değeri `JWT_SECRET=` satırına yapıştır.

> **Kritik:** `api-gateway` ve `code-service` **aynı** `JWT_SECRET`'ı kullanmak
> zorundadır. Tek bir `.env` olduğu için bu kendiliğinden sağlanır; ama IDE run
> config'inde birine elle farklı bir secret girersen her istek 401 döner.

> **Uyarı:** Spring bu dosyayı `.properties` gibi parse eder. Satır **sonuna**
> yorum yazma (`ACCESS_TOKEN_EXPIRATION=900000  # 15 dk` → yorum değerin parçası
> olur). Yorumlar kendi satırında `#` ile başlamalı.

---

## 5. Altyapıyı Docker'da Ayağa Kaldırma

Debug için önerilen kurulum: **altyapı Docker'da, Java servisleri IDE'de.**

Terminalden (veya IntelliJ'in Terminal sekmesinden), proje kökünde:

```bash
docker compose up -d postgres redis rabbitmq mock-vllm
```

Durumu kontrol et — dördü de `healthy` olmalı:

```bash
docker compose ps
```

| Servis | Kontrol |
|---|---|
| PostgreSQL | `docker exec -it postgres_db psql -U postgres -d code_analysis_db -c '\dt'` |
| Redis | `docker exec -it redis redis-cli ping` → `PONG` |
| RabbitMQ | <http://localhost:15672> (guest / guest) |
| mock-vllm | <http://localhost:8000/docs> |

> Veritabanı şemasını **Flyway** oluşturur ve bunu `code-service` açılışta
> yapar (`db/migration/V1__initial_schema.sql`, `V2__...`). Yani PostgreSQL boş
> başlar, `code-service`'i ilk çalıştırdığında tablolar oluşur. `ai-service`
> kendi migration'ına sahip değildir, `code-service`'in şemasını doğrular —
> bu yüzden **ilk açılışta önce `code-service`'i başlat**.

---

## 6. IntelliJ Run/Debug Configuration'ları

Üç servis için birer configuration oluştur. **Run > Edit Configurations… > +**

### 6.1. code-service (önce bu)

| Alan | Değer |
|---|---|
| Tip | **Spring Boot** (Ultimate) veya **Application** (Community) |
| Name | `code-service` |
| Module / Use classpath of module | `code-service` |
| Main class | `com.codementor.codeservice.CodeServiceApplication` |
| JRE | 21 |
| Working directory | `$PROJECT_DIR$/code-service` |

Ek environment variable **gerekmez** — `application.yml` varsayılanları zaten
`localhost:5432 / 6379 / 5672` gösterir ve `.env` otomatik okunur.

### 6.2. ai-service

| Alan | Değer |
|---|---|
| Name | `ai-service` |
| Module | `ai-service` |
| Main class | `com.codementor.aiservice.AiServiceApplication` |
| JRE | 21 |
| Working directory | `$PROJECT_DIR$/ai-service` |
| **Environment variables** | `AI_OPENAI_BASE_URL=http://localhost:8000` |

> **Bu tek override şart.** `.env` içinde `AI_OPENAI_BASE_URL=http://mock-vllm:8000`
> yazar; bu isim yalnızca Docker ağı içinde çözülür. IDE'den çalıştırdığında
> host'tan erişilen adres `http://localhost:8000`'dir. Environment variable,
> `.env` config import'unu ezdiği için run config'e yazmak yeterlidir —
> `.env`'i değiştirme, yoksa Docker senaryosunu bozarsın.
>
> HTTP'ye hiç çıkmadan çalışmak istersen bunun yerine `AI_ENGINE=mock` ver;
> o zaman mock-vllm container'ına da gerek kalmaz.

### 6.3. api-gateway

| Alan | Değer |
|---|---|
| Name | `api-gateway` |
| Module | `api-gateway` |
| Main class | `com.codementor.apigateway.ApiGatewayApplication` |
| JRE | 21 |
| Working directory | `$PROJECT_DIR$/api-gateway` |

Ek environment variable gerekmez; `CODE_SERVICE_URI` verilmediğinde varsayılan
`http://localhost:8084`'tür.

### 6.4. (Opsiyonel) Compound configuration

**+ > Compound** → adı `all-services`, içine `code-service`, `ai-service`,
`api-gateway` ekle. Tek tıkla üçünü birden Debug'da başlatırsın.

---

## 7. Servisleri Başlatma (Debug Modda)

Sırayla **Debug** (🐞) ile başlat:

1. `code-service` → Flyway migration'ları koşar, log'da `Started CodeServiceApplication`
2. `ai-service` → RabbitMQ kuyruğunu (`code.analysis.queue`) declare eder ve dinlemeye başlar
3. `api-gateway` → 8080'i açar

Doğrulama:

```bash
curl http://localhost:8080/actuator/health     # {"status":"UP"}
```

> **Port çakışması:** Aynı servis hem Docker'da hem IDE'de çalışamaz.
> Daha önce `docker compose up -d` (hepsi) yaptıysan, IDE'den çalıştıracaklarını
> durdur:
> ```bash
> docker compose stop api-gateway code-service ai-service
> ```

---

## 8. Web Arayüzünü Açma

`web-ui` derleme gerektirmeyen statik dosyalardır ve nginx container'ından
servis edilir:

```bash
docker compose up -d web-ui
```

Aç: <http://localhost:3000>

> Arayüz `http://localhost:8080`'e konuşur ve gateway'in CORS listesi
> (`CORS_ALLOWED_ORIGINS=http://localhost:3000`) tam olarak bu origin'i içerir.
> Dosyaları IntelliJ'in built-in preview'ı ile (`localhost:63342`) açarsan
> tarayıcı CORS nedeniyle istekleri bloklar — **3000 portunu kullan**.
>
> `web-ui/public/` altındaki dosyaları düzenlersen container'ı yeniden build et:
> `docker compose up -d --build web-ui` (nginx `Cache-Control: no-store`
> gönderdiği için tarayıcı cache'i sorun çıkarmaz).

---

## 9. Uçtan Uca Doğrulama (Smoke Test)

### 9.1. Tarayıcıdan

<http://localhost:3000> → **Kayıt ol** → giriş yap → Java kodu yapıştır →
**Review et**. Arayüz `POST /api/v1/analyze` çağırır, 2 saniyede bir
`GET /api/v1/status/{taskId}` yoklar ve sonuç gelince yeni sekmede gösterir.

> Parola kuralı: en az 8 karakter, en az bir büyük harf, bir küçük harf,
> bir rakam ve bir özel karakter (`@#$%^&+=!`). Örn. `Passw0rd!`

### 9.2. Terminalden

```bash
# 1) Kayıt
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Passw0rd!"}'

# 2) Giriş → access token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Passw0rd!"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 3) Analiz isteği → taskId
curl -s -X POST http://localhost:8080/api/v1/analyze \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sourceCode":"public class A { public static void main(String[] a){ System.out.println(1/0); } }","prompt":"Bu kodu incele"}'

# 4) Durum sorgusu (taskId'yi yukarıdan al)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/status/<taskId>
```

Swagger UI: <http://localhost:8080/swagger-ui/index.html>
(code-service'in 8084 portu bilerek dışa kapalıdır; doküman gateway üzerinden gelir.)

---

## 10. Breakpoint ile Debug

Akışın tamamını takip etmek için tipik breakpoint noktaları:

| Ne izlemek istiyorsun | Nereye breakpoint |
|---|---|
| Login / JWT üretimi | `code-service` → `controller/AuthController#login` |
| Gateway'in JWT doğrulaması | `api-gateway` → JWT filtresi |
| Analiz isteğinin kuyruğa gitmesi | `code-service` → `controller/CodeAnalysisController#analyze`, ardından `publisher/` |
| Mesajın tüketilmesi | `ai-service` → `@RabbitListener` metodu |
| LLM çağrısı | `ai-service` → OpenAI istemcisi |
| Sonucun okunması | `code-service` → `CodeAnalysisController#status` |

Notlar:

- Asenkron akışta breakpoint uzun sürerse gateway rate limiter (2 token/sn)
  ve arayüzün polling'i devreye girer; UI "hâlâ işleniyor" gösterir, bu normaldir.
- Kuyruğa düşen mesajı gözle görmek için RabbitMQ arayüzü:
  <http://localhost:15672> → Queues → `code.analysis.queue`.
- Zehirli mesajlar `code.analysis.dlq` (dead-letter queue) kuyruğuna düşer;
  tüketici tarafında sürekli hata alıyorsan oraya bak.

### 10.1. Container içindeki servise remote debug (alternatif)

Servisleri Docker'da bırakıp IDE'den bağlanmak istersen debug override'ını kullan:

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d --build
```

**Run > Edit Configurations… > + > Remote JVM Debug**, mod *Attach to remote JVM*,
Host `localhost`, Port:

| Servis | Debug portu |
|---|---|
| code-service | 5005 |
| ai-service | 5006 |
| api-gateway | 5007 |

`suspend=n` olduğu için uygulamalar debugger'ı beklemeden açılır. Açılış kodunda
breakpoint'e düşmek istersen `docker-compose.debug.yml` içinde `suspend=y` yap.

> Bu override yalnızca yerel geliştirme içindir: açık bir JDWP portu JVM'de kod
> çalıştırılmasına izin verir, production'da asla kullanılmaz.

---

## 11. Veritabanına IntelliJ'den Bağlanma (opsiyonel, Ultimate)

**Database** tool window → **+ > Data Source > PostgreSQL**

| Alan | Değer |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `code_analysis_db` |
| User | `postgres` |
| Password | `postgres_password` |

İlk bağlantıda IntelliJ PostgreSQL driver'ını indirmeni ister — **Download**.

---

## 12. Kapatma ve Temizlik

```bash
# Sadece container'ları durdur (veri korunur)
docker compose stop

# Container'ları kaldır, veriyi koru
docker compose down

# Veritabanını da sil (temiz başlangıç — Flyway şemayı yeniden kurar)
docker compose down -v
```

IDE'deki servisleri Run/Debug penceresindeki ⏹ ile durdur.

---

## 13. Sorun Giderme

| Belirti | Sebep / Çözüm |
|---|---|
| Açılışta `JWT_SECRET ... required` / secret hatası | `.env` yok ya da `JWT_SECRET` şablon değerinde. Adım 4'ü uygula; secret base64 ve ≥ 32 byte olmalı. |
| Her istek **401** dönüyor | Gateway ile code-service farklı secret/issuer/audience kullanıyor. İkisi de aynı `.env`'i okumalı; run config'lerde elle girilmiş `JWT_*` varsa sil. |
| **403 / CORS** hatası tarayıcıda | Arayüzü 3000 dışında bir porttan açtın. `http://localhost:3000` kullan ya da `.env` içinde `CORS_ALLOWED_ORIGINS`'i güncelle (gateway'i yeniden başlat). |
| `Connection refused: localhost:5432` | Altyapı ayakta değil: `docker compose up -d postgres redis rabbitmq`. |
| `UnknownHostException: mock-vllm` (ai-service) | IDE'den çalışıyorsun ama `.env`'deki Docker DNS adı kullanılıyor. Run config'e `AI_OPENAI_BASE_URL=http://localhost:8000` ekle (Adım 6.2). |
| `Port 8080 already in use` | Aynı servis hem Docker'da hem IDE'de. `docker compose stop api-gateway code-service ai-service`. |
| Hibernate `Schema-validation ... missing table` | `code-service` hiç çalışmadı, Flyway şema kurmadı. Önce `code-service`'i başlat; ya da `docker compose down -v` ile sıfırdan başla. |
| Lombok metotları "cannot resolve" | Lombok plugin'i ve annotation processing kapalı (Adım 3.4). |
| Türkçe karakterler bozuk | Encoding UTF-8 değil (Adım 3.5). |
| Analiz sonsuza kadar `PENDING` | `ai-service` çalışmıyor ya da kuyruğu tüketmiyor. RabbitMQ arayüzünde `code.analysis.queue` birikiyor mu bak; `code.analysis.dlq`'yu kontrol et. |
| İstek **429** dönüyor | Gateway rate limiter (2 istek/sn, burst 5) veya auth brute-force limiti. Birkaç saniye bekle. |

---

## 14. Hızlı Özet (kurulum sonrası her gün)

```bash
docker compose up -d postgres redis rabbitmq mock-vllm web-ui
```
IntelliJ'de Debug ile sırayla: `code-service` → `ai-service` → `api-gateway`
Tarayıcı: <http://localhost:3000>
