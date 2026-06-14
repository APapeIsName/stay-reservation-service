# Round 2 — HLD / LLD 관행 리서치

> **목적** — HLD (High-Level Design) 와 LLD (Low-Level Design) 의 본질·기원·지역별 차이 + 빅테크의 통합 패턴 + 우리 프로젝트 적용 학습.
> 결과는 `docs/design/00-overview.md` (HLD 본체 신설) + `docs/adr/` (ADR 디렉터리 신설) + `docs/design/README.md` 의 HLD/LLD 라벨링에 반영됨 (2026-05-29 갱신).

---

## 한 줄 요약

> HLD/LLD 는 **인도 SI 산업과 폭포수 SDLC 에서 형식화된 산출물 용어**.
> 미국 빅테크는 같은 개념을 *"Design Doc / Tech Spec"* 으로 통합.
> **Simon Brown (C4 model) 은 "high/low" 라는 모호한 이분법 대신 Context → Container → Component → Code 의 4단계 정의된 zoom level** 권장.
> 우리 `docs/design/` 4파일은 *이미 사실상의 HLD→LLD 흐름* — **용어만 명시적으로 얹으면 면접·실무 양쪽 자산**.

---

## 1. 흥미로운 발견 5가지

1. **"HLD vs LLD" 구글 검색의 80%+ 가 인도 사이트** (GeeksforGeeks, naukri code360, lldcoding.com) — 미국 빅테크 1차 자료는 거의 없음. *지역 의존적 기술 문화 표지자(marker)*
2. **Simon Brown (C4) 은 high/low 자체를 회피** — *"어디까지가 high 이고 low 인지 상대적이라 사람마다 다르다"*. *zoom level* 이라는 용어 사용
3. **arc42 § 5 Building Block View 는 *계층적 무한 분해*** — HLD/LLD 라는 단절 자체를 두지 않음. 빅테크 Design Doc + arc42 의 공통점
4. **"Design Twitter" = HLD, "Design Parking Lot" = LLD** — 이 분류 자체가 면접 준비 출발점. Twitter 를 클래스 다이어그램으로 풀면 망하고, Parking Lot 을 Kafka 도입으로 풀면 망함
5. **인도 제품 회사 (Flipkart/Uber India/Razorpay/CRED/Swiggy) 의 "Machine Coding Round"** — 60\~120분 안에 실제 동작하는 코드. SDE1→SDE2 승급 핵심

---

## 2. 정의·기원·현재 통용

### 2.1 HLD (High-Level Design)
- **원래 의미** (1970\~80년대 폭포수 SDLC, IEEE 1016 영향) — "Architectural Design" 단계의 산출물. 시스템 전체 구조, 모듈 식별, 모듈 간 인터페이스, 데이터 흐름, 외부 시스템 연동. SRS 의 직접 후속물
- **현재 통용 의미** — *What & Why*. 시스템의 30,000 피트 뷰. 컴포넌트, 데이터 흐름, 기술 선택과 *그 근거(트레이드오프)*. 아키텍트·PM·비개발 스테이크홀더가 읽을 수 있어야 함

### 2.2 LLD (Low-Level Design)
- **원래 의미** — "Detailed Design" 단계. 각 모듈의 *내부* 알고리즘·자료구조·인터페이스 상세를 기술해 코더가 즉시 구현 가능한 수준
- **현재 통용 의미** — *How*. 클래스 다이어그램, 메서드 시그니처, DB 스키마, API request/response, 에러 코드, 시퀀스 다이어그램, 디자인 패턴, 동시성·트랜잭션 경계

