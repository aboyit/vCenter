# Bàn giao app Android vCenter — WebNew

## Trạng thái source khi đưa lên Git (20/11/2026)

Source hiện tại gồm M1-M3 và phần M4 đang triển khai, chưa phải bản M4 đã nghiệm thu. Đã bổ sung `VmAdvancedPanel` và build debug thành công sau khi sửa giao diện Snapshot ngày 20/11/2026. Các kết quả build/test M1-M3 lịch sử bên dưới là kết quả trước thay đổi M4; không thay thế nghiệm thu M4 trên vCenter thật.

Git lưu source Android, Gradle Wrapper, tài liệu và giao diện tham chiếu. Theo yêu cầu ngày 21/11/2026, toàn bộ source unit test và instrumentation test đã bị xóa khỏi repository và máy; kết quả test và hướng dẫn test bên dưới chỉ còn giá trị lịch sử, không chạy được nếu chưa khôi phục các file test từ lịch sử Git. Không lưu SDK/JDK cục bộ, cache Gradle, thư mục build, APK/AAB, `local.properties`, cấu hình IDE, khóa ký hoặc file môi trường riêng; các file này vẫn giữ trên máy và được loại bằng `.gitignore`.

## Dùng gói này trong IDE

1. Giải nén và mở thư mục `vcenter-android-handoff` trong IDE có AI coding.
2. Cho AI đọc toàn bộ `REQUIREMENTS.md`, `IDE_PROMPT.md` và ba file trong `ui-reference/`.
3. Dán nội dung `IDE_PROMPT.md` vào chat của IDE để triển khai Android.
4. Đọc [PLAN.md](PLAN.md) để triển khai theo từng giai đoạn, từ thiết lập Samsung qua ADB đến nghiệm thu APK. Toàn bộ dữ liệu hạ tầng và task phải lấy từ API/SDK chính thức tương thích vCenter 8; không dùng dữ liệu demo trong bản vận hành.
5. Tạo project Android trong thư mục `android/`. Mã web trong `ui-reference/` là mẫu hình ảnh và luồng tương tác.

## Bố cục danh sách VM

- Cây Cluster/ESXi dùng đệm và thụt lề nhỏ; hàng VM tối thiểu 48 dp, tên và OS giới hạn một dòng, tên dài có dấu ba chấm và xem đầy đủ trong chi tiết.
- Thu gọn tiêu đề bảng, cột tên/trạng thái và badge nguồn; vẫn cuộn ngang để xem các cột còn lại và bấm VM để mở chi tiết.
- Hộp chi tiết VM theo mẫu web: tên và badge nguồn cùng hàng, hai thẻ CPU/RAM và Host/Datastore cạnh nhau, sáu nút thao tác theo lưới hai cột và nút Đóng ở góc phải. RAM đổi từ MiB sang GiB. Nguồn và Snapshot mở hộp thao tác riêng đồng bộ màu với chi tiết, có tiêu đề, tên VM, nút Quay lại và lý do khi thao tác không khả dụng; không dùng menu thả xuống phủ lên nội dung. Giữ xác nhận trước khi gửi lệnh; cây snapshot xem trong hộp Snapshot. Migration, Cấu hình, Clone VM và Console dùng màu vô hiệu hóa và nhãn Chưa hỗ trợ vì chưa tích hợp; không chạy dữ liệu mô phỏng.

### Sua nut thao tac VM ngay 21/11/2026

- Cac nut Cau hinh, Clone VM va Migration trong chi tiet VM mo truc tiep nhom tuong ung va doc lai cau hinh; khong con la nut placeholder khi callback M4 san sang. Migration van chi la cold migration, chua co live/Storage vMotion.
- Moi lan mo chi tiet VM, app tu doc lai inventory va cau hinh tu vCenter; khoa nut gui trong khi kiem tra hoac khi doc loi, co nut thu lai. Khong yeu cau sang tab Task de xem trang thai VM.
- Loi thao tac thong thuong (ke ca loi validation truoc khi gui) khong duoc coi la lenh chua doi soat va khong khoa gui lenh moi. Loi doc so lenh duoc theo doi rieng; lenh chua ket thuc chi khoa dung VM lien quan.
- Lenh chua doi soat van chan gui lenh thay doi, ke ca bat nguon, nhung khong chan doc cau hinh VM. Kiem tra Task va doi chieu vSphere Client truoc khi xac nhan doi soat; khong xoa so lenh de bo qua khoa an toan.

### Chi tiet tai nguyen VM

- Hai khung CPU/RAM/Disk va Host/Datastore cung chieu rong/chieu cao, tu gian theo noi dung.
- Disk la tong dung luong cau hinh VirtualDisk tu `config.hardware.device` (GiB), khong phai dung luong datastore hay dung luong da dung trong guest. Thieu capacity bat ky disk nao thi hien chua co du lieu, khong cong tong thieu. Can refresh inventory de lay property moi.

### Dieu chinh popup Snapshot

