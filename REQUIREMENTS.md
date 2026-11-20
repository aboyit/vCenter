# Yêu cầu app Android vCenter — WebNew

## 1. Mục tiêu và hiện trạng

Xây app Android quản lý hệ thống vCenter 8 và ESXi 8 hiện có của người dùng. Mục tiêu chức năng rộng, gồm theo dõi hạ tầng, quản trị VM, migration và cài đặt host. Đối chiếu từng chức năng với API, quyền hạn, license và cấu hình thực tế của vSphere 8.

Mã trong `ui-reference/` là bản web mô phỏng đã được người dùng chỉnh giao diện. Chưa có kết nối thật, task thật, xác thực NPM thật hoặc APK. Không coi những mục có tên API trong giao diện là đã triển khai chức năng.

Người dùng yêu cầu task phải lấy từ vCenter. Trạng thái thành công phải được xác minh từ server. Không đưa tác vụ mô phỏng vào danh sách task thật.

## 2. Giao diện đã chốt

- Tên app và tên trên header là `vCenter`.
- Dòng phụ thương hiệu là `WebNew`, thay cho `INFRASTRUCTURE MANAGER`.
- Giữ màu, bố cục, tương tác theo mẫu web đang bàn giao. Mẫu hiện dùng nền xanh xám tối theo hình người dùng cung cấp, không tự chuyển màu khác.
- Header trên điện thoại phủ hết chiều ngang từ mép trái sang mép phải.
- Thanh dưới có đúng 5 mục cố định, chia đều chiều ngang và không cuộn ngang gồm Tổng quan, Máy ảo, Hosts & Cluster, Tasks vCenter, Datastore.
- Góc phải header có icon ba gạch. Bấm mở menu các mục còn lại gồm Network, Cảnh báo, Kết nối, API / Phạm vi.
- Giữ mục API / Phạm vi để thể hiện chức năng thực sự đã tích hợp và điều kiện áp dụng.
- Hỗ trợ nút Back, bàn phím, màn hình nhỏ, tablet và vùng system bar của Android.
- Các số liệu, loading, lỗi, không có dữ liệu và thiếu quyền phải có trạng thái rõ ràng.

## 3. Cấu hình kết nối và đăng nhập

Khi mở lần đầu cho cấu hình và nhớ hồ sơ cho lần sau. Các trường cần có gồm tên kết nối, host hoặc domain/IP, port (mặc định 443), tài khoản vCenter và mật khẩu vCenter.

Có tùy chọn HTTP Basic Auth do Nginx Proxy Manager Access List đặt trước vCenter. Khi bật, hiển thị tài khoản HTTP Auth và mật khẩu HTTP Auth ngay trong cùng màn hình. Người dùng nhập cả hai bộ tài khoản một lần tại app. App tự gửi thông tin NPM khi gọi API qua proxy. Không phụ thuộc vào việc người dùng mở trình duyệt nhập lại.

Tài khoản NPM Access List khác tài khoản vCenter. Mật khẩu NPM cũng khác tài khoản quản trị NPM port 81. Hồ sơ gắn với đúng host/port. Không gửi Authorization đến origin khác khi redirect.

Tên host, port, tên kết nối và username cần được nhớ. Đề xuất tùy chọn nhớ mật khẩu để tránh phải nhập lại khi mở app. Nếu nhớ thì mã hóa bằng khóa Android Keystore. Khi không nhớ thì giữ trong bộ nhớ phiên. Xóa thông tin đã nhớ khi người dùng chọn quên hồ sơ. Không ghi credential/token vào log hay mã nguồn. Đây là đề xuất triển khai cho tính năng nhớ đăng nhập, cần cho người dùng kiểm tra hành vi.

Có kiểm tra kết nối, lỗi NPM 401/403, sai tài khoản vCenter, thiếu quyền, lỗi DNS/TLS, timeout, hết phiên và mất mạng. Hỗ trợ chuỗi tin cậy TLS hợp lệ và CA riêng theo cấu hình. Không bật trust-all mặc định. Chỉ gửi lại các yêu cầu đọc an toàn sau khi tái đăng nhập, xử lý trạng thái thao tác thay đổi trên server trước khi cho thử lại.

## 4. Kiến trúc dự kiến

App native Kotlin + Jetpack Compose, ViewModel, Coroutines/Flow, OkHttp. Có lớp data/API, repository và UI. Đề xuất kết nối trực tiếp Android → HTTPS → NPM (nếu có) → vCenter, không bắt buộc một backend trung gian.

