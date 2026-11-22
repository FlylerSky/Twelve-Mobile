# Twelve Mobile (Android)

Ứng dụng Android native chính thức của mạng xã hội **Twelve** (Kotlin, Jetpack Compose, Material 3, Hilt), kết nối trực tiếp tới backend production `https://twelveta.onrender.com/api` và dự án Firebase của Twelve.

## Thông số kỹ thuật

- **Tên hiển thị**: Twelve
- **Application ID**: `com.twelve.mobile`
- **minSdk / compileSdk / targetSdk**: `26` / `36` (`36.1`) / `36`
- **Ngôn ngữ giao diện**: Tiếng Việt (`vi`)
- **Backend REST**: `https://twelveta.onrender.com/api/`
- **Health ping**: `https://twelveta.onrender.com/health`
- **Website (liên kết chia sẻ / Đăng ký / Quên mật khẩu)**: `https://twelveta.vercel.app`

## Hướng dẫn cấu hình Firebase (`google-services.json`)

Dự án cố ý **không** đi kèm tệp `google-services.json` giả hoặc khoá giữ chỗ. Khi chưa có tệp này, ứng dụng vẫn biên dịch sạch và khởi chạy vào màn hình **"Thiếu cấu hình Firebase"** an toàn.

Để kết nối Firebase thật:

1. Mở [Firebase Console](https://console.firebase.google.com/) và chọn đúng dự án Firebase đang vận hành **Twelve** (dự án dùng chung với backend Render và web Vercel).
2. Chọn **Cài đặt dự án (Project Settings)** → **Ứng dụng của bạn (Your apps)** → **Thêm ứng dụng (Add app)** → **Android**.
3. Nhập **Android package name**: `com.twelve.mobile`.
4. Lấy mã **SHA-1** của máy build debug bằng lệnh:
   ```bash
   keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android
   ```
   *(Hoặc dùng `debug.keystore` tại thư mục gốc dự án nếu có.)* Dán chuỗi SHA-1 (và SHA-256 nếu phát hành) vào Firebase Console → nhấn **Đăng ký ứng dụng**.
5. Tải tệp `google-services.json` vừa tạo và đặt vào đúng đường dẫn:
   ```text
   app/google-services.json
   ```
6. Biên dịch lại ứng dụng: Gradle sẽ tự phát hiện `app/google-services.json` và tự động áp dụng plugin `com.google.gms.google-services`.

## Cấu hình Android App Links (`assetlinks.json`)

Ứng dụng đã khai báo bộ lọc liên kết `https://twelveta.vercel.app` cho `/post/`, `/blog/`, và `/u/` với `android:autoVerify="true"` trong `app/src/main/AndroidManifest.xml`.

Để Android tự động mở thẳng ứng dụng khi người dùng bấm link chia sẻ (không hiện hộp thoại chọn trình duyệt), cần đặt tệp `public/.well-known/assetlinks.json` trên Vercel (`https://twelveta.vercel.app/.well-known/assetlinks.json`) với nội dung mẫu:

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.twelve.mobile",
      "sha256_cert_fingerprints": [
        "<SHA256_CHUNG_CHI_KY_RELEASE_HOAC_DEBUG>"
      ]
    }
  }
]
```

## Lệnh biên dịch & kiểm thử

```bash
# Trong môi trường có Gradle wrapper cục bộ:
./gradlew assembleDebug testDebugUnitTest lint

# Trong môi trường container AI Studio (không dùng ./gradlew):
gradle assembleDebug testDebugUnitTest lint
```

## Tài liệu liên quan

- `API-MAP.md`: Bảng đối chiếu màn hình ↔ endpoint ↔ file + hàm backend ↔ trạng thái tích hợp.
- `DECISIONS.md`: Nhật ký quyết định kỹ thuật, phiên bản thư viện xác minh và cấu hình ký/bảo mật.
