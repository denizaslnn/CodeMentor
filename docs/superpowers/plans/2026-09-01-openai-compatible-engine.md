# OpenAI-Uyumlu Analiz Motoru + Mock vLLM Servisi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ai-service`'in kod analizini gercek bir OpenAI-uyumlu HTTP endpoint'inden almasini saglamak ve bu endpoint'in yerel karsiligi olarak Docker'da calisan bir mock vLLM servisi eklemek.

**Architecture:** vLLM ve OpenAI ayni `/v1/chat/completions` semasini konusur; bu yuzden saglayici basina istemci yazilmaz. `ai-service` icinde tek bir `OpenAiCompatibleCodeAnalysisEngine` bulunur ve hangi endpoint'e gidecegi tamamen config'tir (`ai.openai.base-url`). Mevcut `CodeAnalysisEngine` arayuzu degismez; `MockCodeAnalysisEngine` in-process fallback olarak kalir ve iki bean `@ConditionalOnProperty(ai.engine)` ile birbirini disler.

**Tech Stack:** Java 21 / Spring Boot 4.1 (`RestClient`, `@ConfigurationProperties`), Python 3.12 / FastAPI + uvicorn, Docker Compose, JUnit 5 + `MockRestServiceServer`, pytest.

## Global Constraints

- `MockCodeAnalysisEngine`'in bugunku davranisi korunur: `ai.engine` property'si hic verilmezse in-process mock devrede kalir (`matchIfMissing = true`).
- `CodeTaskProcessingService` degistirilmez. Motor exception atarsa task'i `FAILED` yazan mevcut mantik kullanilir.
- `OPENAI_API_KEY` yalnizca `.env`'den gelir (`.env` gitignored). API key hicbir log satirina, exception mesajina veya `toString()` ciktisina yazilmaz.
- `api-key` bos ise `Authorization` header'i **hic gonderilmez** (mock ve auth'suz local vLLM icin sart).
- Mock servis streaming (`stream: true`) desteklemez.
- Python bagimliliklari tam surumle sabitlenir; runtime ve test bagimliliklari ayri dosyalarda tutulur.
- Yeni Python servisi non-root kullanici ile calisir.
- Turkce yorum/metinlerde dosyalar UTF-8 olmalidir (repoda daha once cift kodlama hatasi yasandi).

---

### Task 1: mock-vllm servisi

Docker'da calisan, OpenAI/vLLM chat-completions semasini taklit eden Python servisi. Bu task tek basina calisir ve test edilir; `ai-service` henuz ona baglanmaz.

**Files:**
- Create: `mock-vllm/app.py`
- Create: `mock-vllm/requirements.txt`
- Create: `mock-vllm/requirements-dev.txt`
- Create: `mock-vllm/Dockerfile`
- Create: `mock-vllm/.dockerignore`
- Test: `mock-vllm/test_app.py`

**Interfaces:**
- Consumes: yok (ilk task).
- Produces: HTTP kontrati — `POST /v1/chat/completions` (govde: `{"model": str, "messages": [{"role": str, "content": str}], "temperature": float?, "max_tokens": int?}`, cevap: `{"id": str, "object": "chat.completion", "created": int, "model": str, "choices": [{"index": int, "message": {"role": "assistant", "content": str}, "finish_reason": "stop"}], "usage": {...}}`), `GET /v1/models`, `GET /health`. Model id sabiti: `mock-code-analyzer`. Container portu: `8000`.

- [ ] **Step 1: Bagimlilik dosyalarini olustur**

`mock-vllm/requirements.txt`:

```
fastapi==0.115.6
uvicorn[standard]==0.34.0
pydantic==2.10.5
```

`mock-vllm/requirements-dev.txt`:

```
-r requirements.txt
pytest==8.3.4
httpx==0.28.1
```

`mock-vllm/.dockerignore`:

```
__pycache__/
*.pyc
.pytest_cache/
.venv/
test_app.py
requirements-dev.txt
```