Ưu tiên vSphere Web Services API SOAP `/sdk` để bao phủ cấu hình host/VM, task và tránh tranh chấp hai bộ HTTP Basic Auth. Username/password vCenter nằm trong SOAP Login, còn Authorization dùng cho NPM. Giữ cookie phiên vSphere cho đúng hồ sơ.

REST `/api/session` dùng HTTP Basic Auth để tạo phiên. Không thể gắn hai giá trị Basic Auth khác nhau vào một Authorization header. Nếu dùng REST qua proxy có HTTP Auth, phải thiết kế rõ luồng lấy phiên/SSO và kiểm thử với vCenter 8. Không giả định SOAP cookie có thể dùng thẳng như REST session token.

Đọc ServiceContent/API version và dùng hợp đồng API tương thích vSphere 8. Không lấy chức năng chỉ có trong vSphere 9 từ tài liệu latest rồi coi là được hỗ trợ.

Phiên, dữ liệu và task phải gắn với đúng vCenter/instance. Không rò dữ liệu hoặc credential khi đổi hồ sơ.

## 5. Theo dõi hạ tầng

- Tổng quan CPU, RAM, storage, số VM/host, trạng thái và cảnh báo lấy từ vCenter.
- VM có danh sách, tìm kiếm, lọc trạng thái và chi tiết guest OS, IP khi có VMware Tools, host, datastore, CPU, RAM, power, snapshot.
- Hosts & Cluster có danh sách, sức khỏe, kết nối, maintenance, tài nguyên và VM thuộc host.
- Datastore có capacity, free/used, trạng thái và đối tượng liên quan.
- Network có standard port group, vDS, distributed port group và LAG/LACP khi đủ quyền.
- Cảnh báo lấy alarm/event phù hợp từ vCenter, thể hiện đối tượng, thời điểm, mức độ và nội dung.
- Có refresh, phân trang/đọc theo lô phù hợp, dữ liệu chưa tải xong và đánh dấu số liệu cũ khi mất mạng.

## 6. Thao tác VM

- Power on, power off, reset. Shutdown guest và reboot guest khi VMware Tools hỗ trợ.
- Tạo snapshot có tên/mô tả và tùy chọn phù hợp, xem cây snapshot, revert, xóa snapshot, consolidation khi cần và API cho phép.
- Clone VM với tên, thư mục, host/resource pool và datastore theo môi trường.
- Migration gồm đổi host, đổi datastore hoặc cả hai. Chọn tài nguyên đích, kiểm tra tương thích và hiển thị kết quả trước khi chạy.
- Kiểm tra các điều kiện vMotion, storage/network, license và trạng thái VM do server xác nhận.
- Các thao tác có rủi ro mất trạng thái/dữ liệu như power off, reset, revert, xóa đĩa hoặc reboot host phải cho người dùng xem đối tượng và xác nhận.

### Edit Settings của VM hiện có

Người dùng đã chỉ rõ bản mẫu thiếu nhiều mục. Cần triển khai đầy đủ các nhóm sau, không dừng ở CPU/RAM.

| Nhóm | Nội dung |
| --- | --- |
| CPU | vCPU, core/socket, CPU Hot Add, reservation, limit, shares |
| RAM | Dung lượng, Memory Hot Add, reservation, limit, shares |
| Disk | Thêm disk, gắn VMDK có sẵn, tăng dung lượng, controller/unit, disk mode khi hỗ trợ, tháo disk và phân biệt thao tác xóa file |
| NIC | Thêm/tháo NIC, loại adapter khi hỗ trợ, standard/distributed port group, MAC, Connected và Connect at power on |
| CD/DVD | Thêm/tháo, ISO từ datastore, trạng thái kết nối |
| Controller | SCSI/SATA/NVMe phù hợp hardware version và thiết bị hiện có |
| Boot/VM Options | Guest OS, BIOS/EFI, Secure Boot, boot order/delay, VMware Tools và advanced parameters |

Đọc cấu hình hiện tại trước khi sửa, chỉ gửi trường thay đổi. Giữ đúng device key/controller key. Dùng changeVersion khi có để phát hiện cấu hình bị sửa đồng thời. Kiểm tra giới hạn, khả năng Hot Add, VMware Tools, power state và quyền theo đối tượng. Không tự tắt VM để chỉnh khi chưa có xác nhận. Disk expansion cần xử lý snapshot và các hạn chế do vSphere trả về. Tuyệt đối không giảm dung lượng disk bằng cập nhật capacity.

## 7. Task phải là task thật của vCenter

Dùng TaskManager, TaskHistoryCollector, Task/TaskInfo và PropertyCollector hoặc cơ chế đọc phù hợp. Có danh sách task theo phạm vi quyền, trạng thái, đối tượng, người thực hiện, thời điểm, tiến độ và lỗi.

