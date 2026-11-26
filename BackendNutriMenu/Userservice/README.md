# MenuFood User Service

Java 21, Spring Boot 4.1.1, PostgreSQL. Cấu trúc Controller → Service → Repository; DTO riêng cho request/response.

## Chạy ứng dụng

Configure PostgreSQL in the shared `../.env` file (see `../.env.example`), then run from this module:

```powershell
.\mvnw.cmd spring-boot:run
```

Service chạy tại `http://localhost:8083`. Có thể đổi bằng biến `PORT`. Không lưu mật khẩu thật vào source.

Flyway chạy migration `src/main/resources/db/migration/V1__create_users_and_profiles.sql` trên database rỗng. Hibernate chỉ kiểm tra schema (`validate`), không tự sửa bảng. Migration tạo bảng `users`, `user_profiles` và ba bảng danh sách ăn uống. Không sửa migration đã chạy; các thay đổi sau cần migration mới.

## API hiện có

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/api/auth/csrf` | Lấy token CSRF và cookie phiên |
| POST | `/api/auth/register` | Đăng ký tài khoản, trả HTTP 201 |
| GET | `/api/users/me` | Xem tài khoản đang xác thực |
| GET | `/api/users/me/profile` | Xem hồ sơ; trả 404 nếu chưa tạo |
| PUT | `/api/users/me/profile` | Tạo hoặc thay thế toàn bộ hồ sơ |

Gateway xác thực JWT trước khi chuyển request đến User Service. HTTP Basic vẫn được hỗ trợ khi gọi trực tiếp User Service. `POST /api/auth/login` nhận `email`, `password`, trả `accessToken`, `tokenType`, `expiresAt`, `user`. Gửi `Authorization: Bearer <accessToken>` cho API tài khoản/hồ sơ. Gateway đọc khóa công khai từ `GET /api/auth/jwks` để kiểm tra chữ ký RS256. Đặt `JWT_ISSUER` và `JWT_AUDIENCE` giống nhau ở hai service. User Service tự tạo khóa khi khởi động ở development. Ở các môi trường khác, cấu hình `JWT_PRIVATE_KEY_PATH` và `JWT_PUBLIC_KEY_PATH`. `JWT_TTL_SECONDS` điều khiển thời hạn token.

CSRF được bật cho đăng ký, đăng nhập và HTTP Basic. Trước POST/PUT, gọi `GET /api/auth/csrf`, giữ cookie `JSESSIONID`, rồi gửi token trong header được chỉ ra bởi `headerName` (mặc định `X-CSRF-TOKEN`). Request có Bearer token được miễn CSRF.

Cấu trúc: `controller` nhận request, `service` định nghĩa nghiệp vụ, `service/impl` triển khai, `dao/repository` truy cập dữ liệu, `mapper` chuyển đổi hồ sơ, `dto/request` và `dto/response` định nghĩa API, `config` cấu hình bảo mật, `exception` xử lý lỗi.

Mặc định giữ database của nhánh tích hợp. Có thể bật `SPRING_PROFILES_ACTIVE=postgres` để dùng cấu hình triển khai cũ (cổng 5433, schema `user_service`, mật khẩu từ `DB_PASSWORD`). Profile này không chuyển đổi dữ liệu cũ: database đã chạy `V2__meal_profile_contract.sql` cần kế hoạch migration riêng vì khác V2 hiện tại. Không chạy repair hoặc sửa lịch sử Flyway để bỏ qua khác biệt này.

Ví dụ đăng ký:

```json
{
  "email": "user@example.com",
  "password": "ExamplePassword123!",
  "fullName": "Nguyễn Văn An"
}
```

Email được chuẩn hóa thành chữ thường. Mật khẩu dài 8–64 ký tự và tối đa 72 byte UTF-8 do giới hạn BCrypt. API không trả mật khẩu hoặc giá trị băm. Người đăng ký luôn có vai trò `USER`.

Ví dụ PUT hồ sơ:

```json
{
  "dateOfBirth": "1995-04-12",
  "gender": "MALE",
  "height": 170,
  "weight": 75,
  "activityLevel": "MODERATE",
  "goal": "LOSE_WEIGHT",
  "targetWeight": 68,
  "confirmed": true,
  "pregnant": false,
  "specialDiet": false,
  "expectedVersion": 0,
  "allergies": ["tôm", "đậu phộng"],
  "restrictedIngredients": ["đường"],
  "preferences": ["cá", "rau xanh"]
}
```

- `dateOfBirth`: bắt buộc, người từ đủ 18 tuổi. `age` được tính khi trả hồ sơ, không lưu riêng.
- `height`: cm; `weight`, `targetWeight`: kg. Số dương, tối đa 3 chữ số nguyên và 2 chữ số thập phân.
- `gender`: `MALE`, `FEMALE`, `OTHER`.
- `activityLevel`: `SEDENTARY`, `LIGHT`, `MODERATE`, `HIGH`, `VERY_HIGH`.
- `goal`: `LOSE_WEIGHT`, `MAINTAIN_WEIGHT`, `GAIN_WEIGHT`.
- `targetWeight`: tùy chọn, chưa kiểm tra tương quan với `goal`.
- `confirmed`, `pregnant`, `specialDiet`: giữ theo hợp đồng hồ sơ của nhánh tích hợp. Không thêm lại các trường bệnh lý và `mealActivityLevel` của bản cũ.
- `expectedVersion`: có thể bỏ khi tạo hồ sơ lần đầu; khi cập nhật phải gửi `version` từ lần đọc gần nhất. Thiếu hoặc sai phiên bản trả HTTP 409, cần tải lại hồ sơ trước khi lưu.
- Ba danh sách: tối đa 100 mục/danh sách, mỗi mục tối đa 100 ký tự và không được rỗng. Tạm lưu tên tự do; loại trùng sau khi bỏ khoảng trắng đầu/cuối, phân biệt hoa/thường. Chưa liên kết ID danh mục của Food Service.
- PUT thay thế hồ sơ, không phải cập nhật từng trường. Bỏ qua danh sách sẽ thay bằng danh sách rỗng; truyền `null` bị từ chối. Các trường cốt lõi phải đầy đủ, chưa hỗ trợ lưu hồ sơ nháp.
- API lấy chủ hồ sơ từ thông tin xác thực. Không nhận `userId` từ client hoặc tin cậy header `X-User-ID`.

Phản hồi thành công có `success`, `data`, `message`. Các lỗi nghiệp vụ có `timestamp`, `status`, `message`, `path`, `errors`: 400 cho dữ liệu không hợp lệ, 401 cho xác thực, 403 cho quyền/CSRF, 404 khi chưa có hồ sơ, 409 cho dữ liệu trùng.

## Kiểm thử và build

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

Test dùng H2 ở chế độ PostgreSQL và chạy cùng migration Flyway; không cần thông tin database thật. Đây chưa phải kiểm chứng tương thích trên PostgreSQL thực tế.

## Phần chưa triển khai

Thu hồi JWT trước khi hết hạn, xác minh email, đổi/quên mật khẩu, quản trị tài khoản, sửa họ tên, lịch sử cân nặng và lưu hồ sơ nháp.

User Service chưa tính nhu cầu dinh dưỡng, gợi ý thực đơn hoặc chatbot. Trường `OTHER` hiện chỉ lưu lựa chọn giới tính; service tính mục tiêu dinh dưỡng cần thống nhất cách xử lý dữ liệu này.

## API hồ sơ và nhật ký

- `GET/PUT /api/me/profile` đọc và cập nhật hồ sơ dinh dưỡng.
- `GET/POST /api/me/check-ins` và `DELETE /api/me/check-ins/{id}` quản lý nhật ký.
- `GET/PUT /api/me/shopping-list` đọc và lưu các mục đã đánh dấu.
- `GET /api/internal/identity` xác thực Bearer JWT và trả mã tài khoản ổn định.
- `GET /api/internal/meal-account` cung cấp cho Optimizer hồ sơ và phiên bản của các thực đơn cũ thuộc tài khoản hiện tại.

Flyway V3 giữ ánh xạ UUID sang ID số, nhật ký và danh sách mua sắm trong `workspace_accounts`.
Catalog quản lý món ăn và yêu thích. Optimizer quản lý thực đơn và cung cấp dữ liệu cho chatbot.
Đăng nhập đặt cookie HttpOnly cho tài liệu chatbot. Đăng xuất xóa cookie, JWT đã cấp có hiệu lực đến khi hết hạn.
