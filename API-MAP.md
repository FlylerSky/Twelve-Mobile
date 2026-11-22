# Bảng đối chiếu API — Twelve Mobile

Quy ước trạng thái: **đã nối** (đã có Retrofit/Repository + UI + test kiểm chứng) | **chưa nối** (thuộc mốc sau) | **CHƯA XÁC MINH** (chưa mở file kiểm chứng).

## M0 — Khung nền, Xác thực & Trạng thái tài khoản

| Màn hình / Luồng | Endpoint | File + Hàm backend xác nhận | Trạng thái |
|---|---|---|---|
| Khởi động / Kiểm tra máy chủ | `GET https://twelveta.onrender.com/health` | `backend/server.js` (`app.get("/health")`) | đã nối |
| Đăng nhập phiên / Nạp hồ sơ | `GET /api/users/me` | `backend/src/routes/users.routes.js` (`requireAuthAllowPending`), `backend/src/controllers/users.controller.js#getMe`, `backend/src/middleware/auth.js` | đã nối |
| Đồng bộ kích hoạt `pending` → `active` | `POST /api/auth/activation/sync` | `backend/src/routes/auth.routes.js` (`requireAuthAllowPending`), `backend/src/controllers/auth.controller.js#syncActivation` | đã nối |
| Chặn `suspended` / `deleting` | Mọi endpoint xác thực trả `403` `ACCOUNT_SUSPENDED` / `ACCOUNT_DELETING` | `backend/src/middleware/auth.js#verifyRequestToken`, `backend/src/controllers/auth.controller.js#syncActivation` | đã nối |
| Lỗi tài khoản chưa kích hoạt | `403` `ACCOUNT_NOT_ACTIVE` | `backend/src/middleware/auth.js#requireAuth` | đã nối |
| Đồng bộ hồ sơ realtime | Firestore read-only `users/{uid}` | `backend/firestore.rules` (`allow read` / `allow write: if false`) | đã nối |
| Đăng ký & Quên mật khẩu | Mở `https://twelveta.vercel.app/register` và `/login` qua Custom Tab | `backend/src/routes/auth.routes.js` (`verifyCaptcha`), `backend/src/middleware/captcha.js` | đã nối |

## M1 — Đọc nội dung, Hồ sơ công khai & Xu hướng

| Màn hình / Luồng | Endpoint | File + Hàm backend xác nhận | Trạng thái |
|---|---|---|---|
| Bảng tin Trang chủ | `GET /api/posts?cursor=&limit=10` | `backend/src/controllers/posts.controller.js#listPosts` | đã nối |
| Chi tiết bài viết (đếm lượt xem / lượt mở chia sẻ) | `GET /api/posts/:id?idu=&s=&shared=&sid=` | `backend/src/controllers/posts.controller.js#getPost` | đã nối |
| Danh sách Blog & Chi tiết Blog | `GET /api/blogs?cursor=&limit=10`, `GET /api/blogs/:id?idu=&s=&shared=&sid=` | `backend/src/controllers/blog.controller.js#listBlogs`, `getBlog` | đã nối |
| Đọc bình luận | `GET /api/{posts\|blogs}/:id/comments?cursor=&limit=20` | `backend/src/controllers/comments.controller.js#listComments` | đã nối |
| Hồ sơ công khai (UID / `@tag`) | `GET /api/users/:uid`, `GET /api/users/by-tag/:tag`, `posts`, `pinned`, `blogs`, `extra-info`, `POST /api/users/:uid/visit` | `backend/src/controllers/users.controller.js` (`getPublicProfile`, `getPublicProfileByTag`, `listUserPosts`, `listPinnedPosts`, `listUserBlogs`, `getExtraInfo`, `recordProfileVisit`) | đã nối |
| Xu hướng & Tìm kiếm | `GET /api/trending/users/{weekly\|alltime}`, `hashtags/{weekly\|alltime\|:tag/posts}?mode=`, `search?q=&type=&sort=&mode=` | `backend/src/controllers/trending.controller.js` | đã nối |

## M2 — Tương tác & Quản lý bài viết

