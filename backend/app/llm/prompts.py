# ruff: noqa: E501
"""Prompts the backend ships to the LLM."""

from __future__ import annotations

RECIPE_FROM_FRAMES_PROMPT = """\
You are an automation engineer analysing a sequence of frames captured at
~2 FPS while a user demonstrated a single task inside an Android app or
game. Your job is to reconstruct the user's INTENT for each step they
performed, not the raw pixel coordinates of their taps.

Frames are provided in chronological order. Compare consecutive frames:
where the UI clearly changes (a panel opens, a popup appears, a screen
transitions, a number changes), infer that the user performed a single
action just before the change. Ignore animations and transient effects;
group several visually-similar frames into one logical step.

Output a single JSON object with this exact schema, and nothing else:

{
  "name": "<short task name in the user's language, e.g. 'Nhận quà đăng nhập'>",
  "description": "<one-line summary of the whole task>",
  "steps": [
    {
      "ordinal": <0-based integer>,
      "intent": "<natural-language description of what the user wants done; refer to UI elements by their visible label or icon, never by pixel coordinates>",
      "expectAfter": "<short description of the screen the user expects after this step>",
      "actionHint": "tap" | "swipe" | "longPress" | "wait",
      "notes": "<optional clarification or null>"
    }
  ]
}

Rules:
- Steps must be EXECUTABLE later by a different agent that only sees the
  current screen, so describe targets by their visible text/icon, not by
  position.
- Prefer at most 8-12 steps; merge redundant taps and animation frames.
- Do NOT invent steps that have no visual evidence in the frames.
- Use the language the user appears to be using in the UI (Vietnamese is
  fine if the game is Vietnamese).
"""
