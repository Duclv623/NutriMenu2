# Thu thập toàn bộ danh mục món NIN vào staging

`collect_nin_bulk.py` và `collect-nin-bulk.ps1` chỉ thu thập/kiểm tra bằng chứng nguồn. Không sửa fixture 40 món đang chạy, không import staging vào database, không sinh công thức và không gọi LLM API.

Chạy từ PowerShell trên máy có kết nối được `viendinhduong.vn`:

```powershell
cd C:\testda
.\collect-nin-bulk.ps1 -Mode Collect -RunId bulk-20261125
```

Nếu chính sách máy chặn chạy script, có thể mở PowerShell với quyền thực thi riêng cho lệnh này theo chính sách của máy:

```powershell
powershell -ExecutionPolicy Bypass -File C:\testda\collect-nin-bulk.ps1 -Mode Collect -RunId bulk-20261125
```

Lệnh không cần mật khẩu, API key hoặc quyền quản trị. Dùng lại **cùng RunId** để tiếp tục các trang API đã lưu; ảnh đã có hash hợp lệ được bỏ qua. Muốn một đợt lấy metadata mới, dùng RunId mới. Không chạy hai tiến trình cùng ghi một staging/RunId.

Các chế độ:

| Chế độ | Công việc |
|---|---|
| `Audit` | Không gọi mạng: tái sử dụng ảnh cũ khi mã/ID/URL/hash khớp, audit toàn bộ cache, tạo gallery và hàng đợi OCR. |
| `Collect` | Lấy toàn bộ metadata công khai, tải ảnh, audit, tạo hàng đợi review. |
| `Refresh` | Chỉ cập nhật metadata, sau đó audit/review ảnh hiện có. |
| `Images` | Tải ảnh từ snapshot API hoàn tất gần nhất, hoặc cache cũ nếu chưa có snapshot mới; sau đó audit/review. |
| `Review` | Tạo lại gallery và các batch OCR từ audit đã có; không thực hiện OCR. |

Ví dụ chỉ tải ảnh từ cache khi API trang dữ liệu chưa truy cập được:

```powershell
.\collect-nin-bulk.ps1 -Mode Images -DelaySeconds 1
```

Có thể giới hạn thử một đợt bằng `-ImageLimit 20`. Dừng HTTP 401/403/429, không đổi danh tính, vòng proxy hoặc bỏ qua kiểm soát truy cập. Bộ ảnh dừng sau ba lỗi liên tiếp để tránh tiếp tục gây tải khi mạng hỏng. Mỗi phản hồi giới hạn 20 MB; ảnh phải có Content-Type ảnh và container/signature hợp lệ. Đây **không phải** khẳng định đã giải mã/đối chiếu nội dung bằng mắt. HTML lỗi giả ảnh, hash thay đổi, đường dẫn thoát staging, URL ngoài HTTPS domain chính thức và phân trang không nhất quán đều bị chặn.

## Kết quả kiểm tra ngoại tuyến ban đầu, ngày 2026-11-25

Đây là kết quả của cache có manifest thu thập ngày **2026-10-06**, không phải tuyên bố đã crawl nguồn mới ngày 2026-11-25.

| Trạng thái | Số lượng |
|---|---:|
| Bản ghi nguồn duy nhất có URL ảnh hợp lệ | 1.250 |
| Đủ năng lượng/đạm/béo/carbohydrate/natri, sau nối alias và kiểm tra đơn vị | 1.090 |
| Qua vòng số liệu ban đầu, không có cờ chất lượng số liệu | 1.060 |
| Giữ lại để bổ sung/sửa số liệu trước vòng trên | 190 |
| Ảnh gốc đã có, tái sử dụng sau kiểm tra mã/ID/URL/hash | 49 |
| Ảnh còn chưa tải | 1.201 |
| Công thức từng được trích từ ảnh trong đợt trước | 41 |
| Ảnh từng được ghi nhận không có danh sách nguyên liệu | 8 |
| Món đã được duyệt trong runtime hiện tại | 40 |
| Tác vụ đối chiếu ảnh ngoài 40 món hiện có đã sẵn sàng | 9 |
| OCR mới đã thực hiện / món bulk mới được kích hoạt | 0 / 0 |

