# Kế hoạch v2 — AI Game Bot Android-native cho Tam Quốc Huyễn Tướng VNG

> **Cập nhật scope (v2):**
> - **Game:** Tam Quốc Huyễn Tướng VNG (chiến thuật đấu tướng, server VN — VNG phát hành)
> - **Platform:** Android-native app (1 APK, không cần PC, không root)
> - **AI:** Vision-Language Model qua API (không chạy on-device)
> - **UX:** User chọn task từ list trong app → bot tự chạy
> - **End-product:** App cho người khác dùng (option B)

---

## 1. Đặc điểm game & vì sao approach này khả thi

Tam Quốc Huyễn Tướng VNG là game **chiến thuật đấu tướng (auto-battler)**:
- Chiến đấu **auto** sau khi setup đội hình → không cần real-time control
- Daily tasks chủ yếu là **menu-driven**: vào màn này, bấm nút kia, nhận quà, quét hầm, mua đồ, v.v.
- Latency 1-3s/action (do gọi LLM API) **hoàn toàn chấp nhận được** với loại game này
- Đây là game ra mắt 2025, VNG mới phát hành tại VN

**Daily tasks điển hình** (sẽ refine sau khi xem game thật):
1. Nhận quà đăng nhập
2. Nhận tài nguyên AFK / sản lượng từ thành
3. Hoàn thành nhiệm vụ ngày (đánh PvE x lần, dùng nộ x lần, v.v.)
4. Quét hầm/dungeon (sweep)
5. Đấu trường (arena) — đánh đủ số trận miễn phí
6. Mua hàng miễn phí trong shop
7. Tặng/nhận tâm ý từ liên minh (guild)
8. Triệu hồi miễn phí
9. Nhận quà sự kiện đang diễn ra

→ Hầu hết là **flow tuyến tính, có thể mô hình hóa bằng state machine + VLM hỗ trợ.**

---

## 2. Kiến trúc Android-native

```
┌──────────────────────────────────────────────────────────┐
│              APP CỦA TA (Foreground Service)             │
│                                                          │
│  ┌────────────┐   ┌──────────────┐   ┌──────────────┐    │
│  │ UI         │──▶│ Task         │──▶│ Action       │    │
│  │ (Compose)  │   │ Orchestrator │   │ Executor     │    │
│  │ - chọn task│   │ (state mach.)│   │              │    │
│  │ - schedule │   └──────┬───────┘   └──────┬───────┘    │
│  │ - log      │          │                  │            │
│  └────────────┘          ▼                  ▼            │
│                   ┌──────────────┐   ┌──────────────┐    │
│  ┌────────────┐   │ Perception   │   │ Accessibility│    │
│  │ Capture    │──▶│  - cache     │   │ Service      │    │
│  │ Service    │   │  - VLM call  │   │ (dispatch    │    │
│  │ (MediaProj)│   │  - OCR(opt)  │   │  Gesture)    │    │
│  └────────────┘   └──────┬───────┘   └──────┬───────┘    │
│                          │                  │            │
└──────────────────────────┼──────────────────┼────────────┘
                           │                  │
                ┌──────────▼─────────┐        │
                │ LLM API (Gemini    │        │
                │ Flash / GPT-4o-mini│        │
                │ via HTTPS)         │        │
                └────────────────────┘        │
                                              ▼
                                    ┌────────────────────┐
                                    │  Game: Tam Quốc    │
                                    │  Huyễn Tướng VNG   │
                                    └────────────────────┘
```

### 2.1. Capture: MediaProjection API
- Không cần root, không cần ADB
- User cấp quyền 1 lần khi bật bot → `MediaProjection` cho phép capture màn hình
- Output: `Image` từ `ImageReader` → convert sang Bitmap → JPEG để gửi LLM
- **Risk:** nếu game bật `FLAG_SECURE` trên Activity → screen capture bị đen. Cần verify thực tế (xem mục Risk).
- Chạy trong **Foreground Service** với notification thường trực (Android yêu cầu)

