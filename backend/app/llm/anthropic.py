"""Anthropic-format vision adapter (works with api.anthropic.com or any proxy).

Talks to ``POST {base_url}/v1/messages`` with the Anthropic Messages API
shape. Images are inlined as base64 ``image`` content blocks. We do not use
the Anthropic Python SDK so the same code works against self-hosted proxies
that re-export the Anthropic surface.
"""

from __future__ import annotations

import base64
from typing import Any

import httpx

from app.llm.base import LLMError, VisionImage, VisionProvider, VisionResult

_DEFAULT_BASE_URL = "https://api.anthropic.com"


class AnthropicProvider(VisionProvider):
    name = "anthropic"

    def __init__(self, *, api_key: str, model_id: str, base_url: str = "") -> None:
        if not api_key:
            raise LLMError("anthropic: api_key is empty")
        if not model_id:
            raise LLMError("anthropic: model_id is empty")
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
            raise LLMError("anthropic: at least one image is required")

        content: list[dict[str, Any]] = []
        for img in images:
            content.append(
                {
                    "type": "image",
                    "source": {
                        "type": "base64",
                        "media_type": img.mime_type,
                        "data": base64.b64encode(img.data).decode("ascii"),
                    },
                }
            )
        prefix = (
            "Respond with a single JSON object only, no prose. " if expect_json else ""
        )
        content.append({"type": "text", "text": prefix + instruction})

        payload = {
            "model": self.model_id,
            "max_tokens": 4096,
            "temperature": 0.2,
            "messages": [{"role": "user", "content": content}],
        }
        headers = {
            "x-api-key": self.api_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }
        url = f"{self.base_url}/v1/messages"

        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(url, headers=headers, json=payload)
        if resp.status_code >= 400:
            raise LLMError(
                f"anthropic API error {resp.status_code}: {resp.text[:500]}"
            )
        data = resp.json()
        text = _extract_text(data)
        return VisionResult(text=text, raw=data)


def _extract_text(response: dict[str, Any]) -> str:
    blocks = response.get("content", [])
    parts = [b.get("text", "") for b in blocks if b.get("type") == "text"]
    if not parts:
        raise LLMError(f"anthropic: no text content in response: {response}")
    return "".join(parts).strip()