### 2.3 기원의 비대칭
- **IEEE 1016** 은 1987 권고안 → 1998 개정 → **2009년 정식 표준**. "Architectural design" 과 "Detailed design" 이라는 *유사 개념* 은 정의하지만, "HLD/LLD" 라는 약어 자체는 IEEE 표준이 직접 채택하지 않음
- **UML** 은 다이어그램 종류는 정의하지만 "어느 게 HLD/LLD 인가" 는 규정하지 않음
- 약어 **"HLD"·"LLD" 자체의 보편화는 인도 IT 서비스 산업** (TCS·Infosys·Wipro·HCL) 의 SI 산출물 문화에서 가장 두드러짐. Glassdoor India 의 토론 *"HLD/LLD 는 인도 IT 개념이냐?"* 가 반복 — 그 자체로 인도 중심성 신호
- 미국 금융 서비스 (SI 성격) 에서도 쓰이지만, 빅테크 엔지니어링 문화는 **"Design Doc" 한 단어로 통합** (Google·Stripe·Meta)

### 2.4 경계 모호 영역 — Simon Brown 의 비판 근거

| 항목 | HLD인지 LLD인지 |
|---|---|
| API 엔드포인트 *목록* | HLD |
| API request/response 스키마 | LLD |
| ER 다이어그램 (엔티티·관계만) | HLD 경계 |
| DB 인덱스·제약·타입 | LLD |
| "Redis 캐시 사용" 결정 | HLD |
| TTL·키 네이밍·invalidation 전략 | LLD |
| **시퀀스 다이어그램 (서비스 간)** | **HLD** |
| **시퀀스 다이어그램 (객체 간)** | **LLD** |
| 배포 토폴로지 | HLD |
| K8s manifest | LLD (또는 운영 문서) |

같은 *다이어그램 종류* 라도 추상화 레벨에 따라 HLD/LLD 로 갈림. 이 모호함이 C4 의 *zoom level* 패러다임의 출발점.

### 2.5 누가·언제 작성하나

| 환경 | 작성자 | 시점 |
|---|---|---|
| **인도 SI** | HLD = Solution Architect / Tech Lead, LLD = Senior Dev | 폭포수 게이트 — HLD 사인오프 후 LLD |
| **미국 빅테크** | 기능 owning Senior/Staff Engineer 가 단일 Design Doc | 개발 전. 동료·skip-level 코멘트 리뷰 |
| **한국 SI / 금융** | 인도 모델 답습 | SRS → 기본설계서 → 상세설계서 |
| **한국 스타트업** | 빅테크 답습 | 노션·컨플루언스 Design Doc 1장 + ADR |

---

## 3. HLD 의 구성 요소 (상세)

1. **시스템 컨텍스트 다이어그램** — 외부 액터 (사용자·PG·SMS·메일·OAuth) + 우리 시스템 단일 박스. C4 L1
2. **주요 컴포넌트·서비스 토폴로지** — 모놀리스 모듈 / 마이크로서비스 / BFF / 워커. 프로토콜 (HTTP/gRPC/AMQP) 라벨. C4 L2
3. **데이터 흐름** — Read path / Write path 분리. 예: 예약 생성 시 `Client → API → Reservation Service → DB + Outbox → Notification Worker → SMS`
4. **기술 스택·인프라** — 언어/프레임워크, RDB vs NoSQL *이유*, 캐시·큐·CDN·Object Storage. **선택보다 트레이드오프**
5. **NFR (비기능 요구사항)** — 성능 (p95 latency, RPS), 확장성, 가용성 (SLA/SLO), 일관성 (strong/eventual), 보안, 컴플라이언스
6. **API 개요** — 엔드포인트 *목록* + 그룹핑. 상세 스키마는 LLD
7. **배포 토폴로지** — VPC, 서브넷, LB, 멀티-AZ/리전, DB 레플리카. C4 Deployment View
8. **외부 의존성** — SLA·rate limit·fallback 전략
9. **트레이드오프와 대안 검토** — Google Design Doc 의 *"Alternatives Considered"*. **HLD 의 가장 큰 가치**
10. **위험·미해결 이슈** — 알려진 unknown

---

