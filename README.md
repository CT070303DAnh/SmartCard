# ⚽ Football Member SmartCard (NetBeans + jCardSim + Firebase POS)

Phần mềm thẻ thành viên sân bóng chạy mô phỏng thẻ thông minh **Java Card 3.0.5 (`jCardSim 3.0.5.11`)**, đồng bộ trực tiếp Cloud Firestore với ứng dụng Android `D:\FootballBookingAndroid`.

## ✨ Chức năng đã triển khai
1. **Xác thực PIN & PUK (`OwnerPIN`)**:
   - PIN mặc định: `123456` (sai quá 3 lần tự động khóa thẻ).
   - PUK Admin mặc định: `88888888` (mở khóa & đặt lại PIN).
   - Khóa thẻ khẩn cấp và kiểm tra trạng thái báo mất từ App Android.
2. **Lưu & Mã hóa thông tin cá nhân bằng AES-128-CBC**:
   - Sinh khóa AES-128 ngẫu nhiên ngay trên thẻ.
   - Hỗ trợ xem trực tiếp bản mã (Hex) trong EEPROM để demo bảo mật.
3. **Nạp tiền, Thanh toán sân & dịch vụ, Tích điểm & Đổi quà**:
   - Tự động phân hạng thành viên (*Đồng → Bạc → Vàng → VIP*) theo tổng chi tiêu với hệ số nhân điểm (`x1.0`, `x1.2`, `x1.5`, `x2.0`).
   - Lưu vòng đệm (Ring Buffer) **5 giao dịch gần nhất** ngay trong EEPROM thẻ.
4. **Chữ ký số RSA-1024 chống giả mạo thẻ**:
   - Sinh cặp khóa RSA-1024 ngay trên thẻ, ký Challenge-Response và ký giao dịch thanh toán.
5. **Đồng bộ Realtime với `FootballBookingAndroid` (Cloud Firestore)**:
   - Tìm user từ collection `users`, đồng bộ `cardId`, `cardBalance`, `loyaltyPoints`, `memberTier`, `cardStatus`, `cardPublicKey`.
   - Kéo danh sách đơn đặt sân (`bookings`) chưa thanh toán → Quẹt thẻ Check-in & cập nhật `paymentStatus = "paid"`, `matchStatus = "playing"`.
   - Đọc dịch vụ từ `services` và ghi lịch sử giao dịch vào `card_transactions`.

## 🚀 Cách mở và chạy trên NetBeans
1. Mở **Apache NetBeans** → `File` → `Open Project...` → chọn thư mục **`D:\SmartCard`**.
2. Chờ NetBeans tải dependency Maven (`jcardsim 3.0.5.11` và `firebase-admin 9.4.1`).
3. *(Tùy chọn – để kết nối Firebase thật với app Android)*:
   - Vào **Firebase Console** → **Project Settings** → **Service accounts** → **Generate new private key**.
   - Đổi tên file tải về thành `serviceAccountKey.json` và đặt vào **`D:\SmartCard\serviceAccountKey.json`**.
   - *(Nếu chưa có file này, phần mềm vẫn chạy đầy đủ mọi chức năng thẻ ở chế độ Offline Demo).*
4. Nhấn **Run Project (F6)** để khởi chạy giao diện POS `MainFrame`.
