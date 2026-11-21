# Kế hoạch triển khai Android vCenter — WebNew

## 1. Nguyên tắc bắt buộc

- Toàn bộ dữ liệu hạ tầng, cấu hình, trạng thái, cảnh báo và task phải lấy từ API/SDK chính thức do vCenter 8 cung cấp. Mọi thao tác quản trị phải gọi API chính thức tương ứng và kiểm tra kết quả từ server.
- Không scrape giao diện vSphere Client, không gọi endpoint nội bộ chưa công bố, không dùng dữ liệu demo để thay thế khi API lỗi và không sinh task thành công giả.
- Số liệu tổng hợp được phép tính từ dữ liệu API thật; phải thống nhất công thức, đơn vị, phạm vi và thời điểm lấy dữ liệu. Cache chỉ lưu dữ liệu đã nhận từ server và phải hiển thị thời điểm cập nhật/trạng thái cũ.
- Dữ liệu cục bộ chỉ gồm hồ sơ kết nối, tùy chọn app, cache và thông tin tham chiếu task. Không coi dữ liệu cục bộ là nguồn xác nhận trạng thái hạ tầng.
- Chỉ dùng hợp đồng API tương thích vSphere 8. Ghi rõ phiên bản vCenter/ESXi thực tế và kiểm tra khả năng, quyền, license trước khi mở tính năng.
- Ưu tiên vSphere Web Services API SOAP `/sdk`. SDK hoặc thư viện client phải được đánh giá khả năng chạy trên Android; không mặc định thư viện Java cho máy chủ có thể chạy nguyên trạng trên Android.
- REST chỉ dùng cho chức năng cần thiết sau khi xác minh API bản 8 và luồng xác thực qua NPM. Không giả định cookie SOAP là REST session token.
- Không truy cập ESXi trực tiếp trong phạm vi mặc định. Nếu một tính năng chỉ có thể thực hiện qua host trực tiếp, ghi rõ giới hạn và xin duyệt phạm vi bổ sung, không âm thầm đổi đường kết nối.
- Giữ giao diện theo `ui-reference/`, không đưa dữ liệu mẫu của bản web vào bản APK vận hành.
- Kiểm thử sớm và lặp lại trên điện thoại Samsung thật qua ADB. Emulator là môi trường bổ sung cho kích thước màn hình/API level, không thay thế thiết bị Samsung.
- Không thực hiện thao tác thay đổi hạ tầng thật khi chưa được phép về đối tượng và phạm vi. Không tự sửa vDS/LAG đang vận hành.

## 2. Các mốc bàn giao

| Mốc | Sản phẩm | Điều kiện đạt |
| --- | --- | --- |
| M0 | Môi trường phát triển và Samsung ADB sẵn sàng | Build app khung, cài và mở được trên Samsung |
| M1 | APK kết nối thật | Đăng nhập vCenter 8, kiểm tra NPM/TLS, lưu hồ sơ và logout |
| M2 | APK inventory chỉ đọc | Có dữ liệu thật tổng quan, VM, host/cluster, datastore, network, cảnh báo |
| M3 | Task và thao tác VM cơ bản | Task thật, power/guest actions, snapshot; xác minh kết quả từ server |
| M4 | Quản trị VM nâng cao | Edit Settings đủ nhóm, clone và migration trong phạm vi được hỗ trợ |
| M5 | Quản trị hạ tầng | Host/cluster/network được tích hợp theo ma trận API đã xác minh |
| M6 | Bản nghiệm thu | APK, source, Gradle Wrapper, hướng dẫn build và kết quả kiểm thử Samsung/vCenter |

Mỗi mốc phải có APK build được và kết quả kiểm tra thực tế. Không công bố một chức năng đã hoàn thiện nếu mới có giao diện hoặc chưa được kiểm thử với server.

### Tiến độ thực tế ngày 18/11/2026

