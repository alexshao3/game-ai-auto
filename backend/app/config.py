"""Runtime configuration loaded from environment variables."""

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Backend settings.

    Values come from env vars (or a `.env` file in the backend directory).
    Keep this object small — anything secret stays in env, never in code.

    LLM provider is pluggable. Set ``llm_format`` to one of ``anthropic``,
    ``openai``, or ``gemini``; the matching ``llm_*`` knobs below are then
    consumed by ``app.llm.factory.get_provider()``.
    """

    # ── Generic LLM provider (preferred) ──────────────────────────────────
    llm_format: str = "anthropic"  # one of: anthropic | openai | gemini
    llm_base_url: str = ""
    llm_api_key: str = ""
    llm_model_id: str = ""

    # ── Legacy Gemini-specific knobs (still honoured if llm_format=gemini) ─
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.5-flash-lite"

    # ── Server ────────────────────────────────────────────────────────────
    backend_host: str = "0.0.0.0"
    backend_port: int = 8000
    log_level: str = "INFO"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


settings = Settings()
