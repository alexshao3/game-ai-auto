"""Anthropic-format vision adapter (works with api.anthropic.com or any proxy).

Talks to ``POST {base_url}/v1/messages`` with the Anthropic Messages API
shape. Images are inlined as base64 ``image`` content blocks. We do not use
the Anthropic Python SDK so the same code works against self-hosted proxies
that re-export the Anthropic surface.
"""

from __future__ import annotations

import base64
import json
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
        # Some users supply a proxy base URL that already ends in `/v1`
        # (the OpenAI-compatible convention). Anthropic's spec calls
        # `{root}/v1/messages`, so when the user-supplied URL already ends
        # in `/v1` we must NOT double it up.
        cleaned = (base_url or _DEFAULT_BASE_URL).rstrip("/")
        if cleaned.endswith("/v1"):
            self._messages_url = f"{cleaned}/messages"
        else:
            self._messages_url = f"{cleaned}/v1/messages"
        self.base_url = cleaned

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
            # Several Anthropic-compatible proxies stream by default and
            # then append SSE end-markers (``data: [DONE]``) to the body,
            # which makes ``resp.json()`` fail. Force a single JSON reply.
            "stream": False,
            "messages": [{"role": "user", "content": content}],
        }
        headers = {
            "x-api-key": self.api_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }
        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(self._messages_url, headers=headers, json=payload)
        if resp.status_code >= 400:
            raise LLMError(
                f"anthropic API error {resp.status_code}: {resp.text[:500]}"
            )
        data = _parse_response_body(resp.text)
        text = _extract_text(data)
        return VisionResult(text=text, raw=data)


def _parse_response_body(body: str) -> dict[str, Any]:
    """Parse a non-streaming Anthropic response, tolerating proxies that
    append SSE markers (e.g. ``...}data: [DONE]``) even when ``stream=False``.
    """
    body = body.strip()
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        pass
    # Fast path: trailing SSE marker concatenated to a JSON object.
    cut = body.rfind("}")
    if cut != -1:
        try:
            return json.loads(body[: cut + 1])
        except json.JSONDecodeError:
            pass
    raise LLMError(f"anthropic: unparseable response body: {body[:300]}")


def _extract_text(response: dict[str, Any]) -> str:
    blocks = response.get("content", [])
    parts = [b.get("text", "") for b in blocks if b.get("type") == "text"]
    if not parts:
        raise LLMError(f"anthropic: no text content in response: {response}")
    return "".join(parts).strip()