Ayrica repo kokundeki `.gitignore` dosyasinin sonuna ekle (venv ve pytest artiklari
commit'lenmesin):

```
# Python (mock-vllm)
.venv/
__pycache__/
.pytest_cache/
```

- [ ] **Step 2: Basarisiz testi yaz**

`mock-vllm/test_app.py`:

```python
"""mock-vllm servisinin OpenAI kontratina uydugunu dogrulayan testler."""
from fastapi.testclient import TestClient

from app import MODEL_ID, app

client = TestClient(app)


def test_health_returns_ok():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_models_lists_the_mock_model():
    response = client.get("/v1/models")

    assert response.status_code == 200
    body = response.json()
    assert body["object"] == "list"
    assert [model["id"] for model in body["data"]] == [MODEL_ID]


def test_chat_completions_returns_openai_shaped_response():
    response = client.post(
        "/v1/chat/completions",
        json={
            "model": MODEL_ID,
            "messages": [
                {"role": "system", "content": "Sen bir kod analiz asistanisin."},
                {"role": "user", "content": "class A {}"},
            ],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["object"] == "chat.completion"
    assert body["model"] == MODEL_ID
    assert body["id"].startswith("chatcmpl-")
    assert isinstance(body["created"], int)

    choice = body["choices"][0]
    assert choice["index"] == 0
    assert choice["finish_reason"] == "stop"
    assert choice["message"]["role"] == "assistant"
    assert choice["message"]["content"].strip() != ""

    usage = body["usage"]
    assert usage["total_tokens"] == usage["prompt_tokens"] + usage["completion_tokens"]


def test_chat_completions_returns_the_same_dummy_content_every_time():
    payload = {
        "model": MODEL_ID,
        "messages": [{"role": "user", "content": "class A {}"}],
    }

    first = client.post("/v1/chat/completions", json=payload).json()
    second = client.post(
        "/v1/chat/completions",
        json={"model": MODEL_ID, "messages": [{"role": "user", "content": "bambaska bir kod"}]},
    ).json()

    assert first["choices"][0]["message"]["content"] == second["choices"][0]["message"]["content"]


def test_chat_completions_rejects_malformed_request():
    response = client.post("/v1/chat/completions", json={"model": MODEL_ID})

    assert response.status_code == 422
```

- [ ] **Step 3: Testi calistirip basarisiz oldugunu dogrula**

Run: `cd mock-vllm && python3 -m venv .venv && .venv/bin/pip install -q -r requirements-dev.txt && .venv/bin/pytest -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'app'`

- [ ] **Step 4: Servisi yaz**

`mock-vllm/app.py`:

```python
"""vLLM / OpenAI uyumlu chat-completions endpoint'ini taklit eden mock servis.

Gercek bir model calistirmaz: her istege ayni dummy metni doner. Amaci
ai-service'in AI tarafini (HTTP cagrisi, cevap ayristirma, hata yolu) gercek bir
saglayiciya ihtiyac duymadan test edilebilir kilmaktir. Cevap semasi OpenAI ile
birebir aynidir; ai-service ayni istemciyle gercek vLLM'e veya OpenAI'ye de
baglanabilir.
"""
import time
import uuid
from typing import List, Optional

from fastapi import FastAPI
from pydantic import BaseModel

MODEL_ID = "mock-code-analyzer"

DUMMY_CONTENT = (
    "Kod analizi tamamlandi (mock yanit). Bu metin mock-vllm servisinden gelmistir; "
    "gercek bir model calistirilmamistir. Kritik bir hata bulunmadi."
)


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatCompletionRequest(BaseModel):
    model: str
    messages: List[ChatMessage]
    temperature: Optional[float] = None
    max_tokens: Optional[int] = None
    stream: bool = False


class ResponseMessage(BaseModel):
    role: str = "assistant"
    content: str


class Choice(BaseModel):
    index: int = 0
    message: ResponseMessage
    finish_reason: str = "stop"


class Usage(BaseModel):
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int


class ChatCompletionResponse(BaseModel):
    id: str
    object: str = "chat.completion"
    created: int
    model: str
    choices: List[Choice]
    usage: Usage


app = FastAPI(
    title="Mock vLLM",
    version="1.0.0",
    description="CodeMentor icin OpenAI uyumlu sahte chat-completions servisi.",
)


@app.get("/health")
def health() -> dict:
    """Docker Compose healthcheck'i icin."""
    return {"status": "ok"}


@app.get("/v1/models")
def list_models() -> dict:
    return {
        "object": "list",
        "data": [
            {
                "id": MODEL_ID,
                "object": "model",
                "created": int(time.time()),
                "owned_by": "codementor-mock",
            }
        ],
    }


@app.post("/v1/chat/completions", response_model=ChatCompletionResponse)
def chat_completions(request: ChatCompletionRequest) -> ChatCompletionResponse:
    """Istek OpenAI semasina gore dogrulanir, cevap icerigi her zaman sabittir."""
    prompt_tokens = sum(len(message.content.split()) for message in request.messages)
    completion_tokens = len(DUMMY_CONTENT.split())

    return ChatCompletionResponse(
        id=f"chatcmpl-{uuid.uuid4().hex}",
        created=int(time.time()),
        model=request.model or MODEL_ID,
        choices=[Choice(message=ResponseMessage(content=DUMMY_CONTENT))],
        usage=Usage(
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            total_tokens=prompt_tokens + completion_tokens,
        ),
    )
```

- [ ] **Step 5: Testlerin gectigini dogrula**

Run: `cd mock-vllm && .venv/bin/pytest -q`
Expected: PASS — 5 passed

- [ ] **Step 6: Dockerfile'i yaz**

`mock-vllm/Dockerfile`:

```dockerfile
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py ./

RUN addgroup --system app && adduser --system --ingroup app app
USER app

EXPOSE 8000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
```

- [ ] **Step 7: Imaji derleyip container'da dogrula**

Run:
```bash
cd mock-vllm && docker build -t mock-vllm:test . \
  && docker run -d --rm --name mock-vllm-test -p 8001:8000 mock-vllm:test \
  && sleep 5 \
  && curl -s http://localhost:8001/health \
  && curl -s -X POST http://localhost:8001/v1/chat/completions \
       -H 'Content-Type: application/json' \
       -d '{"model":"mock-code-analyzer","messages":[{"role":"user","content":"class A {}"}]}' \
  && docker stop mock-vllm-test
```
Expected: `{"status":"ok"}` ve `"object":"chat.completion"` iceren, `choices[0].message.content` dolu bir JSON.

- [ ] **Step 8: Commit**

```bash
git add mock-vllm/ .gitignore
git commit -m "feat(mock-vllm): OpenAI uyumlu sahte chat-completions servisi"
```

---

### Task 2: ai-service OpenAI-uyumlu motor

`OpenAiCompatibleCodeAnalysisEngine` ve config'i. Bu task'ta bean henuz devreye alinmaz (secim Task 3'te); burada sinif ve birim testleri uretilir.

**Files:**
- Create: `ai-service/src/main/java/com/codementor/aiservice/config/OpenAiProperties.java`
- Create: `ai-service/src/main/java/com/codementor/aiservice/dto/openai/ChatCompletionRequest.java`
- Create: `ai-service/src/main/java/com/codementor/aiservice/dto/openai/ChatCompletionResponse.java`
- Create: `ai-service/src/main/java/com/codementor/aiservice/service/AnalysisEngineException.java`
- Create: `ai-service/src/main/java/com/codementor/aiservice/service/OpenAiCompatibleCodeAnalysisEngine.java`
- Test: `ai-service/src/test/java/com/codementor/aiservice/service/OpenAiCompatibleCodeAnalysisEngineTest.java`

**Interfaces:**
- Consumes: Task 1'in HTTP kontrati (`POST {base-url}/v1/chat/completions`). Mevcut kod: `com.codementor.aiservice.service.CodeAnalysisEngine` arayuzu, tek metot `String analyze(String sourceCode, String prompt)`.
- Produces:
  - `OpenAiProperties` record: `String baseUrl()`, `String model()`, `String apiKey()`, `Duration connectTimeout()`, `Duration readTimeout()`, `boolean hasApiKey()`.
  - `OpenAiCompatibleCodeAnalysisEngine(RestClient restClient, OpenAiProperties properties)` — `CodeAnalysisEngine` implementasyonu.
  - `AnalysisEngineException extends RuntimeException` — `(String message)` ve `(String message, Throwable cause)` constructor'lari.

- [ ] **Step 1: Basarisiz testi yaz**

`ai-service/src/test/java/com/codementor/aiservice/service/OpenAiCompatibleCodeAnalysisEngineTest.java`:

```java
package com.codementor.aiservice.service;

import com.codementor.aiservice.config.OpenAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleCodeAnalysisEngineTest {

    private static final String BASE_URL = "http://mock-vllm:8000";

    private static final String SUCCESS_BODY = """
            {
              "id": "chatcmpl-1",
              "object": "chat.completion",
              "created": 1735689600,
              "model": "mock-code-analyzer",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "Analiz sonucu"},
                  "finish_reason": "stop"
                }
              ],
              "usage": {"prompt_tokens": 5, "completion_tokens": 2, "total_tokens": 7}
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private OpenAiCompatibleCodeAnalysisEngine engine(String apiKey) {
        OpenAiProperties properties = new OpenAiProperties(
                BASE_URL, "mock-code-analyzer", apiKey, Duration.ofSeconds(5), Duration.ofSeconds(60));
        return new OpenAiCompatibleCodeAnalysisEngine(builder.build(), properties);
    }

    @Test
    void analyze_returnsAssistantContent() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("mock-code-analyzer"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        String result = engine(null).analyze("class A {}", "Guvenlik acigi var mi?");

        assertEquals("Analiz sonucu", result);
        server.verify();
    }

    @Test
    void analyze_sendsUserMessageContainingPromptAndSourceCode() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[1].content").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("Guvenlik acigi var mi?"),
                                org.hamcrest.Matchers.containsString("class A {}"))))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        engine(null).analyze("class A {}", "Guvenlik acigi var mi?");

        server.verify();
    }

    @Test
    void analyze_omitsAuthorizationHeaderWhenApiKeyBlank() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        engine("   ").analyze("class A {}", "prompt");

        server.verify();
    }

    @Test
    void analyze_sendsBearerTokenWhenApiKeyPresent() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test-key"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        engine("sk-test-key").analyze("class A {}", "prompt");

        server.verify();
    }

    @Test
    void analyze_throwsWhenUpstreamFails() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withServerError());

        AnalysisEngineException ex = assertThrows(AnalysisEngineException.class,
                () -> engine(null).analyze("class A {}", "prompt"));

        assertTrue(ex.getMessage().contains("mock-code-analyzer"));
    }

    @Test
    void analyze_throwsWhenChoicesEmpty() {
        String emptyChoices = """
                {"id":"chatcmpl-2","object":"chat.completion","created":1,"model":"mock-code-analyzer","choices":[]}
                """;
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withSuccess(emptyChoices, MediaType.APPLICATION_JSON));

        assertThrows(AnalysisEngineException.class, () -> engine(null).analyze("class A {}", "prompt"));
    }

    @Test
    void analyze_doesNotLeakApiKeyInExceptionMessage() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withServerError());

        AnalysisEngineException ex = assertThrows(AnalysisEngineException.class,
                () -> engine("sk-super-secret").analyze("class A {}", "prompt"));

        assertTrue(!ex.getMessage().contains("sk-super-secret"));
    }
}
```

- [ ] **Step 2: Testi calistirip basarisiz oldugunu dogrula**

Run: `docker run --rm -v "$PWD/ai-service":/w -v "$HOME/.m2":/root/.m2 -w /w maven:3.9.9-eclipse-temurin-21 mvn -B test -Dtest=OpenAiCompatibleCodeAnalysisEngineTest`
Expected: FAIL — derleme hatasi, `OpenAiProperties` / `OpenAiCompatibleCodeAnalysisEngine` / `AnalysisEngineException` bulunamiyor.

- [ ] **Step 3: Config record'unu yaz**

`ai-service/src/main/java/com/codementor/aiservice/config/OpenAiProperties.java`:

```java
package com.codementor.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenAI uyumlu analiz endpoint'inin ayarlari.
 * <p>
 * Ayni istemci mock-vllm'e, kendi vLLM sunucumuza veya api.openai.com'a
 * baglanabilir; fark yalnizca bu degerlerdedir.
 * <p>
 * {@code apiKey} yalnizca environment/.env uzerinden gelir ve HICBIR log satirina
 * yazilmaz. Bu yuzden record'un {@code toString()}'i override edilmistir.
 */
@ConfigurationProperties(prefix = "ai.openai")
public record OpenAiProperties(
        String baseUrl,
        String model,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    public OpenAiProperties {
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
    }

    /** Bos/whitespace api-key "key yok" demektir: Authorization header'i gonderilmez. */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "OpenAiProperties[baseUrl=" + baseUrl
                + ", model=" + model
                + ", apiKey=" + (hasApiKey() ? "***" : "(yok)")
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
```

- [ ] **Step 4: Istek/cevap DTO'larini yaz**

`ai-service/src/main/java/com/codementor/aiservice/dto/openai/ChatCompletionRequest.java`:

```java
package com.codementor.aiservice.dto.openai;

import java.util.List;

/**
 * OpenAI / vLLM {@code POST /v1/chat/completions} istek govdesi (kullandigimiz alt kume).
 */
public record ChatCompletionRequest(String model, List<Message> messages) {

    public record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }
    }
}
```

`ai-service/src/main/java/com/codementor/aiservice/dto/openai/ChatCompletionResponse.java`:

```java
package com.codementor.aiservice.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * OpenAI / vLLM chat-completions cevabi. Yalnizca ihtiyac duyulan alanlar
 * modellenmistir; {@code usage}, {@code finish_reason} gibi alanlar ve
 * saglayiciya ozel ekstra alanlar bilincli olarak yok sayilir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(String id, String model, List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(int index, Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {
    }

    /** Cevaptaki ilk asistan mesajinin icerigi; yoksa {@code null}. */
    public String firstContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Choice first = choices.get(0);
        return first == null || first.message() == null ? null : first.message().content();
    }
}
```

- [ ] **Step 5: Exception sinifini yaz**

`ai-service/src/main/java/com/codementor/aiservice/service/AnalysisEngineException.java`:

```java
package com.codementor.aiservice.service;

/**
 * Analiz motorunun (LLM cagrisi) basarisiz oldugunu bildirir.
 * <p>
 * {@code CodeTaskProcessingService} bunu yakalayip task'i FAILED olarak kaydeder;
 * mesaj yalnizca log/DB icindir, son kullaniciya i18n edilmis metin donmez.
 */
public class AnalysisEngineException extends RuntimeException {

    public AnalysisEngineException(String message) {
        super(message);
    }

    public AnalysisEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 6: Motoru yaz**

`ai-service/src/main/java/com/codementor/aiservice/service/OpenAiCompatibleCodeAnalysisEngine.java`:

```java
package com.codementor.aiservice.service;

import com.codementor.aiservice.config.OpenAiProperties;
import com.codementor.aiservice.dto.openai.ChatCompletionRequest;
import com.codementor.aiservice.dto.openai.ChatCompletionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * OpenAI uyumlu bir chat-completions endpoint'i uzerinden kod analizi yapar.
 * <p>
 * Ayni sinif mock-vllm'e, kendi vLLM sunucumuza ve api.openai.com'a hizmet eder;
 * hepsi ayni semayi konusur, fark {@link OpenAiProperties} degerlerindedir.
 */
@Slf4j
public class OpenAiCompatibleCodeAnalysisEngine implements CodeAnalysisEngine {

    private static final String SYSTEM_PROMPT =
            "Sen bir kod inceleme asistanisin. Verilen kaynak kodu, kullanicinin istegi "
                    + "dogrultusunda incele ve bulgularini kisa, maddeli bir sekilde Turkce anlat.";

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiCompatibleCodeAnalysisEngine(RestClient restClient, OpenAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String analyze(String sourceCode, String prompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(),
                List.of(
                        ChatCompletionRequest.Message.system(SYSTEM_PROMPT),
                        ChatCompletionRequest.Message.user(buildUserMessage(sourceCode, prompt))));

        ChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::applyAuthorization)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientException e) {
            // API key'i sizdirmamak icin yalnizca model adi ve hata tipi loglanir.
            throw new AnalysisEngineException(
                    "Analiz endpoint'i cagrilamadi. model=" + properties.model(), e);
        }

        String content = response == null ? null : response.firstContent();
        if (content == null || content.isBlank()) {
            throw new AnalysisEngineException(
                    "Analiz endpoint'i bos cevap dondu. model=" + properties.model());
        }
        log.info("Analiz tamamlandi. model={}, cevapUzunlugu={}", properties.model(), content.length());
        return content;
    }

    private void applyAuthorization(HttpHeaders headers) {
        if (properties.hasApiKey()) {
            headers.setBearerAuth(properties.apiKey());
        }
    }

    private String buildUserMessage(String sourceCode, String prompt) {
        String effectivePrompt = (prompt == null || prompt.isBlank())
                ? "Bu kodu genel olarak incele."
                : prompt;
        return effectivePrompt + "\n\n--- KAYNAK KOD ---\n" + (sourceCode == null ? "" : sourceCode);
    }
}
```

- [ ] **Step 7: Testlerin gectigini dogrula**

Run: `docker run --rm -v "$PWD/ai-service":/w -v "$HOME/.m2":/root/.m2 -w /w maven:3.9.9-eclipse-temurin-21 mvn -B test -Dtest=OpenAiCompatibleCodeAnalysisEngineTest`
Expected: PASS — 7 tests, 0 failures.

Not: `MockRestServiceServer.bindTo(RestClient.Builder)` Spring Framework 6.2+ ile gelir. Bu adimda `bindTo` overload'u bulunamazsa cozum, `RestClient.builder().requestFactory(...)` yerine testte gercek bir stub sunucu kurmak degil; once `spring-boot-starter-webmvc-test` bagimliliginin `spring-test` getirdigini `mvn dependency:tree | grep spring-test` ile dogrula.

- [ ] **Step 8: Commit**

```bash
git add ai-service/src/main/java/com/codementor/aiservice/config/OpenAiProperties.java \
        ai-service/src/main/java/com/codementor/aiservice/dto/openai/ \
        ai-service/src/main/java/com/codementor/aiservice/service/AnalysisEngineException.java \
        ai-service/src/main/java/com/codementor/aiservice/service/OpenAiCompatibleCodeAnalysisEngine.java \
        ai-service/src/test/java/com/codementor/aiservice/service/OpenAiCompatibleCodeAnalysisEngineTest.java