- Danh sach snapshot gon: padding doc 6 dp, nut chon cach noi dung 8 dp; ten/nhan hien tai cung hang, ngay gio/reference cung hang, mo ta rieng neu co. Vung cham ca hang toi thieu 48 dp. Ngay gio hien `dd-MM-yyyy HH:mm:ss` theo mui gio dien thoai, khong hien chuoi ISO/raw khi loi. Ten VM nam ben phai tieu de Snapshot, toi da hai dong.

- Hop xac nhan can giua checkbox/radio voi nhan; bo dong thong bao chung server kiem tra quyen, giu canh bao nguy hiem theo thao tac. Huy dung nut vien, Xac nhan dung nut nen xanh; khong thay doi validation hay co che gui lenh.

- Icon Nguon/Snapshot ve bang Canvas, khong dung ky tu phu thuoc font Android.
- Mo Snapshot hien ngay danh sach theo cap, danh dau snapshot hien tai va cho chon snapshot truoc khi bam Xoa/Revert; khong can bam mo cay lan nua.
- Cac nut Tao moi, Revert, Xoa, Xoa toan bo va Consolidate disk gon theo noi dung, tu xuong dong theo chieu rong; giu vung cham toi thieu 48 dp. Xoa/Revert dung snapshot da chon va van can xac nhan truoc khi gui. Thieu du lieu/quyen hoac khong du dieu kien thi nut bi khoa.
- Da bo sung `VmAdvancedPanel` de go loi compile M4: CPU/RAM/topology/hot add, disk, NIC, ISO, boot, clone va cold migration su dung hop dong va validation M4 hien co. Chon doi tuong tu inventory, doc config truoc khi sua, xem lai va xac nhan truoc gui; khoa khi busy/cho doi soat. Chua nghiem thu M4 tren vCenter that.

## Bố cục Host

- Tab Tổng quan của từng ESXi có hai thanh CPU/RAM: đã dùng, trống và tổng. CPU dùng quickStats MHz / (core × MHz), RAM dùng quickStats MiB đổi sang byte / memorySize, cùng cách tính Tổng quan. Chọn node khác đổi số liệu theo node; thiếu hoặc không hợp lệ báo chưa đủ dữ liệu, không lấy số tổng toàn cụm thay thế.

- Bảng thông tin quản trị host gom các dòng liền nhau, không cộng khoảng cách panel giữa từng dòng. Hai hàng nút nằm trong một nhóm riêng, giữ vùng chạm mặc định của Material.

- Bỏ dòng chân trang nguồn dữ liệu trên các màn hình. IP/tên ESXi và icon kết nối/health nằm cùng hàng; xanh là kết nối/health tốt, vàng là maintenance/cảnh báo, đỏ là mất kết nối/lỗi, xám là chưa có dữ liệu. Nhãn trạng thái vẫn có cho trình đọc màn hình; thông tin chi tiết giữ trong tab Tổng quan.

- Icon mắt xích thể hiện kết nối vCenter–ESXi (liền/xanh khi connected, ngắt/đỏ khi disconnected hoặc notResponding, xám khi chưa rõ); không dùng icon nguồn. Icon vẽ bằng Canvas để không phụ thuộc font. Chấm tròn riêng thể hiện health; maintenance xem trong chi tiết host, không ghi đè trạng thái kết nối.
- Không hiển thị danh sách Resource pools trong trang Host; việc đọc inventory pool không thay đổi cấu hình vCenter.
- Bộ chọn ESXi dùng hai cột bằng nhau trên điện thoại, căn đầy chiều ngang và giữ ô cuối cùng cùng độ rộng khi số host lẻ. Vùng chọn hẹp chuyển sang một cột.

## Sửa lỗi inventory SOAP ngày 19/11/2026

- Đã xác nhận trên vCenter thực: `RetrievePropertiesEx` trả `InvalidProperty` cho `config.lacpGroupConfig`. Các trường LACP thuộc kiểu runtime `VMwareDVSConfigInfo`, không phải kiểu khai báo `DVSConfigInfo` của `config`.
- Đọc toàn bộ thuộc tính `config` của vDS rồi phân tích các trường LACP, thay vì yêu cầu đường dẫn con của subtype. Không bỏ tính năng đọc LAG.
- Lỗi SOAP hiển thị loại fault và phương thức; tên thuộc tính chỉ hiển thị nếu nằm trong danh sách yêu cầu của app. Không hiển thị faultstring hoặc nội dung tùy ý từ server.
- Đã build, chạy test parser/request liên quan và cài qua ADB Wi-Fi; trang tổng quan lấy lại dữ liệu và hiển thị tổng tài nguyên ESXi. Đây chưa phải nghiệm thu toàn bộ M2.

## Mở thiết kế hiện tại

Tại thư mục gói chạy `python -m http.server 8080`, rồi mở `http://localhost:8080/ui-reference/`. Trên Windows có thể dùng `py -m http.server 8080`. Hoặc mở `ui-reference/index.html` trực tiếp trong trình duyệt. Chế độ HTTP cục bộ cho phép thử việc lưu hồ sơ ổn định hơn.