- M0: đã có project Kotlin/Compose, Gradle Wrapper và APK debug; đã cài/mở trên Samsung SM-N976N Android 12/API 31 qua ADB. Hai test điều hướng đạt, lint không có lỗi.
- Giao diện khung có 5 tab cố định, menu phụ, trạng thái chưa kết nối và API / Phạm vi; không có dữ liệu hạ tầng giả. Chưa kiểm tra đầy đủ font lớn, xoay màn hình và tablet.
- M1 đã có mã kết nối HTTPS SOAP `/sdk` (RetrieveServiceContent/Login/Logout), NPM Auth riêng, hồ sơ và tùy chọn nhớ mật khẩu bằng Android Keystore. APK đã build/cài/mở trên Samsung; 6 unit test và 5 test thiết bị đạt, lint 0 lỗi (12 cảnh báo còn lại).
- Người dùng xác nhận đăng nhập server thật thành công ngày 18/11/2026, cho phép chuyển sang M2. Chưa nghiệm thu đầy đủ NPM/logout/TLS/lưu hồ sơ trên mọi trường hợp. Chưa hỗ trợ nhập CA riêng hoặc tự tái đăng nhập. Không coi thông tin phiên đã đăng nhập là trạng thái phiên còn hiệu lực mãi mãi.
- M2 đã có baseline inventory chỉ đọc: PropertyCollector phân trang, VM, host/cluster, datastore, Network/vDS/distributed portgroup, tổng quan từ dữ liệu trả về, tìm VM và refresh có trạng thái lỗi/dữ liệu cũ. Đã build/cài/mở APK trên Samsung, 13 unit test và 3 test thiết bị đạt; lint 0 lỗi, 15 cảnh báo. Chưa đối chiếu dữ liệu live với vSphere Client.
- M2 chưa hoàn tất toàn bộ: alarm/event, vSwitch/LAG/LACP chưa tích hợp. Task và thao tác thay đổi hạ tầng thuộc các mốc sau, không được mô phỏng.

### Bổ sung M2 ngày 19/11/2026

- Đã thêm lọc trạng thái nguồn VM kết hợp tìm tên; trạng thái thiếu/không nhận diện có nhóm chưa rõ.
- Đã đọc `VirtualMachine.snapshot` qua PropertyCollector và hiển thị cây snapshot chỉ đọc, gồm nhánh/cấp, tên, reference, mô tả, thời gian server, power state lúc tạo, quiesced và snapshot hiện tại. Báo riêng property không đọc được; chưa có thao tác tạo/xóa/revert.
- Unit test: 17 test đạt, gồm parser snapshot nhiều nhánh, dữ liệu thiếu/quyền, reference lỗi và kết hợp lọc/tìm kiếm. APK debug build thành công; lint 0 lỗi, 15 cảnh báo. Hai UI test trong DashboardLayoutTest đạt trên Samsung SM-N976N Android 12 bằng app uitest riêng, gồm lọc VM/mở cây snapshot và hồi quy dashboard. Đã cài đè APK debug bằng Update-App.ps1 và xác nhận mở thành công; không gỡ app/xóa dữ liệu hồ sơ.
- Đã chạy lại 2 UI test DashboardLayoutTest qua ADB Wi-Fi trên Samsung SM-N976N Android 12 ngày 19/11/2026: tất cả đạt. Chỉ định thiết bị Wi-Fi bằng ANDROID_SERIAL, dùng app uitest riêng; không xóa hồ sơ app chính. Đây là test UI bằng fixture, không phải kiểm thử kết nối/dữ liệu vCenter thật.
- Đã chỉnh Tổng quan theo cấu trúc mẫu web: header/tên hồ sơ/menu, thanh tab vạch nhấn, tiêu đề và refresh cùng hàng, dải cập nhật API, thẻ chỉ số, host có icon/CPU-RAM, bảng VM cuộn ngang và hai cột task/cần chú ý trên màn rộng. Không sao chép dữ liệu hay biểu đồ mô phỏng. Build debug và 17 unit test đạt; lint 0 lỗi/15 cảnh báo; 6 UI test đạt trên Samsung qua Wi-Fi sau khi sửa selector trùng tên host và đóng bàn phím trước khi bấm đăng nhập trong test. Đã cài đè/mở APK qua Wi-Fi, không xóa hồ sơ. Chưa nghiệm thu độ khớp hình ảnh với mẫu trên mọi kích thước/font.
- Trang Máy ảo đã chuyển từ card từng VM sang layout mẫu HTML: header/mô tả, nút Tạo VM vô hiệu hóa có giải thích, tìm kiếm/lọc cùng hàng, bảng cuộn ngang dùng chung với Tổng quan; chạm VM mở dialog chi tiết chỉ đọc và snapshot. Ba UI test DashboardLayoutTest đạt trên Samsung qua Wi-Fi (lọc/tìm kiếm, mở chi tiết/snapshot, hồi quy Tổng quan); APK debug build và lint đạt. Chưa nghiệm thu độ khớp hình ảnh trên mọi kích thước/font.
- Tổng quan thay chart lịch sử từng host bằng ba thanh tổng CPU/RAM/Storage (đã dùng/trống/tổng); đọc quickStats và datastore duy nhất trong inventory, tải lại mỗi 20 giây khi foreground, không chồng request. Thiếu dữ liệu hiển thị cảnh báo; chưa đối chiếu số liệu vCenter thật.
- Chưa đối chiếu bộ lọc/cây snapshot và biểu đồ hiệu suất với dữ liệu vCenter thật; không coi M2 đã nghiệm thu.

