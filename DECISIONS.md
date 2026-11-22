# Nhật ký Quyết định Kỹ thuật — Twelve Mobile

## 1. Cấu hình SDK & Phiên bản Thư viện (`gradle/libs.versions.toml`)

- **SDK đã xác minh trên container build**: `ANDROID_SDK_ROOT=/opt/android/sdk` có sẵn nền tảng `android-36` và `android-36.1`, `Gradle 9.3.1`, JDK `21.0.13`. Đặt `compileSdk = 36 (minor 1)`, `targetSdk = 36`, `minSdk = 26`.
- **Phiên bản cụ thể trong `gradle/libs.versions.toml`**:
  - AGP `9.1.1`, Kotlin `2.2.10`, KSP `2.3.5`
  - Jetpack Compose BOM `2024.09.00`, Activity Compose `1.10.1`, Navigation Compose `2.8.9`, Lifecycle `2.8.7`
  - Dagger Hilt `2.60.1` (bắt buộc `>= 2.60.1` để tương thích AGP 9.x built-in Kotlin), Hilt Navigation Compose `1.2.0`
  - Retrofit `2.12.0` + `converter-kotlinx-serialization` `2.12.0`, `kotlinx-serialization-json` `1.8.0`
  - OkHttp & MockWebServer `4.12.0`, Coil Compose `2.7.0`, DataStore Preferences `1.1.7`, AndroidX Browser `1.8.0`
  - Firebase BoM `34.17.0` (`firebase-auth`, `firebase-firestore`)
  - JUnit `4.13.2`, Coroutines Test `1.10.2`, Robolectric `4.16.1`
  - **Trạng thái kiểm chứng M0**: Tất cả phiên bản trên đã resolve, biên dịch sạch qua `gradle assembleDebug` và chạy `gradle testDebugUnitTest` (9/9 test M0 đạt: `M0AuthAndNetworkTest` 6/6, `ExampleRobolectricTest` 3/3).

## 2. Xử lý khi thiếu `google-services.json`

- Trong `app/build.gradle.kts`, plugin `com.google.gms.google-services` **chỉ được áp dụng** khi `file("google-services.json").exists()` trả về `true`, đồng thời xuất biến `BuildConfig.HAS_GOOGLE_SERVICES`.
- Tại runtime, `DefaultFirebaseAvailability` kiểm tra cả `BuildConfig.HAS_GOOGLE_SERVICES` và `FirebaseApp.getApps(context)`. Khi chưa cấu hình, ứng dụng hiển thị màn hình chặn **"Thiếu cấu hình Firebase"** kèm hướng dẫn từng bước và không bao giờ gọi `FirebaseAuth.getInstance()` hay `FirebaseFirestore.getInstance()`.

## 3. Ký ứng dụng & R8

- Dự án **không** lưu trữ tệp keystore (`.jks`, `.keystore`), mật khẩu ký hoặc khoá bí mật nào.
- Hai khối `signingConfigs` (`release` và `debugConfig`) trong `app/build.gradle.kts` là cấu hình chuẩn của hạ tầng build AI Studio: `debugConfig` trỏ tới `debug.keystore` tự sinh ở runtime với khoá debug mặc định Android; `release` đọc biến môi trường bên ngoài (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) nếu được truyền lúc xuất bản dựng và được giữ nguyên cấu trúc theo quy tắc bắt buộc của nền tảng.
- Bản `release` bật `isMinifyEnabled = true` và `isShrinkResources = true` kèm `app/proguard-rules.pro` cho R8.

## 4. Trạng thái tài khoản & Chuẩn hoá `tagName`

- Xác nhận từ `backend/src/controllers/users.controller.js#getMe` và `backend/src/middleware/auth.js`: trường trạng thái trên đối tượng trả về của `/users/me` là `status` (`pending | active | suspended | deleting`).
- `ACCOUNT_SUSPENDED` và `ACCOUNT_DELETING` (HTTP `403` trên mọi endpoint kể cả `/users/me` và `/auth/activation/sync`) được xử lý là trạng thái chặn cứng: hiển thị đúng thông báo máy chủ, đăng xuất ngay và không thử lại.
- `GET /users/me` trả `tagName` không có tiền tố `@`, trong khi hồ sơ công khai và tác giả bài viết có `@`. `TagNameNormalizer` thống nhất chuẩn hoá cả hai đầu vào.

