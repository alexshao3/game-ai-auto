# AI Game Bot Backend

FastAPI proxy that brokers calls from the Android app to Gemini Vision API.

## Why a proxy?

- Keep `GEMINI_API_KEY` off user devices.
- Apply rate limits and shared caching.
- Swap LLM providers without releasing a new app version.

## Endpoints (Phase 0)

- `GET  /health` — liveness probe.
- `POST /v1/llm/vision` — multipart upload of a screenshot + JSON instruction; returns Gemini response.

## Run locally

```bash
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env  # then fill GEMINI_API_KEY
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Test from the Android emulator at `http://10.0.2.2:8000`.

## Roadmap

- Phase 1: device-bound auth, per-user rate limiting.
- Phase 2: shared screen-hash cache backed by Redis.
- Phase 3: usage tracking and tiered billing.
