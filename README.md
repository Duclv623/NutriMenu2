# NutriMenu2

Đã tích hợp commit `8eb7061` trên nhánh `main`. Hợp đồng hồ sơ, món và thực đơn cá nhân dùng các tên trường mới. Đổi món có kiểm tra hỗ trợ 0,5–12 suất theo quy tắc từng món. Xem [hợp đồng API](frontend/API_CONTRACT.md) và [nhật ký tích hợp trước đó](UPSTREAM-20261126.md).

Bản tích hợp dùng cấu trúc của repo nhóm, JWT do User Service Java cấp và Spring Cloud Gateway Java định tuyến. Mã nguồn được tích hợp tại `C:\NutriMenu2`; bản `C:\testda` chỉ được dùng để đọc và đối chiếu.

| Thư mục | Công nghệ | Cổng | Trách nhiệm |
|---|---|---:|---|
| BackendNutriMenu/Userservice | Java 21, Spring Boot, PostgreSQL | 8083 | Đăng ký, JWT, hồ sơ, nhật ký, trạng thái mua sắm |
| BackendNutriMenu/foodcatalogservice | Java 21, PostgreSQL | 8081 | Món NIN, thành phần, chất lượng nguồn, yêu thích |
| BackendNutriMenu/mealoptimizerservice | Java 21, OR-Tools CP-SAT | 8082 | Sinh, lưu, tạo lại, đổi món có kiểm tra |
| BackendNutriMenu/gateway | Spring Cloud Gateway | 8080 | Kiểm tra JWT, phân quyền và định tuyến |
| ai-service | Python 3.12, FastAPI, ChromaDB, PostgreSQL | 8000 | RAG, ngữ cảnh thực đơn, hội thoại |
| frontend/src/features | React, TypeScript, Vite | 5173 | Giao diện chia theo tính năng |

## Chạy tại máy này

Dừng các terminal đang chạy bản cũ để giải phóng các cổng trên. Kiểm tra mật khẩu PostgreSQL trong `BackendNutriMenu/.env` và DSN chatbot trong `ai-service/.env`.

```powershell
cd C:\NutriMenu2
.\setup.ps1 -CreateDatabases -Build
.\run-all.ps1
```

Lệnh setup tạo thêm database còn thiếu, giữ database đang có và chuẩn bị khóa JWT lưu bền trong `.runtime/keys`. Lệnh build cần truy cập Maven Central để tải dependency JWT/Spring Cloud còn thiếu. Máy mới cần Java 21, Maven, Node 22.12+, Python 3.12, PostgreSQL; dùng thêm `-Install` để cài thư viện Python/Node và chuẩn bị tokenizer.

`run-all.ps1` chạy dịch vụ nền, ghi log vào `.runtime/<service>.log` và `<service>-error.log`. Nếu muốn mở 6 cửa sổ PowerShell riêng để xem log trực tiếp như bản cũ, dùng `.\run-all.ps1 -LaunchAll`. Lệnh chỉ báo đã khởi chạy tiến trình; cần kiểm tra sau khi dịch vụ khởi động xong:

```powershell
.\check-demo.ps1
```

Mở http://127.0.0.1:5173 và dùng thống nhất hostname này. Để dừng các tiến trình do lần chạy này tạo:

```powershell
.\stop-all.ps1
```

Có thể chạy từng dịch vụ ở các terminal riêng bằng `run-service.ps1 users`, `catalog`, `optimizer`, `gateway`, `chatbot`, `frontend`.

## Giữ liên kết dữ liệu tài khoản cũ

Các migration V1/V2 của User Service khớp bản cũ; V3 thêm bảng ánh xạ tài khoản và trạng thái giao diện. Gateway Python cũ dùng một ánh xạ số khác. Cần chuyển ánh xạ trước lần đăng nhập đầu tiên trên bản mới để đọc đúng thực đơn, yêu thích và nhật ký đang có.

