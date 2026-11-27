# Báo cáo tích hợp NutriMenu2 — 26/11/2026

Mã được đưa từ bản repo mới tại `C:\testda\NutriMenu2` vào `C:\NutriMenu2`, sau khi ghi/đọc/xóa file kiểm tra quyền thành công. Các cải tiến được đối chiếu từ bản đang hoạt động ở `C:\testda`. Không sửa mã nguồn trong bản cũ.

## Phần đã hoàn thành trong mã

| Phần | Kết quả |
|---|---|
| Kiến trúc | Giữ BackendNutriMenu gồm 4 module Java, ai-service và frontend/src/features |
| Xác thực | Giữ CSRF cho đăng ký/đăng nhập, JWT RSA và Java Gateway; thêm khóa lưu bền, health endpoint |
| Catalog NIN | Ghép importer, quality/source policy, nullable schema, 1.023 món và ảnh đúng định danh |
| Yêu thích | PUT/DELETE qua JWT Gateway; bỏ thích giữ đúng trạng thái khi lỗi và sau khi tải lại |
| Sinh/tạo lại | Chuyển planningMode và profileVersion qua adapter Java; tạo lại có xác nhận, kiểm tra phiên bản và archive |
| Đổi món | Preview/apply dùng chữ ký, phiên bản và kiểm tra quyền sở hữu; giữ trạng thái cũ nếu bị từ chối |
| Dashboard | 7/14/30 ngày, nhật ký món đã ăn, lượng còn thiếu dữ liệu, cảnh báo và hành động liên quan |
| Mua sắm | Gom theo thực đơn, giữ số liệu chưa biết và lượng nguồn dạng chữ, lưu checkbox ở User Service; 37 món có ghi chú nguyên liệu chưa định lượng được đưa vào giao diện/ngữ cảnh chat |
| RAG | Corpus/vector Chroma, hội thoại PostgreSQL, JWT introspection Java, đọc thực đơn và chuyển sang trình chỉnh sửa |
| Vận hành | setup.ps1, run-service.ps1, run-all.ps1, stop-all.ps1, check-demo.ps1, verify.ps1 |
| Dữ liệu tài khoản | Có công cụ audit/chuyển ánh xạ PostgreSQL cũ, kiểm tra xung đột và giữ nguyên nguồn; chưa thực thi live |

## Bằng chứng tự động đã đạt

| Bộ kiểm tra | Kết quả | Bằng chứng |
|---|---:|---|
| Food Catalog | 19/19 | catalog-final.log và target/surefire-reports |
| Meal Optimizer, adapter và CP-SAT native | 60/60 | weekly-optimizer-test.log và target/surefire-reports |
| Frontend, UI và hợp đồng JWT | 78/78 | navigation-test.log |
| Production frontend build | Đạt | navigation-build.log |
| Collector/builder NIN | 29/29 | collector-test.log |
| RAG có cấu trúc, nguồn, đối thoại thực đơn và JWT bridge | 136/136 | rag-final.log |
| Tổng các ca trên | **322 đạt** | Không gồm test cần database/HTTP live |
| Toàn bộ ảnh và nguồn đóng gói | Đạt | catalog-data-verification.json |
| Corpus Chroma | 193 đoạn, hybrid; embedding và dấu vân tay nguồn khớp | rag-corpus-verification.log |
| Tái tạo catalog từ bằng chứng | Đạt khi giữ nguyên nội dung catalog, cập nhật provenance builder cho đường dẫn mới | catalog-reproducibility.log |

Ca RAG dùng dữ liệu/corpus thật và provider/HTTP được kiểm soát khi cần; không gọi API mô hình thật. Corpus được đối chiếu dấu vân tay với các tài liệu hiện tại trước khi dùng. Các ca HTTP FastAPI/TestClient và hội thoại PostgreSQL live chưa được tính là đạt.

CP-SAT native trên bộ mới đã tìm được các phương án sau; số đo của lần chạy này, không phải cam kết thời gian hoặc kết quả cho mọi hồ sơ:

| Trường hợp | Candidates | Trạng thái | kcal | Natri mg |
|---|---:|---|---:|---:|
| Loại trứng + đậu nành, mục tiêu 1.600 | 55 | FEASIBLE | 1.599,80 | 1.842,95 |
| Loại trứng + đậu nành, mục tiêu 1.800 | 55 | FEASIBLE | 1.800,15 | 1.181,60 |
| Loại trứng + đậu nành, mục tiêu 2.000 | 55 | FEASIBLE | 2.000,00 | 824,70 |
| Không dị ứng, mục tiêu 1.800 | 138 | FEASIBLE | 1.799,20 | 2.399,35 |