### Cập nhật triển khai M2 ngày 19/11/2026

- VM theo cây Cluster → ESXi → VM, giữ tìm kiếm/lọc và snapshot chỉ đọc; Host theo bố cục HTML với chọn host/chi tiết/tab. Nút quản trị vẫn khóa (thuộc M3/M5).
- Đã bổ sung ResourcePool và quan hệ parent/VM/pool con, overallStatus Host/Cluster; datastore có used/free/capacity.
- Đã đọc mạng host standard vSwitch, portgroup/VLAN, VMkernel/IP, uplink/link speed; vDS có lacpApiVersion và cấu hình LAG/LACP. Thiếu property/quyền hiển thị riêng, không suy ra cấu hình rỗng.
- Đã tích hợp AlarmState và Event riêng biệt: tối đa 100 cảnh báo đang kích hoạt trên rootFolder, tra tên alarm; tối đa 100 sự kiện trong 24 giờ theo giờ server. Lỗi từng nguồn độc lập, hiển thị entity, thời gian, nội dung/mức độ khi API cung cấp; không acknowledge/reset alarm. Tải lại khi mở trang foreground mỗi 20 giây.
- Đã bổ sung giải phóng continuation token khi vượt giới hạn/hủy inventory và xóa dữ liệu giám sát khi đổi phiên. Cache trong bộ nhớ có thời điểm cập nhật; không tự khôi phục phiên.
- Kiểm thử tự động: build/lint và 8 UI test trên Samsung qua Wi-Fi đạt; unit test parser inventory/network/monitoring và hồi quy đạt. Fixture không thay thế kiểm thử server thật.
- Trạng thái M2: đã triển khai các nhóm chức năng chỉ đọc; CHƯA nghiệm thu. Còn đối chiếu từng màn hình với vSphere Client cùng tài khoản, xác minh LAG/alarms/events thực tế, quyền hạn chế, mất mạng/hết phiên/đổi hồ sơ, font lớn/xoay màn hình. Chưa có kết quả xác minh live cho các phần mới; không đánh dấu đạt M2 hoặc chuyển M3 chỉ dựa vào UI test.

### Kiểm thử M2 ngày 20/11/2026

- Chạy toàn bộ bộ kiểm thử hiện có: 34 unit test và 11 UI/instrumentation test đạt trên Samsung SM-N976N Android 12 qua ADB Wi-Fi; assembleDebug thành công; lintDebug có 0 lỗi, 15 cảnh báo. UI test dùng app uitest riêng, không xóa hồ sơ app chính.
- Đăng nhập app chính bằng hồ sơ đã lưu qua giao diện thông thường thành công; Tổng quan hiển thị dữ liệu API có thời điểm cập nhật và CPU/RAM/Storage đã dùng, trống, tổng. Smoke test mở các tab VM, Host, Datastore không thấy thông báo lỗi trong vùng hiển thị. Đây chưa phải đối chiếu độ chính xác từng số liệu với vSphere Client.
- M2 vẫn CHƯA nghiệm thu: cần đối chiếu cùng tài khoản với vSphere Client; kiểm chứng dữ liệu snapshot, mạng/LAG và alarms/events thực tế; thử quyền hạn chế, mất mạng/hết phiên/đổi hồ sơ và font lớn/xoay màn hình. Không thực hiện thao tác thay đổi hạ tầng trong đợt kiểm thử này.

### Triển khai M3 ngày 20/11/2026

