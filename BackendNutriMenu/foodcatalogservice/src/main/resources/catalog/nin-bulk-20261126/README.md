# Danh mục NIN mở rộng 2026-11-26

Fixture này chỉ dùng nguồn NIN mới và kết quả OCR/ảnh đã thu thập, không bù món QA cũ. Mã mới `NIN_BULK_*` giữ riêng với 40 món `NIN_REAL_*` để không đổi ID lịch sử.

| Trạng thái | Số món |
|---|---:|
| Món nguồn duy nhất, có ảnh và metadata | 1.023 |
| Đủ điều kiện dữ liệu cho chế độ phổ thông/tham khảo | 597 |
| Được giữ để xem nhưng chưa đủ điều kiện lập thực đơn | 426 |
| Trong 597 món, xác nhận không có trứng và đậu nành theo danh sách nguồn đã đọc | 55 |
| Đủ dữ liệu bệnh lý/GI trực tiếp | 0 |

975 phản hồi OCR đã hoàn thành, được kiểm tra schema và hash ảnh; 40 công thức dùng đối chiếu nguồn đã làm trước đó; 8 ảnh trước đó đã xác nhận không có danh sách nguyên liệu. Vì vậy 426 món chờ điều kiện dữ liệu **không có nghĩa OCR của 426 món chưa chạy**. Chúng có thể thiếu định lượng, không có công thức trên ảnh, thiếu chỉ số chính, chưa khớp cơ sở khẩu phần, hoặc còn mâu thuẫn nguồn cần xem lại.

| Vai trò | Đủ điều kiện phổ thông | Đồng thời có bằng chứng không trứng/đậu nành |
|---|---:|---:|
| Món chính tổng hợp | 258 | 2 |
| Tinh bột | 24 | 15 |
| Món đạm | 128 | 13 |
| Món rau | 18 | 3 |
| Canh | 21 | 0 |
| Ăn phụ | 148 | 22 |

Không biến canh có nước dùng/gia vị chưa rõ thành món an toàn dị ứng để lấp chỗ trống. Hệ thống cần kiểm tra khả thi theo mẫu bữa và dị ứng thực tế. Chế độ phổ thông không thay thế tư vấn điều trị.

## Bằng chứng và giới hạn

- Giữ nguyên mọi số hợp lệ đã có trong nguồn. Mảng `nutritional_components` và `total_energy` gốc luôn có trong `qualityJson.sourceNutritionUnmodified`, kể cả đơn vị bất thường hoặc ô chưa biết.
- Giữ toàn bộ Glucid làm carbohydrate bảo thủ, không tự trừ chất xơ. Không tự điền 0 cho GI, GL, cholesterol, chất xơ, purine hay chất béo bão hòa còn thiếu.
- Chỉ dùng tham chiếu nguyên liệu đã duyệt trước đó khi tên và trạng thái thực phẩm khớp chính xác; không áp số liệu nguyên liệu sống cho thành phần ghi đã nấu. Nếu một thành phần chưa có tham chiếu phù hợp, phép cộng ước tính bị bỏ và ô vẫn chưa biết. Không dùng dinh dưỡng của món QA.
- Điều kiện phổ thông yêu cầu có công thức đọc được, lượng gram dương, đủ năng lượng/đạm/béo/carbohydrate/natri, ảnh nguồn có heading nguyên liệu/khẩu phần và năng lượng khớp API trong 0.11 kcal. Đây là suy luận cho demo từ nguồn, không phải phép đo xác nhận rằng mọi chỉ số đều có cùng cơ sở khẩu phần. Không mặc định 100 g.
- Một vài chênh lệch làm tròn nhỏ giữa tổng kcal dòng và kcal trên ảnh được giữ dưới dạng ghi chú, không sửa số nguồn. Ảnh bị cắt mất dòng, lượng chưa đọc được, trùng hàng đáng ngờ hoặc sai cơ sở khẩu phần vẫn bị chặn.
- `rawWeightG` là tên trường có sẵn của ứng dụng. Trong fixture này, nó biểu diễn **gram đúng như nguồn, sống hoặc chín như tên ghi**, không có nghĩa mọi thành phần đều sống. Không cộng các gram đó thành khối lượng món chín; khối lượng chín vẫn null.
- Nếu nhiều dòng có cùng toàn bộ tên nguồn và trạng thái chế biến, ledger lưu tổng gram để đáp ứng khóa duy nhất của database. Các dòng OCR gốc vẫn giữ nguyên. Khác tên/English gloss/trạng thái chế biến thì giữ danh tính riêng.
- Khẩu phần được nhân tuyến tính từ phần nguồn: món thông thường 0.5–3 phần, ăn phụ 0.5–4 phần, sushi 1–12 miếng nguồn. Không tự đổi một miếng thành một suất lớn.
- `perAllergenEvidence` ghi riêng `PRESENT`, `ABSENT_CONFIRMED` hoặc `UNKNOWN` cho từng loại trứng, đậu nành, hải sản, lạc và sữa. Nước dùng, sốt, gia vị pha, thực phẩm chế biến không rõ thành phần, bột pha chưa rõ và dầu thực vật chưa rõ loại được giữ chưa biết ở những dị ứng liên quan. Muối vừng chưa rõ công thức không được xác nhận không lạc. Cờ tổng quát không được ghi đè trạng thái chưa biết của từng dị ứng.
- Bằng chứng không có dị ứng chỉ nói về danh sách/thực phẩm đã đọc, không phải chứng nhận nhà sản xuất hoặc bảo đảm không nhiễm chéo.

