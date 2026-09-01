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
