# Kế hoạch & Kiến trúc — AI Game Automation cho Android

> **Mục tiêu:** Xây dựng ứng dụng dùng AI để "hiểu" trạng thái game trên Android và tự động thực hiện các tác vụ hàng ngày (daily tasks), thay vì auto-click cứng theo tọa độ. AI phải hiểu được màn hình, ngữ cảnh, và tự ra quyết định action phù hợp.

---

## 1. Phân tích vấn đề

### Auto-click truyền thống vs. AI-driven automation

| Tiêu chí | Auto-click cứng | AI-driven |
|---|---|---|
| Cách hoạt động | Tap theo tọa độ X,Y cố định | Nhìn màn hình → hiểu → quyết định → tap |
| Khi UI thay đổi | Hỏng ngay | Tự thích ứng |
| Khi có popup/event lạ | Hỏng | Có thể xử lý |
| Đa độ phân giải | Phải config riêng | Tự động |
| Đa game | Phải viết lại | Có thể tái sử dụng module |
| Phức tạp triển khai | Thấp | Cao |

### Các thách thức chính
1. **Capture màn hình** với độ trễ thấp và không bị game phát hiện
2. **Perception**: nhận diện UI elements, text, trạng thái game
3. **Decision making**: biết "đang ở đâu" và "nên làm gì tiếp theo"
4. **Action injection**: tap/swipe/long-press chính xác, giả lập giống người
5. **Anti-detection**: tránh các cơ chế anti-bot của game
6. **Đa game / đa độ phân giải**: kiến trúc phải mở rộng được

---

## 2. Lựa chọn nền tảng triển khai

Có 3 hướng chính, mỗi hướng có trade-off riêng:

### Hướng A — PC điều khiển Android qua ADB (KHUYẾN NGHỊ cho PoC)
```
[PC chạy AI nặng] ──USB/WiFi ADB──> [Android device chạy game]
```
**Ưu:**
- AI model có thể rất nặng (YOLO lớn, VLM như Qwen2-VL, GPT-4V)
- Dễ debug, dễ phát triển (Python full ecosystem)
- Không cần root, không cần cài app vào device
- Capture qua `scrcpy`/`minicap` rất nhanh (60fps)
- Inject input qua `adb shell input` hoặc `minitouch`

**Nhược:**
- Cần PC kết nối liên tục
- Không tiện cho end-user phổ thông (phải bật USB debugging)

### Hướng B — App Android độc lập, không root (Accessibility Service + MediaProjection)
```
[Android app] ──Accessibility/MediaProjection──> [Game trên cùng device]
```
**Ưu:**
- End-user chỉ cần cài 1 APK
- Không cần PC

**Nhược:**
- Model AI phải on-device (TFLite/ONNX/MNN) → giới hạn size & độ chính xác
- MediaProjection có thể bị game block (Android 14+ có FLAG_SECURE)
- Accessibility Service bị Google Play hạn chế nghiêm ngặt
- Một số game phát hiện được Accessibility đang bật

### Hướng C — App Android có root (Magisk module / shell)
```
[Android app + root] ──input event injection──> [Game]
```
**Ưu:**
- Inject input ở mức kernel, gần như không phát hiện được
- Capture qua framebuffer cực nhanh

**Nhược:**
- Yêu cầu root → giới hạn user lớn
- Anti-cheat hiện đại phát hiện root dễ dàng

### **Đề xuất:** Đi theo **Hướng A** trước (PoC + MVP), sau khi pipeline ổn thì port sang Hướng B cho end-user.

---

## 3. Kiến trúc tổng thể