## 4. LLD 의 구성 요소 (상세)

1. **클래스·인터페이스 다이어그램** — 핵심 도메인 모델, 상속·합성, 책임
2. **메서드 시그니처** — public API 메서드, 파라미터·반환·예외
3. **DB 스키마** — 테이블·컬럼·타입·NULL·기본값·인덱스·외래키·유니크. DDL 수준
4. **API 상세** — request/response JSON, status code, error code 카탈로그, idempotency key, pagination, versioning
5. **알고리즘 / pseudocode** — 비자명한 로직 (가격·추천·좌석 배정), 시간·공간 복잡도
6. **시퀀스 다이어그램 (객체 협업)** — `ReservationController → ReservationService → RoomRepository → DB`
7. **상태 다이어그램** — 예약 상태 + 전이 조건
8. **디자인 패턴 적용** — Strategy / Factory / State / Observer / Repository
9. **트랜잭션 경계** — `@Transactional` 범위, propagation, isolation, Saga·2PC 여부
10. **동시성 제어** — 비관/낙관 락, `SELECT FOR UPDATE`, 분산 락, 멱등성, race condition
11. **에러 처리 정책** — 도메인 예외 vs 인프라 예외, 재시도, circuit breaker, DLQ
12. **테스트 전략** — 단위/통합/계약 경계, 픽스처, 테스트더블 정책

---

## 5. 지역·산업별 컨벤션

| 지역/산업 | HLD/LLD 사용 | 대체 용어 | 비고 |
|---|---|---|---|
| **인도 IT 서비스** (TCS·Infosys·Wipro·HCL) | 강함, 산출물 필수 | SRS → HLD → LLD | 폭포수·CMMI, 클라이언트 사인오프 게이트 |
| **인도 제품 회사** (Flipkart·Swiggy·Uber India·Razorpay·CRED) | LLD 강함 (특히 면접) | "System Design" / Machine Coding | SDE2 승급 핵심 |
| **미국 빅테크** (Google·Meta·Stripe·Amazon) | **거의 안 씀** | Design Doc / Tech Spec / RFC / ADR | 단일 문서 10\~20p |
| **미국 금융 SI** (Goldman·JPMorgan) | 일부 사용 | "Technical Specification" | 규제·감사 추적 |
| **유럽 (특히 독일)** | arc42 영향 강함 | arc42 12 섹션 | "Building Block View" 계층 = HLD/LLD 통합 |
| **한국 SI/금융/공공** (삼성SDS·LGCNS·SK C&C) | 강함, 인도 답습 | 기본설계서·상세설계서 | 폭포수, 발주처 검수 |
| **한국 대기업** (네이버·카카오·라인·쿠팡·우아한·당근) | 거의 안 씀 | Design Doc / RFC / ADR | 빅테크 답습. ADR 최근 한국에서도 표준 자리잡음 |
| **한국 스타트업** | 안 씀 | 노션 1\~3p Design Doc | 애자일·가벼움 |
| **C4 model 진영** (Simon Brown) | **명시적 회피** | Context / Container / Component / Code | "high/low" 는 상대적이라 모호 |

**비대칭 자체가 통찰** — *지역 의존적 기술 문화 표지자*.

---

## 6. 표준·프레임워크 대응

### 6.1 C4 model (Simon Brown)

| C4 Level | 내용 | HLD/LLD 매핑 |
|---|---|---|
| L1 Context | 시스템 + 사용자 + 외부 시스템 | **HLD 최상위** |
| L2 Container | 앱·서비스·DB·메시지큐 (배포 가능한 것) | **HLD 본체** |
| L3 Component | 컨테이너 내부의 논리적 컴포넌트 | **HLD ↔ LLD 경계** |
| L4 Code | 클래스·인터페이스 (UML, optional) | **LLD** |

