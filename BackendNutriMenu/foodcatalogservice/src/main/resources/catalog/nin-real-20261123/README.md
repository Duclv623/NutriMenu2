# Danh mục NIN mới: dữ liệu và cách tái tạo

Runtime chỉ nạp `nin-source-import.json`. Không nạp công thức món QA cũ, không bù món QA khi thiếu ứng viên và không thay giá trị chưa biết bằng 0.

| Trạng thái | Số món |
|---|---:|
| Món NIN có ảnh và công thức nguồn | 40 |
| Đủ các chỉ số đầu vào cơ bản của solver | 40 |
| Còn thiếu chỉ số đầu vào cơ bản | 0 |
| Qua ngưỡng GI 50 trước các bộ lọc bệnh lý khác | 19 |
| Có dữ liệu purine / chất béo bão hòa đầy đủ | 0 |

40 món đủ đầu vào không có nghĩa cả 40 phù hợp mọi bệnh nhân. Với GI tối đa 50 hiện chỉ còn 3 món canh, 6 món đạm, 8 món rau, 2 món ăn phụ; không còn món tinh bột hoặc món chính tổng hợp. Chế độ bệnh lý vẫn có thể báo không khả thi. Ngay cả trước lọc GI, cohort chỉ có một món `MAIN_STARCH`; quy tắc không lặp món không cho dùng món đó cả trưa và tối. Không nới các quy tắc bệnh lý để tuyên bố thành công.

Chế độ phổ thông/tham khảo được giữ riêng với bệnh lý. Kiểm thử `NinSourceDatasetTest` dùng toàn bộ 40 món nguồn, kiểm tra thực đơn phổ thông 1600/2000 kcal theo chính sách server, các giới hạn năng lượng từng bữa/ngày, natri, cholesterol và số lượng khẩu phần. Đây là kiểm thử kỹ thuật dữ liệu/công cụ, không phải thực đơn điều trị đã được thẩm định.

## Các quan sát nguồn luôn được giữ nguyên

- Mọi giá trị số đã có trong nguồn NIN được giữ nguyên, kể cả giá trị bất thường. Ước tính chỉ điền ô còn thiếu của carbohydrate, chất béo, chất xơ hoặc cholesterol. Không tự sửa số nguồn để khớp công thức.
- Giữ toàn bộ Glucid làm carbohydrate bảo thủ; không tự trừ chất xơ khi định nghĩa Glucid chưa rõ.
- Ảnh, mã nguồn, ID, hash ảnh, công thức gốc, từng giá trị và phương pháp suy luận đều được giữ trong provenance/`qualityJson`.
- Định lượng nguồn dùng làm cơ sở nhân khẩu phần. Món thường cho 0.5–3 lần khẩu phần nguồn, trái cây cho 0.5–4 lần, bước 0.5; sushi cho 1–12 miếng nguồn, bước 1. Đây là nhân tuyến tính dinh dưỡng/nguyên liệu, không đổi âm thầm một miếng thành một suất lớn. Không suy ra gram chín bằng tổng gram nguyên liệu.
- Năm dòng ghi chú không có định lượng (nước canh, gia vị không định lượng, muối đã có trong dưa) được giữ nguyên trong `originalRecipe` và `nonQuantifiedSourceAnnotations`; không bịa gram. Danh sách mua sắm chỉ có nguyên liệu định lượng, giao diện nêu rõ phần còn thiếu.
- Purine và chất béo bão hòa của món vẫn chưa biết. Hồ sơ cần các chỉ số này, hoặc bằng chứng dị ứng chưa đầy đủ, vẫn bị chặn. Không tuyên bố đầy đủ lâm sàng.

## Dữ liệu nguyên liệu và GI là ước tính có ghi nguồn

`nin_ingredient_model.py` có 88 liên kết tên đã chỉ định rõ, không fuzzy matching. `ingredient-model-links.json` xuất giá trị, nguồn, phần ăn được và hạn chế của từng liên kết. Dữ liệu thành phần lấy từ bản NIN web, bảng thành phần thực phẩm Việt Nam 2007, các bảng USDA, MEXT, NOAA và nghiên cứu rau ngót. Mỗi giá trị bổ sung có đường dẫn/ID/bảng hoặc trang, tên thực phẩm tham chiếu và phương pháp. Chỉ ô số 0 tường minh của bảng nguồn được dùng như số 0; dấu `-` không phải số 0.

Cần đặc biệt rà soát các proxy còn bất định cao trước khi mở rộng hoặc dùng lâm sàng:

