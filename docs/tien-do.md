# Tiến độ

<!-- workflow-setup:tien-do -->
PROJECT: keyloop-scheduler
LOAI: mới
STACK: Java 21 · Spring Boot 4.1 · PostgreSQL 16 · Gradle
GIAI_DOAN: 5/8 Phát triển
VONG: 1
TINH_NANG:
NHANH:
BUOC:
TRANG_THAI: chờ tính năng đầu tiên
CAP_NHAT: 2026-10-27 14:55
<!-- /workflow-setup:tien-do -->

## Điểm chốt đã duyệt

| Điểm chốt | Ngày | Ghi chú |
| --- | --- | --- |
| 1 · Yêu cầu (docs/yeu-cau.md) | 2026-10-27 | 5 tính năng MVP; observability: health, logs, latency |
| Stack | 2026-10-27 | Spring Boot 4.1 thay 3.5 (3.x hết OSS 30/6/2026); Java 21 |
| 2 · Thiết kế (docs/thiet-ke.md) | 2026-10-27 | Hexagonal; giữ PostgreSQL; 40P01 chạy lại insert một lần; Compose trust auth |

## Tính năng đã xong

| Tính năng | Nhánh | Kết quả | Ngày |
| --- | --- | --- | --- |

## Nhật ký

- 2026-10-27 12:48 · bắt đầu giai đoạn 1
- 2026-10-27 12:52 · viết docs/yeu-cau.md, chờ duyệt
- 2026-10-27 12:56 · duyệt điểm chốt 1, bắt đầu giai đoạn 2
- 2026-10-27 13:05 · chốt stack: Spring Boot 4.1 + Java 21
- 2026-10-27 13:07 · bắt đầu giai đoạn 3 (3a kiến trúc; không có 3b vì chỉ có backend)
- 2026-10-27 13:40 · duyệt điểm chốt 2, bắt đầu giai đoạn 4
- 2026-10-27 14:55 · dựng nền xong: Gradle 9.8.2, Boot 4.1.2, Spotless, 1 test xanh với PostgreSQL 16; CI để làm sau (backlog #1)