- Theo phạm vi người dùng duyệt: triển khai Task, nguồn VM và snapshot; chỉ kiểm thử tự động, không thử lệnh thay đổi trên vCenter thật.
- Đã nối TaskManager/TaskHistoryCollector, lịch sử 24 giờ tối đa 100 task, giải phóng collector; theo dõi task reference bằng Task.info, hiển thị trạng thái/entity/user/thời điểm/progress/lỗi/cancelable. Task không truy xuất được không suy ra thành công.
- Đã nối power on/off/reset, shutdown/reboot guest và tạo/revert/xóa snapshot, xóa toàn bộ/consolidation. Có xác nhận, kiểm tra nguồn/Tools/cây snapshot, đọc lại inventory trước gửi và đọc lại sau thao tác. Server kiểm tra quyền/capability thực tế; guest đồng bộ chỉ báo đã gửi, không tạo task giả.
- Sổ lệnh ghi bền trước gửi, gắn hồ sơ/endpoint/tài khoản/instance; mở lại app sau đăng nhập đọc lại task, không tự gửi lại. Lệnh chưa rõ kết quả khóa gửi mới cho cùng VM; chỉ mở sau task terminal hoặc xác nhận đối soát thủ công trên vSphere Client. Đổi phiên xóa trạng thái hiển thị nhưng không làm mất sổ lệnh chờ đối soát.
- Kiểm thử hồi quy tự động: 48 unit test và 14 test Samsung đạt; build debug thành công, lint 0 lỗi/16 cảnh báo (thêm gợi ý UseKtx cho ghi preferences đồng bộ của sổ lệnh). Có test parser/task/operation, chặn gửi lại theo phạm vi, điều kiện nguồn/Tools/snapshot, xác nhận nguồn, đối soát và khôi phục sổ lệnh. Sau thay đổi cuối đã chạy lại build/lint, test policy và cả 4 test M3 trên Samsung (gồm xác nhận hủy task): tất cả đạt. Tổng cộng 15 test thiết bị khác nhau đã đạt trong đợt này. APK debug đã tạo; lúc 10:22 ngày 20/11/2026 đã cài đè app chính trên Samsung qua ADB Wi-Fi, giữ nguyên dữ liệu/hồ sơ, mở app thành công ở màn hình đăng nhập. Không chạy thao tác thay đổi trên server.
- Trạng thái M3: đã tích hợp mã và UI; CHƯA nghiệm thu live. Chưa chạy power/snapshot/cancel trên hạ tầng thật. Còn kiểm thử VM lab được duyệt, lỗi quyền/capability và mất mạng sau gửi/mở lại app trên server; M2 cũng còn các mục nghiệm thu nêu trên.

### Cap nhat M4 va UI Snapshot ngay 20/11/2026

- Theo phe duyet mo rong pham vi: bo sung panel M4 con thieu cho cac hop dong hien co (resources, disk, NIC, ISO, boot, clone, cold migration). Giu validation, baseline config, khoa busy/doi soat va buoc xac nhan truoc gui.
- Sua icon Nguon/Snapshot bang Canvas; popup hien danh sach snapshot ngay, cho chon truoc Xoa/Revert; nut gon tu xuong dong.
- Build debug thanh cong; 54 unit test va 11 UI test M3/panel M4 dat tren Samsung Android 12; lint 0 loi/15 canh bao. Cai de app chinh bi chan boi chu ky khac (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); giu nguyen app/du lieu, can keystore goc. M4 van CHUA nghiem thu live; khong chay lenh thay doi ha tang that trong dot nay. Cac ket qua test tu dong khong thay the doi chieu vCenter/quyen/license/topology.

### Ra soat baseline M4 ngay 21/11/2026

- Da ket noi lai Samsung SM-N976N Android 12 qua ADB Wi-Fi va chieu man hinh bang scrcpy; khong ghi endpoint thiet bi vao source.
- Kiem tra hien trang truoc khi sua code: assembleDebug thanh cong, 2 unit test AdvancedExecutionPolicyTest va 2 UI test VmAdvancedPanelTest dat tren Samsung; lintDebug 0 loi/15 canh bao. Selector VmConfigTest khong khop test hien co, khong tinh la da kiem thu parser M4.
- M4 van dang trien khai: co CPU/RAM/topology/hot add, mot phan disk/NIC/ISO/boot, clone VM tat va cold migration. Chua co precheck tuong thich migration phia server; con thieu reservation/limit/shares, quan ly controller va VM Options day du theo ke hoach. Chua nghiem thu live.
- Khong sua code trong dot ra soat, khong cai de app chinh, khong xoa ho so va khong gui lenh thay doi ha tang that. Blocker chu ky APK khac ban da cai van chua duoc xu ly.

## 3. Giai đoạn 0 — Chốt phạm vi, API và môi trường

### Bước 0.1 — Đọc thiết kế và lập danh sách chức năng

1. Đọc `REQUIREMENTS.md`, `IDE_PROMPT.md` và toàn bộ ba file giao diện tham chiếu.
2. Chốt danh sách màn hình, điều hướng, form và trạng thái loading/empty/error/permission denied.
3. Lập ma trận tính năng trong tài liệu kỹ thuật: chức năng, managed object/phương thức/property, phiên bản API 8, quyền cần thiết, điều kiện license/power state, kiểu kết quả task hoặc đồng bộ và cách xác minh.
4. Đối chiếu từng API với tài liệu vSphere Web Services API 8; các tên phương thức trong kế hoạch là điểm triển khai dự kiến, phải xác minh trước khi code chức năng.
5. Tính năng chưa có API phù hợp phải hiển thị giới hạn tại API / Phạm vi, không thay bằng thao tác demo.

