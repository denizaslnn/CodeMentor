# CodeMentor Mimarisi ve "Merhaba Dünya" Akışının Adım Adım Yolculuğu

Bu doküman, CodeMentor projesindeki mikroservis mimarisini ve bileşenlerin neden bu şekilde tasarlandığını, doğrudan proje kodları ve konfigürasyonlarına dayanarak açıklar.

---

## 1) API Gateway & JWT

### Gateway neden var?
Gateway (`api-gateway`), sisteme tek giriş noktasıdır:

- Güvenlik (JWT doğrulama)
- İstek yönlendirme (routing)
- Rate limiting (aşırı isteği sınırlama)
- CORS ve ortak politikalar

Bu sayede istemci doğrudan iç servislere (`code-service`, `ai-service`) erişmez.

### Projedeki referanslar

- `docker-compose.yml`  
  Gateway dışarıya `8080` portunu açar, `code-service` ise içte `8084` üzerindedir.
- `api-gateway/src/main/resources/application.yml`  
  `/api/v1/**` isteklerini `code-service`e yönlendirir.
- `api-gateway/src/main/java/com/codementor/apigateway/filter/JwtGlobalFilter.java`  
  JWT kontrolü + `X-User-Id` header ekleme + body koruma.
- `api-gateway/src/main/java/com/codementor/apigateway/security/JwtUtil.java`  
  Token imza/süre doğrulaması.
- `api-gateway/src/main/java/com/codementor/apigateway/filter/UnauthorizedResponseWriter.java`  
  Geçersiz token durumunda standart `401 ErrorResponse` JSON üretir.

### JwtGlobalFilter güvenliği nasıl yönetiyor?

1. `/api/v1` altındaki çağrıları yakalar.
2. `Authorization: Bearer ...` header var mı kontrol eder.
3. `JwtUtil.validateAndParse(...)` ile token imza + expiration doğrular.
4. `sub/userId` claim’inden kullanıcıyı çıkarır.
5. Downstream servise `X-User-Id` header’ını ekler.
6. Request body’yi okuyup tekrar enjekte ederek gövde kaybını önler.
7. Hata varsa `401` ve JSON hata gövdesi döner.

---

## 2) HTTP ve JSON Standartları (Otobüs ve Paket Mantığı)

- **HTTP** = taşıma protokolü (otobüs): Method, path, status code, header.
- **JSON** = taşınan veri paketi.

### Neden POST ve GET ayrıldı?

- `POST /api/v1/analyze` yeni analiz işi başlatır (yan etki var).
- `GET /api/v1/status/{taskId}` mevcut işin durumunu okur (sorgu).

Bu ayrım, REST semantiğini korur ve polling akışını temiz tutar.

Referans:

- `code-service/src/main/java/controller/CodeAnalysisController.java`

---

## 3) Code Service & Task ID

### Code Service’in görevi
Code Service, gelen analiz isteğini sistemde izlenebilir bir “iş” haline getirir:

1. `taskId` üretir
2. DB’ye `PENDING` kayıt atar
3. Event publish eder
4. RabbitMQ’ya mesaj yollar
5. İstemciye anında `taskId` döner

Referans:

- `code-service/src/main/java/com/codementor/codeservice/service/CodeAnalysisService.java`
- `code-service/src/main/java/com/codementor/codeservice/publisher/RabbitMqTaskPublisher.java`
- `code-service/src/main/java/controller/CodeAnalysisController.java`

### taskId neden burada üretiliyor?

- **Gateway’de üretilseydi:** Domain mantığı gateway’e taşınırdı (katman sorumluluğu bozulur).
- **AI Service’de üretilseydi:** İstemci, asenkron tüketimi beklemeden kimlik alamazdı; “anında taskId dön” şartı bozulurdu.

Bu yüzden en doğru yer: **Code Service**.

---

## 4) Redis + PostgreSQL (İki Katmanlı Hafıza)

### PostgreSQL neden var?

- Kalıcı kayıt (source of truth)
- Geçmiş analizler/audit
- Servis restart sonrası veri kaybını engelleme

### Redis neden var?

- Polling isteklerine çok hızlı cevap
- `task:{id}` ve `task:{id}:result` gibi sıcak veriyi RAM’den sunma

Referans:

- `code-service/src/main/java/com/codementor/codeservice/service/RedisStatusService.java`
- `ai-service/src/main/java/com/codementor/aiservice/service/RedisStatusService.java`
- `code-service/src/main/java/com/codementor/codeservice/service/CodeAnalysisService.java` (Redis miss → DB fallback)

### Sadece Postgres olsaydı?