**Simon Brown 의 입장** — "high/low" 단어 의도적 회피, *zoom level* 표현. 핵심 비판 ① 어디까지 high/low 인지 *상대적*, ② 한 다이어그램에 두 레벨 섞이는 흔한 안티패턴 조장, ③ 청중·목적이 빠짐

### 6.2 arc42

| 섹션 | 매핑 |
|---|---|
| §3 Context & Scope | HLD |
| **§5 Building Block View** (1\~N 레벨 계층 분해) | **HLD\~LLD 통합** — "필요한 만큼 더 깊이 들어가라" |
| §6 Runtime View | 시퀀스/액티비티 → HLD(서비스간)·LLD(객체간) 둘 다 |
| §7 Deployment View | HLD |
| §8 Crosscutting Concepts | LLD 가까움 (로깅·보안·예외) |
| §9 Architecture Decisions | ADR |
| §10 Quality Requirements | NFR (HLD) |

### 6.3 UML 다이어그램 매핑

| 다이어그램 | 통상적 위치 |
|---|---|
| Use Case | HLD |
| Component | HLD |
| Deployment | HLD |
| Class | LLD |
| Sequence (서비스 레벨) | HLD |
| Sequence (객체 레벨) | LLD |
| State Machine | LLD |
| Activity | 둘 다 |

### 6.4 IEEE 1016 (Software Design Description)
- "Design view" 개념 — *복수의 뷰포인트*. HLD/LLD 같은 *이분법* 정의하지 않음
- 권장 섹션: Introduction, System architecture description, Detailed description of components, Reuse/relationships, Design decisions/trade-offs, Pseudocode, Appendices
- 사실상 HLD + LLD 를 **한 문서** 에 묶는 모델 → 빅테크 Design Doc 과 가까움

### 6.5 TOGAF ADM (엔터프라이즈 아키텍처)
- Phase C (Information Systems: Data + Application) → **HLD 해당**
- Phase D (Technology Architecture) → 인프라 HLD + LLD 일부
- 개별 시스템 LLD 는 TOGAF 범위 밖

---

## 7. 시스템 디자인 면접 활용

### 7.1 HLD Round — 무엇을 보는가
- **기능·비기능 요구사항 도출** + 적절한 클러리피케이션 질문
- **용량 추정** (DAU, QPS, 저장 용량)
- **API 개요** (엔드포인트 목록)
- **상위 컴포넌트 다이어그램** + 데이터 흐름
- **데이터 모델·파티셔닝 전략**
- **확장성·가용성 트레이드오프** (캐시·CDN·샤딩·복제·큐)
- **병목 식별과 완화책**

**Alex Xu 4단계 프레임**: ① 문제 이해/요구사항 → ② 상위 설계 + 면접관 합의 → ③ 핵심 컴포넌트 딥다이브 → ④ 마무리/트레이드오프

### 7.2 LLD Round — 무엇을 보는가
- **도메인 엔티티 식별** + 책임 분배 (God class 회피)
- **SOLID** (특히 SRP·OCP)
- **디자인 패턴** (Strategy·Factory·State·Observer·Decorator·Command)
- **확장성** — *"요금 정책 추가되면 어디 수정?"* (OCP 검증)
- **동시성** — 멀티스레드 환경 안전성
- **인도형 회사의 "Machine Coding Round"** — 60\~120분, 흔한 문제: Parking Lot, Splitwise, Snake & Ladder, BookMyShow, Vending Machine, In-memory Cache, Rate Limiter, LRU

### 7.3 "Design Twitter" / "Design Parking Lot" — HLD 인가 LLD 인가

| 질문 | 분류 | 이유 |
|---|---|---|
| Design Twitter / Instagram / Uber | **HLD** | 피드·팬아웃·샤딩·캐시·큐·지오해시 등 *분산 시스템* 문제 |
| Design Parking Lot / Elevator / Chess / BookMyShow / Splitwise | **LLD** | *단일 프로세스 내* 객체·클래스 설계, 디자인 패턴, OOP |