```
┌─────────────────────────────────────────────────────────────┐
│                     ORCHESTRATOR (Python)                   │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐  │
│  │ Capture  │──▶│Perception│──▶│ Decision │──▶│  Action  │  │
│  │  Module  │   │  Module  │   │  Engine  │   │ Executor │  │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘  │
│       ▲              │              ▲              │       │
│       │              ▼              │              ▼       │
│       │        ┌──────────┐         │        ┌──────────┐  │
│       │        │  State   │◀────────┘        │  Input   │  │
│       │        │  Memory  │                  │ Injector │  │
│       │        └──────────┘                  └──────────┘  │
│       │                                            │       │
└───────┼────────────────────────────────────────────┼───────┘
        │                                            │
        │   ADB / scrcpy                ADB / minitouch
        ▼                                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    ANDROID DEVICE (Game)                    │
└─────────────────────────────────────────────────────────────┘
```

### 3.1. Capture Module
- **Công nghệ:** `scrcpy-server` (mở stream H.264) hoặc `minicap` (raw frame)
- **Output:** numpy array RGB ~30-60fps
- **Tối ưu:** chỉ capture khi cần (không stream liên tục), hoặc throttle xuống 5-10fps cho daily task

### 3.2. Perception Module — đa tầng
Đây là phần "AI hiểu màn hình", chia thành nhiều tầng để cân bằng tốc độ/độ chính xác:

**Tầng 1 — Template matching (nhanh, dùng cho UI cố định)**
- OpenCV `matchTemplate` cho các nút biết trước (nút "Đóng", "Nhận thưởng"...)
- Cực nhanh (<5ms), dùng làm fallback

**Tầng 2 — Object Detection (YOLOv8/v11 nano)**
- Train custom với screenshot game đã label
- Phát hiện: nút, item, NPC, monster, icon nhiệm vụ
- Tốc độ: ~20-50ms/frame trên GPU, ~100ms trên CPU

**Tầng 3 — OCR**
- `PaddleOCR` (đa ngôn ngữ, mạnh tiếng Việt) hoặc `EasyOCR`
- Đọc số lượng item, tên nhiệm vụ, dialog, thời gian cooldown

**Tầng 4 — Vision-Language Model (VLM) — cho ngữ cảnh phức tạp**
- **Lựa chọn:**
  - Local: `Qwen2-VL-7B`, `MiniCPM-V 2.6` (chạy được trên 1 GPU consumer)
  - API: GPT-4o, Gemini 2.0 Flash, Claude 3.5 Sonnet
- **Khi nào dùng:** chỉ khi tầng 1-3 không đủ (popup lạ, sự kiện mới, screen chưa biết)
- **Prompt mẫu:**
  ```
  Đây là screenshot game. Trạng thái hiện tại là gì?
  Có popup nào cần đóng không? Vị trí (x,y) của nút "Đóng"?
  Output JSON: {"screen": "...", "actions": [{"type": "tap", "x": ..., "y": ...}]}
  ```

→ **Output của Perception:** một `GameState` object chứa screen_id, các UI element đã detect, text đã OCR, confidence.

### 3.3. Decision Engine
Đây là "bộ não" quyết định làm gì tiếp theo. 3 cách triển khai từ đơn giản đến phức tạp:

**Cách 1 — Behavior Tree / State Machine (KHUYẾN NGHỊ cho daily task)**
- Mỗi daily task = 1 task graph có thứ tự bước rõ ràng
- Ví dụ task "Nhận quà đăng nhập":
  ```
  → mở menu → tìm icon "Phần thưởng" → tap → chờ animation
  → tap "Nhận tất cả" → chờ → tap "OK" → quay lại home
  ```
- Mỗi node có điều kiện vào/ra dựa trên `GameState`
- Có retry & timeout
- Library: tự viết hoặc `py_trees`

**Cách 2 — LLM-as-planner (cho task linh hoạt)**
- Mỗi turn, gửi screenshot + lịch sử gần nhất + danh sách action có thể → LLM trả về action tiếp theo
- Cost cao, latency cao, nhưng linh hoạt
- Dùng cho task chưa biết hoặc UI thay đổi