Hai cách đếm số liệu không tương đương: đếm key chính mà không nối alias cho kết quả khoảng 1.074 món có chỉ số chính; bộ kiểm tra này nối tường minh `lipid`, `carbohydrate`, `carbglucid` và yêu cầu đúng đơn vị, nên có 1.090. Có 309 quan sát xơ có số bất kể đơn vị, nhưng chỉ 292 bản ghi có đơn vị gram được chấp nhận; 17 quan sát còn lại dùng mg/μg được giữ nguyên và gắn cờ, không tự coi thành gram. Alias trùng trong một món cũng phải được rà soát, không chọn tùy tiện một số.

Lần thử API công khai trong môi trường làm việc đã dừng ngay ở lỗi DNS `[Errno 11001] getaddrinfo failed`. Checkpoint `api-runs/network-check-20261125/checkpoint.json` ghi `complete=false`, không có trang API mới hoặc snapshot hoàn tất. Bộ thu thập có thể chạy tiếp trên máy có kết nối; đây không phải vấn đề API key.

## Các file trong `nin-bulk-staging-20261125`

- `api-runs/<RunId>/`: từng phản hồi API nguyên gốc, hash, thời điểm lấy và checkpoint. Chỉ sau khi đủ trang, số lượng/ID/mã duy nhất mới công bố `latest-complete-source.json`.
- `image-collection.json`, `images/`: trạng thái từng ảnh, URL, mã nguồn, hash, lỗi; manifest được cập nhật sau từng bản ghi và không bị cắt mất các bản ghi của đợt trước khi chạy giới hạn nhỏ.
- `collection-report.json`: tổng quan, nguồn cache/snapshot, hash, vai trò tạm thời và lý do chờ review.
- `audit-all-dishes.json`, `audit-summary.csv`: kiểm tra đủ 1.250 bản ghi, chỉ số/đơn vị nguồn, lỗi trùng alias, trạng thái ảnh/công thức/khẩu phần riêng biệt.
- `numeric-review-candidates.json`: ứng viên **để đối chiếu**, không phải danh sách dùng được cho planner.
- `numeric-review-rejected.json`: các bản ghi chưa qua vòng số liệu, kèm lý do; không xóa khỏi nguồn.
- `review-gallery.html`: browser hiển thị thumbnail trực tiếp từ byte ảnh gốc; bấm để mở ảnh gốc, không sửa/cắt nguồn.
- `ocr-tasks/queue-index.json`, `batch-*.json`, `response-schema.json`: batch tác vụ có đường dẫn ảnh/hash và schema tường minh. Chỉ các batch được liệt kê trong `activeBatches` thuộc queue mới nhất; batch cũ được nêu riêng.

Queue ghi nhãn người dùng muốn worker **GPT6Luna**, nhưng `apiModelIdentifier=null`, `apiCallsMade=0`, `executionStatus=NOT_EXECUTED`. Không giả định tên đó là model API đang khả dụng. Muốn thực hiện tác vụ phải đính kèm đúng ảnh gốc cho model nhìn thấy, lưu phản hồi riêng trong `ocr-responses/`, rồi đối chiếu độc lập. Không ghi đè `nin-real-20261123/extracted-recipes.json` bằng phản hồi chưa kiểm tra.

## Điều kiện trước khi một món mới vào planner

URL ảnh không phải ảnh đã tải. Đủ số kcal/macros/natri không chứng minh có công thức, khẩu phần hay GI. Hàng đợi giữ nguyên toàn bộ mảng chỉ số và đơn vị nguồn; không tự điền 0, không tự trừ xơ khỏi Glucid, không tự đổi ml/số chiếc/cả vỏ/cả xương thành gram ăn được.

Manifest cũ có xác nhận của chủ dữ liệu rằng các chỉ số là trên 100 g, trong khi một số ảnh có tiêu đề thành phần theo đĩa/bát/suất. Vì vậy staging không gán 100 g hoặc khẩu phần cho từng món mới trước đối chiếu. Giữ cả xác nhận lịch sử và heading đọc từ ảnh để giải quyết bất nhất.

Tên vai trò mới chỉ là heuristic ưu tiên review: tinh bột → món chính tổng hợp → rau → canh → đạm → ăn phụ → chưa phân loại. Không coi đây là phân vai đã duyệt. Ưu tiên món tinh bột/cấu trúc bữa để mở rộng thực đơn; tìm đúng nguồn nguyên liệu ít GI nếu có bằng chứng. Không đổi loại nguyên liệu trong món và không hạ GI tùy ý xuống 50. Toàn bộ 1.250 bản ghi chưa có GI nguồn, purine và chất béo bão hòa đầy đủ; thu thập nhiều món hơn tự nó không chứng minh sẽ giải được mẫu bệnh lý GI 50.

