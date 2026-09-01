# OpenAI-uyumlu analiz motoru + mock vLLM servisi

Tarih: 2026-09-01
Durum: onaylandi, implementasyon bekliyor

## Problem

`ai-service` bugun hicbir LLM cagrisi yapmiyor. `MockCodeAnalysisEngine` sabit bir
metin donduruyor, yani "AI tarafi" uctan uca test edilemiyor. Ayrica gercek bir
saglayiciya (kendi vLLM'imiz ya da OpenAI) baglanmanin yolu yok.

## Cozum ozeti

Iki parca:

1. **`mock-vllm`** — Docker icinde calisan, vLLM/OpenAI'nin `/v1/chat/completions`
   semasini taklit eden kucuk bir Python servisi. Sabit dummy cevap doner.
2. **`OpenAiCompatibleCodeAnalysisEngine`** — `ai-service` icinde, bu semayi konusan
   tek bir HTTP istemcisi.

Kritik gozlem: vLLM ve OpenAI **ayni** API semasini konusur. Fark yalnizca base URL,
model adi ve API key'dir. Bu yuzden saglayici basina ayri istemci yazilmaz; tek
istemci config ile yonlendirilir.

## Mimari

```
POST /api/v1/analyze
  -> code-service (Postgres'e PENDING yazar, RabbitMQ'ya mesaj atar)
  -> ai-service / CodeTaskConsumer -> CodeTaskProcessingService
       -> CodeAnalysisEngine.analyze(sourceCode, prompt)
            = OpenAiCompatibleCodeAnalysisEngine
              --HTTP POST {base-url}/v1/chat/completions-->  mock-vllm | vLLM | OpenAI
       -> sonuc Postgres (COMPLETED) + Redis read-model
GET /api/v1/status/{taskId} -> sonuc
```

## Bilesen 1: mock-vllm

Yeni ust duzey klasor: `mock-vllm/`

- **Teknoloji:** Python 3.12, FastAPI + uvicorn. FastAPI secildi cunku OpenAI semasini
  pydantic modelleriyle birebir tanimlar ve mock'u elle kurcalarken kullanilacak
  Swagger sayfasini (`/docs`) ek is yapmadan verir.
- **Dosyalar:** `app.py` (tek dosya, ~60 satir), `requirements.txt` (sabitlenmis
  surumler), `Dockerfile` (`python:3.12-slim`, non-root kullanici), `test_app.py`.

| Endpoint | Davranis |
|---|---|
| `POST /v1/chat/completions` | OpenAI cevap semasi (`id`, `object`, `created`, `model`, `choices[].message.{role,content}`, `finish_reason`, `usage`) ile **sabit dummy** metin doner |
| `GET /v1/models` | Tek sahte model listeler: `mock-code-analyzer` |
| `GET /health` | Compose healthcheck'i icin `{"status":"ok"}` |

Istek govdesi OpenAI semasina gore parse edilir (`model`, `messages[]`, opsiyonel
`temperature`/`max_tokens`), ama cevap icerigi isteğe bagli degildir: her cagriya ayni
dummy metin doner. Streaming (`stream: true`) desteklenmez.

## Bilesen 2: ai-service istemcisi

- `OpenAiCompatibleCodeAnalysisEngine implements CodeAnalysisEngine`
- Spring `RestClient` ile `{base-url}/v1/chat/completions` cagrilir.
- Istek: `model` config'ten; `messages` = system (analiz talimati) + user (prompt +
  kaynak kod). Cevaptan `choices[0].message.content` alinir.
- `api-key` **bos degilse** `Authorization: Bearer ...` header'i eklenir; bossa header
  hic gonderilmez (mock ve auth'suz local vLLM icin gerekli).
- Hata (HTTP hatasi, timeout, bos `choices`) -> exception. `CodeTaskProcessingService`
  bunu zaten yakalayip task'i `FAILED` olarak kaydediyor; o mantik degismez.

### Config

```properties
ai.engine=openai
ai.openai.base-url=http://mock-vllm:8000
ai.openai.model=mock-code-analyzer
ai.openai.api-key=${OPENAI_API_KEY:}
ai.openai.connect-timeout=5s
ai.openai.read-timeout=60s
```

- `ai.engine=openai` -> HTTP istemcisi; `ai.engine=mock` -> mevcut in-process
  `MockCodeAnalysisEngine`.
- Secim `@ConditionalOnProperty` ile yapilir. `MockCodeAnalysisEngine` tarafinda
  `matchIfMissing=true`: property hic verilmezse bugunku davranis korunur.
- Ayarlar `@ConfigurationProperties(prefix="ai.openai")` ile tek bir record'a baglanir.
- Gercek ChatGPT'ye gecis = `base-url`, `model` ve `OPENAI_API_KEY` degistirmek. Kod
  degismez.

### Guvenlik

- `OPENAI_API_KEY` yalnizca `.env`'den gelir (gitignored). `.env.example`'a bos
  placeholder ve aciklama eklenir.
- API key hicbir log satirina, exception mesajina veya `toString()` ciktisina
  yazilmaz.

## Bilesen 3: Docker Compose

- `mock-vllm` servisi `codementor-net`'e eklenir, `8000:8000` host'a acilir (elle curl
  ve `/docs` icin), `/health` uzerinden healthcheck tanimlanir.
- `ai-service`, `mock-vllm`'e `depends_on: condition: service_healthy` ile baglanir ve
  varsayilan olarak `AI_ENGINE=openai` + `AI_OPENAI_BASE_URL=http://mock-vllm:8000`
  degerleriyle calisir.

## Test

- **mock-vllm (pytest):** `/v1/chat/completions` cevabi OpenAI semasina uyuyor mu;
  `/v1/models` model listesi; `/health`.
- **ai-service (JUnit):** `OpenAiCompatibleCodeAnalysisEngine`, `RestClient.Builder`'a
  bagli `MockRestServiceServer` ile test edilir (gercek soket acilmaz): content dogru
  cikariliyor mu, HTTP 500 exception'a ceviriliyor mu, `choices` bos gelirse ne oluyor,
  api-key bosken `Authorization` header'i yok / doluyken var.
- **Uctan uca:** `analyze` -> `status` = `COMPLETED` ve sonuc mock'un dummy metni.

## Kapsam disi

- Streaming (`stream: true`)
- Retry / circuit breaker
- Token maliyeti takibi, gercekci token sayimi
- Gercek vLLM deployment'i (mock ayni semayi konustugu icin base-url degistirmek yeter)
- Mock'ta hata/gecikme simulasyonu (istege gore basit tutuldu)