Bản trực tuyến tại https://vsphere-pocket-webnew.majorcoil4.chatgpt.site

Trong DevTools chọn kích thước điện thoại để xem 5 menu dưới và menu ba gạch. Bản desktop có thanh điều hướng bên trái.

- `ui-reference/index.html` chứa HTML, CSS, màu sắc và layout.
- `ui-reference/app.js` chứa dữ liệu mẫu, các màn hình VM, migration, snapshot và cấu hình kết nối.
- `ui-reference/host-admin.js` mở rộng quản trị host, task, cluster và điều hướng.

Mã web chỉ là mẫu hình ảnh và tương tác; Edit VM mẫu mới có CPU/RAM. Project Android đã tích hợp đăng nhập SOAP HTTPS `/sdk` cho M1 (người dùng xác nhận đăng nhập vCenter thật) và bản M2 inventory chỉ đọc. Chưa đối chiếu dữ liệu inventory trên Samsung/vSphere Client; không tích hợp task thật. Không sử dụng dữ liệu demo trong app Android.

## Kết nối M1

Trong menu **Kết nối**, tạo hồ sơ (tên, host/IP, port 443, username vCenter), nhập mật khẩu vCenter, tùy chọn bật NPM Access List HTTP Basic Auth và nhập tài khoản/mật khẩu HTTP Auth riêng. Bấm **Kiểm tra & đăng nhập**; tên server, phiên bản và instance ID chỉ hiển thị khi SOAP Login trả phiên thành công. Có thể lưu nhiều hồ sơ, chọn hồ sơ khác (phiên cũ và cookie bị xóa), đăng xuất hoặc quên/xóa hồ sơ. Mật khẩu chỉ lưu khi chọn **Nhớ mật khẩu**, mã hóa AES/GCM với Android Keystore; hồ sơ không chứa mật khẩu. Khi không chọn, mật khẩu ở bộ nhớ ViewModel cho tới khi xóa/đóng tiến trình, và được xóa khi logout. Backup bị vô hiệu hóa. Không có tự đăng nhập sau khi mở lại, không tự thử lại thao tác và không có yêu cầu thay đổi hạ tầng. Sau login app tự đọc inventory; task chưa kết nối.

Chỉ chấp nhận HTTPS, không chuyển hướng, TLS/CA hệ thống Android và hostname verification mặc định. **Chưa hỗ trợ nhập CA riêng**: chứng chỉ self-signed/CA riêng không có trong kho tin cậy của Android sẽ bị từ chối; không có chế độ trust-all. Phiên sống trong bộ nhớ; khi hết hạn cần đăng nhập lại thủ công. Nếu không liên lạc được khi logout, phiên cục bộ bị xóa nhưng không xác nhận phiên server đã bị thu hồi. App không lưu hoặc in raw SOAP response, cookie hay credential. NPM Basic Auth đi trong HTTP Authorization; vCenter credentials chỉ nằm trong SOAP Login trên TLS; REST `/api/session` không dùng vì tranh chấp Authorization.