이 분류 자체가 면접 준비 출발점.

### 7.4 지역별 차이

| 회사군 | HLD | LLD |
|---|---|---|
| 미국 빅테크 (Google/Meta/Amazon) | 명시적 라운드 ("System Design") | 별도 라운드 드묾, 코딩에 흡수 |
| 인도 제품 (Flipkart/Uber India/Razorpay/CRED/Swiggy) | "System Design" 라운드 | **"Machine Coding"/"LLD" 라운드 별도 필수** |
| 한국 대기업/스타트업 | 일부 회사만 (빅테크 약함) | 거의 없음, 라이브 코딩으로 대체 |
| 한국 SI/금융 | *현업 산출물* | *현업 산출물* |

### 7.5 면접관이 두 라운드에서 보는 것
- **HLD** — 사고 *과정* + *커뮤니케이션*. *"NoSQL 고른 이유?"* 에 "확장성" 답하면 fail, *"key 기반 액세스 95% + schema-less 필요"* 답하면 pass
- **LLD** — *코드 품질* + *확장성 감각*. 새 요구가 와도 클래스 *추가* 만으로 끝나야지 *수정* 필요하면 OCP 위반

---

## 8. 모범 사례 Top 8

1. **HLD 와 LLD 를 분리 — 단, 같은 문서의 다른 섹션도 OK**. 빅테크 Design Doc 처럼 한 문서에 "Architecture" + "Detailed Design" 섹션도 충분
2. **HLD 가 LLD 의 근거 — Why → What → How 흐름**. 트레이드오프 결정이 HLD, 구현이 LLD
3. **추상화 수준 일관** — 한 다이어그램에 시스템·서비스·클래스 섞이지 말 것. C4 가 강하게 권하는 규율
4. **HLD 는 비개발자도 읽을 수 있게, LLD 는 구현자가 바로 코드 시작 가능**. 청중 명확
5. **Alternatives Considered 필수** — HLD 의 가치는 결론이 아니라 *왜 그 결론에 도달했는가*. Google Design Doc 핵심
6. **docs-as-code + Mermaid** — 텍스트로 git 에 두면 코드 PR 과 같이 리뷰·diff. 문서 부패 방지
7. **ADR 로 의사결정 잘라 두기** — HLD/LLD = *현재 상태*, ADR = *왜 그렇게 됐는가의 시점 스냅샷*. 보완재
8. **NFR 을 정량적으로** — "빨라야 한다" ❌ → "p95 200ms, RPS 5000" ✅. 이게 LLD 의 동시성 설계 근거

## 9. 안티 패턴 Top 7

1. **HLD 에 클래스명·메서드 시그니처** — 큰 그림 망침. C4 L2 에 클래스 박스 → 즉시 의심
2. **LLD 에 비즈니스 KPI/매출 가설** — 구현자 헷갈림. KPI 는 HLD/PRD 에
3. **한 다이어그램에 추상화 섞기** — Container 와 Class 가 같은 그림
4. **HLD 없이 LLD 만** — 클래스는 깔끔한데 *왜 이렇게 됐는지* 없음. 신규 입사자가 모름
5. **LLD 없이 HLD 만** — 구현자가 매번 헤맴. "Redis 캐시 쓴다" 만 있고 키·TTL·invalidation 없으면 사람마다 다르게 짬
6. **폭포수 강제** — HLD 사인오프 후만 LLD. 애자일과 충돌
7. **다이어그램만 있고 결정 근거 없음** — 박스·화살표만 예쁘게 + *"왜 SQS 아니라 Kafka?"* 답 없음 → 죽은 문서

---

## 10. 우리 프로젝트 적용 진단

