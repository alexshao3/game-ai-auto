# Kế hoạch v3 — Generic AI Game Bot Framework (Programming by Demonstration)

> **Cập nhật scope (v3):**
> - Core engine **generic** — không gắn cứng vào 1 game
> - User **dạy bằng demonstration**: record 1 lần → AI sinh "Task Recipe" → replay
> - Validate trên **Tam Quốc Huyễn Tướng VNG** làm game đầu tiên
> - **Backend proxy** quản lý LLM API key (user không phải nhập)
> - Distribution: **APK download trực tiếp**

---

## 1. Triết lý thiết kế: Programming by Demonstration (PbD)

Thay vì hard-code task cho từng game, app cho phép user **dạy bằng cách làm thật**:

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│ 1. RECORD       │   →     │ 2. UNDERSTAND   │   →     │ 3. REPLAY       │
│ User làm task   │         │ AI phân tích   │         │ AI tự chạy lại  │
│ 1 lần như bình  │         │ → sinh recipe  │         │ với VLM adapt   │
│ thường          │         │ (intent-based) │         │ UI thay đổi     │
└─────────────────┘         └─────────────────┘         └─────────────────┘
```

### Vì sao không hard-code?
- Mỗi game khác nhau → viết task riêng tốn thời gian
- Game update UI → bot hỏng
- User là người **biết task nào quan trọng** với họ — họ tự định nghĩa được

### Vì sao cần AI hiểu intent?
- Pixel-perfect replay sẽ hỏng khi:
  - Độ phân giải khác
  - Có popup mới (sự kiện, ad)
  - UI animation chậm hơn
  - Game update di chuyển nút
- Intent-based replay (AI hiểu "tap nút Nhận quà") thì robust hơn nhiều

---

## 2. Kiến trúc tổng thể

```
┌──────────────────────────────────────────────────────────────┐
│                     ANDROID APP                              │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │              UI LAYER (Compose)                      │    │
│  │  - Task list, Record screen, Run screen, Settings    │    │
│  └──────────────────────────────────────────────────────┘    │
│                          │                                   │
│  ┌──────────────────────────────────────────────────────┐    │
│  │              ORCHESTRATOR                            │    │
│  │  ┌────────────┐         ┌────────────┐               │    │
│  │  │ Recorder   │         │ Executor   │               │    │
│  │  │ - capture  │         │ - capture  │               │    │
│  │  │ - log tap  │         │ - VLM step │               │    │
│  │  │ - sequence │         │ - dispatch │               │    │
│  │  │ - generate │         │ - verify   │               │    │
│  │  │   recipe   │         │ - recover  │               │    │
│  │  └────────────┘         └────────────┘               │    │
│  └──────────────────────────────────────────────────────┘    │
│              │                            │                  │
│  ┌─────────────────────┐    ┌─────────────────────────┐      │
│  │ CORE SERVICES       │    │ STORAGE                 │      │
│  │ - CaptureService    │    │ - Recipes (Room)        │      │
│  │ - AccessibilityServ │    │ - Run history           │      │
│  │ - InputMonitor      │    │ - Recordings (encrypted)│      │
│  │ - BackendClient     │    │ - Settings              │      │
│  └─────────────────────┘    └─────────────────────────┘      │
└──────────────────────────────────────────────────────────────┘
                              │
                  HTTPS  ◀────┘
                              │
                              ▼
              ┌─────────────────────────────┐
              │   BACKEND PROXY (FastAPI)   │
              │   - auth (device token)     │
              │   - rate limit per user     │
              │   - LLM API call (Gemini)   │
              │   - shared cache            │
              │   - usage tracking          │
              └─────────────────────────────┘
                              │
                              ▼
                       Gemini 2.5 Flash API
```

---

## 3. Recorder Mode — chi tiết

### Flow user dạy task
```
1. Bấm "Tạo task mới" → đặt tên "Nhận quà đăng nhập"
2. Bấm "Bắt đầu record"
3. App switch sang game (hoặc user tự mở)
4. App overlay 1 floating button (đỏ = recording)
5. User làm task như bình thường:
   - Tap vào icon Phần thưởng
   - Tap Nhận tất cả
   - Tap OK đóng popup
   - Quay về home
