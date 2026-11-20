Hãy triển khai app Android native tên vCenter, thương hiệu WebNew, quản lý vCenter 8 và ESXi 8 theo toàn bộ REQUIREMENTS.md trong thư mục này.

Trước khi code, đọc REQUIREMENTS.md và ba file ui-reference/index.html, app.js, host-admin.js. Dùng giao diện web đó làm mẫu màu/layout và luồng tương tác. Header full chiều ngang, 5 mục dưới cố định, các mục còn lại trong menu ba gạch. Bản web hiện tại là demo nên không coi dữ liệu, task hoặc thao tác demo là chức năng thật.

Tạo project Android trong android/ bằng Kotlin và Jetpack Compose. Dùng kết nối HTTPS thật, cho nhập vCenter credentials và NPM HTTP Basic Auth credentials trong cùng màn hình, quản lý phiên theo hồ sơ. Làm rõ xung đột Authorization giữa NPM và REST login. Ưu tiên SOAP /sdk cho phạm vi quản lý và đăng nhập qua NPM, kiểm tra API vSphere 8.

Hoàn thiện lần lượt theo lộ trình trong REQUIREMENTS.md. Trước tiên build và giao APK thật có cấu hình kết nối cùng inventory dữ liệu thật. Sau đó triển khai đầy đủ thao tác VM, Edit Settings, migration, host/cluster/network và task phía vCenter. Ghi rõ chức năng đã kiểm thử và chức năng còn thiếu. Các API không trả task không được tạo task vCenter giả. Không tự thao tác thay đổi trên hạ tầng thật trong quá trình kiểm thử nếu chưa có đối tượng và phạm vi được người dùng cho phép.

Tự kiểm tra môi trường Android SDK/JDK/Gradle, dùng phiên bản tương thích và cung cấp Gradle Wrapper. Nếu thiếu dependency hoặc môi trường build, xử lý trong khả năng và nêu blocker cụ thể. Không báo APK đã hoàn thành khi chưa build ra file. Bàn giao code, APK và hướng dẫn build. Dữ liệu đăng nhập thật do người dùng nhập trong app, không đưa vào source/log.