Hợp đồng tham chiếu: [ServiceInstance vSphere 8.0.3](https://developer.broadcom.com/xapis/vsphere-web-services-api/8.0.3/vim.ServiceInstance.html#retrieveContent), [SessionManager vSphere 8.0.3](https://developer.broadcom.com/xapis/vsphere-web-services-api/8.0.3/vim.SessionManager.html#login). Người dùng đã xác nhận đăng nhập với vCenter thật; inventory chưa được xác nhận trên hạ tầng thật.

## Inventory M2 (chỉ đọc)

Sau đăng nhập hoặc bấm **Làm mới**, phiên SOAP hiện tại tạo `ContainerView` từ `rootFolder`, lấy `VirtualMachine`, `HostSystem`, `ClusterComputeResource`, `Datastore`, `Network`, `DistributedVirtualPortgroup`, `VmwareDistributedVirtualSwitch` qua `RetrievePropertiesEx`/`ContinueRetrievePropertiesEx` theo trang 100 đối tượng (giới hạn 100 trang/10.000 đối tượng), rồi `DestroyView`. Token còn lại được hủy bằng `CancelRetrievePropertiesEx` khi có thể. Chỉ có API view lifecycle tạo/hủy và truy vấn đọc; không có lệnh thay đổi VM/host.

Tổng quan đếm đối tượng trong phạm vi tài khoản, CPU host là tổng core × MHz danh định, RAM host tổng dung lượng và `overallMemoryUsage` (MiB), storage là tổng capacity và used = capacity − free của datastore duy nhất. Nếu thiếu property cho bất kỳ đối tượng nào trong tổng, tổng tương ứng hiện chưa có dữ liệu; không hiển thị số 0 giả. VM hiển thị trạng thái/CPU/RAM/guest OS/IP (IP tùy VMware Tools), host và datastore theo reference; host/cluster, datastore và network có danh sách chi tiết. Refresh lỗi giữ snapshot cũ với dấu dữ liệu cũ/thời gian; đổi hồ sơ/logout xóa ngay snapshot, lỗi `NotAuthenticated` đưa về login, không tự đăng nhập lại. Property thiếu quyền hoặc không trả về hiển thị thiếu dữ liệu; số lượng không đại diện tài nguyên ngoài quyền truy cập.

Trang Máy ảo dùng bố cục mẫu HTML: tiêu đề/mô tả, nút Tạo VM vô hiệu hóa kèm thông báo chưa tích hợp, khối tìm kiếm và lọc cùng hàng trong một panel, bảng cuộn ngang với tên/OS, trạng thái, host, CPU, RAM, IP. Chạm hàng VM để mở hộp thoại chi tiết chỉ đọc (bao gồm datastore, property thiếu và cây snapshot); không hiển thị card chi tiết cho mọi VM ngay trên danh sách. Bảng dùng chung với Tổng quan.

Danh sách VM hỗ trợ kết hợp tìm tên và lọc nguồn: tất cả, đang bật, đã tắt, tạm ngưng, chưa rõ (thiếu property hoặc giá trị chưa nhận diện). Property `VirtualMachine.snapshot` cung cấp cây snapshot chỉ đọc: tên, reference, cấp cha/con, mô tả, thời gian server, power state lúc tạo, quiesced và đánh dấu currentSnapshot nếu server có trả về. Bấm **Xem cây snapshot** trên từng VM để mở; thiếu property được báo riêng, không suy ra không có snapshot khi server báo lỗi đọc. Không có thao tác tạo/xóa/revert. Hợp đồng API 8.0.3: [SnapshotInfo](https://developer.broadcom.com/xapis/vsphere-web-services-api/8.0.3/vim.vm.SnapshotInfo.html), [SnapshotTree](https://developer.broadcom.com/xapis/vsphere-web-services-api/8.0.3/vim.vm.SnapshotTree.html). Phần bổ sung ngày 19/11/2026 chưa được đối chiếu với vCenter thật.

**Chưa tích hợp:** cảnh báo/event (không báo danh sách trống giả), standard vSwitch/portgroup cấu hình host, LAG/LACP, thao tác snapshot, task và mọi thao tác M3+, CA riêng. Network chỉ gồm các managed entity nói trên; Network có thể đại diện standard portgroup nhưng không bao gồm cấu hình switch. Kiểm tra trên Samsung với cùng tài khoản và phạm vi vSphere Client vẫn cần thực hiện trước khi coi M2 đã nghiệm thu.

API 8.0.3 chính thức: [PropertyCollector RetrievePropertiesEx/Continue/Cancel](https://developer.broadcom.com/xapis/vsphere-web-services-api/8.0.3/vmodl.query.PropertyCollector.html), [ViewManager CreateContainerView](https://developer.broadcom.com/xapis/vsphere-web-services-api/8.0.3/vim.view.ViewManager.html), [ContainerView](https://developer.broadcom.com/xapis/vsphere-web-services-api/8.0.3/vim.view.ContainerView.html), [ManagedObjectView DestroyView](https://developer.broadcom.com/xapis/vsphere-web-services-api/8.0.3/vim.view.ManagedObjectView.html).

## Code va debug qua ADB Wi-Fi trong VS Code

Bo cong cu rieng cua du an: JDK 17 trong `.tools/jdk/`, Android SDK trong `.tools/android-sdk/` (platform-tools, platforms;android-35, build-tools;35.0.0). Cac thu muc nay khong commit; khong thay doi Java/PATH mac dinh cua Windows.

Trong PowerShell tai thu muc goc:

```powershell
. .\android\scripts\Enter-AndroidEnv.ps1
adb devices -l
```

Android 11+: bat Developer options > Wireless debugging, PC va dien thoai cung mang Wi-Fi. Lan dau mo **Pair device with pairing code**, lay **IP address & port** cua hop thoai de chay lenh sau; nhap ma khi terminal yeu cau (khong ghi ma vao file):

```powershell
.\android\scripts\Android-Dev.ps1 -Action Pair -Serial '10.0.1.52:PORT_PAIR'
.\android\scripts\Android-Dev.ps1 -Action Connect -Serial '10.0.1.52:PORT_CONNECT'
.\android\scripts\Android-Dev.ps1 -Action Run -Serial '10.0.1.52:PORT_CONNECT'
.\android\scripts\Android-Dev.ps1 -Action Logcat -Serial '10.0.1.52:PORT_CONNECT'
```

Thay PORT_PAIR/PORT_CONNECT bang cong TCP that (1-65535). Cong ket noi nam o man hinh Wireless debugging chinh, KHAC cong pairing; ma pairing khong phai cong. Cong co the doi sau khi bat/tat Wi-Fi/debug. `adb mdns services` giup xem dich vu dang quang ba; chi ket noi thiet bi cua ban. Khong tu dong bat ADB TCP 5555, sua firewall, kill ADB server hay xoa du lieu.

VS Code: **Terminal > Run Task** co **Android: Pair Wi-Fi**, **Android: Connect Wi-Fi**, **Android: Build Debug**, **Android: Build, Install and Run**, **Android: App Logcat**. Ctrl+Shift+B build debug. Task hoi endpoint moi lan, khong luu IP/ma pairing trong Git. Run chi cai de bang `adb install -r` sau khi build thanh cong; khong uninstall/clear data. Neu chu ky APK cu khac debug keystore may nay, dung lai va dung dung keystore cu; khong go app de vuot loi. Logcat chi loc process app dang chay; Ctrl+C de dung, chay lai task khi process restart. Khong chia se log chua thong tin nhay cam. Day la debug bang log, khong phai breakpoint Kotlin/Java.

Kiem tra ngay 20/11/2026: JDK 17/SDK 35 va ADB Wi-Fi da hoat dong voi Samsung SM-N976N Android 12. Sau bo sung panel M4 va sua Snapshot: assembleDebug thanh cong, 54 unit test va 11 UI test M3/panel M4 dat; lint 0 loi/15 canh bao. Cai de app chinh bi tu choi `INSTALL_FAILED_UPDATE_INCOMPATIBLE` do chu ky khac APK cu; khong go app hay xoa du lieu. Can debug keystore goc de cai de giu ho so. UI test dung app uitest rieng, khong thao tac vCenter that.

## Build Android trên Windows

Cấu hình M0: JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Kotlin 2.1.20, Android SDK/target 35, Build Tools 35.0.0, minSdk 26 (Android 8.0). minSdk là lựa chọn ban đầu, cần đối chiếu với Samsung thử nghiệm. Application ID: `vn.webnew.vcenter`.

Bộ công cụ cục bộ nằm trong `.tools/`, cache Gradle trong `.gradle-user-home/`; không bàn giao hoặc commit các thư mục này. Không thay đổi Java mặc định của máy. Trên máy khác, cài JDK 17/Android SDK và đặt `sdk.dir` trong `android/local.properties` hoặc `ANDROID_HOME`.

Với bộ công cụ cục bộ đã cài, chạy từ thư mục gốc dự án:

```powershell
$env:JAVA_HOME = (Get-ChildItem .\.tools\jdk -Directory | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "$PWD\.tools\android-sdk"
$env:GRADLE_USER_HOME = "$PWD\.gradle-user-home"
Set-Location .\android
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:assembleUitestAndroidTest --no-daemon --console plain
```

APK dự kiến tại `android/app/build/outputs/apk/debug/app-debug.apk`; chỉ coi build thành công khi lệnh hoàn tất và file tồn tại. Lệnh `assembleUitestAndroidTest` chỉ biên dịch test, chưa chạy test thiết bị.

Luồng mở app: chưa có phiên đăng nhập trong bộ nhớ thì chỉ hiển thị form login, ẩn tab và menu quản trị. Đăng nhập thành công chuyển Tổng quan; menu ba gạch có Đăng xuất và không hiện form kết nối. Logout quay về login. Nhớ mật khẩu không đồng nghĩa đã đăng nhập: sau khi process bị hủy, phải xác thực lại với server; chưa tự phục hồi phiên. Tổng quan sau đăng nhập hiển thị tổng hợp property từ inventory nếu đọc thành công.

Tổng quan đã chỉnh theo mẫu web: header 65dp với tên hồ sơ/menu, thanh tab có vạch nhấn phía trên thay cho pill Material; bỏ khối tiêu đề Tổng quan hạ tầng/mô tả/nút làm mới để tiết kiệm diện tích (vẫn tự tải lại mỗi 20 giây khi foreground), giữ dải thời điểm dữ liệu API, lề ngang 16dp. Có 4 thẻ VM/CPU/RAM/storage (2 cột trên điện thoại, 4 trên màn rộng), khu hiệu suất, host có icon/trạng thái/CPU-RAM, bảng VM cuộn ngang đủ tên/OS/trạng thái/host/CPU/RAM/IP và khối task/cần chú ý (hai cột trên màn rộng). Các giá trị lấy từ inventory/quickStats thật. Tổng tài nguyên ESXi hiển thị ba thanh CPU/RAM/Storage với đã dùng, trống và tổng dung lượng, không còn chart lịch sử từng host. CPU cộng quickStats overallCpuUsage và công suất core × MHz; RAM cộng quickStats overallMemoryUsage và memorySize. Storage cộng datastore duy nhất theo reference, không cộng trùng datastore dùng chung. Thiếu dữ liệu thì báo chưa đủ dữ liệu, không coi là 0. Tổng quan tải lại inventory mỗi 20 giây khi foreground, bỏ lượt nếu request trước chưa xong; vì vậy cả CPU/RAM và Storage cùng được làm mới. Không truy vấn PerformanceManager cho khối này. Chưa đối chiếu số liệu với vCenter thật. Tổng CPU sử dụng đọc `HostSystem.summary.quickStats.overallCpuUsage` theo MHz.

M2 bổ sung đọc ResourcePool (parent/VM/pool con), health tổng thể Host/Cluster, mạng host standard vSwitch/portgroup/VMkernel/uplink và cấu hình nhóm LAG/LACP của vDS qua PropertyCollector. Cấu hình thiếu quyền hoặc không trả về được báo riêng, không suy ra danh sách rỗng. Datastore hiển thị cả dung lượng đã dùng.

Menu Cảnh báo / Sự kiện đọc riêng triggeredAlarmState trên rootFolder và QueryEvents từ EventManager: tối đa 100 alarm và 100 event trong 24 giờ theo giờ server; báo giới hạn và lỗi từng nguồn. Tên alarm được tra riêng, entity reference vẫn hiện nếu thiếu tên. Event không có severity được ghi API không cung cấp, không tự suy đoán. Tải lại mỗi 20 giây khi mở trang ở foreground; không acknowledge/reset alarm hay thay đổi hạ tầng. Chưa đối chiếu dữ liệu với vSphere Client hoặc kiểm thử tài khoản quyền hạn chế; M2 chưa nghiệm thu live.

Tab Hosts & Cluster theo bố cục HTML: chọn ESXi theo Cluster (host không rõ Cluster có nhóm riêng), khung chi tiết và thanh tab cuộn ngang. Điện thoại xếp chọn host phía trên chi tiết; màn rộng đặt hai cột. Tổng quan đọc trạng thái, maintenance, VM chạy, CPU/RAM từ inventory; các tab cấu hình chưa tích hợp báo rõ, các nút quản trị bị vô hiệu hóa, không hiển thị cấu hình mẫu.

Tab Máy ảo hiển thị cây Cluster → ESXi → VM, mở/thu gọn từng nhánh; ESXi không nằm trong Cluster được hiển thị ở cấp ngoài. Tìm kiếm/lọc trạng thái áp dụng cho VM bên trong cây; VM thiếu host hoặc host ngoài phạm vi quyền nằm trong nhóm Chưa xác định ESXi / Cluster. Quan hệ dùng Cluster.host và VM.runtime.host, dự phòng HostSystem.vm khi runtime.host không trả về; không suy đoán Cluster.

Dung lượng RAM và lưu trữ ở Tổng quan/danh sách tự chuyển từ GiB sang TiB khi đạt 1.000 GiB, giữ một chữ số thập phân và cách tính nhị phân (1 TiB = 1.024 GiB). 

### Hộp cấu hình, Clone và Migration

Hộp CPU/RAM bố trí vCPU và core/socket cùng hàng, nhóm hot add tự xuống dòng khi thiếu chỗ; Tải lại cấu hình và Đóng ở cùng hàng cuối. Màn xác nhận chỉ trình bày thay đổi và lỗi liên quan, vẫn yêu cầu xác nhận gửi lệnh. Lỗi HTTP 401/403 ghi đúng API đang gọi (ví dụ ReconfigVM_Task), không gắn nhãn đăng xuất. HTTP lỗi sau khi gửi vẫn được đối soát Task; không tự gửi lại vì chưa thể xác nhận lệnh đã chạy hay chưa. Nếu gặp 403, kiểm tra Access List/IP và log NPM/WAF/upstream cho POST /sdk cùng quyền cấu hình VM trên vCenter; ứng dụng không thể tự sửa chính sách từ chối của server.

Ba nút trong chi tiết VM mở hộp riêng, không chèn form quản trị vào cuối chi tiết. Cấu hình chọn nhóm phần cứng; Clone và Migration chỉ hiển thị trường liên quan. Các trường chọn có cùng chiều rộng, tên VM và trạng thái nguồn hiển thị gọn; không đưa milestone/changeVersion lên giao diện. Clone và Migration yêu cầu chọn ESXi đích trước khi hiển thị các trường còn lại. Resource pool lấy theo compute/cluster của ESXi; folder VM lấy theo datacenter của ESXi; datastore chỉ gồm datastore ESXi truy cập được. Đổi ESXi sẽ xóa lựa chọn folder/resource pool/datastore cũ. Migration chỉ cho giữ datastore hiện tại khi ESXi đích truy cập đủ mọi datastore của VM; nếu không phải chọn datastore đích. Chỉ hiển thị lỗi nhập liệu sau khi bấm Tiếp tục; màn xác nhận trình bày tên đối tượng và thay đổi dễ đọc, vẫn cần xác nhận trước khi gửi lên vCenter.

## M3: Task, nguồn VM và snapshot

- Task tu dong lam moi moi 20 giay khi app dang foreground va da dang nhap, ke ca khi dang o tab Task (truoc day 5 giay). Overview/Canh bao van giu chu ky 20 giay; khong thay doi viec doc lai task ngay sau khi gui lenh.

- Danh sach Task sap theo thoi gian giam dan: ket thuc, neu chua co/khong hop le thi dung bat dau, sau do vao hang doi; thieu tat ca thoi gian xep cuoi. Moc hien tren the cung la moc dung sap xep.
- Ten thao tac VM va host network da nhan dien duoc hien bang tieng Viet (vi du `host.NetworkSystem.removeVirtualSwitch` thanh Xoa vSwitch); thao tac chua nhan dien giu mo ta/ma server, khong doan. The giu doi tuong/IP, tai khoan, trang thai, thoi gian/thoi luong va ma task; Chi tiet giu mo ta va API/Description ID goc.

- Thời điểm cập nhật và nút làm mới nằm trên header chung, theo nguồn của màn đang xem (Task, cảnh báo/sự kiện hoặc inventory). Loading chạy ngay dưới thời gian trong vùng cao cố định, không chèn/xóa dòng trong nội dung khi polling. Bỏ các khối tiêu đề/mô tả/làm mới trùng trong Task, VM, Host, Tổng quan, Datastore và Network; giữ các nhãn phạm vi truy vấn, trạng thái lỗi và thanh tiến độ tài nguyên/task thật. Kết nối cũng hiển thị loading ở header thay vì chèn dòng vào biểu mẫu.

- Các popup chi tiết VM, thao tác/xác nhận VM, chi tiết/hủy task và đối soát dùng bo góc 5dp, mở rộng theo màn hình với lề ngang 8dp mỗi bên; không dùng chiều rộng dialog mặc định của Android.

- Màn Task có tìm theo thao tác/đối tượng/tài khoản/reference và lọc tất cả/đang xử lý/thất bại/thành công. Mỗi thẻ tách tên thao tác, đối tượng, badge trạng thái, tiến độ đang chạy và thời gian gọn. Bấm thẻ hoặc **Chi tiết** để xem mô tả, người thực hiện, ba mốc thời gian theo múi giờ thiết bị, thời lượng thực thi, lỗi đầy đủ, reference/API và property bị thiếu. Chi tiết cập nhật theo lần refresh; nội dung có thể chọn sao chép. Tên ưu tiên `TaskInfo.description.message`, sau đó `descriptionId`, `name`; không đoán tên thao tác khi server không trả. Thời lượng chỉ tính khi có đủ start/complete time hợp lệ, không tự coi task đã hoàn tất.

- Tab Task đọc TaskHistoryCollector trong 24 giờ theo giờ server, tối đa 100 task lịch sử theo quyền tài khoản; giải phóng collector sau truy vấn. Task từ app có reference được đọc riêng, kể cả ngoài trang lịch sử. Khi foreground, tải lại mỗi 5 giây ở tab Task hoặc 20 giây ở tab khác; không polling khi app ở nền.
- Chi tiết VM có menu nguồn/snapshot: power on/off/reset, shutdown/reboot guest, tạo/revert/xóa snapshot, xóa toàn bộ và consolidate disk. Mọi lệnh đều cần xác nhận; guest cần Tools đang chạy, consolidation cần server báo cần thiết. Đọc lại inventory trước khi gửi để kiểm tra nguồn/snapshot/Tools mới nhất; server quyết định quyền/capability cuối cùng. Không tự tắt VM hoặc tự gửi lại lệnh.
- Sổ lệnh riêng trong preferences `operations` lưu reference, thời điểm, loại lệnh và phạm vi hồ sơ/endpoint/tài khoản/instance, không lưu mật khẩu hoặc cookie. Ghi bền trước khi gửi; khởi động lại và đăng nhập đúng phạm vi sẽ đối soát task từ server. Task mất/không truy cập được không được coi là thành công. Bản ghi lệnh cũ không khóa thao tác mới; bỏ đối soát thủ công. Mỗi lệnh vẫn cần xác nhận, kiểm tra trạng thái/cấu hình mới nhất và ghi lịch sử trước khi gửi. Sau khi nhận Task reference, app tải kết quả ngay và theo dõi mỗi 3 giây khi có lệnh chưa kết thúc ở foreground (20 giây khi không có); chỉ Task success/error mới xác nhận kết quả. SOAP Fault được ghi là lệnh bị từ chối, không nhầm với mất phản hồi mạng. Shutdown guest được theo dõi bằng trạng thái nguồn đọc lại; reboot guest chỉ ghi server đã nhận yêu cầu vì API không trả Task và VM đang chạy không chứng minh reboot hoàn tất. Mất phản hồi sau gửi không tự gửi lại, không đoán thành công và không khóa VM; Task không truy cập được vẫn được thử đọc lại. Lỗi đọc/ghi lịch sử cục bộ hoặc đang gửi yêu cầu vẫn ngăn gửi đồng thời. VM tắt/tạm dừng hiển thị Bật nguồn, VM đang chạy hiển thị Tắt nguồn.
- ShutdownGuest/RebootGuest chỉ báo đã gửi, không tạo task giả hoặc coi guest đã hoàn tất. Nút hủy chỉ xuất hiện cho task queued/running có cancelable; kiểm tra lại trước khi gửi và đọc lại sau gửi, không tự đánh dấu đã hủy. Server có thể từ chối do thiếu quyền.
- M3 mới kiểm thử tự động bằng fixture/app uitest, chưa chạy bất kỳ thao tác thay đổi nào trên vCenter thật; chưa nghiệm thu M3. Cần VM lab được chỉ định và cho phép trước khi test thành công/lỗi quyền/mất mạng sau gửi/mở lại app trên server. Tạo VM, clone, migration và Edit Settings vẫn ngoài phạm vi đợt này.

## Lưu dữ liệu và cập nhật không xóa hồ sơ

- Hồ sơ lưu trong SharedPreferences `profiles`, vùng riêng của app trên Android: `/data/user/0/vn.webnew.vcenter/shared_prefs/profiles.xml` (không đọc/truy xuất file này để debug vì chứa dữ liệu cá nhân và ciphertext).
- Mật khẩu được nhớ chỉ nằm trong ciphertext AES-GCM; khóa nằm trong Android Keystore. Nếu không chọn nhớ, mật khẩu chỉ ở RAM. Cookie phiên và inventory hiện chỉ ở RAM, không phải dữ liệu hồ sơ lưu bền.
- Giữ nguyên applicationId `vn.webnew.vcenter`, tên preferences, định dạng dữ liệu và alias Keystore. Thay đổi schema sau này phải có migration giữ dữ liệu cũ, không clear toàn bộ preferences.
- Cập nhật dùng cùng chứng chỉ ký và versionCode không thấp hơn bản đã cài. Không gỡ app, không `pm clear`, không tự uninstall khi lỗi chữ ký. Nếu đổi từ debug sang release có chứng chỉ khác, phải có phương án chuyển dữ liệu trước; không hứa cập nhật trực tiếp được.
- Gỡ app, xóa dữ liệu hoặc factory reset vẫn làm mất hồ sơ/khóa. Backup/chuyển dữ liệu hệ thống bị tắt theo thiết kế bảo vệ credential; chưa có export/import hồ sơ.
- Test thiết bị dùng build type `uitest`, applicationId `vn.webnew.vcenter.uitest`, kho dữ liệu và Keystore UID riêng. Chỉ chạy `connectedUitestAndroidTest`, không dùng bản người dùng để chạy instrumentation.
- Sau build `assembleDebug`, cập nhật an toàn bằng `.\scripts\Update-App.ps1 -Serial SERIAL`; script chỉ cài đè và dừng nếu lỗi, không có bước xóa app/data. Có thể truyền `-Adb` là đường dẫn adb.exe nếu chưa có trong PATH.

## Kiểm thử Samsung qua ADB

Bật Developer options/USB debugging, cắm cáp dữ liệu, mở khóa Samsung và xác nhận RSA. Cài Samsung USB driver nếu Windows chưa nhận máy. Chạy từ thư mục `android/`, thay `SERIAL` bằng serial thật từ `adb devices -l`:

```powershell
adb devices -l
adb -s SERIAL install -r .\app\build\outputs\apk\debug\app-debug.apk
adb -s SERIAL shell am start -W -n vn.webnew.vcenter/.MainActivity
$env:ANDROID_SERIAL = 'SERIAL'
.\gradlew.bat :app:connectedUitestAndroidTest --no-daemon --console plain
```

Chỉ chạy bước cài sau khi build thành công và Samsung có trạng thái `device`. Trong phiên PowerShell mới phải thiết lập lại JDK/SDK/cache như hướng dẫn build. Kiểm tra 5 tab, menu, Back, xoay màn hình, font lớn và vùng system bar trên máy thật. ADB không tạo kết nối mạng tới vCenter; khi triển khai đăng nhập, Samsung cần LAN/VPN phù hợp. Không chạy test ghi lên hạ tầng thật nếu chưa được phép.

Trạng thái kiểm tra M0 ngày 18/11/2026: đã build APK debug, lint không có lỗi; hai test điều hướng chạy đạt trên Samsung SM-N976N, Android 12/API 31, One UI 4.1, độ phân giải hiện dùng 1080 × 2280. Đã cài và mở app qua ADB. Test bao phủ đúng 5 tab, chuyển màn hình, khôi phục sau tạo lại Activity, menu phụ và Back về tab trước. Chưa nghiệm thu đầy đủ font lớn/xoay màn hình/tablet. M1 đã build/cài/mở trên Samsung; 6 unit test và 5 test thiết bị đạt, gồm kiểm tra parser SOAP chạy trên Android và form kết nối. Lint 0 lỗi, 12 cảnh báo chưa xử lý. Người dùng xác nhận đăng nhập vCenter thật; chưa đối chiếu inventory thật, chưa hỗ trợ nhập CA riêng và chưa kiểm tra phiên định kỳ/tái đăng nhập tự động. Không coi hoàn thành app quản trị chỉ từ kết quả M0/M1.

Chẩn đoán HTTP 401/403: app hiển thị bước `RetrieveServiceContent` (chưa gửi tài khoản vCenter) hoặc `Login` (đã đọc được `/sdk`), trạng thái gửi NPM Auth và gợi ý kiểm tra Access List/IP/proxy. Không kết luận nguồn lỗi chỉ từ HTTP status hoặc header Server. SOAP InvalidLogin/NoPermission có thông báo riêng; không hiển thị raw body/header nhạy cảm.

Báo cáo lint: `android/app/build/reports/lint-results-debug.html`. Báo cáo test thiết bị: `android/app/build/reports/androidTests/connected/debug/index.html`. Các file báo cáo và APK được tạo khi build, không đưa vào source.