Sau khi User Service chạy migration V3, kiểm tra cấu hình `LEGACY_GATEWAY_POSTGRES_DSN` và `LEGACY_GATEWAY_SCHEMA` trong `BackendNutriMenu/.env`, rồi chạy:

```powershell
.\ai-service\.venv\Scripts\python.exe scripts\migrate_legacy_accounts.py
.\ai-service\.venv\Scripts\python.exe scripts\migrate_legacy_accounts.py --apply
```

Lệnh đầu chỉ đọc và thống kê. Lệnh thứ hai ghi trong một transaction ở database đích, giữ nguồn cũ, kiểm tra xung đột ID/trạng thái và dừng nếu có dữ liệu mới khác nhau. Không đọc danh tính từ `state.json` của bản Node. Chỉ chuyển các UUID đã có trong User Service. Việc chuyển này chưa chạy được trong môi trường kiểm thử bị chặn socket.

## Dữ liệu và luồng đã ghép

- Catalog `nin-bulk-20261126`: 1.023 món, 597 món đủ dữ liệu cho bộ sinh phổ thông; 426 món cần bổ sung hoặc đối chiếu. Có 1.023 ảnh cục bộ, 1.063 ánh xạ ảnh gồm cả mã lịch sử.
- Chỉ mục RAG đã chuyển có 193 đoạn; 33 nguồn đã kiểm tra SHA-256. Chroma giữ corpus/vector, PostgreSQL giữ hội thoại theo tài khoản.
- Yêu thích hỗ trợ thêm/bỏ và lưu qua Catalog; lịch sử vẫn đọc được món đã rời danh mục chọn mới.
- Dashboard có khoảng 7/14/30 ngày, phân biệt kế hoạch với món đã ghi nhận ăn, cảnh báo và liên kết hành động.
- Điều hướng dùng cache theo tài khoản trong bộ nhớ; tái sử dụng danh mục và dữ liệu đã tải, làm mới nền khi quá hạn. Nhóm món/bộ lọc và nhãn dị ứng hiển thị tiếng Việt.
- Danh sách mua sắm theo thực đơn giữ lượng dạng chữ, đơn vị nguồn và cảnh báo phần còn thiếu; trạng thái đã mua lưu ở User Service.
- Màn thực đơn cá nhân hỗ trợ tạo 1 ngày hoặc 7 ngày liên tiếp. Cả 7 ngày phải còn trống; ghi cả tuần trong một transaction, giữ cùng phiên bản hồ sơ. Các món có thể lặp giữa các ngày.
- Tạo lại từng ngày cần ID, phiên bản và xác nhận; bộ tối ưu lưu revision trước khi thay thế thành công. Món khóa/đã ăn ngăn tạo lại. Đổi món cần preview và chữ ký để apply.
- Chatbot đọc thực đơn sở hữu bởi JWT hiện tại. Yêu cầu chỉnh sửa mở trình đổi món để người dùng chọn và xác nhận.

Giá trị dinh dưỡng hoặc định lượng chưa biết được giữ là `null`. Bộ NIN hiện có 0 món đủ bằng chứng cho chế độ bệnh lý do thiếu GI/GL; chọn rõ **Phổ thông / tham khảo** để thử bộ mới. Phạm vi này không phải xác nhận dùng cho điều trị.

## Kiểm thử

```powershell
.\verify.ps1 -Offline
```

Kiểm tra Catalog, Optimizer/CP-SAT, frontend, dữ liệu/ảnh, collector và 136 ca RAG/JWT đã chọn. RAG mặc định dùng corpus đã xuất sau khi kiểm tra dấu vân tay nguồn; `-RebuildCorpus` xây lại từ tài liệu và cần tokenizer. `-AllJava` thêm build/test User Service và Gateway, cần đủ dependency; `-WithDatabases` bật các test khởi động Spring với PostgreSQL.

Báo cáo chi tiết: [INTEGRATION-20261126.md](INTEGRATION-20261126.md). Các kiểm tra tự động đã đạt không thay thế nghiệm thu trên dịch vụ đang chạy.
