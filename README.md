# AI Game Bot

Generic AI-driven game automation framework for Android.

User demonstrates a task once → AI understands intent → bot replays and adapts to UI changes.

**Status:** Phase 0 — infrastructure scaffold.

## Architecture

```
[Android App] → MediaProjection (capture screen)
              → AccessibilityService (dispatch tap/swipe)
              → Backend Proxy (FastAPI)
                  → Gemini Vision API
```

See [`docs/`](docs/) for the full plan.

## Components

- `android/` — Android app (Kotlin + Jetpack Compose)
- `backend/` — FastAPI proxy that brokers calls to Gemini

## Phase 0 milestone

Verify on a real device that:

1. MediaProjection can capture the screen of the target game (no `FLAG_SECURE` block).
2. AccessibilityService can dispatch taps to the game.
3. App → backend → Gemini → JSON response → tap loop works end-to-end.

Target game for validation: **Tam Quốc Huyễn Tướng VNG**.

## Quick start

### Backend

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -e .
cp .env.example .env  # then fill GEMINI_API_KEY
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### Android

Open `android/` in Android Studio, or build from CLI:

```bash
cd android
./gradlew :app:assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

Configure backend URL in app: edit `BuildConfig.BACKEND_URL` or override via the in-app Settings screen.