Bằng chứng dữ liệu: 1.023 món, 597 đủ điều kiện phổ thông, 0 đủ điều kiện bệnh lý; 1.063 ánh xạ / 1.023 file ảnh. SHA-256 catalog xuất: `36b983a54379072ad90b4f9f31e0642d98719690c56a46a70187d411c88d7da3`, khớp bản cũ. Nguồn API có 1.024 dòng thô và 1.023 bản ghi được xuất; không tuyên bố đã bao phủ toàn bộ website. 686 ảnh crop được chọn, 337 ảnh gốc dự phòng; chưa duyệt thị giác lại toàn bộ.

## Phần chưa thể xác nhận

1. Maven của máy chưa có dependency Spring Cloud 2025.1.2 và OAuth2 resource server. DNS tới Maven Central bị chặn trong môi trường thực thi. Gateway và User Service vì vậy chưa compile/chạy được ở đây. Cặp Spring Cloud/Boot được giữ theo repo; starter OAuth2 đổi sang tên chính thức cho Boot 4.
2. Socket localhost/PostgreSQL bị chặn: test context Spring thất bại ở kết nối database; FastAPI TestClient bị kẹt khi asyncio tạo socketpair. Đã kiểm tra nguyên nhân bằng stack trace, không tính các ca đó là đạt.
3. `check-demo.ps1` chạy trong môi trường này chưa kết nối được các dịch vụ (runtime-check.log). Chưa xác nhận đăng nhập, sinh/lưu/tạo lại/đổi món và chat qua trình duyệt trên bản chạy thật.
4. Chưa chạy công cụ chuyển ánh xạ tài khoản cũ hoặc copy hội thoại PostgreSQL cũ. Cấu hình nguồn được giữ trong file .env cục bộ; công cụ chỉ ghi đích khi dùng --apply và từ chối dữ liệu xung đột.
5. Số liệu GI/GL của bộ NIN mới còn thiếu. Chế độ bệnh lý chưa có món đủ điều kiện; số liệu thiếu được giữ nguyên. Luồng phổ thông đã qua native solver.

Hai test khởi động Spring cần database được gắn tag `integration`; chạy bằng profile `integration-tests` hoặc `verify.ps1 -WithDatabases`. Bộ RAG đã chọn không thay cho toàn bộ suite HTTP/database và không chứng minh câu trả lời bằng API mô hình thật.

## Nghiệm thu tiếp trên máy chạy dịch vụ

1. Dừng bản cũ, kiểm tra PostgreSQL và chạy `setup.ps1 -CreateDatabases -Build` ở PowerShell thông thường có mạng.
2. Chạy `run-all.ps1`; kiểm tra log từng dịch vụ và chạy `check-demo.ps1`.
3. Nếu giữ dữ liệu tài khoản cũ, chạy audit rồi chuyển ánh xạ trước lần đăng nhập đầu tiên. Chỉ tiếp tục khi không có xung đột; nguồn cũ được giữ.
4. Đăng ký tài khoản thử riêng và đăng nhập; tải lại trang, kiểm tra JWT và hồ sơ được lưu.
5. Mở danh mục, xem ảnh/thành phần, thêm rồi bỏ yêu thích, tải lại và đối chiếu.
6. Sinh thực đơn Phổ thông với đúng dị ứng/món không ăn. Xem kcal, chất còn thiếu dữ liệu và lưu sau tải lại.
7. Tạo lại có xác nhận; thử với món khóa hoặc đã ăn, kiểm tra không ghi đè. Xem trước đổi món rồi xác nhận; trường hợp bị từ chối phải giữ kế hoạch cũ.
8. Ghi nhận/bỏ ghi nhận đã ăn; kiểm tra dashboard 7/14/30 ngày, cảnh báo và phiên bản. Mở mua sắm, đánh dấu rồi tải lại.
9. Chọn đúng thực đơn trong chatbot: hỏi bữa trưa gồm gì, hỏi tiếp thành phần một món, tổng kcal và dữ liệu chất xơ đã đủ chưa. Thử yêu cầu đổi món và hoàn thành trong trình chỉnh sửa có kiểm tra.
10. Đăng nhập tài khoản thử thứ hai, kiểm tra thực đơn/yêu thích/nhật ký/hội thoại không lẫn tài khoản đầu.

Tài liệu dependency chính thức: https://spring.io/projects/spring-cloud/ và https://docs.spring.io/spring-boot/4.0/reference/using/build-systems.html .


## Sửa lỗi tạo phổ thông và nối sinh 7 ngày