**Cách 3 — Reinforcement Learning**
- KHÔNG khuyến nghị cho dự án này — cần training rất nhiều, không phù hợp daily task có flow rõ ràng

→ **Đề xuất:** Mặc định dùng Cách 1, fallback sang Cách 2 khi state machine bị stuck.

### 3.4. Action Executor
- Convert action logic thành ADB command
- **Tap đơn giản:** `adb shell input tap X Y` (chậm, ~100-200ms latency)
- **Tap/swipe nhanh + nhân bản người:** `minitouch` (gửi raw event qua socket)
- **Giả lập người:** thêm jitter ngẫu nhiên ±2-5px, delay ngẫu nhiên 50-150ms, swipe có Bezier curve thay vì đường thẳng
- **Long press, multi-touch, gesture phức tạp:** dùng `minitouch` hoặc `sendevent`

### 3.5. State Memory
- Lưu lịch sử màn hình gần nhất (vài frame) để phát hiện stuck
- Lưu trạng thái task đang chạy (đã làm tới bước nào)
- Persistent: SQLite/JSON cho lịch sử daily đã hoàn thành
- Detect stuck: nếu 3 frame liên tiếp giống nhau & không phải màn hình target → trigger recovery

---

## 4. Tech Stack đề xuất

| Module | Công nghệ |
|---|---|
| Ngôn ngữ chính | Python 3.11+ |
| ADB wrapper | `adbutils` hoặc `pure-python-adb` |
| Screen capture | `scrcpy` (subprocess) + `av` (decode H.264), hoặc `minicap` |
| Image processing | OpenCV, Pillow, numpy |
| Object detection | Ultralytics YOLOv8/v11 |
| OCR | PaddleOCR (mạnh tiếng Việt) |
| VLM (local) | Qwen2-VL-7B qua `transformers` hoặc `vllm` |
| VLM (API) | OpenAI/Gemini SDK |
| Decision | `py_trees` hoặc state machine custom |
| Input injection | `minitouch` (qua TCP socket) |
| Config | YAML/Pydantic |
| Logging | `loguru` |
| UI điều khiển (sau) | FastAPI + React, hoặc Textual TUI |
| Đóng gói (sau) | PyInstaller, hoặc dockerize |

### Cấu trúc repo đề xuất
```
ai-game-bot/
├── core/
│   ├── capture/       # scrcpy, minicap wrapper
│   ├── perception/    # detection, ocr, vlm
│   ├── decision/      # behavior trees, planners
│   ├── action/        # input injection
│   └── state/         # memory, history
├── games/
│   ├── _base/         # base classes
│   ├── game_a/
│   │   ├── tasks/     # daily_login.py, daily_dungeon.py...
│   │   ├── screens/   # screen detectors
│   │   └── config.yaml
│   └── game_b/...
├── models/            # weights (gitignored)
├── data/              # screenshots, labels for training
├── scripts/           # training, eval, debug tools
├── tests/
└── pyproject.toml
```

---

## 5. Roadmap theo phase

### **Phase 0 — Setup & Spike (1 tuần)**
- [ ] Chọn 1 game cụ thể làm target (xem câu hỏi cuối)
- [ ] Setup ADB + scrcpy, capture frame được vào Python
- [ ] Test inject tap qua ADB và minitouch
- [ ] Đo latency end-to-end (capture → tap)

### **Phase 1 — PoC: 1 daily task đơn giản (2 tuần)**
- [ ] Chọn task dễ nhất (ví dụ: "Nhận quà đăng nhập")
- [ ] Thu thập 50-100 screenshot, label thủ công
- [ ] Build template matching cho các nút chính
- [ ] Build state machine cho task đó
- [ ] Chạy được end-to-end thành công 10 lần liên tiếp
- **Mốc thành công:** chạy lệnh `python run.py --task daily_login`, bot tự hoàn thành