### Bước 0.2 — Kiểm tra toolchain Windows

1. Kiểm tra JDK, Android SDK, SDK Platform, Build Tools, Platform Tools/ADB và Gradle.
2. Chọn tổ hợp Kotlin, Android Gradle Plugin, Gradle, Compose và JDK tương thích; khóa phiên bản và cung cấp Gradle Wrapper.
3. Chọn và ghi rõ minSdk, compileSdk, targetSdk sau khi biết phiên bản Android của Samsung thử nghiệm và yêu cầu phân phối.
4. Chỉ cài/khôi phục dependency khi cấu hình đã yêu cầu hoặc lệnh kiểm tra báo thiếu; không nâng cấp công cụ dùng chung ngoài phạm vi cần thiết.
5. Không đưa đường dẫn SDK cá nhân, credential hoặc signing password vào source.

### Bước 0.3 — Kết nối điện thoại Samsung qua ADB

1. Ghi nhận model Samsung, Android/One UI, độ phân giải và kiểu điều hướng đang dùng.
2. Trên điện thoại: bật Developer options và USB debugging; dùng cáp truyền dữ liệu, mở khóa và xác nhận RSA cho đúng máy phát triển.
3. Cài Samsung USB driver trên Windows nếu thiết bị chưa được nhận; kiểm tra cáp/USB mode nếu cần.
4. Chạy `adb devices -l`; chỉ tiếp tục khi Samsung có trạng thái `device`, không phải `unauthorized` hoặc `offline`.
5. Nếu có nhiều thiết bị, mọi lệnh cài/mở/test đều chỉ định `adb -s <SERIAL>` để không tác động nhầm thiết bị.
6. Dùng USB cho vòng test đầu. Wireless debugging chỉ dùng khi cần, trên mạng tin cậy và tắt khi không còn sử dụng.
7. Xác nhận Samsung truy cập được endpoint vCenter/NPM qua LAN hoặc VPN; ADB không tự tạo đường mạng từ điện thoại tới vCenter.

Điều kiện đạt M0: app khung chạy trên Samsung qua ADB, có thông tin toolchain và thiết bị được ghi nhận. Nếu thiếu thiết bị hoặc chưa được cấp USB debugging thì đánh dấu bị chặn, không ghi là đã test Samsung.

## 4. Giai đoạn 1 — Project Android và giao diện khung

1. Tạo project trong `android/`, Kotlin + Jetpack Compose, ViewModel, Coroutines/Flow, OkHttp; tách lớp UI, repository và API/data.
2. Giữ tên vCenter/WebNew, theme xanh xám tối theo mẫu và header toàn chiều ngang.
3. Làm 5 tab dưới cố định: Tổng quan, Máy ảo, Host, Task, Datastore (nhãn rút gọn theo phản hồi Samsung ngày 18/11/2026). Icon vector 24dp đồng nhất, nhãn một dòng để không lệch hàng; Datastore dùng biểu tượng storage. Phạm vi Host vẫn gồm host/cluster và Task vẫn là task thật của vCenter.
4. Menu ba gạch gồm Network, Cảnh báo, Kết nối, API / Phạm vi.
5. Làm các thành phần dùng chung: loading, chưa kết nối, empty, lỗi, thiếu quyền, dữ liệu cũ và xác nhận thao tác nguy hiểm.
6. Chưa có API thì hiển thị chưa kết nối/chưa tích hợp, không điền inventory hoặc chỉ số mẫu.
7. Build debug APK và cài trên Samsung; kiểm tra system bar, bàn phím, Back, xoay màn hình, font lớn và bố cục màn hình nhỏ.

Đầu ra: project build được, Gradle Wrapper và APK khung đã kiểm tra trên Samsung.

## 5. Giai đoạn 2 — Kết nối, xác thực và phiên thật

