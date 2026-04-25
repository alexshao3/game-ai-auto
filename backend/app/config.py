"""Runtime configuration loaded from environment variables."""

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Backend settings.

    Values come from env vars (or a `.env` file in the backend directory).
    Keep this object small — anything secret stays in env, never in code.
    """

    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.5-flash-lite"
    backend_host: str = "0.0.0.0"
    backend_port: int = 8000
    log_level: str = "INFO"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


settings = Settings()