### **Phase 2 — MVP: 5-10 daily task của 1 game (3-4 tuần)**
- [ ] Train YOLOv8 nano trên dataset đã thu thập
- [ ] Tích hợp PaddleOCR
- [ ] Viết task graph cho các daily tasks chính
- [ ] Recovery mechanism (stuck detection, popup handler)
- [ ] CLI tool: chọn task, chạy, xem log
- [ ] Test ổn định: chạy 100 lần, ≥95% success rate

### **Phase 3 — VLM fallback & UI (2-3 tuần)**
- [ ] Tích hợp VLM (API hoặc local) làm fallback khi state machine fail
- [ ] Web UI / TUI để monitor real-time
- [ ] Scheduler: tự động chạy theo lịch (cron-style)
- [ ] Notification (Telegram/Discord) khi xong/lỗi

### **Phase 4 — Mở rộng đa game (open-ended)**
- [ ] Refactor `core` thành thư viện
- [ ] Thêm game thứ 2 (đo effort thực tế)
- [ ] Tooling để label dataset & train nhanh cho game mới
- [ ] (Tùy) Port sang Android app (Hướng B)

---

## 6. Rủi ro & giảm thiểu

| Rủi ro | Mức độ | Giảm thiểu |
|---|---|---|
| **Anti-cheat phát hiện bot** | Cao | Dùng Hướng A (không inject vào app), nhân bản hành vi người (jitter, delay, Bezier swipe), tránh chạy 24/7 |
| **Tài khoản bị ban (vi phạm ToS)** | Cao | Cảnh báo user rõ ràng. Chỉ dùng cho tài khoản phụ. KHÔNG bypass paywall/anti-cheat |
| **Game update UI → bot hỏng** | Cao | Kiến trúc layer (template → YOLO → VLM fallback). VLM có thể adapt UI mới mà không cần retrain |
| **MediaProjection bị block (Android 14+)** | Trung bình | Hướng A không bị ảnh hưởng. Hướng B cần dùng scrcpy-server hoặc shizuku |
| **Latency cao → bỏ lỡ event** | Trung bình | Pipeline async, batch model inference, dùng ONNX/TensorRT |
| **Dataset thu thập thủ công tốn thời gian** | Trung bình | Dùng VLM auto-label sơ bộ → human review (active learning) |
| **VLM API tốn chi phí** | Thấp-TB | Chỉ gọi khi state machine fail. Cache aggressive theo screen_hash |
| **Pháp lý (ở 1 số quốc gia)** | Thấp | Để user tự chịu trách nhiệm, ghi disclaimer rõ |

---

## 7. Câu hỏi cần xác nhận trước khi bắt đầu code

1. **Game cụ thể nào sẽ là target Phase 1?** (1 game thôi để PoC nhanh)
   - Tên game, server (VN/Global), thể loại
2. **Môi trường chạy:** PC (Windows/Linux/Mac) + Android device thật, hay dùng emulator (LDPlayer/MEmu/BlueStacks)?
3. **Có sẵn GPU trên PC không?** (ảnh hưởng việc dùng VLM local hay API)
4. **Daily task nào bạn muốn tự động hóa?** Liệt kê 3-5 task ưu tiên.
5. **Budget cho VLM API** (nếu có): có ok chi $5-20/tháng cho Gemini/GPT-4o-mini không?
6. **Bạn muốn end-product là:**
   - (a) Tool cá nhân Python chạy trên PC của bạn
   - (b) App Android cho người khác dùng
   - (c) SaaS web dashboard điều khiển từ xa

---

## 8. Tóm tắt khuyến nghị

- **Bắt đầu:** Hướng A (PC + ADB), Python, scrcpy + minitouch
- **AI:** Layered perception (template → YOLO → OCR → VLM fallback)
- **Decision:** Behavior tree / state machine cho daily task
- **Phase 1:** 1 game, 1 task, ~2 tuần để có PoC chạy được
- **Cẩn trọng:** anti-cheat & ToS — dùng tài khoản phụ, hành vi giống người