- Her polling DB’ye giderdi
- Yük altında DB connection/IO bottleneck olurdu
- Latency artardı

Mevcut model: Redis hızlı katman, Postgres güvenli kalıcı katman.

---

## 5) RabbitMQ & Asenkron Haberleşme (Event-Driven)

### Neden doğrudan servis-to-servis HTTP değil?

- AI analizi yavaş olabilir; kullanıcıyı bekletmek istemiyoruz.
- Producer (Code Service) ile Consumer (AI Service) gevşek bağlı kalmalı.
- Kuyruk, ani trafik patlamalarını tamponlar.

Referans:

- Producer: `code-service/.../RabbitMqTaskPublisher.java`
- Consumer: `ai-service/.../consumer/CodeTaskConsumer.java`
- Topology:  
  `code-service/.../config/RabbitMQConfig.java`  
  `ai-service/.../config/RabbitTopologyConfig.java`

### ACK veri kaybını nasıl azaltır?

RabbitMQ’da mesaj, tüketici başarılı işleyip onaylayana (ACK) kadar broker tarafında tutulur.  
Tüketici işlem sırasında düşerse mesaj kaybolmak yerine yeniden teslim edilebilir (at-least-once yaklaşımı).

Bu projede listener varsayılan Spring AMQP yönetimiyle çalışır (`@RabbitListener`); gerekirse ileride manual-ack + DLQ politikası eklenebilir.

---

# "Merhaba Dünya" Kodu Bu Sistemde Adım Adım Neler Yaşıyor?

Örnek istek:

```json
{
  "prompt": "Bu kodu analiz et",
  "sourceCode": "public class HelloWorld { public static void main(String[] args) { System.out.println(\"Merhaba Dünya\"); } }"
}
```

## A) İstek girişi (Gateway)

1. İstemci `POST /api/v1/analyze` çağrısını `api-gateway:8080`a gönderir.
2. Gateway JWT’yi doğrular (`JwtGlobalFilter` / `JwtUtil`).
3. Token geçerliyse `X-User-Id` header’ı eklenir.
4. Body korunur ve istek `code-service:8084`e forward edilir.

## B) Job oluşturma (Code Service)

5. `CodeAnalysisController.analyzeCode(...)` isteği alır.
6. `CodeAnalysisService.initiateAnalysis(...)` yeni `taskId` üretir.
7. `analysis_requests` tablosuna kayıt atılır:
   - `id = taskId`
   - `status = PENDING`
   - `sourceCode = HelloWorld kodu`
   - `prompt = ...`
8. Transaction commit olduktan sonra event tetiklenir (`@TransactionalEventListener(AFTER_COMMIT)`).
9. `RabbitMqTaskPublisher`:
   - Redis’e `task:{taskId}=PENDING` yazar.
   - RabbitMQ `code.analysis.queue` kuyruğuna mesaj gönderir.
10. Kullanıcıya anında `202 Accepted` + `taskId` döner.

## C) Asenkron analiz (AI Service)

11. `CodeTaskConsumer` RabbitMQ’dan mesajı alır.
12. Redis `task:{taskId}=PROCESSING` güncellenir.
13. DB’de aynı satır `PROCESSING` yapılır.
14. `AiAnalysisService.analyze(...)` kod analizi/simülasyonu çalıştırır.
15. İş bitince:
    - Redis `task:{taskId}=COMPLETED`
    - Redis `task:{taskId}:result=<analiz metni>`
    - DB `status=COMPLETED`, `ai_response=<analiz metni>`

## D) Sonuç sorgulama (Polling)

16. İstemci `GET /api/v1/status/{taskId}` çağrısını gateway’e atar.
17. Gateway yine JWT doğrular, isteği `code-service`e yollar.
18. `CodeAnalysisService.getTaskStatus(...)` önce Redis’e bakar:
    - varsa hızlıca döner
    - yoksa DB fallback yapar
19. JSON yanıt döner:

```json
{
  "taskId": "...",
  "status": "COMPLETED",
  "result": "Kod analizi tamamlandı..."
}
```

## E) Hata senaryoları

- JWT geçersizse: `401 ErrorResponse` (Gateway)
- taskId hiç yoksa: `404 ErrorResponse` (Code Service GlobalExceptionHandler)

---

## Kısa Mimari Özet

CodeMentor, istekleri anında kabul edip uzun süren analizi arka plana atan; hızlı cevap için Redis kullanan; doğruluk ve kalıcılık için PostgreSQL’e dayanan; servisleri RabbitMQ ile gevşek bağlı tutan bir mikroservis tasarımıdır.