Trước kích hoạt cần có ảnh thật đã xem, nguyên liệu/định lượng và phần không đọc được ghi rõ, cơ sở dinh dưỡng/khẩu phần được đối chiếu, vai trò/meal slots phù hợp, mô hình chỉ số thiếu có tham chiếu và test solver bằng dữ liệu thật. Các giới hạn và cảnh báo bệnh lý vẫn được giữ riêng.

## Trùng dòng API và số món duy nhất

Đợt nguồn trực tiếp `bulk-20261125` trả tổng API **1.024 dòng trên 11 trang**, nhưng có một dòng trùng hoàn toàn: HAP-211091 / ID `68f883f13ecfb2912d08df13`, dòng cuối trang 5 và dòng đầu trang 6. Hai payload không khác trường nào. Vì vậy snapshot dùng cho tải ảnh có **1.023 danh tính duy nhất**, không gọi đây là 1.024 công thức hoặc tuyên bố đã liệt kê đầy đủ toàn bộ nguồn.

Bộ thu thập giữ nguyên byte của cả 11 trang API và lưu thêm:

- `dishes-source-raw.json`: đủ 1.024 dòng, gồm cả dòng trùng.
- `dishes-source.json`: 1.023 dòng duy nhất được chấp nhận.
- `source-reconciliation.json`: hash JSON chuẩn hóa, vị trí trang/dòng, hash trang và thời điểm lấy của mỗi lần xuất hiện; ghi tường minh dòng trùng.
- `quarantined-source-records.json`: toàn bộ payload nếu có xung đột/không hợp lệ, không gộp trường hoặc chọn tùy tiện một phiên bản.

Chỉ những payload giống hoàn toàn sau chuẩn hóa JSON mới được dùng một lần trong danh sách duy nhất. Cùng mã nhưng khác ID, cùng ID nhưng payload khác, hoặc các chuỗi bản ghi liên quan qua mã/ID đều bị cách ly toàn bộ nhóm. Các dòng nguyên gốc vẫn được giữ. Một snapshot đã nhận đủ trang có thể dùng để tải ảnh của các món không xung đột, trong khi `sourceEnumerationComplete=false` vẫn giữ cảnh báo rằng tổng API và số danh tính không khớp.

Chênh lệch có thể do dữ liệu nguồn trùng hoặc phân trang thay đổi giữa các yêu cầu; không suy ra món bị thiếu là món nào. Muốn kiểm tra một đợt nguồn độc lập, dùng RunId mới thay vì sửa/ghi đè các trang đã có. Lần khôi phục đợt này đã tái sử dụng đủ 11 trang từ checkpoint, **0 yêu cầu mạng**, hash tất cả trang không đổi.

Queue OCR không chạy lại 8 ảnh đã được đối chiếu không có danh sách nguyên liệu nếu hash ảnh vẫn giống. Bằng chứng `ABSENT` được giữ trong `known-photo-only-no-ingredient-list.json`; ảnh món vẫn có trong gallery. Khi nguồn ảnh thay đổi, ảnh mới phải được xem lại. Việc này tiết kiệm lần gọi model và không biến ảnh món thành công thức suy đoán.

## Kiểm thử bộ thu thập

```powershell
chatbot/.venv/Scripts/python.exe -m unittest discover -s foodcatalogservice/scripts/tests -p test_nin_bulk.py -v
```

Mười hai kiểm thử dùng thư mục tạm, không gọi mạng hoặc sửa staging đang thu thập: nối alias/đơn vị/giá trị chưa biết; mã/URL hợp lệ; resume ảnh và giữ các bản ghi cũ; dừng 429 không retry; ảnh giả/hash bị sửa; resume trang API; đối chiếu dòng trùng hoàn toàn và provenance; cách ly mọi biến thể liên quan qua mã/ID; giữ dòng nguồn không hợp lệ trong quarantine; chặn phân trang không nhất quán; tái sử dụng bằng chứng ảnh không có nguyên liệu; queue OCR không nhận là đã thực hiện hoặc đủ điều kiện planner.