1. Làm form tên kết nối, host/domain/IP, port mặc định 443, username/password vCenter và tùy chọn NPM HTTP Auth với bộ credential riêng.
2. Tích hợp `RetrieveServiceContent`, đọc phiên bản/API capability và định danh instance vCenter; đăng nhập qua SessionManager của SOAP.
3. Chỉ gửi Authorization NPM tới đúng origin đã cấu hình. Không chuyển credential sang origin khác qua redirect; cookie/token phải tách theo hồ sơ.
4. Hỗ trợ TLS hợp lệ và phương án CA riêng có phạm vi rõ ràng. Không dùng trust-all hoặc bỏ kiểm tra hostname mặc định.
5. Nhớ thông tin hồ sơ; tùy chọn nhớ mật khẩu phải được người dùng chọn rõ. Nếu lưu thì mã hóa bằng khóa Android Keystore, nếu không thì giữ trong bộ nhớ phiên.
6. Xử lý DNS/TLS/timeout/mất mạng, NPM 401/403, lỗi đăng nhập vCenter, thiếu quyền và hết phiên. Khi không thể phân biệt chắc lớp gây lỗi phải báo trung thực, không đoán.
7. Khi hết phiên, chỉ tự gửi lại yêu cầu đọc an toàn sau tái đăng nhập; không tự lặp lệnh thay đổi.
8. Logout, quên hồ sơ và đổi hồ sơ phải xóa/đóng phiên phù hợp, hủy yêu cầu đang chạy và không để dữ liệu hồ sơ cũ xuất hiện ở hồ sơ mới.
9. Test Samsung với vCenter thật do người dùng nhập trong app: trực tiếp nếu có, qua NPM, sai từng lớp credential, TLS lỗi, mất mạng và mở lại app.

Điều kiện đạt M1: APK đăng nhập được môi trường được cung cấp, đọc được định danh/phiên bản thật và không lộ credential trong log.

## 6. Giai đoạn 3 — Inventory và giám sát chỉ đọc

1. Tích hợp PropertyCollector và cơ chế duyệt inventory/đọc theo lô phù hợp; xử lý continuation token và giải phóng tài nguyên collector/view khi không còn dùng.
2. Đọc VM, host, cluster/resource pool, datastore, standard network và distributed network từ vCenter.
3. Làm tổng quan CPU/RAM/storage và số lượng đối tượng; xác định rõ số liệu capacity, usage hay allocation, không cộng trùng datastore dùng chung.
4. Làm danh sách/tìm kiếm/lọc VM và chi tiết OS, IP nếu có VMware Tools, CPU, RAM, power, host, datastore và snapshot.
5. Làm host/cluster health, connection/maintenance, tài nguyên và VM liên quan; datastore capacity/free/used và trạng thái.
6. Đọc vSwitch/port group, vDS/distributed port group và LAG/LACP khi API/quyền cho phép. Giai đoạn này chỉ đọc, không sửa cấu hình.
7. Đọc alarm/event phù hợp, thể hiện đối tượng, mức độ, thời gian và nội dung; không giả định alarm và event là cùng một loại dữ liệu.
8. Bổ sung refresh, hủy request, giới hạn truy vấn, phân trang/đọc theo lô, thiếu property do thiếu quyền và cache có thời điểm cập nhật.
9. Đối chiếu dữ liệu trên Samsung với vSphere Client cùng tài khoản, cùng đối tượng và thời điểm gần nhau; kiểm tra quyền hạn chế và đổi hồ sơ.

Điều kiện đạt M2: bàn giao APK inventory dữ liệu thật đầu tiên; liệt kê màn hình đã đối chiếu, màn hình còn thiếu quyền hoặc chưa có điều kiện kiểm thử.

## 7. Giai đoạn 4 — Task thật và thao tác VM cơ bản

1. Làm TaskManager, TaskHistoryCollector, Task/TaskInfo và theo dõi property phù hợp; xử lý vòng đời collector và phạm vi task tài khoản được xem.
2. Danh sách task có trạng thái, đối tượng, người thực hiện, thời điểm, tiến độ và lỗi; không giả định history lưu vô hạn.
3. Với lệnh trả task reference, lưu reference kèm instance/hồ sơ và đọc đúng task tới queued/running/success/error.
4. Khi app chạy nền, process bị hủy hoặc mở lại, đọc lại trạng thái từ server. Task đã hết thời gian lưu phải hiện không còn truy xuất được, không suy ra thành công.
5. Khi timeout chưa rõ server nhận lệnh hay chưa, hiện trạng thái chưa xác định và đối soát trước khi cho gửi lại.
6. Chỉ hiện hủy task khi cancelable và quyền cho phép; xác minh kết quả hủy từ server.
7. Tích hợp power on/off/reset, shutdown/reboot guest; kiểm tra Tools và power state. Lệnh đồng bộ không trả task chỉ hiện đã gửi và đọc trạng thái thật, không tạo task vCenter giả.
8. Tích hợp cây snapshot, tạo/revert/xóa và consolidation khi hỗ trợ; xác nhận các thao tác rủi ro.
9. Kiểm thử trên VM lab được cho phép: thành công, lỗi quyền, điều kiện không hợp lệ, mất mạng ngay sau gửi lệnh và mở lại app.

Điều kiện đạt M3: thao tác và task được đối chiếu trên vCenter; app không gửi trùng lệnh sau timeout hoặc tự đánh dấu thành công.

