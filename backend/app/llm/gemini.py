"""Google Gemini Vision adapter (kept for backward compatibility).

Talks to ``POST {base_url}/v1beta/models/{model}:generateContent`` with
inline base64 image parts. ``base_url`` defaults to the public Generative
Language API but can be overridden for proxies.
"""

from __future__ import annotations

import base64
from typing import Any

import httpx

from app.llm.base import LLMError, VisionImage, VisionProvider, VisionResult

_DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"


class GeminiError(LLMError):
    """Backwards-compatible alias for callers still importing GeminiError."""


class GeminiProvider(VisionProvider):
    name = "gemini"

    def __init__(self, *, api_key: str, model_id: str, base_url: str = "") -> None:
        if not api_key:
            raise GeminiError("gemini: api_key is empty")
        if not model_id:
            raise GeminiError("gemini: model_id is empty")
        self.api_key = api_key
        self.model_id = model_id
        self.base_url = (base_url or _DEFAULT_BASE_URL).rstrip("/")

    async def vision(
        self,
        images: list[VisionImage],
        instruction: str,
        *,
        expect_json: bool,
    ) -> VisionResult:
        if not images:
            raise GeminiError("gemini: at least one image is required")

        parts: list[dict[str, Any]] = [{"text": instruction}]
        for img in images:
            parts.append(
                {
                    "inline_data": {
                        "mime_type": img.mime_type,
                        "data": base64.b64encode(img.data).decode("ascii"),
                    }
                }
            )

        payload = {
            "contents": [{"parts": parts}],
            "generationConfig": {
                "responseMimeType": "application/json" if expect_json else "text/plain",
                "temperature": 0.2,
            },
        }
        headers = {
            "x-goog-api-key": self.api_key,
            "Content-Type": "application/json",
        }
        url = f"{self.base_url}/v1beta/models/{self.model_id}:generateContent"

        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(url, headers=headers, json=payload)
        if resp.status_code >= 400:
            raise GeminiError(f"gemini API error {resp.status_code}: {resp.text[:500]}")
        data = resp.json()
        text = _extract_text(data)
        return VisionResult(text=text, raw=data)


def _extract_text(response: dict[str, Any]) -> str:
    try:
        return response["candidates"][0]["content"]["parts"][0]["text"].strip()
    except (KeyError, IndexError, TypeError) as exc:
        raise GeminiError(f"gemini: unexpected response shape: {response}") from exc