git commit -m "feat(ai-service): OpenAI uyumlu kod analiz motoru"
```

---

### Task 3: Motor secimi, Compose entegrasyonu ve uctan uca dogrulama

Iki motoru birbirini disleyen bean'lere cevirir, `mock-vllm`'i Compose'a baglar ve tum zinciri calistirarak dogrular.

**Files:**
- Create: `ai-service/src/main/java/com/codementor/aiservice/config/OpenAiEngineConfig.java`
- Modify: `ai-service/src/main/java/com/codementor/aiservice/service/MockCodeAnalysisEngine.java`
- Modify: `ai-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `TEKNIK_DOKUMANTASYON.md`
- Test: `ai-service/src/test/java/com/codementor/aiservice/config/EngineSelectionTest.java`

**Interfaces:**
- Consumes: Task 1'in `mock-vllm` servisi (container adi `mock-vllm`, port `8000`, healthcheck `/health`); Task 2'nin `OpenAiProperties`, `OpenAiCompatibleCodeAnalysisEngine(RestClient, OpenAiProperties)`.
- Produces: `ai.engine` property'si (`openai` | `mock`) ile secilen tek bir `CodeAnalysisEngine` bean'i.

- [ ] **Step 1: Basarisiz testi yaz**

`ai-service/src/test/java/com/codementor/aiservice/config/EngineSelectionTest.java`:

