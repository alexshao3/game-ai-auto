"""OpenAI-compatible vision adapter (vLLM, LM Studio, OpenRouter, OneAPI...).

Talks to ``POST {base_url}/chat/completions`` using the OpenAI chat shape
with ``image_url`` content parts (data URIs).
"""

from __future__ import annotations

import base64
from typing import Any

import httpx

from app.llm.base import LLMError, VisionImage, VisionProvider, VisionResult


class OpenAICompatProvider(VisionProvider):
    name = "openai"

    def __init__(self, *, api_key: str, model_id: str, base_url: str) -> None:
        if not base_url:
            raise LLMError("openai: base_url is required (e.g. https://api.openai.com/v1)")
        if not model_id:
            raise LLMError("openai: model_id is empty")
        self.api_key = api_key
        self.model_id = model_id
        self.base_url = base_url.rstrip("/")

    async def vision(
        self,
        images: list[VisionImage],
        instruction: str,
        *,
        expect_json: bool,
    ) -> VisionResult:
        if not images:
            raise LLMError("openai: at least one image is required")

        content: list[dict[str, Any]] = []
        for img in images:
            uri = (
                f"data:{img.mime_type};base64,"
                + base64.b64encode(img.data).decode("ascii")
            )
            content.append({"type": "image_url", "image_url": {"url": uri}})
        prefix = (
            "Respond with a single JSON object only, no prose. " if expect_json else ""
        )
        content.append({"type": "text", "text": prefix + instruction})

        payload: dict[str, Any] = {
            "model": self.model_id,
            "messages": [{"role": "user", "content": content}],
            "temperature": 0.2,
        }
        if expect_json:
            payload["response_format"] = {"type": "json_object"}
        headers = {
            "Content-Type": "application/json",
        }
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        url = f"{self.base_url}/chat/completions"

        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(url, headers=headers, json=payload)
        if resp.status_code >= 400:
            raise LLMError(f"openai API error {resp.status_code}: {resp.text[:500]}")
        data = resp.json()
        text = _extract_text(data)
        return VisionResult(text=text, raw=data)


def _extract_text(response: dict[str, Any]) -> str:
    try:
        text = response["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as exc:
        raise LLMError(f"openai: unexpected response shape: {response}") from exc
    if text is None:
        # Happens for content-policy violations and function-call responses.
        raise LLMError(f"openai: content is null in response: {response}")
    return text.strip()