6. (Optional) User nói voice note: "đây là bước cuối"
7. Bấm Stop trên floating button
8. App show: "Đang phân tích recording..."
9. App show recipe đã sinh:
   ┌────────────────────────────────┐
   │ Task: Nhận quà đăng nhập       │
   │ ─────────────────────────────  │
   │ Bước 1: Mở màn hình Phần thưởng│
   │   └─ tap icon góc phải         │
   │ Bước 2: Nhận tất cả            │
   │   └─ tap nút "Nhận tất cả"     │
   │ Bước 3: Đóng popup             │
   │   └─ tap "OK"                  │
   │ Bước 4: Verify quay về home    │
   │ ─────────────────────────────  │
   │ [Edit] [Test run] [Save]       │
   └────────────────────────────────┘
```

### Record được những gì?
- **Screenshot** mỗi 500ms (hoặc khi có tap)
- **Tap/swipe events** từ AccessibilityService (`onAccessibilityEvent` + InputManager) — **lưu ý:** đây là điểm khó kỹ thuật, sẽ giải thích bên dưới
- **Window/activity changes** (giúp xác định "đang ở màn nào")
- **Optional voice notes** (Android STT API)
- **Optional manual annotations** (user tap "đây là bước verify")

### Vấn đề kỹ thuật: Capture user input
Android không cho app thường đọc được tap trong app khác. Có 2 hướng:

**Hướng 1: AccessibilityService events**
- `TYPE_VIEW_CLICKED`, `TYPE_VIEW_SCROLLED` — chỉ hoạt động với app dùng View native (Tam Quốc Huyễn Tướng dùng Unity → KHÔNG có view event!)
- → Hướng này **không dùng được** với hầu hết game

**Hướng 2: Overlay transparent + manual replay**
- App overlay 1 lớp invisible bắt touch event
- User tap → app log → app forward tap xuống game (qua AccessibilityService.dispatchGesture)
- **Vấn đề:** UX bất tiện — user phải tap qua overlay, có thể delay cảm thấy được
- **Giải pháp:** chỉ overlay ở chế độ Record, bình thường tắt

**Hướng 3: Periodic screenshot diff** (KHUYẾN NGHỊ)
- Không cần bắt tap real-time
- Capture 2-3 frame/giây
- Sau khi record xong, AI phân tích sequence: "frame 5 vs frame 6 thay đổi gì? → có vẻ user vừa tap chỗ ABC"
- Dùng VLM để hiểu user đã làm gì chỉ qua screenshot delta
- **Ưu điểm:** không cần overlay, UX tự nhiên
- **Nhược điểm:** không biết chính xác tọa độ tap, nhưng vì recipe lưu intent chứ không lưu tọa độ → OK

→ **Đề xuất:** dùng **Hướng 3** (screenshot-based reconstruction). Đơn giản hơn, robust hơn.

### Recipe generation — VLM call
Sau khi record, gửi sequence frames đến Gemini với prompt:
```
Đây là chuỗi N screenshot ghi lại 1 task user đã làm trong game [GAME_NAME].
Phân tích sequence và sinh ra "task recipe" gồm các bước intent-based.

Mỗi bước phải có:
- description: ngắn gọn (vd. "Mở menu Phần thưởng")
- target_element: mô tả element user đã tương tác (vd. "icon hộp quà ở góc trên phải")
- action_type: tap | swipe | wait | back | verify
- expected_screen_after: mô tả ngắn màn hình kế tiếp (vd. "panel danh sách phần thưởng")
- success_criteria: dấu hiệu nhận biết bước thành công

Trả về JSON theo schema sau:
{
  "task_name": "...",
  "game": "...",
  "steps": [{ ... }]
}
```

---

## 4. Executor Mode — chi tiết

### Flow chạy 1 task
```
For each step in recipe:
  1. Capture màn hình hiện tại
  2. Hash → check cache: đã có quyết định cho screen này + step này chưa?
  3. (cache miss) Gọi VLM:
     - Input: ảnh hiện tại + step.description + step.target_element
     - Output: JSON {"found": true, "x_pct": 78, "y_pct": 12, "action": "tap", "confidence": 0.9}
  4. Nếu confidence < 0.5 → recovery (close popups, scroll, back)
  5. Dispatch gesture (tap/swipe) qua AccessibilityService
  6. Wait expected delay (động dựa vào loading)
  7. Verify: capture lại, gọi VLM với expected_screen_after
  8. Nếu verify pass → step tiếp; fail → retry hoặc abort
End