```java
package com.codementor.aiservice.config;

import com.codementor.aiservice.service.CodeAnalysisEngine;
import com.codementor.aiservice.service.MockCodeAnalysisEngine;
import com.codementor.aiservice.service.OpenAiCompatibleCodeAnalysisEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * ai.engine property'sinin dogru motoru sectigini dogrular.
 * Tam Spring context'i (DB/Redis/Rabbit) ayaga kaldirmadan, yalnizca ilgili
 * config siniflari ile calisir.
 * <p>
 * RestClient.Builder bean'i elle saglanir: Spring Boot 4'te autoconfiguration
 * siniflarinin paketleri degistigi icin RestClientAutoConfiguration'a isimle
 * bagimli olmak kirilgan olurdu.
 */
class EngineSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(OpenAiEngineConfig.class, MockCodeAnalysisEngine.class);

    @Test
    void defaultsToInProcessMockWhenPropertyMissing() {
        runner.run(context -> assertInstanceOf(MockCodeAnalysisEngine.class,
                context.getBean(CodeAnalysisEngine.class)));
    }

    @Test
    void selectsMockEngineExplicitly() {
        runner.withPropertyValues("ai.engine=mock")
                .run(context -> assertInstanceOf(MockCodeAnalysisEngine.class,
                        context.getBean(CodeAnalysisEngine.class)));
    }

    @Test
    void selectsOpenAiEngineWhenConfigured() {
        runner.withPropertyValues(
                        "ai.engine=openai",
                        "ai.openai.base-url=http://mock-vllm:8000",
                        "ai.openai.model=mock-code-analyzer")
                .run(context -> assertInstanceOf(OpenAiCompatibleCodeAnalysisEngine.class,
                        context.getBean(CodeAnalysisEngine.class)));
    }

    @Test
    void onlyOneEngineBeanIsRegistered() {
        runner.withPropertyValues(
                        "ai.engine=openai",
                        "ai.openai.base-url=http://mock-vllm:8000",
                        "ai.openai.model=mock-code-analyzer")
                .run(context -> assertEquals(1, context.getBeansOfType(CodeAnalysisEngine.class).size()));
    }
}
```

