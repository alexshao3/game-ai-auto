"""Provider-agnostic LLM vision interface.

Each adapter implements :class:`VisionProvider` so the rest of the app talks
to a single shape regardless of which AI service is configured.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


class LLMError(RuntimeError):
    """Raised when the underlying provider returns an error or bad response."""


@dataclass(frozen=True)
class VisionImage:
    """A single image to ship to the model."""

    data: bytes
    mime_type: str = "image/jpeg"


@dataclass(frozen=True)
class VisionResult:
    """Provider response normalised into text. Callers parse JSON themselves."""

    text: str
    raw: dict | None = None


class VisionProvider(Protocol):
    """Single-method protocol for vision calls.

    Implementations accept one or more images plus a natural-language
    instruction and return the model's textual response.
    """

    name: str
    model_id: str

    async def vision(
        self,
        images: list[VisionImage],
        instruction: str,
        *,
        expect_json: bool,
    ) -> VisionResult: ...