## 8. Giai đoạn 5 — Edit Settings đầy đủ

1. Đọc cấu hình hiện tại, hardware version, capability, device/controller key, power state và changeVersion trước khi cho sửa.
2. CPU/RAM: số lượng, core/socket, Hot Add, reservation/limit/shares theo khả năng thực tế.
3. Disk: thêm mới, gắn VMDK, tăng dung lượng, controller/unit, disk mode nếu hỗ trợ; phân biệt tháo khỏi VM và xóa file. Không hỗ trợ giảm dung lượng disk.
4. NIC: thêm/tháo, adapter type khi hỗ trợ, standard/distributed port group, MAC, Connected và Connect at power on.
5. CD/DVD và controller: ISO datastore, trạng thái kết nối, SCSI/SATA/NVMe theo hardware version và cấu hình hiện có.
6. Boot/VM Options: guest OS, BIOS/EFI, Secure Boot, boot order/delay, VMware Tools và advanced parameters theo API 8.
7. Validate dữ liệu và giới hạn, chỉ gửi phần thay đổi qua cấu hình API tương ứng; phát hiện sửa đồng thời và yêu cầu tải lại thay vì ghi đè im lặng.
8. Hiển thị bản xem trước thay đổi, yêu cầu xác nhận thao tác nguy hiểm và không tự tắt VM khi cần chỉnh offline.
9. Theo dõi task rồi đọc lại cấu hình từ server; với disk expansion, hiển thị các hạn chế snapshot/trạng thái mà server trả về.
10. Test Samsung trên VM lab, ưu tiên trường hợp xung đột cấu hình, sai controller/unit, thiếu quyền, VM đang chạy và thao tác xóa disk.

Điều kiện đạt: từng nhóm có trạng thái đã tích hợp/đã kiểm thử/không hỗ trợ; không báo hoàn thiện Edit Settings nếu chỉ có CPU/RAM.

## 9. Giai đoạn 6 — Clone và migration

1. Đọc đích hợp lệ từ inventory: folder, host, resource pool, datastore và network cần ánh xạ.
2. Làm clone với tên và tài nguyên đích; theo dõi task và xác minh VM kết quả từ server.
3. Làm migration đổi host, đổi datastore hoặc cả hai theo API được hỗ trợ.
4. Gọi cơ chế kiểm tra tương thích/phương án migration phù hợp API 8; hiển thị lỗi/cảnh báo server trước khi chạy. Không coi precheck là bảo đảm thao tác sẽ luôn thành công.
5. Kiểm tra vMotion, network/storage, license, power state và quyền; không cho phép lựa chọn mà server xác nhận không hợp lệ.
6. Test trên tài nguyên lab được duyệt; kiểm tra task, đích sau migration và xử lý mất kết nối mà không tự gửi lại lệnh.

Điều kiện đạt M4: clone/migration và các nhóm Edit Settings trong phạm vi hỗ trợ được kiểm tra trên môi trường có đủ điều kiện; ghi rõ phần chưa thể test do license hoặc topology.

## 10. Giai đoạn 7 — Host, cluster và network

Triển khai lần lượt theo mức rủi ro, mỗi nhóm phải có đọc cấu hình, validate, xem trước thay đổi, xác nhận, gọi API và đọc lại trạng thái:

1. Host reconnect/disconnect, maintenance enter/exit; reboot/shutdown với kiểm tra VM đang chạy và điều kiện maintenance.
2. Host services SSH/ESXi Shell và policy, DNS/hostname, NTP/time service, firewall ruleset/allowed hosts theo API 8.
3. Storage adapter/disk/path, rescan, datastore và iSCSI theo quyền và capability.
4. Host networking: vSwitch/port group, VMkernel, uplink, VLAN/MTU; cảnh báo nguy cơ mất đường quản trị trước khi thực thi.
5. Advanced settings, truy cập/quyền, PCI passthrough, VM autostart và yêu cầu reboot khi có.
6. Cluster HA/DRS và vDS/distributed port group/LACP qua managed object/API tương ứng ở vCenter.
7. Với vDS/LAG 2 NIC 10Gb của người dùng: chỉ đọc cấu hình thật trước; mọi sửa đổi phải có phạm vi được duyệt và phương án khôi phục ngoài app nếu mất kết nối quản trị.
8. Không thử thay đổi network/host production để chứng minh tính năng; ưu tiên lab và cửa sổ bảo trì được cho phép.

Điều kiện đạt M5: ma trận chức năng chỉ rõ đã tích hợp, đã kiểm thử, chưa kiểm thử, thiếu quyền/license hoặc ngoài phạm vi API. Không hứa mọi tính năng của vSphere Client đều có API khả dụng tương đương.