### 2.2. Action: AccessibilityService
- `AccessibilityService.dispatchGesture()` cho phép tap/swipe/long-press chính xác
- Không cần root
- User cấp quyền Accessibility trong Settings (1 lần)
- Hỗ trợ multi-touch, gesture có path (Bezier curve để giả người)
- **Khuyến nghị:** thêm jitter ±3-8px và delay random 100-400ms để giảm nguy cơ phát hiện bot

### 2.3. Perception: VLM-first
Vì user chọn dùng LLM API và budget thoải mái, simplify pipeline:

**Flow chính:**
```
Bitmap màn hình
    │
    ▼
Cache check (perceptual hash) — nếu screen giống hệt screen đã biết → reuse decision
    │ (cache miss)
    ▼
Resize/compress (768px max → ~50KB JPEG)
    │
    ▼
Gọi VLM API với structured prompt
    │
    ▼
Parse JSON response → action(s)
```

**KHÔNG cần** YOLO/training custom model trong giai đoạn đầu vì:
- Tam Quốc Huyễn Tướng là menu-driven → VLM đủ tốt
- Tránh phải thu thập dataset & train (tốn nhiều thời gian)
- Cache + prompt tối ưu sẽ giảm cost xuống rất thấp

**Có thể thêm OCR** (ML Kit on-device, free) cho:
- Đọc số lượng/giá trị numeric chính xác
- Verify text trên nút trước khi tap
- Detect screen ID nhanh (không cần gọi VLM mỗi lần)

### 2.4. Decision: Hybrid State Machine + VLM

**State Machine cho mỗi daily task:**
```kotlin
TaskGraph("daily_login") {
  step("open_main_menu") { 
    expect = ScreenId.MAIN
    // không action nếu đã ở main
  }
  step("click_reward_icon") {
    findAndTap("reward_icon")  // VLM tìm icon
    expect = ScreenId.REWARD_PANEL
  }
  step("claim_all") {
    findAndTap(text = "Nhận tất cả")
  }
  step("close_popups") {
    closeAnyPopup() // loop until back to expected screen
  }
  step("verify") {
    expect = ScreenId.MAIN
  }
}
```

**Mỗi step gọi VLM với prompt như:**
```
Bạn là AI điều khiển game Tam Quốc Huyễn Tướng.
Task hiện tại: nhận quà đăng nhập, đang ở bước "click_reward_icon".
Yêu cầu: tìm và trả về tọa độ icon "Phần thưởng" hoặc "Quà" trên màn hình.
Output JSON:
{
  "current_screen": "main_menu" | "loading" | "popup" | "unknown",
  "found_target": true/false,
  "action": {
    "type": "tap" | "swipe" | "wait" | "back",
    "x": int (0-100, % chiều ngang),
    "y": int (0-100, % chiều dọc),
    ...
  },
  "confidence": 0.0-1.0,
  "reason": "..."
}
```

**Tọa độ dùng % thay vì pixel** → portable giữa các độ phân giải.

**Recovery:**
- Nếu screen không khớp expected sau X giây → gọi VLM với prompt "đang ở đâu, cần làm gì để quay về home"
- Stuck detection: 3 frame liên tiếp giống nhau → trigger recovery

### 2.5. Caching aggressive (giảm API cost)

```
ScreenHash (pHash 64-bit) → CachedDecision
```
- Nếu hash của frame mới gần giống (Hamming distance < 5) → reuse decision cũ
- TTL 24h, invalidate khi game update
- Persist trong Room DB → giữ qua các session

**Ước tính:** sau khi chạy 1 lần đầy đủ daily, ~80% screen đã được cache → chỉ ~20% phải gọi API ở các lần sau.

---

## 3. Tech Stack Android

| Layer | Công nghệ |
|---|---|
| Ngôn ngữ | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Async | Coroutines + Flow |
| DI | Hilt |
| HTTP | Ktor Client (hoặc Retrofit + OkHttp) |
| JSON | kotlinx.serialization |
| DB | Room |
| Image | Bitmap + libwebp (compress nhanh) |
| OCR (optional) | Google ML Kit Text Recognition (on-device, free) |
| Logging | Timber |
| Build | Gradle KTS |
| Min SDK | 26 (Android 8.0) — đủ rộng cho gaming users |
| Target SDK | 34 (Android 14) |