## 5. Bảo mật ngay ở M0

- `app/src/main/res/xml/network_security_config.xml`: đặt `cleartextTrafficPermitted="false"`.
- `app/src/main/AndroidManifest.xml`: đặt `android:allowBackup="false"`, chỉ `MainActivity` có `android:exported="true"`.
- `NetworkModule.kt`: không cài body/header logger để bảo đảm không ghi token, mật khẩu, email hay tên thật vào log; R8 xoá `Log.v/d` ở bản release.
- `AuthTokenInterceptor.kt`: kiểm tra `Looper.myLooper() != Looper.getMainLooper()` trước khi chờ `Task` lấy ID token từ Firebase trên luồng worker của OkHttp.

## 6. Kiến trúc M1 (Đọc nội dung, HTML an toàn, Điều hướng)

- **Hiển thị `contentHtml` không dùng WebView (`SafeHtmlParser` + `SafeHtmlRenderer`)**: Phân tích HTML Quill thành AST Compose thuần, loại bỏ hoàn toàn `<script>`, `<style>`, `on*`, chặn `javascript:`, `intent:`, `file:`, `content:`, `data:`; thẻ `<iframe>`/`<video>`/`<audio>` hợp lệ được dựng thành thẻ liên kết mở ra Chrome Custom Tab thay vì nhúng web bên thứ ba trực tiếp. Các trường văn bản thuần (`title`, `displayName`, `bio`, `comment.content`) chỉ dùng `Text` thuần.
- **Mã phiên chia sẻ (`SessionIdStore`)**: Lưu UUID phiên cục bộ trong DataStore để truyền `sid` cùng `idu`/`s`/`shared` khi mở chi tiết bài/blog (tương ứng `frontend/src/utils/session.js`). Không bao giờ gọi `GET /posts/:id` hay `GET /blogs/:id` từ màn hình danh sách để tránh đếm sai lượt xem.
- **Điều hướng thích ứng (`TwelveAppShell` & `TwelveRoutes`)**: Dùng Navigation Compose an toàn kiểu (`@Serializable` route classes), tự động chuyển giữa `NavigationBar` (`< 600.dp`) và `NavigationRail` (`>= 600.dp`) theo `BoxWithConstraints`.

## 7. Kiến trúc M2 (Tương tác, Soạn thảo HTML & Realtime giới hạn)

- **Biên dịch HTML tương thích Quill (`QuillHtmlCodec` & `ContentLimits`)**: Thanh công cụ soạn thảo nhanh tạo cú pháp gọn chuyển sang whitelist HTML của Quill (`<p>`, `<h2>`, `<h3>`, `<blockquote>`, `<ul><li>`, `<ol><li>`, `<pre><code>`, `<strong>`, `<em>`, `<a href>`, `<img src>`), tự escape ký tự đặc biệt và kiểm tra URL `https://`. Hỗ trợ giải mã ngược `fromHtmlToEditorText` khi mở chế độ sửa bài.
- **Tương tác lạc quan có hoàn tác (`ContentActionHelper` & `ContentEventsBus`)**: Chỉ áp dụng UI lạc quan cho nút Thích / Không thích; khi API trả lỗi (`429`, offline, `ACCOUNT_NOT_ACTIVE`), hoàn tác đúng bộ đếm + `myReaction` trước đó và báo lỗi. Không retry tự động các request `POST`/`PUT`/`PATCH`/`DELETE`.
- **Listener Firestore có giới hạn (`FirestoreContentObserver`)**: Chỉ gắn khi `FirebaseAvailability.isConfigured == true` trong vòng đời `ON_START`..`ON_STOP` của màn hình Chi tiết. Bình luận realtime chỉ lắng nghe cửa sổ `updatedAt >= serverTimeMs - 60_000` với `.limit(50)` để tuân thủ đúng `firestore.rules`. Không bao giờ ghi đề `myReaction` từ snapshot công khai.

## 8. Kiến trúc M3 (Hồ sơ cá nhân, Ảnh đại diện/Bìa, Reauth & Blog SA)