| Màn hình / Luồng | Endpoint | File + Hàm backend xác nhận | Trạng thái |
|---|---|---|---|
| Thích / Không thích (lạc quan + rollback) | `POST /api/{posts\|blogs}/:id/react` | `backend/src/controllers/reactions.controller.js#reactToPost`, `reactToBlog` | đã nối |
| Bình luận (thêm / sửa / xoá) | `POST /api/{posts\|blogs}/:id/comments`, `PATCH/DELETE .../comments/:commentId` | `backend/src/controllers/comments.controller.js` (`addComment`, `editComment`, `deleteComment`) | đã nối |
| Chia sẻ bài viết / blog | `POST /api/{posts\|blogs}/:id/share` | `backend/src/controllers/share.controller.js` (`sharePost`, `shareBlog`) | đã nối |
| Tạo / Sửa / Xoá / Ghim bài viết | `POST /api/posts`, `PATCH /api/posts/:id`, `DELETE /api/posts/:id`, `PUT/DELETE /api/posts/:id/pin` | `backend/src/controllers/posts.controller.js` (`createPost`, `updatePost`, `deletePost`, `pinPost`, `unpinPost`) | đã nối |
| Listener realtime doc & bình luận | Firestore read-only `posts|blogs/{id}` & `comments` (`limit <= 50`) | `backend/firestore.rules`, `frontend/src/hooks/useRealtimeDoc.js`, `usePaginatedComments.js` | đã nối |

## M3 — Hồ sơ cá nhân, Cài đặt & Blog SA

| Màn hình / Luồng | Endpoint | File + Hàm backend xác nhận | Trạng thái |
|---|---|---|---|
| Sửa thông tin & Quốc gia | `PATCH /api/users/me`, `GET /api/countries` | `backend/src/controllers/users.controller.js#updateMe`, `countries.controller.js#listCountries` | đã nối |
| Ảnh đại diện & Ảnh bìa | `POST /api/users/me/avatar`, `POST/PUT/DELETE /api/users/me/cover`, `PUT /api/users/me/cover/crop` | `backend/src/controllers/users.controller.js` (`uploadAvatar`, `uploadCover`, `setCoverUrl`, `setCoverCrop`, `removeCover`) | đã nối |
| Đổi Tag Name Search | `GET /api/auth/check-tagname`, `PUT /api/users/me/tag-name` | `backend/src/controllers/auth.controller.js#checkTagName`, `account.controller.js#updateTagName` | đã nối |
| Thông tin mở rộng & Khách thăm | `PUT /api/users/me/extras`, `GET /api/users/me/visitors` | `backend/src/controllers/users.controller.js#updateExtras`, `listMyVisitors` | đã nối |
| Tên thật & Xoá tài khoản (Reauth) | `GET/PUT /api/users/me/real-name`, `POST /api/users/me/deletion` | `backend/src/controllers/account.controller.js#getRealName`, `updateRealName`, `requestDeletion` | đã nối |
| Tạo / Sửa / Xoá Blog (chỉ SA) | `POST /api/blogs`, `PATCH /api/blogs/:id`, `DELETE /api/blogs/:id` | `backend/src/routes/blog.routes.js` (`requireSA`), `backend/src/controllers/blog.controller.js` | đã nối |
| Đổi mật khẩu | Firebase Auth `reauthenticate` + `updatePassword` | `frontend/src/pages/settings/ChangePasswordPanel.jsx` | đã nối |

## M4 — Liên kết sâu (Deep Links) & Hoàn thiện

| Màn hình / Luồng | Đường dẫn Web ↔ Tuyến Mobile | File đối chiếu | Trạng thái |
|---|---|---|---|
| Mở bài viết chia sẻ | `https://twelveta.vercel.app/post/:id?idu=&s=&shared=` → `ContentDetailRoute` | `frontend/src/App.jsx`, `backend/src/controllers/share.controller.js` | đã nối |
| Mở Blog chia sẻ | `https://twelveta.vercel.app/blog/:id?idu=&s=&shared=` → `ContentDetailRoute` | `frontend/src/App.jsx`, `backend/src/controllers/blog.controller.js` | đã nối |
| Mở hồ sơ theo `@tag` / `myProfile` / UID | `https://twelveta.vercel.app/u/{@tag\|myProfile\|uid}` → `PublicProfileRoute` / `MyProfileTabRoute` | `frontend/src/utils/profilePath.js#classifyProfileRef` | đã nối |
