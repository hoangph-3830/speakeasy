# SpeakEasy cho iPad / iPhone (bản web – PWA)

Bản web của app Android SpeakEasy, có đủ tính năng: 46 chủ đề trong 6 nhóm, 3000 từ vựng thông dụng theo 30 chủ đề,
đọc báo BBC/CNN song ngữ (chạm từ để xem nghĩa), mẫu câu, câu bản xứ, thẻ từ vựng, hội thoại (Nghe / Nhập vai / Phản xạ),
luyện nói có chấm điểm, luyện nhại (shadowing) có ghi âm và biểu đồ ngữ điệu, ôn tập ngắt quãng FSRS, 6 game, Phòng phát âm, Truyện nghe, Nói với AI (Gemini) và Gọi thoại, nhiệm vụ, XP, chuỗi ngày.
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

## Đọc báo

- Danh sách tin: BBC News (RSS, qua rss2json) và CNN Lite, tự làm mới 5 phút/lần, lưu bản gần nhất để xem khi mất mạng.
- Đọc bài: nội dung được tải về máy người đọc khi mở (chế độ đọc), không lưu lại. Luôn có nguồn và link bài gốc.
- Trình duyệt chặn đọc trực tiếp BBC/CNN, nên bản web đi qua proxy: Jina Reader, allorigins… lần lượt.
  Muốn ổn định hơn, tự dựng proxy miễn phí theo [tools/README.md](tools/README.md).
- Tô màu từ: gạch chân xanh = từ mới, nền vàng = đang ôn, chữ thường = đã thuộc (bỏ qua giới từ, đại từ, mạo từ, liên từ),
  kèm "Bạn đã biết X% từ trong bài" và nút thêm hết từ mới vào Ôn tập. Ý tưởng từ Lute (MIT).
- Chạm từ: tra trong 3000 từ (có đoán từ gốc: went → go, stopped → stop), không có thì dịch tự động (Google Translate, dự phòng Gemini).

## Ôn tập & luyện nhại

- Lịch ôn dùng FSRS-4.5 với bộ trọng số mặc định (thuật toán mở của open-spaced-repetition, viết lại trong `js/logic.js`).
  Thẻ cũ xếp lịch bằng SM-2 được chuyển sang tự động, không mất tiến độ.
- Luyện nhại: mở từ trang chủ đề (🎙️). Nghe mẫu / lặp, ghi âm tối đa 10 giây, nghe lại, "Mẫu → Bạn",
  xem sóng âm + đường cao độ, nhận xét tốc độ và ngữ điệu cuối câu. Trên iPad cần cho phép micro cho trang.

## Khác với bản Android

- Tiến độ lưu trong Safari (localStorage) trên từng máy. Tab Tiến độ có **Tải file sao lưu / Khôi phục từ file** để chuyển giữa các máy.
- Chấm phát âm dùng nhận diện giọng nói của Safari nên điểm có thể hơi khác bản Android.

## Chạy thử trên máy tính

```bash
python -m http.server 5173 --directory web   # rồi mở http://localhost:5173
node web/test/logic.test.mjs                 # chấm điểm, FSRS, chuỗi ngày, game, nội dung, tra từ, cao độ
```

Trên localhost, service worker (bộ nhớ offline) được tắt để sửa code là thấy ngay.

## Cập nhật

- Bài học: sửa `app/src/main/assets/*.json` như bình thường rồi chép sang `web/data/`.
  Nội dung mới soạn theo lô (`topics-*.json`, `vocab-*.json`) gộp bằng `node web/tools/merge-content.cjs <thư mục>`,
  script tự loại từ trùng và ghi vào cả hai nơi.
- Sau mỗi lần sửa file trong `web/`, tăng `VERSION` trong `web/sw.js` để iPad tải bản mới.
- Đưa bản mới lên: commit rồi chạy
  `git subtree split --prefix web -b gh-pages && git push -f origin gh-pages` (xoá nhánh `gh-pages` cục bộ trước nếu đã có).
  Nhánh `gh-pages` được phát ở hai nơi:
  - Link chính, Cloudflare Pages tự deploy mỗi lần nhánh đổi: https://speak-easy-english.pages.dev/
  - GitHub Pages, link cũ vẫn chạy: https://hoangph-3830.github.io/speakeasy/
  Tiến độ học lưu theo từng link; muốn chuyển link thì dùng Tiến độ › Sao lưu rồi Khôi phục ở link mới.

## Cấu trúc

```
web/
├── index.html, styles.css, manifest.webmanifest, sw.js
├── data/        content.json, vocab.json, pronunciation.json, stories.json (chép từ app/src/main/assets)
├── tools/       merge-content.cjs, cors-worker.js (proxy đọc báo tuỳ chọn)
├── icons/
└── js/
    ├── logic.js    chấm điểm câu nói, SM-2, chuỗi ngày & nhiệm vụ, game, prompt AI (port từ data/*.kt)
    ├── store.js    tiến độ, bộ thẻ ôn tập, cài đặt AI, sao lưu (localStorage)
    ├── speech.js   giọng đọc + nhận diện giọng nói (Web Speech API)
    ├── ai.js       gọi Gemini
    ├── dict.js     tra từ offline + dịch tự động
    ├── news.js     tải tin BBC/CNN, tách nội dung bài
    ├── wordsheet.js  bảng nghĩa khi chạm vào từ
    ├── audio.js, recorder.js  ghi âm và phân tích cao độ cho luyện nhại
    ├── ui.js, app.js
    └── screens/    home, lesson (chủ đề · cụm từ · hội thoại · luyện nói), games, review, lab, stories, ai, progress, vocab, news, shadow
```
