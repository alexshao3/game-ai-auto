"""Build a :class:`VisionProvider` from settings."""

from __future__ import annotations

from functools import lru_cache

from app.config import settings
from app.llm.anthropic import AnthropicProvider
from app.llm.base import LLMError, VisionProvider
from app.llm.gemini import GeminiProvider
from app.llm.openai_compat import OpenAICompatProvider


@lru_cache(maxsize=1)
def get_provider() -> VisionProvider:
    fmt = settings.llm_format.lower().strip()
    if fmt == "anthropic":
        return AnthropicProvider(
            api_key=settings.llm_api_key,
            model_id=settings.llm_model_id or "claude-3-5-haiku-20241022",
            base_url=settings.llm_base_url,
        )
    if fmt == "openai":
        return OpenAICompatProvider(
            api_key=settings.llm_api_key,
            model_id=settings.llm_model_id or "gpt-4o-mini",
            base_url=settings.llm_base_url or "https://api.openai.com/v1",
        )
    if fmt == "gemini":
        return GeminiProvider(
            api_key=settings.llm_api_key or settings.gemini_api_key,
            model_id=settings.llm_model_id or settings.gemini_model,
            base_url=settings.llm_base_url,
        )
    raise LLMError(f"Unknown LLM_FORMAT: {settings.llm_format!r}")