- **Chọn ảnh không cần quyền bộ nhớ (`ActivityResultContracts.PickVisualMedia`)**: Sử dụng Android Photo Picker hệ thống; giải mã ảnh có xoay theo `ExifInterface` (`ImageUploadProcessor.decodeForPreview`), xuất ảnh đại diện `512x512` JPEG `<= 2MB` và ảnh bìa cạnh dài `<= 2400px` JPEG `<= 3MB` trên `Dispatchers.IO`.
- **Giữ `"crop": null` khi đặt lại ảnh bìa**: Do cấu hình `Json { explicitNulls = false }` ở tầng mạng toàn cục, `AccountRepository.setCoverCrop(null)` đóng gói bằng `buildJsonObject { put("crop", JsonNull) }` để bảo đảm `PUT /api/users/me/cover/crop` nhận đúng `{ "crop": null }` thay vì thân rỗng (`400`).
- **Chỉ gửi trường thay đổi (`PATCH /api/users/me`)**: `EditableProfileForm.dirtyMapAgainst` tính hiệu với `baseline` qua `buildJsonObject`, vẫn gửi chuỗi rỗng `bio = ""` khi người dùng xoá trắng tiểu sử.
- **Bảo mật tên thật & thao tác nhạy cảm**: Tên thật không lưu vào cache hay state lâu dài; chỉ hiện sau `reauthenticateWithPassword` và tự ẩn sau 60 giây. Đổi mật khẩu và xoá tài khoản yêu cầu xác thực lại bằng mật khẩu hiện tại qua Firebase Auth Client SDK (`reauthWithPassword`), đồng thời chặn nút xoá với tài khoản `SA` ở UI và xử lý mã `SA_CANNOT_SELF_DELETE` / `ACCOUNT_DELETING`.

## 9. Kiến trúc M4 (Deep Links & Củng cố bảo mật)

