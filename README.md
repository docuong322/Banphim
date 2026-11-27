# VietKey - Bàn phím tiếng Việt (Telex) cho Android

## Tính năng
- Gõ Telex: `uow` / `uwow` ra **ươ** đủ cả hai dấu móc, `dduwocj` ra **được**.
- Không tự chuyển sang EN. Chỉ đổi khi bấm phím **VI/EN** và được nhớ lại.
- Gõ lẫn Anh - Việt: từ không phải tiếng Việt tự khôi phục khi nhấn cách (`farther` hiện "fảthẻ" lúc gõ rồi về "farther").
  Từ nhập nhằng (six = sĩ) theo các từ gõ ngay trước: "ca sĩ" nhưng "for five six". Chưa có ngữ cảnh thì ưu tiên tiếng Việt.
  Muốn ra tiếng Anh thì gõ đôi phím dấu: `iss` = is, `ass` = as, `offf` = off.
- Bảng màu: chỉnh riêng màu nền, màu phím, màu phím chức năng, màu chữ; 9 bộ màu có sẵn; có xem trước.
- Ảnh nền tùy chọn, chỉnh độ đậm phím, độ tối ảnh, chiều cao phím.
- Xoay ngang tự **tách đôi** bàn phím, chỉnh độ rộng khoảng trống ở giữa.
- Giữ phím hàng trên cùng (q w e r t y u i o p) để gõ số 1 2 3 ... 0.
- Chế độ tương thích cho app/trình duyệt gõ bị lỗi.

## Build APK bằng GitHub
1. Tạo repo mới trên GitHub, upload TOÀN BỘ nội dung thư mục này (nhớ cả thư mục `.github`).
2. Vào tab **Actions** → workflow **Build APK** (tự chạy sau khi push, hoặc bấm **Run workflow**).
3. Chạy xong (khoảng 3-5 phút), mở lần chạy đó → mục **Artifacts** → tải **VietKey-apk**, giải nén ra `app-release.apk`.
4. Chép APK vào điện thoại, cài (cho phép "cài từ nguồn không xác định").
5. Mở app VietKey → **Bật VietKey** → bật công tắc → **Chọn VietKey làm bàn phím đang dùng**.

## Nếu không upload được thư mục .github (thư mục ẩn)
Trên GitHub: **Add file → Create new file**, ở ô tên gõ `.github/workflows/build.yml`
(gõ dấu `/` thì GitHub tự tạo thư mục), dán nội dung file `build.yml` đi kèm, rồi **Commit**.
