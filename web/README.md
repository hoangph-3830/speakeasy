# SpeakEasy cho iPad / iPhone (bản web – PWA)

Bản web của app Android SpeakEasy, có đủ tính năng: 16 chủ đề, mẫu câu, câu bản xứ, thẻ từ vựng, hội thoại (Nghe / Nhập vai / Phản xạ),
luyện nói có chấm điểm, ôn tập SM-2, 6 game, Phòng phát âm, Truyện nghe, Nói với AI (Gemini) và Gọi thoại, nhiệm vụ, XP, chuỗi ngày.
Chỉ dùng HTML/CSS/JavaScript thuần, không cần build và không cần máy Mac.

## Cài lên iPad

1. Đưa thư mục `web/` lên một host **HTTPS** (GitHub Pages, Netlify, Cloudflare Pages…). Micro chỉ chạy trên HTTPS.
2. Trên iPad, mở link bằng **Safari**, bấm **Chia sẻ → Thêm vào MH chính**.
3. Mở app từ icon trên màn hình chính. Từ lần mở thứ hai, bài học chạy được cả khi không có mạng.

Điều kiện trên iPad:
- iPadOS 15.4+ (khuyên dùng 16+). Nhận diện giọng nói dùng tính năng Đọc chính tả của Apple: bật **Cài đặt › Siri › Đọc chính tả**.
- Giọng đọc hay hơn: **Cài đặt › Trợ năng › Nội dung được đọc › Giọng nói › Tiếng Anh**, tải giọng “Nâng cao”.
- Nói với AI: nhập Gemini API key trong tab **Tiến độ › Cài đặt** (key miễn phí tại https://aistudio.google.com/apikey).
  Key chỉ lưu trên máy đó, không nằm trong code.

## Khác với bản Android

- Tiến độ lưu trong Safari (localStorage) trên từng máy. Tab Tiến độ có **Tải file sao lưu / Khôi phục từ file** để chuyển giữa các máy.
- Chấm phát âm dùng nhận diện giọng nói của Safari nên điểm có thể hơi khác bản Android.

## Chạy thử trên máy tính

```bash
python -m http.server 5173 --directory web   # rồi mở http://localhost:5173
node web/test/logic.test.mjs                 # kiểm tra chấm điểm, SM-2, chuỗi ngày, game, nội dung
```

Trên localhost, service worker (bộ nhớ offline) được tắt để sửa code là thấy ngay.

## Cập nhật

- Bài học: sửa `app/src/main/assets/*.json` như bình thường rồi chép sang `web/data/`.
- Sau mỗi lần sửa file trong `web/`, tăng `VERSION` trong `web/sw.js` để iPad tải bản mới.
- Đưa bản mới lên GitHub Pages (https://hoangph-3830.github.io/speakeasy/): commit rồi chạy
  `git subtree split --prefix web -b gh-pages && git push -f origin gh-pages` (xoá nhánh `gh-pages` cục bộ trước nếu đã có).

## Cấu trúc

```
web/
├── index.html, styles.css, manifest.webmanifest, sw.js
├── data/        content.json, pronunciation.json, stories.json (chép từ app/src/main/assets)
├── icons/
└── js/
    ├── logic.js    chấm điểm câu nói, SM-2, chuỗi ngày & nhiệm vụ, game, prompt AI (port từ data/*.kt)
    ├── store.js    tiến độ, bộ thẻ ôn tập, cài đặt AI, sao lưu (localStorage)
    ├── speech.js   giọng đọc + nhận diện giọng nói (Web Speech API)
    ├── ai.js       gọi Gemini
    ├── ui.js, app.js
    └── screens/    home, lesson (chủ đề · cụm từ · hội thoại · luyện nói), games, review, lab, stories, ai, progress
```
