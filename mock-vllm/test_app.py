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