- [ ] **Step 2: Testi calistirip basarisiz oldugunu dogrula**

Run: `docker run --rm -v "$PWD/ai-service":/w -v "$HOME/.m2":/root/.m2 -w /w maven:3.9.9-eclipse-temurin-21 mvn -B test -Dtest=EngineSelectionTest`
Expected: FAIL — derleme hatasi, `OpenAiEngineConfig` bulunamiyor.

- [ ] **Step 3: Config sinifini yaz**

`ai-service/src/main/java/com/codementor/aiservice/config/OpenAiEngineConfig.java`:

```java
package com.codementor.aiservice.config;

import com.codementor.aiservice.service.CodeAnalysisEngine;
import com.codementor.aiservice.service.OpenAiCompatibleCodeAnalysisEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * {@code ai.engine=openai} verildiginde HTTP tabanli analiz motorunu devreye alir.
 * Property yoksa veya {@code mock} ise bu config hic yuklenmez ve in-process
 * {@code MockCodeAnalysisEngine} devrede kalir.
 */
@Configuration
@ConditionalOnProperty(name = "ai.engine", havingValue = "openai")
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiEngineConfig {

    /**
     * Analiz cagrilari icin ayri bir RestClient: timeout'lari uygulamanin geri
     * kalanindan bagimsiz olsun diye kendi request factory'si ile kurulur.
     */
    @Bean
    public RestClient openAiRestClient(RestClient.Builder builder, OpenAiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public CodeAnalysisEngine openAiCodeAnalysisEngine(RestClient openAiRestClient,
                                                       OpenAiProperties properties) {
        return new OpenAiCompatibleCodeAnalysisEngine(openAiRestClient, properties);
    }
}
```