### Permissions cần xin
- `BIND_ACCESSIBILITY_SERVICE` — cho gesture
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `POST_NOTIFICATIONS` (Android 13+)
- `INTERNET` — cho LLM API
- `SYSTEM_ALERT_WINDOW` (optional) — cho overlay điều khiển

---

## 4. LLM API: lựa chọn & ước tính chi phí

### Lựa chọn (xếp theo ưu tiên)

| Model | $/image (768px) | Latency | Vision quality | Ghi chú |
|---|---|---|---|---|
| **Gemini 2.5 Flash-Lite** | ~$0.0003 | ~1s | Tốt | **Ưu tiên #1** — rẻ, nhanh, đủ dùng cho UI game |
| Gemini 2.5 Flash | ~$0.001 | ~1.5s | Rất tốt | Fallback khi Flash-Lite fail |
| GPT-4o-mini | ~$0.001-0.002 | ~1.5s | Tốt | Backup vendor |
| Claude Haiku | ~$0.0016 | ~1.5s | Tốt | Backup |

### Ước tính chi phí thực tế

**Giả sử 1 user chạy daily đầy đủ:**
- ~10 task × ~10 step/task = ~100 action/ngày
- Cache hit rate ~70-80% sau lần đầu → ~25 API call/ngày
- Cost với Gemini 2.5 Flash-Lite: 25 × $0.0003 = **$0.0075/ngày = ~$0.23/tháng/user**

→ Cực rẻ. Có thể bao trọn gói hoặc free.

### Prompt engineering strategy
- **System prompt** dài, chi tiết về game (tải 1 lần, cache trên server nếu có context caching)
- **User prompt** ngắn: chỉ task hiện tại + bước hiện tại + ảnh
- **Structured output** (JSON schema) → parse chắc chắn
- **Few-shot examples** trong system prompt: 2-3 ví dụ "screen này thì action này"
- **Reasoning traces** trong response để debug

---

## 5. UI app — flow user

```
[Splash]
   │
   ▼
[Onboarding: cấp 3 quyền]
  - MediaProjection
  - Accessibility Service
  - Notifications
   │
   ▼
[Main Screen]
  ├─ Game đang link: Tam Quốc Huyễn Tướng VNG
  ├─ [Section: Daily Tasks]
  │    □ Nhận quà đăng nhập       [auto] [chạy]
  │    □ Nhận sản lượng AFK       [auto] [chạy]
  │    □ Quét hầm                 [auto] [chạy]
  │    □ Đấu trường (5 trận)      [auto] [chạy]
  │    □ Triệu hồi miễn phí       [auto] [chạy]
  │    ...
  ├─ [Run all selected]
  ├─ [Schedule] (tự chạy hàng ngày lúc X giờ)
  │
  ├─ [Tab: Logs / Lịch sử chạy]
  ├─ [Tab: Settings]
  │    - LLM API key (user nhập, hoặc dùng key của app)
  │    - Tốc độ (chậm/vừa/nhanh — random delay khác nhau)
  │    - Bật/tắt OCR
  │    - Export log
  └─ [Tab: About / Disclaimer]
```

**Trải nghiệm chạy:**
1. User mở app, chọn 5 task
2. Bấm "Run all"
3. App tự bật Tam Quốc Huyễn Tướng (qua Intent) → game load
4. App overlay 1 floating button (có thể stop) → bot chạy nền
5. Bot tự navigate, tap, hoàn thành lần lượt
6. Xong → notification "Hoàn thành 5/5 tasks"

---

## 6. Roadmap Phase 0 → MVP

### **Phase 0 — Hạ tầng (1.5-2 tuần)**
- [ ] Tạo Android project Kotlin/Compose
- [ ] Cài đặt MediaProjection capture → lưu được Bitmap
- [ ] **Verify FLAG_SECURE** trên Tam Quốc Huyễn Tướng — quan trọng!
  - Nếu có: cần fallback (Shizuku / yêu cầu root) hoặc bỏ approach này
