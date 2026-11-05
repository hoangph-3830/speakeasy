# Proxy riêng cho tính năng Đọc báo (miễn phí, khoảng 5 phút)

Trình duyệt không cho trang web đọc trực tiếp BBC/CNN, nên bản web phải tải qua một máy chủ trung gian (proxy).
Mặc định app dùng các proxy công cộng. Các proxy này đôi khi bận hoặc chặn. Bạn có thể tự dựng proxy riêng trên Cloudflare:

1. Đăng ký tài khoản miễn phí tại https://dash.cloudflare.com.
2. Vào **Workers & Pages → Create → Create Worker**, đặt tên (ví dụ `speakeasy-news`) rồi bấm **Deploy**.
3. Bấm **Edit code**, xoá code mẫu, dán toàn bộ nội dung file `cors-worker.js` trong thư mục này vào, rồi bấm **Deploy**.
4. Copy địa chỉ worker (ví dụ `https://speakeasy-news.ten-ban.workers.dev`).
5. Trong app, vào **Tiến độ › Cài đặt › Proxy đọc báo**, dán `https://speakeasy-news.ten-ban.workers.dev/?url=` rồi bấm **Lưu**.

Worker chỉ cho phép tải từ BBC và CNN. Gói miễn phí cho 100.000 lượt/ngày, dư sức cho một người dùng.
Bản Android tải tin trực tiếp nên không cần proxy.
