# SpeakEasy 🗣️

App Android học **tiếng Anh giao tiếp** qua các chủ đề hằng ngày, vừa chơi vừa học. Chạy hoàn toàn offline.

Fork từ [Engrisk](https://github.com/dexdev-info/EngriskApp) (MIT): giữ lại ý tưởng flashcard lật thẻ, phát âm bằng Text-to-Speech
và các dạng bài nghe-chọn / dịch. Phần Firebase (đăng nhập, Firestore) đã được bỏ, nội dung đóng gói sẵn trong app,
UI viết lại bằng Jetpack Compose.

## Tính năng

- **16 chủ đề, 4 nhóm**: 💼 Công việc (ngày đầu đi làm, họp, phỏng vấn, gọi điện), 🏡 Đời sống (cà phê, mua sắm,
  hiệu thuốc, cuối tuần), ✈️ Du lịch (sân bay, khách sạn, hỏi đường, nhà hàng), 🎉 Giao lưu (làm quen, sở thích, rủ đi chơi,
  giữ liên lạc).
- **Lộ trình 4 bước mỗi chủ đề**
  1. 📚 Mẫu câu + mẹo dùng, thẻ từ vựng lật được (IPA, ví dụ, đánh dấu đã thuộc)
  2. 💬 Hội thoại: chế độ **Nghe** (phát cả đoạn, highlight câu đang đọc) và **Nhập vai** (app đóng vai đối phương,
     bạn nói vai của mình qua micro)
  3. 🎤 Luyện nói: nói từng câu, app chấm % và tô xanh/đỏ từng từ
  4. 🎮 Game ôn tập
- **Nói như người bản xứ** 🗣️: mỗi chủ đề có câu "sách vở" đặt cạnh cách người bản xứ nói thật (48 câu: idiom, phrasal verb, wanna/gonna…)
- **Phản xạ** ⚡: hội thoại không có chữ tiếng Anh, nghe đối phương, nhìn gợi ý tiếng Việt, micro tự bật để trả lời ngay
- **Ôn tập ngắt quãng** 🔁: từ, mẫu câu, câu bản xứ của bài đã học được đưa vào bộ thẻ SM-2, nhắc ôn đúng lúc sắp quên; có chế độ "Chỉ nghe"
- **Tab Chơi**: Ghép cặp từ 🧩, Xếp câu 🧱, Nghe & chọn 🎧, Nghe & chép ✍️ (chính tả), Đoán nghĩa 💡, Thử thách tổng hợp 🌈 (combo 🔥)
- **Nói với AI** 🤖: trò chuyện tự do hoặc nhập vai theo 16 chủ đề với Google Gemini (gói miễn phí, tự thử
  `gemini-flash-latest` → `gemini-3.5-flash` → `gemini-flash-lite-latest`). AI đáp ngắn như người bản xứ (đọc thành tiếng),
  sửa từng câu bạn nói ("Người bản xứ sẽ nói…" + giải thích tiếng Việt) và gợi ý câu trả lời tiếp. Có 3 độ khó.
  Chỉ tính năng này cần mạng.
- **Phòng phát âm** 🎯 (kiểu ELSA): 10 bài cho các âm người Việt hay sai (th, âm cuối, ship/sheep, bad/bed, she/see, l/n, r/l,
  ch/sh, nối âm, trọng âm). Mỗi bài: mẹo khẩu hình → nghe phân biệt cặp từ → nói từ (app báo "nghe thành sheep") → nói câu.
  Điểm 0–100 cho từng âm và điểm phát âm tổng.
- **Truyện nghe** 📖 (kiểu Duolingo Stories): 4 truyện, mỗi nhân vật một giọng, câu hỏi chen giữa truyện, chế độ chỉ nghe.
- **Nhiệm vụ hằng ngày** 📜 + **đóng băng chuỗi** 🧊: 4 nhiệm vụ có rương XP; đạt mục tiêu ngày được 1 lượt đóng băng (tối đa 2), tự dùng khi lỡ nghỉ.
- **Gọi thoại với AI** 📞 (kiểu Duolingo Max): rảnh tay, AI nói xong thì micro tự bật, chữ ẩn để luyện nghe.
- Giọng đọc Anh - Mỹ 🇺🇸 hoặc Anh - Anh 🇬🇧
- **Động lực**: XP, cấp độ, mục tiêu hằng ngày, chuỗi ngày học, 8 huy hiệu, "chủ đề hôm nay" đổi mỗi ngày
- Cài đặt tốc độ giọng đọc, nút nghe chậm 🐢, ẩn/hiện bản dịch

## Yêu cầu thiết bị

Android 8.0+. Phát âm dùng engine TTS của máy (Google TTS). Luyện nói dùng nhận diện giọng nói của Google —
máy cần có app Google; không cần cấp quyền micro cho SpeakEasy.

## Build

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # kiểm tra nội dung + chấm điểm phát âm
```

Cần JDK 17 và Android SDK 35. `local.properties` (không commit) chứa:

```
sdk.dir=C:/Users/<bạn>/AppData/Local/Android/Sdk
GEMINI_API_KEY=<key tạo tại https://aistudio.google.com/apikey>
```

Key được build vào `BuildConfig.GEMINI_API_KEY`. Lưu ý: ai có file APK đều có thể trích key ra, nên chỉ dùng key miễn phí
và giới hạn key cho ứng dụng Android `com.speakeasy.app` trong Google Cloud Console trước khi phát hành.

## Thêm nội dung

Toàn bộ bài học ở `app/src/main/assets/content.json`. Thêm một object vào `topics` (cần `phrases`, `words`,
`dialogue` với vai `A` = đối phương, `B` = người học, và `native` gồm `plain`/`natural`/`vi`/`note`); `ContentTest` sẽ báo nếu thiếu trường.

## Cấu trúc

```
app/src/main/java/com/speakeasy/app/
├── data/        Models, ContentRepository (đọc JSON), ProgressStore (XP/streak), Review (SM-2), Games, AiTutor (Claude)
├── speech/      Speaker (TTS), SpeechInput (nhận diện giọng), SpeechMatch (chấm điểm theo từ)
└── ui/          Theme, components, screens (Home, Topic, Phrases, Dialogue, Speak, Games, Progress)
```

## License

MIT — xem [LICENSE.md](LICENSE.md). Mã gốc © 2025 Dex (Engrisk).