- [ ] Cài đặt AccessibilityService → dispatchGesture được tap/swipe
- [ ] Test gửi 1 ảnh tới Gemini API → nhận JSON response
- [ ] Foreground service chạy ổn định, không bị kill
- [ ] **Mốc thành công:** App có thể capture màn hình game + tap vào tọa độ do Gemini trả về

### **Phase 1 — PoC: 1 task (2 tuần)**
- [ ] Chọn task dễ nhất: **"Nhận quà đăng nhập"**
- [ ] Thu thập 20-30 screenshot các màn hình liên quan (cần user/tester)
- [ ] Viết system prompt chi tiết cho game này
- [ ] Build TaskGraph cho task này
- [ ] Implement screen hash cache
- [ ] Recovery cơ bản (popup handler, back button)
- [ ] **Mốc thành công:** chạy 10 lần liên tiếp đều xong, log đẹp

### **Phase 2 — MVP: 5-7 task chính + UI hoàn chỉnh (3-4 tuần)**
- [ ] UI Compose hoàn chỉnh (chọn task, run, log, schedule)
- [ ] Implement 5-7 daily task
- [ ] Scheduler (WorkManager) — chạy hàng ngày lúc X giờ
- [ ] Settings: speed, API key, log retention
- [ ] Stuck detection + recovery tốt hơn
- [ ] Test ổn định: 20 lần daily run → ≥95% success rate
- [ ] Disclaimer + ToS warning rõ ràng

### **Phase 3 — Hoàn thiện & phân phối (2 tuần)**
- [ ] Crash reporting (Sentry/Firebase Crashlytics)
- [ ] Analytics cơ bản (success rate per task)
- [ ] Build APK release, sign
- [ ] Phân phối: APK direct download (Play Store sẽ reject vì policy)
- [ ] (Optional) Auto-update mechanism

### **Phase 4+ — Mở rộng**
- Thêm game khác (refactor TaskGraph thành config YAML/JSON)
- Cộng đồng đóng góp task graph
- Cloud sync settings/logs

**Tổng thời gian PoC → MVP demo được: ~6-8 tuần (1 dev full-time)**

---

## 7. Cấu trúc repo dự kiến

```
ai-game-bot-android/
├── app/
│   ├── src/main/java/com/yourorg/gamebot/
│   │   ├── MainActivity.kt
│   │   ├── ui/                    # Compose screens
│   │   │   ├── home/
│   │   │   ├── tasks/
│   │   │   ├── logs/
│   │   │   └── settings/
│   │   ├── service/
│   │   │   ├── BotForegroundService.kt
│   │   │   ├── BotAccessibilityService.kt
│   │   │   └── CaptureService.kt
│   │   ├── core/
│   │   │   ├── capture/           # MediaProjection wrapper
│   │   │   ├── perception/        # VLM client, OCR, cache
│   │   │   ├── decision/          # TaskGraph, state machine
│   │   │   ├── action/            # gesture dispatcher
│   │   │   └── state/             # repository, history
│   │   ├── games/
│   │   │   └── tamquochuyentuong/
│   │   │       ├── tasks/
│   │   │       │   ├── DailyLoginTask.kt
│   │   │       │   ├── ClaimAfkTask.kt
│   │   │       │   ├── SweepDungeonTask.kt
│   │   │       │   └── ...
│   │   │       ├── prompts/
│   │   │       │   └── SystemPrompt.kt
│   │   │       └── GameConfig.kt
│   │   ├── data/
│   │   │   ├── db/                # Room
│   │   │   ├── api/               # LLM client
│   │   │   └── prefs/
│   │   └── di/                    # Hilt modules
│   ├── src/main/AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── README.md
```

---

## 8. Rủi ro CỤ THỂ với approach này