| 우리 파일 | HLD/LLD 위치 | C4 매핑 | arc42 매핑 |
|---|---|---|---|
| `01-requirements.md` | HLD 이전 (입력) | — | § 1 Intro, § 10 Quality |
| `02-sequence-diagrams.md` (Runtime View) | HLD~LLD 경계 (현재는 객체 간 — LLD) | L3\~L4 | § 6 Runtime View |
| `03-class-diagram.md` (예정) | **LLD 본체** | L4 Code | § 5 하위 |
| `04-erd.md` (예정) | **LLD** | — | § 5 + § 8 (Persistence) |

### 진단 (2026-05-29 이전)
- ✅ **사실상 HLD→LLD 흐름이 이미 잡혀 있다** — 폭포수 산출물 패턴과 거의 동형
- ⚠️ **HLD 가 약하다** — *컨텍스트 다이어그램, 컴포넌트 토폴로지, 외부 의존성, 트레이드오프 종합* 같은 HLD 핵심 산물 부재
- ⚠️ **추상화 일관성** — 우리는 *모두 객체 간 시퀀스* (LLD) 라 일관성 있음. 미래 외부 시스템 도입 시 서비스 간 시퀀스 별도 필요

### 적용 후 (2026-05-29)
- ✅ **`00-overview.md` 신규** — HLD 본체. 컨텍스트 + C4 L2 Container + NFR + 트레이드오프 종합 + ADR 인덱스
- ✅ **`docs/design/README.md` 갱신** — 4파일의 HLD/LLD 위치 라벨링 + C4/arc42 매핑
- ✅ **`docs/adr/` 디렉터리 신설** — ADR-001 (모듈 분리) / ADR-002 (즉시 CONFIRMED) / ADR-003 (한 테이블 daily_room) 3건 역작성

---

## 11. 현대적 트렌드 — HLD/LLD 가 낡은 용어인가?

- **빅테크 = 통합 추세** — Google·Stripe·Meta 는 "Design Doc" 하나에 HLD/LLD 통합. 10\~20p 가 스위트 스폿. Stripe 는 *템플릿보다 샘플* 권장
- **C4 model 은 "high/low" 자체 회피** — 정의된 4 zoom level 로 대체. 산업 표준화 중
- **arc42** 는 12 섹션 중 building block view 를 *계층적 무한 분해* — HLD/LLD 단절 없앰
- **docs-as-code + Mermaid** 사실상 표준 — "HLD/LLD 분리 산출물" 무게 감소, *living architecture* 로 이동
- **AI/LLM 시대 변화** — HLD 주면 LLD 스캐폴딩 (클래스·DDL·OpenAPI spec) LLM expand. 인간은 *트레이드오프 결정·검증* 집중. 코드 → HLD/LLD 역추출도 활발. 결과: HLD/LLD *문서 산물의 가치* 감소, *결정 (ADR) + 트레이드오프 산문* 가치 상승
- **그래도 HLD/LLD 살아 있는 이유** — 인도 IT 시장 규모, 한국·일본·유럽 SI/금융/공공의 감사·검수, 면접 어휘로서 *간결·명확*

**판단** — 용어 자체가 죽지는 않았지만, "HLD/LLD 를 별도 산출물로 분리해 폭포수 게이트로 운영" 모델은 낡았다. **개념은 유효, 산출물 형식은 가벼워지는 중**.

---

## 12. 면접·실무 적용 (난이도 신호)