- [ ] **Step 4: MockCodeAnalysisEngine'i kosullu hale getir**

`ai-service/src/main/java/com/codementor/aiservice/service/MockCodeAnalysisEngine.java` dosyasinda `@Component` anotasyonunun hemen ustune ekle ve import'u ekle:

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
```

```java
/**
 * MOCK analiz motoru: HTTP cagrisi yapmaz, sabit metin doner.
 * <p>
 * {@code ai.engine} verilmezse veya {@code mock} ise devrededir
 * ({@code matchIfMissing = true}), yani mevcut davranis korunur.
 * {@code ai.engine=openai} verildiginde yerini
 * {@link OpenAiCompatibleCodeAnalysisEngine} alir.
 */
@Component
@ConditionalOnProperty(name = "ai.engine", havingValue = "mock", matchIfMissing = true)
public class MockCodeAnalysisEngine implements CodeAnalysisEngine {
```

Sinifin govdesi (analyze metodu) DEGISMEZ.

- [ ] **Step 5: Testlerin gectigini dogrula**

Run: `docker run --rm -v "$PWD/ai-service":/w -v "$HOME/.m2":/root/.m2 -w /w maven:3.9.9-eclipse-temurin-21 mvn -B test -Dtest=EngineSelectionTest`
Expected: PASS — 4 tests, 0 failures.

- [ ] **Step 6: application.yml'e varsayilanlari ekle**

`ai-service/src/main/resources/application.yml` dosyasinin SONUNA ekle:

```yaml
# Kod analiz motoru secimi.
#   mock   -> in-process sahte motor (HTTP cagrisi yok, sabit metin)
#   openai -> OpenAI uyumlu HTTP endpoint (mock-vllm, kendi vLLM'imiz veya api.openai.com)
# Property hic verilmezse mock devrede kalir.
ai:
  engine: ${AI_ENGINE:mock}
  openai:
    # Gercek ChatGPT icin: https://api.openai.com/v1 + gpt-4o-mini + OPENAI_API_KEY
    base-url: ${AI_OPENAI_BASE_URL:http://localhost:8000}
    model: ${AI_OPENAI_MODEL:mock-code-analyzer}
    # Bos birakilirsa Authorization header'i hic gonderilmez (mock / auth'suz vLLM).
    api-key: ${OPENAI_API_KEY:}
    connect-timeout: ${AI_OPENAI_CONNECT_TIMEOUT:5s}
    read-timeout: ${AI_OPENAI_READ_TIMEOUT:60s}
```

- [ ] **Step 7: Compose'a mock-vllm servisini ekle**

`docker-compose.yml` icinde `code-service:` servisinin ONUNE yeni servisi ekle:

```yaml
  mock-vllm:
    build:
      context: ./mock-vllm
      dockerfile: Dockerfile
    container_name: mock-vllm
    healthcheck:
      test: ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8000/health').read()"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 10s
    ports:
      # Elle curl ve FastAPI'nin kendi /docs sayfasi icin host'a acilir.
      - "8000:8000"
    networks:
      - codementor-net
```

Ayni dosyada `ai-service:` servisinin `depends_on` blogunu asagidaki ile degistir:

```yaml
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      mock-vllm:
        condition: service_healthy
```

Ve `ai-service:` servisinin `environment` blogunda `TASK_STATUS_TTL` satirindan SONRA ekle:

```yaml
      # Analiz motoru: varsayilan olarak yerel mock-vllm servisine baglanir.
      # Gercek ChatGPT icin: AI_OPENAI_BASE_URL=https://api.openai.com/v1,
      # AI_OPENAI_MODEL=gpt-4o-mini ve .env icinde OPENAI_API_KEY.
      AI_ENGINE: ${AI_ENGINE:-openai}
      AI_OPENAI_BASE_URL: ${AI_OPENAI_BASE_URL:-http://mock-vllm:8000}
      AI_OPENAI_MODEL: ${AI_OPENAI_MODEL:-mock-code-analyzer}
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
```

- [ ] **Step 8: .env.example'a AI ayarlarini ekle**

`.env.example` dosyasinin SONUNA ekle:

```
# --- Kod analiz motoru ---
# mock   -> in-process sahte motor (HTTP cagrisi yok)
# openai -> OpenAI uyumlu endpoint (mock-vllm / kendi vLLM'in / api.openai.com)
AI_ENGINE=openai
AI_OPENAI_BASE_URL=http://mock-vllm:8000
AI_OPENAI_MODEL=mock-code-analyzer
# Gercek OpenAI kullanacaksan:
#   AI_OPENAI_BASE_URL=https://api.openai.com/v1
#   AI_OPENAI_MODEL=gpt-4o-mini
#   OPENAI_API_KEY=sk-...
# Mock veya auth'suz local vLLM icin bos birak: Authorization header'i gonderilmez.
OPENAI_API_KEY=
```

- [ ] **Step 9: Yerel .env dosyasini guncelle**

Repo kokundeki (gitignored) `.env` dosyasinin sonuna ayni dort satiri ekle ki Compose ve IDE ayni degerleri gorsun:

```bash
cat >> .env <<'EOF'

AI_ENGINE=openai
AI_OPENAI_BASE_URL=http://mock-vllm:8000
AI_OPENAI_MODEL=mock-code-analyzer
OPENAI_API_KEY=
EOF
```

- [ ] **Step 10: Tum stack'i derleyip ayaga kaldir**

Run:
```bash
docker compose build && docker compose up -d && sleep 60 && docker compose ps
```
Expected: `mock-vllm` dahil tum container'lar `Up`; `mock-vllm` `(healthy)`.

- [ ] **Step 11: Uctan uca dogrula**

Run:
```bash
curl -s http://localhost:8000/health
L=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"kontrol_test","password":"Test1234!x"}')
T=$(printf '%s' "$L" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
A=$(curl -s -X POST http://localhost:8080/api/v1/analyze -H "Authorization: Bearer $T" \
     -H 'Content-Type: application/json' -d '{"sourceCode":"class Deneme {}"}')
TASK=$(printf '%s' "$A" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["taskId"])')
sleep 5
curl -s "http://localhost:8080/api/v1/status/$TASK" -H "Authorization: Bearer $T"
docker logs mock-vllm 2>&1 | tail -5
```
Expected: status cevabi `"status":"COMPLETED"` ve `result` alani mock-vllm'in dummy metnini ("mock-vllm servisinden gelmistir") iceriyor; `mock-vllm` loglarinda `POST /v1/chat/completions 200` satiri var.

Not: `kontrol_test` kullanicisi yoksa once kaydet:
`curl -s -X POST http://localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{"username":"kontrol_test","password":"Test1234!x"}'`

- [ ] **Step 12: Motorun mock'a dusebildigini dogrula**

Run:
```bash
AI_ENGINE=mock docker compose up -d ai-service && sleep 30 \
  && docker logs ai-service 2>&1 | grep -c "Started AiServiceApplication"
```
Expected: `1` — ai-service, mock-vllm'e hic baglanmadan aciliyor. Sonra varsayilana don:
`docker compose up -d ai-service`

- [ ] **Step 13: Tum test paketlerini calistir**

Run:
```bash
NET=codementor_codementor-net
for s in code-service ai-service api-gateway; do
  docker run --rm --network $NET -v "$PWD/$s":/w -v "$HOME/.m2":/root/.m2 -w /w \
    maven:3.9.9-eclipse-temurin-21 mvn -B test \
    -Dspring.datasource.url=jdbc:p6spy:postgresql://postgres:5432/code_analysis_db \
    -Dspring.data.redis.host=redis -Dspring.rabbitmq.host=rabbitmq \
    2>&1 | grep -E "Tests run:.*Skipped: [0-9]+$|BUILD"
done
cd mock-vllm && .venv/bin/pytest -q && cd ..
```
Expected: uc Maven modulunde de `BUILD SUCCESS`, pytest'te `5 passed`.

- [ ] **Step 14: Dokumantasyonu guncelle**

`TEKNIK_DOKUMANTASYON.md` dosyasinin SONUNA ekle:

```markdown

---

## 8. Kod Analiz Motoru (AI Tarafi)

`ai-service` analizi `CodeAnalysisEngine` arayuzu uzerinden yapar. Iki implementasyon
vardir ve secim `ai.engine` (env: `AI_ENGINE`) ile yapilir:

| `AI_ENGINE` | Motor | Davranis |
|---|---|---|
| `mock` (veya bos) | `MockCodeAnalysisEngine` | HTTP cagrisi yok, sabit metin doner |
| `openai` | `OpenAiCompatibleCodeAnalysisEngine` | `{base-url}/v1/chat/completions` cagrilir |

vLLM ve OpenAI ayni API semasini konusur, bu yuzden tek istemci hepsine yeter.
Hangi saglayiciya gidilecegi tamamen config'tir:

| Hedef | `AI_OPENAI_BASE_URL` | `AI_OPENAI_MODEL` | `OPENAI_API_KEY` |
|---|---|---|---|
| Yerel mock (varsayilan) | `http://mock-vllm:8000` | `mock-code-analyzer` | bos |
| Kendi vLLM sunucun | `http://<host>:8000` | sunucudaki model id | bos veya key |
| Gercek ChatGPT | `https://api.openai.com/v1` | `gpt-4o-mini` | `sk-...` |

`OPENAI_API_KEY` bos birakilirsa `Authorization` header'i hic gonderilmez. Key yalnizca
`.env`'den okunur (gitignored) ve hicbir log satirina yazilmaz.

### mock-vllm servisi

`mock-vllm/` altinda, FastAPI ile yazilmis kucuk bir servistir. Gercek model
calistirmaz; her istege ayni dummy metni doner. Amaci AI tarafini gercek bir
saglayiciya ihtiyac duymadan test edilebilir kilmaktir.

| Adres | Ne |
|---|---|
| http://localhost:8000/docs | Mock servisin kendi Swagger sayfasi |
| http://localhost:8000/v1/chat/completions | OpenAI uyumlu chat endpoint'i |
| http://localhost:8000/v1/models | Sahte model listesi |
| http://localhost:8000/health | Healthcheck |

Streaming (`stream: true`) desteklenmez.
```

- [ ] **Step 15: Commit**

```bash
git add ai-service/src/main/java/com/codementor/aiservice/config/OpenAiEngineConfig.java \
        ai-service/src/main/java/com/codementor/aiservice/service/MockCodeAnalysisEngine.java \
        ai-service/src/main/resources/application.yml \
        ai-service/src/test/java/com/codementor/aiservice/config/EngineSelectionTest.java \
        docker-compose.yml .env.example TEKNIK_DOKUMANTASYON.md
git commit -m "feat(ai-service): motor secimini config'e bagla ve mock-vllm'i compose'a ekle"
```
