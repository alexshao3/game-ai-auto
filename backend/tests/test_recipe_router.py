"""Smoke tests for the recipe router and the LLM provider abstraction."""

from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.llm import VisionImage, VisionResult
from app.llm.factory import get_provider
from app.main import app


class FakeProvider:
    name = "fake"
    model_id = "fake-model"

    def __init__(self, response_text: str) -> None:
        self._response_text = response_text
        self.calls: list[dict[str, Any]] = []

    async def vision(
        self,
        images: list[VisionImage],
        instruction: str,
        *,
        expect_json: bool,
    ) -> VisionResult:
        self.calls.append(
            {"n_images": len(images), "instruction": instruction, "expect_json": expect_json}
        )
        return VisionResult(text=self._response_text, raw=None)


@pytest.fixture(autouse=True)
def _reset_provider_cache() -> None:
    get_provider.cache_clear()
    yield
    get_provider.cache_clear()


def _override(provider: FakeProvider) -> None:
    """Replace the LLM provider symbol used by both routers with our fake."""
    import app.routers.llm as llm_router
    import app.routers.recipe as recipe_router

    llm_router.get_provider = lambda: provider  # type: ignore[assignment]
    recipe_router.get_provider = lambda: provider  # type: ignore[assignment]


_VALID_JPEG = (
    b"\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00"
    b"\xff\xdb\x00C\x00" + b"\x08" * 64 + b"\xff\xd9"
)


def test_health_reports_provider_format() -> None:
    client = TestClient(app)
    resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert "provider" in body


def test_recipe_generate_happy_path() -> None:
    fake = FakeProvider(
        response_text='{"name":"Login","steps":[{"ordinal":0,'
        '"intent":"Tap login button","actionHint":"tap"}]}'
    )
    _override(fake)
    client = TestClient(app)

    files = [("frames", ("f1.jpg", _VALID_JPEG, "image/jpeg"))]
    data = {"session_name": "Daily login"}
    resp = client.post("/v1/recipe/generate", files=files, data=data)

    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["recipe"]["name"] == "Login"
    assert body["recipe"]["steps"][0]["intent"] == "Tap login button"
    assert fake.calls and fake.calls[0]["n_images"] == 1


def test_recipe_generate_strips_markdown_fences() -> None:
    fake = FakeProvider(
        response_text='```json\n{"name":"X","steps":[{"ordinal":0,'
        '"intent":"open menu"}]}\n```'
    )
    _override(fake)
    client = TestClient(app)
    files = [("frames", ("f1.jpg", _VALID_JPEG, "image/jpeg"))]
    resp = client.post(
        "/v1/recipe/generate", files=files, data={"session_name": "X"}
    )
    assert resp.status_code == 200
    assert resp.json()["recipe"]["steps"][0]["intent"] == "open menu"


def test_recipe_generate_rejects_empty_steps() -> None:
    fake = FakeProvider(response_text='{"name":"X","steps":[]}')
    _override(fake)
    client = TestClient(app)
    files = [("frames", ("f1.jpg", _VALID_JPEG, "image/jpeg"))]
    resp = client.post(
        "/v1/recipe/generate", files=files, data={"session_name": "X"}
    )
    assert resp.status_code == 502


def test_recipe_generate_handles_null_ordinal() -> None:
    # Model returns null for ordinal — must not 500, must fall back to index.
    fake = FakeProvider(
        response_text='{"name":"X","steps":[{"ordinal":null,"intent":"open menu"},'
        '{"ordinal":"two","intent":"tap reward"}]}'
    )
    _override(fake)
    client = TestClient(app)
    files = [("frames", ("f1.jpg", _VALID_JPEG, "image/jpeg"))]
    resp = client.post(
        "/v1/recipe/generate", files=files, data={"session_name": "X"}
    )
    assert resp.status_code == 200
    steps = resp.json()["recipe"]["steps"]
    assert steps[0]["ordinal"] == 0
    assert steps[1]["ordinal"] == 1  # "two" is non-numeric → fallback to idx


def test_recipe_generate_invalid_json_blob_returns_502() -> None:
    # Model emits prose containing a {...} blob that is itself not valid JSON.
    fake = FakeProvider(response_text='Here is your recipe: {not valid json}')
    _override(fake)
    client = TestClient(app)
    files = [("frames", ("f1.jpg", _VALID_JPEG, "image/jpeg"))]
    resp = client.post(
        "/v1/recipe/generate", files=files, data={"session_name": "X"}
    )
    assert resp.status_code == 502


def test_recipe_generate_downsamples() -> None:
    fake = FakeProvider(
        response_text='{"name":"X","steps":[{"ordinal":0,"intent":"a"}]}'
    )
    _override(fake)
    client = TestClient(app)
    # Send 50 frames; provider should receive at most 24.
    files = [
        ("frames", (f"f{i}.jpg", _VALID_JPEG, "image/jpeg")) for i in range(50)
    ]
    resp = client.post(
        "/v1/recipe/generate", files=files, data={"session_name": "X"}
    )
    assert resp.status_code == 200
    assert fake.calls[0]["n_images"] == 24
