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