## Ảnh

Đã xác minh hash của 1.023 ảnh gốc và 1.023 ảnh crop. Có 1.017 crop nằm trong giới hạn ảnh nguồn; crop `READY` và có bounds hợp lệ mới được chọn. Crop chưa duyệt hoặc bounds không hợp lệ dùng ảnh nguồn gốc. `imageUrl` vẫn là URL chính thức để giao diện khớp danh tính, không thay bằng ảnh không cùng món. Metadata crop và hash đều còn trong `qualityJson.dishPhoto`.

## Tái tạo và kiểm tra

```powershell
chatbot/.venv/Scripts/python.exe foodcatalogservice/scripts/build_nin_bulk_catalog.py
chatbot/.venv/Scripts/python.exe foodcatalogservice/scripts/build_nin_bulk_catalog.py --check
chatbot/.venv/Scripts/python.exe -m unittest discover -s foodcatalogservice/scripts/tests -p test_nin_bulk_catalog.py -v
```

Không cần mạng hoặc gọi LLM để dựng lại. `--check` so sánh các artifact đã xuất với các đầu vào/hash hiện tại.

- `nin-source-import.json`: fixture schema 1 cho importer, dataset ID `nin-bulk-20261126`.
- `integration-readiness.json`: điều kiện phổ thông, lý do chưa đủ, vai trò, chỉ số còn thiếu và bằng chứng dị ứng theo từng món.
- `input-provenance.json`: hash ảnh gốc/crop, phản hồi OCR, metadata, tham chiếu cũ và script dựng.
- `database-boundary-audit.json`: kiểm tra độ dài trường và miền số từ các annotation `@Column` thực tế; có kiểm thử hồi quy cho trường `nutrition_basis` tối đa 50 ký tự. Dinh dưỡng nguồn hiện không có giá trị vượt scale database.

Đợt API báo 1.024 dòng nhưng có một dòng trùng hoàn toàn, nên chỉ có 1.023 danh tính duy nhất. Tất cả phản hồi gốc và provenance vẫn ở staging; `sourceEnumerationComplete=false` giữ cảnh báo chưa chứng minh được đã liệt kê toàn bộ danh tính nguồn. Không bịa món thứ 1.024. Builder chỉ xuất file; trạng thái áp dụng database và chạy demo phải được kiểm tra riêng trên hệ thống đang chạy.
