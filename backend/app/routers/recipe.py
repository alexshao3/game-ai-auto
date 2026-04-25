"""Multi-frame recipe extraction endpoint.

Accepts a sequence of JPEG frames recorded by the Android app while the
user demonstrated a task, and returns an intent-based JSON ``Recipe`` the
executor can later replay.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.llm import LLMError, VisionImage, get_provider
from app.llm.prompts import RECIPE_FROM_FRAMES_PROMPT

log = logging.getLogger(__name__)

router = APIRouter(prefix="/v1/recipe", tags=["recipe"])

# Hard cap so a runaway recording doesn't blow past provider context limits.
# Most game daily tasks finish in 30-90 seconds at 2 fps → ~60-180 frames;
# we down-sample uniformly if more than this arrive.
_MAX_FRAMES_TO_LLM = 24


@router.post("/generate")
async def generate(
    frames: list[UploadFile] = File(...),
    session_name: str = Form(...),
    game_package: str | None = Form(default=None),
) -> dict[str, Any]:
    if not frames:
        raise HTTPException(status_code=400, detail="No frames provided")

    raw_frames: list[VisionImage] = []
    for f in frames:
        body = await f.read()
        if not body:
            continue
        mime = f.content_type or "image/jpeg"
        if not mime.startswith("image/"):
            raise HTTPException(status_code=400, detail=f"Bad mime: {mime}")
        raw_frames.append(VisionImage(data=body, mime_type=mime))

    if not raw_frames:
        raise HTTPException(status_code=400, detail="All frames were empty")

    sampled = _down_sample(raw_frames, _MAX_FRAMES_TO_LLM)
    instruction = (
        RECIPE_FROM_FRAMES_PROMPT
        + f"\n\nUser-provided session name: {session_name!r}."
        + (f" App package: {game_package}." if game_package else "")
        + f" Frames provided: {len(sampled)} (downsampled from {len(raw_frames)})."
    )

    try:
        provider = get_provider()
        result = await provider.vision(
            images=sampled,
            instruction=instruction,
            expect_json=True,
        )
    except LLMError as e:
        log.warning("Recipe generation failed: %s", e)
        raise HTTPException(status_code=502, detail=str(e)) from e

    recipe = _coerce_recipe_json(result.text, fallback_name=session_name)
    return {"recipe": recipe, "rawText": result.text}


def _down_sample(items: list[VisionImage], k: int) -> list[VisionImage]:
    if len(items) <= k:
        return items
    step = (len(items) - 1) / (k - 1)
    return [items[round(i * step)] for i in range(k)]


def _safe_int(value: Any, default: int) -> int:
    """Coerce model-supplied ordinals to int, falling back when the model
    emits ``null``, a non-numeric string, or anything else weird."""
    if value is None:
        return default
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _coerce_recipe_json(text: str, *, fallback_name: str) -> dict[str, Any]:
    """Be liberal in what we accept from the model.

    Models occasionally wrap JSON in ``` fences or trailing prose. We strip
    those and then validate the minimum schema the Android client expects.
    """
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```[a-zA-Z0-9]*\n?", "", cleaned)
        cleaned = re.sub(r"\n?```\s*$", "", cleaned)
    try:
        obj = json.loads(cleaned)
    except json.JSONDecodeError as exc:
        # Last-resort: find the first {...} blob.
        m = re.search(r"\{.*\}", cleaned, flags=re.DOTALL)
        if not m:
            raise HTTPException(
                status_code=502,
                detail=f"Model did not return JSON: {cleaned[:300]}",
            ) from exc
        try:
            obj = json.loads(m.group(0))
        except json.JSONDecodeError as exc2:
            raise HTTPException(
                status_code=502,
                detail=f"Model did not return valid JSON: {cleaned[:300]}",
            ) from exc2

    if not isinstance(obj, dict):
        raise HTTPException(status_code=502, detail="Model JSON not an object")

    name = (obj.get("name") or fallback_name or "Untitled").strip() or "Untitled"
    description = obj.get("description")
    steps_raw = obj.get("steps") or []
    if not isinstance(steps_raw, list):
        raise HTTPException(status_code=502, detail="steps is not a list")

    steps: list[dict[str, Any]] = []
    for idx, step in enumerate(steps_raw):
        if not isinstance(step, dict):
            continue
        intent = (step.get("intent") or step.get("description") or "").strip()
        if not intent:
            continue
        steps.append(
            {
                "ordinal": _safe_int(step.get("ordinal"), idx),
                "intent": intent,
                "expectAfter": step.get("expectAfter") or step.get("expect_after"),
                "notes": step.get("notes"),
                "actionHint": (step.get("actionHint") or step.get("action_hint") or "tap"),
            }
        )

    if not steps:
        raise HTTPException(status_code=502, detail="Model returned no usable steps")

    return {"name": name, "description": description, "steps": steps}
