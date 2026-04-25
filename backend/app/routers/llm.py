"""Single-image vision router used by the executor (Phase 2)."""

from __future__ import annotations

import json
import logging

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.llm import LLMError, VisionImage, get_provider

log = logging.getLogger(__name__)

router = APIRouter(prefix="/v1/llm", tags=["llm"])


@router.post("/vision")
async def vision(
    image: UploadFile = File(...),
    instruction: str = Form(...),
    expect_json: bool = Form(default=True),
) -> dict:
    """Forward a screenshot + instruction to the configured LLM provider.

    Returns ``{"text": "...", "json": {...?}}`` so the Android client can
    parse without knowing the underlying provider format.
    """
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Expected an image upload")

    data = await image.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty image")

    try:
        provider = get_provider()
        result = await provider.vision(
            images=[VisionImage(data=data, mime_type=image.content_type)],
            instruction=instruction,
            expect_json=expect_json,
        )
    except LLMError as e:
        log.warning("LLM call failed: %s", e)
        raise HTTPException(status_code=502, detail=str(e)) from e

    parsed: dict | None = None
    if expect_json:
        try:
            parsed = json.loads(result.text)
        except json.JSONDecodeError:
            log.info("LLM returned non-JSON despite expect_json=true")
    return {"text": result.text, "json": parsed}