| 항목 | 면접 가치 | 실무 가치 | 도입 난이도 |
|---|---|---|---|
| HLD 작성 능력 (컨텍스트·NFR·트레이드오프) | 🟢 매우 높음 | 🟢 매우 높음 | 🟡 트레이드오프 산문 어려움 |
| LLD 작성 능력 (SOLID·디자인 패턴·동시성) | 🟢 매우 높음 | 🟢 매우 높음 | 🟢 연습으로 빠르게 향상 |
| C4 model 도입 | 🟡 빅테크 직접 어필 X | 🟢 onboarding 강력 | 🟢 Mermaid 면 즉시 |
| arc42 12 섹션 풀 적용 | 🔴 면접엔 과함 | 🟡 SI/엔터프라이즈 강함 | 🔴 무거움 |
| Design Doc 한 장 (Google 스타일) | 🟢 빅테크 라이팅 샘플 | 🟢 가벼우면서 효과 큼 | 🟢 즉시 |
| ADR 도입 | 🟡 간접적 (트레이드오프 답변) | 🟢 결정 근거 추적 | 🟢 즉시 |
| Machine Coding 연습 | 🟢 인도계·일부 한국 회사 | 🟡 실무는 PR 리뷰 | 🟡 60\~120분 timed |

---

## 13. 추천 학습 동선 (4 주)

### Week 1 — HLD 기초
- DDIA 1\~5장 (Replication·Partitioning·Transactions)
- Alex Xu Vol.1 1\~4장 + 4단계 프레임 암기
- 우리 프로젝트에 `00-overview.md` 작성 ✅ (2026-05-29 완료)
- 결과물: 1장짜리 HLD Design Doc ✅

### Week 2 — LLD 기초
- *Head First Design Patterns* 핵심 5개 (Strategy, Factory, Observer, State, Decorator)
- *Clean Code* + SOLID 복습
- Machine Coding 1문제: **Parking Lot** 1시간 Kotlin
- 결과물: `lld-practice/parking-lot/` 디렉터리

### Week 3 — 통합·표준 매핑
- C4 model 공식 + Simon Brown YouTube 1편
- arc42 § 5/§ 6/§ 9 읽기
- C4 Container 다이어그램 (L2) Mermaid 추가 ✅ (00-overview.md 안)
- ADR 디렉터리 신설 + *이미 내린 결정* 3개 역작성 ✅ (2026-05-29 완료)

### Week 4 — 면접 시뮬레이션
- HLD: "Design Booking.com / Airbnb" 1시간 화이트보드 (우리 stay 도메인과 가까움)
- LLD: "BookMyShow 좌석 예약" 1시간 코딩 (락·동시성·State 패턴)
- 모의 면접 1회 (혼자면 녹음 후 자가 리뷰)

---

## 14. 출처 모음

### 1차 출처 (공식·표준)