- **Phân tích Deep Link nghiêm ngặt (`DeepLinkParser`)**: Chỉ nhận scheme `https` với host `twelveta.vercel.app` (khớp `BuildConfig.WEB_BASE_URL`). Mọi tham số `:id`, `:ref`, `idu`, `s` đều được kiểm tra định dạng an toàn trước khi đưa vào `NavHost`, chặn path traversal (`..`), ID đặt trước Firestore (`__.*__`) và `@tag` sai quy tắc `profilePath.js#classifyProfileRef`.
- **Android App Links (`AndroidManifest.xml` & `MainActivity`)**: Đặt `android:launchMode="singleTop"`, xử lý cả cold-start (`onCreate`) lẫn warm-start (`onNewIntent`). Chuyển luồng `/u/myProfile` của khách vãng lai sang hộp đăng nhập thay vì mở hồ sơ rỗng.
- **Kiểm tra bảo mật & Google Play Policy**: Không yêu cầu quyền đọc bộ nhớ rộng (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`), dùng Android Photo Picker; `allowBackup="false"`, `cleartextTrafficPermitted="false"`, `Log.*` bằng 0 trong `app/src/main/java`. Xác minh App Links thực tế trên thiết bị cần tệp `assetlinks.json` triển khai trên domain `twelveta.vercel.app` (đã hướng dẫn trong `README.md`).

## 10. Tinh chỉnh giao diện toàn nền tảng & sửa lỗi hiển thị ảnh bìa

- **Sửa tận gốc lỗi mất ảnh bìa (`CoverCropMath.computePixelPlacement` + `Modifier.layout`)**: Trước đây `ProfileCoverBox` và `CoverAdjustDialog` đặt kích thước ảnh bằng `Modifier.width(targetW).height(targetH).offset(...)` bên trong `BoxWithConstraints`, khiến Compose kẹp kích thước ảnh về kích thước khung 3:1 trong khi `.offset` vẫn dịch lên trên theo phần trăm âm (`y / height`), làm ảnh trôi khỏi vùng nhìn thấy hoặc lộ nền tím. Chuyển sang `Modifier.layout` đo bằng `Constraints.fixed(widthPx, heightPx)` và `.clipToBounds()`, kèm nền trung tính (`surfaceVariant`) + hiệu ứng loading và `onError` dự phòng cho avatar/ảnh bìa.
- **Chuẩn hoá bố cục không xuống dòng lung tung**:
  - `PostCard` & `ContentDetailScreen`: thay `FlowRow` nút tương tác thành `PostMetricsActionBar` một hàng thống nhất (nút phản ứng bên trái, lượt xem & điểm bài bên phải) có viền card mảnh và phân cách rõ ràng.
  - `AuthorHeaderLine` & `TrendingScreen` (`RankingUserRow`): giữ `Tên hiển thị + Huy hiệu + @tag` thẳng hàng không bị bóp 2-3 dòng.
  - `TrendingScreen`, `HashtagPostsScreen`, `SettingsScreen`: thay các tầng `SecondaryTabRow` và nhóm `FilterChip` bị rớt lẻ hàng bằng `SingleChoiceSegmentedButtonRow` cân đối.
  - `PublicProfileScreen`: bỏ khung 3:1 trống khi người dùng chưa có ảnh bìa, tách nút `Xem thêm` khỏi hàng tên hồ sơ để tiêu đề không bị ép xuống dòng, nâng cấp hộp `Xem thêm` lên `Dialog` rộng 92% màn hình kèm nút sao chép nhanh UID.
  - `LoginScreen`, `FeedScreen` (Guest banner), `EmbedLinkCard`, `AccountMediaDialogs`: căn chỉnh lại liên kết, thẻ nhúng 1 dòng có nút `Mở`, và hộp cắt avatar/ảnh bìa dùng khung rộng đồng bộ.

## 11. Đồng bộ chi tiết giao diện với Twelve Web (theo ảnh đối chiếu)

- **Hiển thị khối mã `<pre>` (`SafeHtmlRenderer.kt`)**: Bỏ `.horizontalScroll` gây cắt mất chữ ở mép phải trên màn hình dọc; dùng `Text` tự động xuống dòng mềm bên trong khung bo góc nền mờ `surfaceVariant` + viền mảnh, và kéo thanh nhấn dọc bên trái `blockquote` bằng `IntrinsicSize.Min` + `fillMaxHeight()`.
- **Trang chi tiết bài viết & bình luận (`ContentDetailScreen.kt`)**:
  - Đưa nút Ghim (`Pin`) trực tiếp lên `TopAppBar` khi người dùng có quyền ghim; bỏ thanh `bottomBar` rời bên dưới và bỏ hàng số liệu chật bên trong thẻ bài.
  - Bố trí 5 nút pill tương tác bên dưới thẻ bài (`[Thích | Không thích]`, `[Lượt xem | Chia sẻ]`, và nút `[Điểm bài viết]` toàn chiều rộng) theo đúng bố cục Twelve Web.
  - Gắn thẻ soạn bình luận nội tuyến ngay đầu mục `Bình luận (N)` kèm ảnh đại diện người dùng hiện tại, ô nhập bo tròn có nút gửi ở góc dưới phải và `imePadding()`.
- **Trang Hồ sơ (`PublicProfileScreen.kt`, `UserIdentityComponents.kt`, `TwelveAppShell.kt`)**:
  - Bỏ `Card` viền bao quanh phần đầu hồ sơ; dựng khối ảnh bìa 3:1 (hoặc dải tonal khi chưa có ảnh bìa) với ảnh đại diện tròn `84.dp` chồng lên góc dưới trái có viền tách nền `3.5.dp`.
  - Hiển thị Tên + Huy hiệu, dòng `@tag` viền mảnh + biểu tượng quả cầu & mã quốc gia viết hoa (`VN`), tiểu sử thuần, một nút pill `Xem thêm` duy nhất và tab có biểu tượng (`Trang chủ`, `Bài viết`, `Blog`) + tiêu đề `Bài viết đã ghim`.
  - Đưa các nút thao tác (`Chỉnh sửa`, `Khách thăm`, `Sao chép liên kết hồ sơ`) lên `TopAppBar` cả ở tab `Hồ sơ` và màn hình hồ sơ mở qua điều hướng; bổ sung `TagNameNormalizer.buildWebProfilePath` khớp `frontend/src/utils/profilePath.js` kèm unit test.