Đã tái hiện HTTP 400 bằng MockMvc với đúng JSON của frontend: trường optional `replacementConfirmed` bị thiếu, nhưng DTO cũ khai báo primitive boolean. Jackson 3 từ chối chuyển null thành boolean trước khi vào controller/CP-SAT. DTO hiện dùng Boolean và mặc định false; không tắt kiểm tra JSON trên toàn ứng dụng. JSON lỗi có thông báo rõ hơn. Đồng thời sửa khai báo JPA template_code để tránh DDL PostgreSQL `alter column ... type varchar(50) default ...` sai cú pháp.

Repo nhóm đã có tác vụ sinh bảy ngày trong công cụ quản trị và core Optimizer. Màn cá nhân nay nối vào core này qua `POST /api/meal-plans/generate-weekly`, dùng JWT/profile/dị ứng/món không ăn của tài khoản hiện tại. API kiểm tra toàn khoảng ngày, giữ phiên bản hồ sơ trên từng ngày và dùng transaction để ghi cả tuần. Giao diện có lựa chọn 1/7 ngày khi backend công bố capability, giữ toàn bộ bảy kết quả và không ghi đè một ngày đã có. Tạo lại vẫn xử lý từng ngày có xác nhận.

Native test đã chạy core weekly với 55 candidates NIN đủ bằng chứng loại trứng/đậu nành: cả 7 ngày từ 26/11 đến 02/12/2026 đều FEASIBLE, năng lượng 1.798,95–1.800,50 kcal/ngày. Kho dữ liệu/repository được kiểm soát trong test; chưa phải kiểm chứng ghi PostgreSQL live. Frontend có test tạo đủ bảy ngày, chặn ngày đã có, giữ dữ liệu khi kết quả thiếu ngày. Tổng mới: 60 test Optimizer và 64 test frontend đều đạt.

Cần khởi động lại Meal Optimizer để JVM nạp DTO và endpoint mới; frontend cần tải lại để đọc capability mới. Các tiến trình đang chạy trước khi sửa vẫn dùng lớp Java cũ. Công cụ tác vụ hiện vẫn bị chặn socket localhost, nên chưa xác nhận lại qua phiên trình duyệt đang chạy của người dùng.


## Cập nhật điều hướng và tiếng Việt

Trước sửa, UserDataProvider gọi lại loadPageData theo pathname và bật Loading toàn trang; mỗi lượt có thể tải lại profile, cả danh mục 1.023 món, yêu thích và thực đơn. Hiện có cache riêng cho từng tài khoản trong bộ nhớ, gộp các request đang chạy và làm ấm phần dữ liệu còn lại sau màn đầu. Danh mục giữ tối đa 5 phút, dữ liệu cá nhân 30 giây trước lần kiểm tra lại khi điều hướng. Màn đã có dữ liệu giữ nội dung khi cập nhật nền hoặc khi làm mới gặp lỗi; lần đầu thiếu dữ liệu vẫn có trạng thái tải.

Cập nhật yêu thích, thực đơn, ghi nhận ăn, hồ sơ và mua sắm được áp dụng ngay vào cache; phản hồi đọc cũ không ghi đè thay đổi mới. Đổi/thoát tài khoản tạo cache mới; phản hồi cũ và 401 từ JWT cũ không xóa phiên mới. Món lịch sử còn trong kế hoạch được giữ để đọc khi danh mục làm mới, và được đánh dấu không dùng để chọn món mới.

Các nhãn COMPOSITE_DISH, MAIN_STARCH, PROTEIN_DISH, VEGGIE_DISH, SNACK, SOUP được ánh xạ sang tiếng Việt ở adapter và phần hiển thị, gồm bộ lọc, thẻ món và chi tiết. Mã role/dị ứng dùng cho các quy tắc API được giữ nguyên. Nhãn dị ứng trong chi tiết cũng hiển thị tiếng Việt.

78 test frontend đạt, build production đạt. Các test mới kiểm tra điều hướng ngay trên dữ liệu đã tải, số lần gọi API, gộp request, cập nhật dữ liệu cục bộ, phiên tài khoản, phản hồi đến muộn, lỗi làm mới, món lịch sử và bộ lọc tiếng Việt. Đây là bằng chứng tự động; chưa đo thời gian điều hướng trên trình duyệt đang chạy của người dùng. Tải lại cứng bằng Ctrl+F5 để nạp phiên bản frontend mới.


## Mã nguồn nhóm cập nhật sau tích hợp

Đã ghép hai commit mới đến `760f06630e6c258d6b4611ca5ba716d9ed381b43` vào workspace. Chi tiết kiểm thử, phần giữ lại và cách khởi động lại dịch vụ nằm trong [UPSTREAM-20261126.md](UPSTREAM-20261126.md). Gateway hiện đã compile thành công với cache dependency tại máy; giới hạn kiểm tra socket/database trực tiếp vẫn còn.