- [IEEE 1016 (Wikipedia)](https://en.wikipedia.org/wiki/Software_design_description) · [IEEE SA](https://standards.ieee.org/ieee/1016/4502/)
- [C4 model 공식](https://c4model.com/)
- [arc42 § 5 Building Block View](https://docs.arc42.org/section-5/) · [§ 6 Runtime View](https://docs.arc42.org/section-6/) · [§ 7 Deployment View](https://docs.arc42.org/section-7/)
- [TOGAF ADM Phase C](https://togaf.visual-paradigm.com/2025/01/20/comprehensive-guide-to-togaf-adm-phase-c-information-systems-architectures-deliverables/) · [Phase D](https://www.itar.pro/phase-d-technology-architecture/)

### 빅테크 / Design Doc 문화

- [Industrial Empathy — Design Docs at Google (Malte Ubl)](https://www.industrialempathy.com/posts/design-docs-at-google/)
- [Slab — How Stripe Built a Writing Culture](https://slab.com/blog/stripe-writing-culture/)
- [Pragmatic Engineer — Inside Stripe's Engineering Culture](https://newsletter.pragmaticengineer.com/p/stripe) · [Part 2](https://newsletter.pragmaticengineer.com/p/stripe-part-2)
- [Pragmatic Engineer — RFC & Design Doc Examples](https://newsletter.pragmaticengineer.com/p/software-engineering-rfc-and-design)

### 인도 / 한국 SI 컨벤션

- [Glassdoor India — Is HLD/LLD an Indian concept?](https://www.glassdoor.co.in/Community/technology/1-is-hld-high-level-design-and-lld-low-level-design-an-indian-concept-most-of-the-youtube-videos-on-these-topicis-are-created-by)
- [GeeksforGeeks — HLD vs LLD](https://www.geeksforgeeks.org/system-design/difference-between-high-level-design-and-low-level-design/)
- [Low Level Design Mastery — HLD vs LLD](https://www.lowleveldesignmastery.com/blog/hld-vs-lld/) · [Machine Coding Guide](https://www.lowleveldesignmastery.com/blog/machine-coding-round/)
- [CNCF Korea — ADR 가이드](https://www.cncf.co.kr/blog/adr-guide/) · [ADR 작성](https://www.cncf.co.kr/blog/adr-architecture-decision-record/)
- [Chaos and Order — RFC·ADR·Design Doc·Tech Spec 가이드](https://www.youngju.dev/blog/english/2026-03-12-english-technical-writing-rfc-adr-design-doc-guide)

### C4 model vs UML 비교

- [icepanel — C4 model vs UML](https://icepanel.io/blog/2024-07-29-comparison-c4-model-vs-uml)
- [InfoQ — The C4 Model for Software Architecture](https://www.infoq.com/articles/C4-architecture-model/)
- [Working Software — Misuses and Mistakes of the C4 model](https://www.workingsoftware.dev/misuses-and-mistakes-of-the-c4-model/)

### 면접 자료

- [HelloInterview — System Design in a Hurry](https://www.hellointerview.com/learn/system-design/in-a-hurry/introduction)
- [F-Lab — 시스템 디자인 인터뷰 준비](https://f-lab.ai/en/insight/system-design-interview-preparation-20250906)
- [Pragmatic Engineer — System Design Interview review](https://blog.pragmaticengineer.com/system-design-interview-an-insiders-guide-review/)
- [Pulkitent — Parking Lot LLD GitHub](https://github.com/pulkitent/parking-lot-lld-oop-ood-assignment)
- [Prashant Priyadarshi — Top 10 LLD Interview Questions (Medium)](https://medium.com/@prashant558908/solving-top-10-low-level-design-lld-interview-questions-in-2024-302b6177c869)

### AI 시대 / 트렌드

- [Sprint2Scale — AI-Assisted Architecture in 2025](https://sprint2scale.com/ai-assisted-architecture-in-2025-how-llms-are-transforming-software-design/)
- [Sunil Mamilla — LLD in the Era of Generative AI](https://medium.com/@smamilla/low-level-design-in-the-era-of-generative-ai-what-still-needs-a-human-touch-a95e7e76551e)
- [Docsie — Docs-as-Code 2026 with Mermaid](https://www.docsie.io/blog/articles/technical-diagrams-docs-as-code-2026/)

### 책

- **Alex Xu** — *System Design Interview* Vol.1, Vol.2 (HLD 표준 텍스트)
- **Martin Kleppmann** — *Designing Data-Intensive Applications (DDIA)* (HLD 이론적 기반)
- **Sam Newman** — *Building Microservices* 2nd ed.
- **Simon Brown** — *The C4 Model* (O'Reilly) + *Software Architecture for Developers*
- **Eric Evans** — *DDD* / **Vaughn Vernon** — *Implementing DDD* (LLD 도메인 모델링)
- **Robert C. Martin** — *Clean Architecture* / *Clean Code* (SOLID)
- **Joshua Bloch** — *Effective Java* (JVM 진영 LLD 직관)
- **GoF** — *Design Patterns* (정전)
- *Head First Design Patterns* (Freeman & Robson) — 입문

### 한국 자료
- 검색 시점 *방법론* 글 1차 출처 확인되지 않음. ADR 가이드 (CNCF Korea) 가 가장 가까움. **공개 자료 부족**

---

## 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-05-29 | 첫 작성 | Round 2 HLD/LLD 빅테크 리서치. 출처 + 정의·기원 + 면접 활용 + 우리 진단 + 추천 동선 |
