"""FastAPI entrypoint for the AI Game Bot backend."""

from __future__ import annotations

import logging

from fastapi import FastAPI

from app.config import settings
from app.routers import llm, recipe

logging.basicConfig(level=settings.log_level)

app = FastAPI(title="AI Game Bot Backend", version="0.2.0")
app.include_router(llm.router)
app.include_router(recipe.router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "provider": settings.llm_format}