Với API trả task reference, theo dõi đúng task đó đến queued/running/success/error. Giữ task reference để xem lại sau khi app chuyển nền hoặc khởi động lại. Không tự gửi lại lệnh tạo task khi timeout chưa rõ server đã nhận hay chưa. Khi trở lại foreground đọc lại trạng thái task từ server.

Chỉ cho hủy khi task cancelable và tài khoản có quyền. Các API đồng bộ như ShutdownGuest có thể không trả task reference. Hiển thị đó là yêu cầu đã gửi và kiểm tra trạng thái thực tế, không tự tạo một task giả của vCenter.

## 8. Quản trị host, cluster và network

Người dùng yêu cầu có các cấu hình trong host, không chỉ vài thông tin tổng quan.

- Host connection/reconnect/disconnect, maintenance enter/exit và trạng thái tiến trình.
- Reboot/shutdown host với kiểm tra VM đang chạy và maintenance phù hợp.
- Dịch vụ host như SSH/ESXi Shell, start/stop và policy khi được hỗ trợ.
- DNS, hostname, NTP/time service và cấu hình thời gian phù hợp API.
- Firewall ruleset, allowed hosts theo API và quyền.
- Storage adapters, disk/path, rescan, datastore và iSCSI khi hỗ trợ.
- Host network gồm vSwitch/port group, VMkernel, uplink, VLAN/MTU theo API.
- Advanced settings, truy cập/quyền, PCI passthrough và VM autostart theo khả năng, điều kiện reboot và quyền.
- Cluster HA/DRS, vDS/distributed port group và LACP là chức năng quản lý cấp vCenter cần tích hợp API tương ứng.

Hạ tầng người dùng có vDS và 2 NIC 10Gb cấu hình LAG. Không áp dụng cấu hình mẫu hoặc tự thay đổi LAG/vDS của hệ thống. Màn hình sửa phải đọc và thể hiện cấu hình thật, nêu rõ thay đổi trước khi thực thi. Những tính năng yêu cầu truy cập host trực tiếp cần được xác định và khai báo riêng.

## 9. Lộ trình và tiêu chí bàn giao

1. Project Android build được, tên vCenter/WebNew, theme và điều hướng như bản mẫu.
2. Đăng nhập thật qua HTTPS, tùy chọn NPM HTTP Auth, nhớ hồ sơ, quản lý phiên/TLS và logout.
3. Inventory, tổng quan, VM, host, datastore, network và cảnh báo thật. APK đầu tiên để kiểm tra kết nối và đối chiếu dữ liệu.
4. Task thật, power/guest actions, snapshot, Edit Settings đầy đủ, clone/migration.
5. Quản trị host/cluster/vDS theo phạm vi API đã kiểm chứng.
6. Kiểm thử trên vCenter 8/ESXi 8, bàn giao mã nguồn Android, Gradle Wrapper, hướng dẫn build, APK cài được và ghi chú chức năng đã kiểm thử/còn thiếu.

Người dùng mới yêu cầu plan và chuyển sang IDE ở thời điểm đóng gói. Các công nghệ, thứ tự giai đoạn, cách lưu credential và kiến trúc trực tiếp nêu ở trên là đề xuất kỹ thuật, không phải phần đã được triển khai.

Chưa có endpoint và tài khoản vCenter/NPM trong bộ bàn giao. Không tự điền credential từ hội thoại khác. Chỉ cần người dùng nhập trên thiết bị khi thử thật. Chưa có dữ liệu về minSdk/targetSdk, máy thử hay khóa ký release, cần chọn và ghi rõ trong project. Có thể build debug APK trước, khóa ký release do chủ dự án quản lý.

## 10. Nguồn tham khảo chính thức

- Android architecture https://developer.android.com/topic/architecture/recommendations
- Android Keystore https://developer.android.com/privacy-and-security/keystore
- vSphere 8 API https://developer.broadcom.com/xapis/vsphere-web-services-api/8.0.0.0/
- SessionManager https://developer.broadcom.com/xapis/vsphere-web-services-api/latest/vim.SessionManager.html
- VirtualMachineConfigSpec https://developer.broadcom.com/xapis/vsphere-web-services-api/latest/vim.vm.ConfigSpec.html
- TaskManager https://developer.broadcom.com/xapis/vsphere-web-services-api/latest/vim.TaskManager.html
- REST session https://developer.broadcom.com/xapis/vsphere-automation-api/latest/api/session/post/

Các link latest chỉ làm điểm tra cứu. Kiểm tra lại phiên bản 8 trước khi chọn thuộc tính/phương thức.
