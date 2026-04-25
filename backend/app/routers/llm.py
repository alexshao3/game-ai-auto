"""LLM routing endpoints."""

from __future__ import annotations

import json
import logging

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.llm.gemini import GeminiError, extract_text, vision_call

log = logging.getLogger(__name__)

router = APIRouter(prefix="/v1/llm", tags=["llm"])


@router.post("/vision")
async def vision(
    image: UploadFile = File(...),
    instruction: str = Form(...),
    expect_json: bool = Form(default=True),
) -> dict:
    """Forward a screenshot + instruction to Gemini.

    The Android app sends a JPEG plus a structured instruction; we return
    `{"text": "...", "json": {...?}}` so the client can parse without
    knowing the underlying provider format.
    """
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Expected an image upload")

    data = await image.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty image")

    try:
        response = await vision_call(
            image_bytes=data,
            instruction=instruction,
            mime_type=image.content_type,
            response_mime_type="application/json" if expect_json else "text/plain",
        )
        text = extract_text(response)
    except GeminiError as e:
        log.warning("Gemini call failed: %s", e)
        raise HTTPException(status_code=502, detail=str(e)) from e

    parsed: dict | None = None
    if expect_json:
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError:
            log.info("Gemini returned non-JSON despite expect_json=true")
    return {"text": text, "json": parsed}
