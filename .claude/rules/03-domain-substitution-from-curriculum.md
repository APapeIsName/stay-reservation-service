# 도메인 치환 (발제 e-commerce → 숙박)

## Rule
발제 본문의 예제·과제·용어는 부트캠프 원본 도메인인 **e-commerce(유저/포인트/상품/주문)** 기준으로 쓰여 있다. 코드 구현 시 모든 도메인 개념을 **숙박 예매(`com.stay`)** 로 치환한다. 발제 원문 자체는 [발제 동결](./02-curriculum-docs-frozen.md) 에 따라 수정 안 함.

## Why
이 레포의 실제 도메인은 숙박 예매. e-commerce 잔재가 코드/네이밍/모델에 섞이면 도메인 응집성과 면접 설명력 모두 훼손.

## How to apply
- 치환 예시
  - "포인트 충전 한도 초과" → "예약 시 객실 잔여 수량 초과"
  - "주문 → 결제 → 배송" → "예약 → 체크인 → 체크아웃"
  - "상품 재고" → "일자별 객실 재고(`DailyRoomInventory`)"
  - "상품 카탈로그" → "숙소·객실 타입 카탈로그"
- 회원 도메인처럼 도메인 무관한 영역은 그대로 사용 (회원가입/조회/비번 변경)
- **"이전 도메인이 묻어 있을 수 있다"** 는 의심을 기본 자세로: 새 발제 받을 때 e-commerce 용어 1차 스크리닝
- 기준 어휘: `Property` (숙소), `RoomType` (객실 타입), `DailyRoomInventory` (일자별 재고), `Reservation` (예약), `Wishlist` (찜)
- **위반 신호**: 코드/네이밍에 `Order`, `Product`, `Cart`, `Point` 등 e-commerce 어휘 등장

## References
- 사용자 방침: 2026-05-20 ("이전 도메인이 그대로 묻어져 있을 수 있음을 알아둬")
- 발제: `docs/curriculum/round-1.md` 도메인 메모 섹션
- 메모리: `weekly-task-approach`