## 11. Vòng kiểm thử Samsung qua ADB ở mỗi giai đoạn

Chạy từ thư mục `android` sau khi project và applicationId đã được tạo; thay các placeholder bằng giá trị thật, không đoán tên package hoặc serial:

```powershell
.\gradlew.bat :app:assembleDebug
# Chỉ cài khi build thành công.
adb devices -l
adb -s <SERIAL> install -r .\app\build\outputs\apk\debug\app-debug.apk
adb -s <SERIAL> shell am start -W -n <APPLICATION_ID>/<LAUNCHER_ACTIVITY>
```

1. Kiểm tra kết quả build, kết quả install và app mở được; lưu vị trí APK thực tế nếu cấu hình build làm đổi đường dẫn.
2. Chạy unit test và instrumented/UI test nhỏ nhất bao phủ thay đổi; với test thiết bị, chỉ chọn Samsung mục tiêu, không tự chạy trên mọi thiết bị đang kết nối.
3. Kiểm tra logcat có phạm vi theo app/process, không thu log của app khác và không ghi SOAP body/cookie/Authorization/password vào log.
4. Test thao tác chạm, cuộn, 5 tab, menu, bàn phím, Back, system bar, xoay màn hình và font lớn.
5. Test Home/mở lại, khóa/mở khóa, mất mạng, đổi Wi-Fi/VPN, hết phiên và process recreation. Phân biệt force-stop với process bị hệ điều hành hủy khi đánh giá hành vi nền.
6. Theo dõi lỗi crash/ANR và khả năng phục hồi dữ liệu/task; số liệu cache phải được đánh dấu cũ khi không còn xác minh được.
7. Đối chiếu kết quả với vCenter bằng cùng tài khoản; không tự chạy test ghi lên hạ tầng ngoài danh sách được phép.
8. Ghi kết quả theo APK/build, model Samsung, Android/One UI, phiên bản vCenter/ESXi và nhóm chức năng. Không đưa thông tin nhạy cảm vào ảnh/log bàn giao.

Test tự động được phép dùng fixture/mock có kiểm soát cho parser, lỗi mạng và trường hợp biên; dữ liệu đó chỉ tồn tại trong test, không vào luồng dữ liệu APK vận hành và không thay thế kiểm thử tích hợp thật.

## 12. Giai đoạn 8 — Nghiệm thu và bàn giao

1. Chạy các kiểm tra build/lint/test liên quan; kiểm tra toàn bộ luồng đã tích hợp trên Samsung mục tiêu và vCenter 8/ESXi 8 được cấp.
2. Kiểm tra không còn nguồn dữ liệu demo trong luồng vận hành, không có credential/token trong source/log và không có trust-all mặc định.
3. Kiểm tra đổi hồ sơ không rò dữ liệu/phiên, xác nhận thao tác nguy hiểm và không tự retry lệnh thay đổi.
4. Cập nhật README: yêu cầu môi trường, lệnh build/cài ADB, thiết lập kết nối/NPM/CA riêng và vị trí APK.
5. Bàn giao source Android, Gradle Wrapper, APK debug cài được và ma trận chức năng/kết quả kiểm thử/giới hạn còn lại.
6. Chỉ tạo bản release khi có cấu hình ký được chủ dự án quản lý; không đưa khóa ký hoặc password vào kho mã.
7. Không ghi hoàn thành nghiệm thu nếu chưa có APK hoặc chưa test trên Samsung; ghi blocker cụ thể thay vì đánh dấu đạt.

## 13. Thông tin cần bổ sung khi bắt đầu thực hiện

- Model Samsung và Android/One UI; thiết bị có sẵn để bật USB debugging hay không.
- Phiên bản/build vCenter 8 và ESXi 8 thực tế; license và các tính năng được bật.
- Đường mạng điện thoại tới vCenter/NPM: LAN/VPN, port và CA riêng nếu có. Credential nhập trên app, không cần đưa vào tài liệu.
- Danh sách VM/host/datastore/network lab được cho phép dùng cho kiểm thử ghi; mặc định chỉ kiểm thử đọc cho tới khi được duyệt.
- Yêu cầu Android tối thiểu và kênh phân phối để chốt SDK; người giữ khóa ký nếu cần APK release.

Thứ tự ưu tiên: M0 Samsung ADB → M1 kết nối thật → M2 inventory thật → M3 task/thao tác cơ bản → M4 VM nâng cao → M5 hạ tầng → M6 nghiệm thu. Không chốt thời gian cố định trước khi kiểm tra toolchain, API, quyền/license và khả năng tiếp cận môi trường thử.