| Rủi ro | Khả năng | Mức nghiêm trọng | Giảm thiểu |
|---|---|---|---|
| **Tam Quốc Huyễn Tướng bật `FLAG_SECURE`** → MediaProjection ra màn đen | Trung bình | **Hard blocker** | Verify ngay Phase 0. Nếu có, cần Shizuku (yêu cầu user setup phức tạp) hoặc bỏ MediaProjection, dùng Accessibility Service đọc View tree (giới hạn — game render bằng Canvas/Unity nên thường không có view tree) |
| **VNG anti-cheat phát hiện Accessibility Service đang bật** → ban tài khoản | Trung bình | Cao | (a) Cảnh báo user dùng acc phụ. (b) Disable Accessibility khi không chạy bot. (c) Dùng `canRetrieveWindowContent="false"` để giảm signature |
| **VNG cập nhật game** → UI thay đổi, prompt cũ không hoạt động | Cao | Trung bình | VLM có khả năng adapt UI mới tốt hơn template matching. Khi fail, app gửi anonymous screenshot về để dev cập nhật prompt |
| **LLM API rate limit / outage** | Thấp | Trung bình | Multi-vendor fallback (Gemini → GPT-4o-mini → Claude) |
| **Game bị popup quảng cáo, sự kiện lạ** chưa có trong prompt | Cao | Thấp | Generic "close any popup" handler dùng VLM với prompt mở rộng |
| **App bị Android giết khi chạy nền** | Trung bình | Trung bình | Foreground service, request battery optimization exemption |
| **Google Play reject** vì là automation app | Cao (gần chắc 100%) | Thấp (vẫn distribute APK trực tiếp được) | Phân phối qua website / GitHub Releases |
| **Cost API tăng đột biến** nếu cache fail | Thấp | Thấp | Hard limit per day per user |
| **Pháp lý / ToS VNG** | Trung bình | User chịu | Disclaimer rõ ràng. KHÔNG bypass paywall, KHÔNG hack server |

### **Ưu tiên #1 phải xác định ngay:** FLAG_SECURE
Đây là điều kiện sống/chết của approach. Phase 0 đầu tiên phải test:
1. Bật Tam Quốc Huyễn Tướng
2. Cài app demo nhỏ chỉ làm 1 việc: capture màn hình qua MediaProjection
3. Xem có capture được nội dung game hay là màn đen

---

## 9. Câu hỏi cuối trước khi bắt đầu code

1. **Bạn có thiết bị Android thật để test không?** (Android version? Có root không?)
2. **Bạn có tài khoản Tam Quốc Huyễn Tướng VNG không?** (Cần để test thực tế. Tôi không thể tạo account giúp bạn.)
3. **Bạn có thể chia sẻ 5-10 screenshot các màn hình daily task không?** (Sẽ rất hữu ích để tôi viết prompt + tạo task graph chính xác)
4. **API key:**
   - User tự nhập (mỗi user dùng key riêng) — bạn không phải trả tiền
   - App có sẵn key (bạn trả tiền cho tất cả user) — cần proxy server
   - Mode hybrid (free tier có sẵn, premium thì user nhập key của họ)
5. **Distribution:**
   - APK download trực tiếp (qua website/GitHub)
   - Beta test private trước
   - F-Droid (sau)
6. **Bạn có muốn tôi:**
   - (X) **Bắt đầu Phase 0 ngay** — tạo Android Studio project, scaffold capture + accessibility + LLM client, push PR đầu tiên
   - (Y) Refine plan thêm trước khi code
   - (Z) Thay đổi gì đó trong scope/kiến trúc trên

---

## 10. Tóm tắt nhanh (TL;DR)

- **Stack:** Kotlin + Compose, MediaProjection (capture), AccessibilityService (action), Gemini 2.5 Flash-Lite (vision + decision)
- **Cost:** ~$0.23/user/tháng — rất rẻ
- **Phù hợp với game:** Tam Quốc Huyễn Tướng là auto-battler menu-driven → VLM-only approach (không cần train YOLO) đủ tốt
- **Rủi ro lớn nhất phải verify trước:** **FLAG_SECURE** trên game
- **Thời gian:** PoC 2 tuần, MVP 6-8 tuần
- **Cần xác nhận:** thiết bị test, screenshot game, mô hình API key, OK với Phase 0?