| Vị trí | Ước tính đang dùng | Hạn chế cần rà soát |
|---|---|---|
| Nước luộc hến trong cơm hến | Cholesterol của thịt trai nước ngọt 34 mg/100 g | Nước luộc khác thịt; chưa biết độ chuyển, cô đặc hoặc kích thước mẻ. Không gọi đây là cận trên đã chứng minh. |
| Sả trong gà nướng | Cellulose của riềng tươi 3.7 g/100 g cho 10 g nguyên liệu nguồn | Khác loài và bộ phận; không phải phép đo chất xơ tổng số của sả. |
| Ngũ vị hương | Chất xơ bột cà ri 8.9 g/100 g | Khác công thức phối trộn. |
| Bột canh/hạt nêm | Bouillon gà khô USDA, cholesterol 13 mg và chất xơ 0 g/100 g | Chưa biết nhãn hàng/thành phần nguồn. |
| Cá bống/hến/cá thu | Cá chép/trai/Atlantic mackerel tương ứng | Khác loài hoặc loài nguồn chưa được xác định. |
| Rong biển sushi (bản NIN web mã 4139 còn có số năng lượng/chất béo đáng nghi, được giữ lại và gắn cờ) | Iwanori khô MEXT, xơ 36.4 g và cholesterol 30 mg/100 g | Chưa biết loại rong và chế biến; không mặc định rong biển không cholesterol. |
| Rau ngót luộc | Lipid lá tươi 0.82 g/100 g từ nghiên cứu Việt Nam 2022 | Chưa đo giống/trồng và độ giữ lại sau luộc của suất nguồn. |

Celluloza trong FCT2007 chỉ được ghi là proxy cellulose/chất xơ thô, không gọi là phép đo chất xơ tổng số tương đương. Ước tính tổng công thức mặc định giữ lại 1.0 để tính phần còn thiếu; chưa có phép đo hao hụt chế biến hoặc khối lượng chín. Phần bỏ đi cho nguyên liệu cả xương/cả vỏ lấy bảng FCT như một ước tính, không coi là cân đo suất ảnh.

GI không có phép đo trực tiếp cho các món NIN này. Mô hình dùng trung bình/proxy nguyên liệu và phép gán cho thực phẩm ít carbohydrate từ *International tables of glycemic index and glycemic load values: 2008*, rồi tính trung bình trọng số carbohydrate theo FAO. Không hạ GI của cơm để vượt bộ lọc. Khi không xác định được thành phần, dùng giả định kiểm tra GI 100 trong miền đầu vào 0–100 của mô hình. Không gọi đây là GI đo hoặc cận trên sinh lý được bảo đảm: một số GI thực phẩm đo được lớn hơn 100. Thịt/cá/trứng nguyên chất dùng trạng thái GI không áp dụng; số 0 chỉ là ký hiệu kỹ thuật. Nếu nguồn vẫn có carbohydrate vết, GL dùng giả định GI 100 trên lượng carbohydrate đó, không tính GL bằng 0; điều này không bảo đảm cận trên đáp ứng sinh lý.

## Tái tạo và kiểm tra

```powershell
chatbot/.venv/Scripts/python.exe -B foodcatalogservice/scripts/build_nin_reference_snapshot.py
chatbot/.venv/Scripts/python.exe -B foodcatalogservice/scripts/build_nin_source_import.py
chatbot/.venv/Scripts/python.exe -B foodcatalogservice/scripts/build_nin_source_import.py --check
chatbot/.venv/Scripts/python.exe -B -m unittest discover -s foodcatalogservice/scripts/tests -v
chatbot/.venv/Scripts/python.exe -B foodcatalogservice/scripts/build_nin_frontend_assets.py
```

Các đầu vào tham chiếu được cố định trong repository; không cần API key hoặc gọi mạng khi dựng lại. Snapshot không chứa công thức món QA. `prepare_nin_catalog.py` là công cụ staging/audit, không phải runtime; QA cũ có thể tồn tại làm tư liệu ngoại tuyến/lịch sử.

- `integration-readiness.json`: số lượng, role, GI và các trường còn chưa biết theo món.
- `ingredient-estimation-reference.json`: snapshot các quan sát/proxy nguyên liệu có nguồn.
- `ingredient-model-links.json`: kết quả nối tên và bằng chứng theo trường.
- `nin-source-import.json`: fixture duy nhất được runtime import.
- `data-change-manifest.json`: hash và phạm vi file đã rà soát, không nhận sở hữu toàn bộ diff sẵn có của dự án.
- `images/`, `extracted-recipes.json`, `nin-catalog-provenance.json`: ảnh gốc và các chứng cứ để đối chiếu.

Importer upsert theo mã, giữ ID qua lần chạy lại, sau đó nghỉ dùng món ngoài cohort cho lựa chọn mới. Không xóa tài khoản, món hay thực đơn lịch sử. Các nguyên liệu identity runtime không biến thành dữ liệu 100 g đầy đủ để tính món tùy chỉnh.

**Chưa áp dụng/xác minh thay đổi trên PostgreSQL trong phiên này.** Môi trường chặn kết nối DB (10013); kết quả hiện là kiểm thử/build. Khi catalog khởi động thành công với DB, fixture mới được upsert và chính sách nghỉ dùng món cũ được áp dụng trong transaction.
