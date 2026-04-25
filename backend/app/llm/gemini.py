"""Gemini Vision client.

Wraps the Generative Language REST API. We use REST instead of the
google-genai SDK to keep dependencies lean and the call surface explicit.
"""

from __future__ import annotations

import base64
from typing import Any

import httpx

from app.config import settings

_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"


class GeminiError(RuntimeError):
    """Raised when the Gemini API returns an error or a malformed response."""


async def vision_call(
    image_bytes: bytes,
    instruction: str,
    *,
    mime_type: str = "image/jpeg",
    model: str | None = None,
    response_mime_type: str = "application/json",
) -> dict[str, Any]:
    """Send an image + instruction to Gemini and return the parsed response.

    Returns the raw JSON response from the API. Callers are responsible for
    extracting the text/JSON payload from `candidates[0].content.parts[0].text`.
    """
    if not settings.gemini_api_key:
        raise GeminiError("GEMINI_API_KEY is not configured")

    chosen_model = model or settings.gemini_model
    url = f"{_API_BASE}/{chosen_model}:generateContent"
    payload = {
        "contents": [
            {
                "parts": [
                    {"text": instruction},
                    {
                        "inline_data": {
                            "mime_type": mime_type,
                            "data": base64.b64encode(image_bytes).decode("ascii"),
                        }
                    },
                ]
            }
        ],
        "generationConfig": {
            "responseMimeType": response_mime_type,
            "temperature": 0.2,
        },
    }
    headers = {"x-goog-api-key": settings.gemini_api_key, "Content-Type": "application/json"}

    async with httpx.AsyncClient(timeout=30.0) as client:
        resp = await client.post(url, headers=headers, json=payload)
    if resp.status_code >= 400:
        raise GeminiError(f"Gemini API error {resp.status_code}: {resp.text[:500]}")
    return resp.json()


def extract_text(response: dict[str, Any]) -> str:
    """Pull the first text part from a Gemini response, or raise."""
    try:
        return response["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError, TypeError) as exc:
        raise GeminiError(f"Unexpected Gemini response shape: {response}") from exc