→ Notify user (success/fail summary)
```

### Cache layer (giảm 70-80% API call)
```
Key: pHash(screen) + step_id
Value: {
  decision (action + coordinates),
  confidence,
  ttl
}
```
- Sau lần chạy đầu thành công, hầu hết screen đã cache
- Lần chạy sau hầu như không gọi API (trừ khi screen thay đổi)
- Cache shared qua backend (nếu nhiều user cùng game) → thậm chí lần đầu cũng có hit

### Recovery mechanics
- **Stuck detection:** 3 frame liên tiếp giống nhau, không phải expected screen
- **Popup detector:** mỗi bước, gọi VLM check "có popup lạ không?" — nếu có, gọi VLM tìm nút Đóng/X
- **Force abort:** user có nút Stop nổi để dừng bất cứ lúc nào
- **Game crash detection:** nếu Tam Quốc Huyễn Tướng package không còn foreground → abort

---

## 5. Backend Proxy

### Vai trò
- Quản lý LLM API key (user không phải nhập)
- Rate limit per user (chống abuse)
- Shared cache (nhiều user cùng game share decision)
- Usage tracking để billing/giới hạn
- Có thể swap LLM provider mà không cần update app

### Stack đề xuất
- **FastAPI** (Python) — quen thuộc, dễ deploy
- **Postgres** — user, usage, billing
- **Redis** — rate limit, cache hot
- **Auth:** device-bound JWT (đăng ký 1 lần, app lưu token)
- **Deploy:** Fly.io / Railway / VPS
- **Cost:** $5-20/tháng cho VPS đủ chạy vài trăm user

### API endpoints
```
POST /v1/auth/register     → token (device-bound)
POST /v1/llm/vision        → forward to Gemini, return JSON
POST /v1/cache/lookup      → check shared cache
POST /v1/cache/contribute  → user share decision back
GET  /v1/recipes/community → (sau) recipes do người khác share
POST /v1/recordings/upload → (optional) gửi recording để improve
```

### Bảo mật
- Rate limit: 200 calls/user/ngày (free tier), nhiều hơn = trả tiền
- Hash device fingerprint, ban abuse
- Secret rotation cho LLM API key
- Không log raw screenshot (privacy)

---

## 6. Tech Stack Android (cập nhật)

| Layer | Công nghệ | Notes |
|---|---|---|
| Ngôn ngữ | Kotlin 2.0 | |
| UI | Jetpack Compose + Material 3 | |
| DI | Hilt | |
| Async | Coroutines + Flow | |
| HTTP | Ktor Client | gọi backend |
| JSON | kotlinx.serialization | |
| DB | Room | recipes, history |
| Image | Bitmap, libwebp compress | giảm payload tới backend |
| OCR (optional) | ML Kit Text Recognition | on-device, free |
| STT (optional) | Android SpeechRecognizer | voice annotation |
| Logging | Timber | |
| Min SDK | 26 | Android 8.0+ |
| Target SDK | 34 | Android 14 |

### Permissions
- `BIND_ACCESSIBILITY_SERVICE` — gesture dispatch
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `POST_NOTIFICATIONS` (Android 13+)
- `INTERNET` — backend
- `RECORD_AUDIO` (optional) — voice note
- `SYSTEM_ALERT_WINDOW` — floating control button

---

## 7. Cấu trúc repo (mono-repo)

```
ai-game-bot/
├── android/                       # Android app
│   ├── app/
│   │   ├── src/main/java/.../gamebot/
│   │   │   ├── ui/
│   │   │   │   ├── home/
│   │   │   │   ├── tasks/
│   │   │   │   ├── record/
│   │   │   │   ├── run/
│   │   │   │   └── settings/
│   │   │   ├── service/
│   │   │   │   ├── BotForegroundService.kt
│   │   │   │   ├── BotAccessibilityService.kt
│   │   │   │   └── CaptureService.kt
│   │   │   ├── core/
│   │   │   │   ├── capture/
│   │   │   │   ├── recorder/
│   │   │   │   ├── executor/
│   │   │   │   ├── recipe/
│   │   │   │   ├── action/
│   │   │   │   └── cache/
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   ├── api/
│   │   │   │   └── prefs/
│   │   │   └── di/
│   │   ├── src/main/AndroidManifest.xml
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── backend/                       # FastAPI proxy
│   ├── app/
│   │   ├── main.py
│   │   ├── routers/
│   │   ├── llm/
│   │   ├── cache/
│   │   └── auth/
│   ├── tests/
│   ├── pyproject.toml
│   └── Dockerfile
├── docs/
│   ├── architecture.md
│   ├── prompts/
│   └── tasks/
└── README.md
```

---

## 8. Roadmap chi tiết (~10-12 tuần MVP)

### **Phase 0 — Hạ tầng cơ bản (2 tuần)**
- [ ] Setup Android project (Kotlin/Compose/Hilt/Room)
- [ ] MediaProjection capture → Bitmap, lưu được file
- [ ] **TEST FLAG_SECURE trên Tam Quốc Huyễn Tướng** ← CRITICAL trước khi đi tiếp
- [ ] AccessibilityService → dispatchGesture(tap, swipe) ổn định
- [ ] Foreground service không bị Android giết
- [ ] Floating button overlay (start/stop)
- [ ] Backend skeleton: FastAPI + 1 endpoint forward Gemini
- [ ] App gọi backend → backend gọi Gemini → trả về JSON
- [ ] **Mốc:** demo capture → gọi VLM → tap đúng tọa độ VLM trả về

### **Phase 1 — Recorder MVP (3 tuần)**
- [ ] Recording service: capture frame mỗi 500ms khi record
- [ ] UI: chế độ record (tạo task, đặt tên, start/stop)
- [ ] (Optional) Voice annotation
- [ ] Storage: lưu recording (encrypted) trong app
- [ ] Recipe generator: gửi sequence → VLM → JSON recipe
- [ ] UI: hiển thị recipe đã sinh, cho user review/edit
- [ ] **Mốc:** record được 1 task trên Tam Quốc Huyễn Tướng → recipe trông hợp lý

### **Phase 2 — Executor MVP (3 tuần)**
- [ ] Executor engine: chạy recipe step-by-step
- [ ] VLM step caller (gọi Gemini với current screen + step intent)
- [ ] Cache layer (pHash + Room)
- [ ] Gesture dispatcher với jitter + Bezier curve
- [ ] Stuck detection + popup recovery
- [ ] Verify mechanism
- [ ] **Mốc:** chạy 1 task daily trên Tam Quốc Huyễn Tướng thành công 10 lần liên tiếp

### **Phase 3 — UI hoàn chỉnh + multi-task (2 tuần)**
- [ ] Task list UI
- [ ] Run all selected
- [ ] Scheduler (WorkManager) — auto chạy theo lịch
- [ ] Run history & logs
- [ ] Settings (tốc độ, log retention)
- [ ] Disclaimer + ToS warning
- [ ] **Mốc:** record 5 task, chạy all daily auto trong 7 ngày liên tiếp ổn định

### **Phase 4 — Backend hoàn chỉnh + APK release (2 tuần)**
- [ ] Auth (device token)
- [ ] Rate limit
- [ ] Shared cache layer
- [ ] Usage tracking
- [ ] Deploy backend (Fly.io)
- [ ] Sign APK release
- [ ] Landing page download APK
- [ ] **Mốc:** APK public download, backend xử lý được nhiều user

### **Phase 5+ — Tính năng nâng cao**
- Recipe sharing community
- Multi-game cùng lúc
- Cloud sync recipes
- Pro version (unlimited API)

---

## 9. Rủi ro với approach generic + PbD

| Rủi ro | Giảm thiểu |
|---|---|
| **PbD khó hơn nhiều so với hard-code task** | Bắt đầu với task đơn giản. Nếu PbD fail, fallback cho phép user edit recipe thủ công |
| **VLM hiểu sai intent từ screenshots** | Cho phép user edit recipe sau khi record, voice annotation |
| **FLAG_SECURE trên Tam Quốc Huyễn Tướng** | Verify Phase 0 ngày đầu |
| **Anti-cheat phát hiện Accessibility Service** | Disable khi không chạy bot, dùng acc phụ |
| **Backend cost vượt** nếu user nhiều | Rate limit chặt, free tier giới hạn 200 calls/ngày |
| **Recipe không generalize tốt** giữa các trường hợp khác nhau | Cache + recovery + fallback recipe edit |
| **Scope creep** vì generic engine ambitious | Validate trên 1 game trước, không vội mở rộng |

---

## 10. Tóm tắt khác biệt v3 vs v2

| | v2 | v3 |
|---|---|---|
| Scope | 1 game (Tam Quốc HT) | Generic, validate trên Tam Quốc HT |
| Cách định nghĩa task | Hard-code Kotlin (TaskGraph) | User record demonstration → AI sinh recipe |
| LLM API | App → Gemini trực tiếp | App → Backend proxy → Gemini |
| API key | User nhập hoặc app builtin | App builtin (qua backend) |
| Scope codebase | Android only | Android + Backend |
| Thời gian MVP | 6-8 tuần | 10-12 tuần |
| Rủi ro chính | FLAG_SECURE, anti-cheat | + PbD difficulty, generalization quality |

---

## 11. 3 câu hỏi cuối trước Phase 0

1. **Voice annotation:** muốn không?
2. **Recipe review:** user phải review trước khi run lần đầu?
3. **OK bắt đầu Phase 0 ngay?** Sẽ scaffold Android + backend + verify FLAG_SECURE
