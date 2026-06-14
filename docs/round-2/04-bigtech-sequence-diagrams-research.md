# Round 2 — 빅테크의 시퀀스 다이어그램 관행 리서치

> **목적** — 빅테크가 시퀀스 다이어그램을 어떻게 그리고 운영하는지, 어떤 규칙을 중요시 여기는지 학습.
> 결과는 `docs/design/02-sequence-diagrams.md` 의 명명·구조·외부 시스템 표현·상태 다이어그램 추가에 반영됨 (2026-05-29 갱신).
>
> **사용법** — 원문 출처 모음 + 통찰 압축. 실제 학습은 § 12 의 *추천 동선* 을 따라 외부 자료로.

---

## 한 줄 요약

> 빅테크의 시퀀스 다이어그램 관행은 **"한 시나리오·한 다이어그램·5\~7 참가자·트레이드오프 문장과 함께 텍스트 기반으로 코드와 함께 산다"** 한 줄로 수렴.
> **arc42 § 6 "Runtime View"** 가 공식 자리, **Mermaid + PlantUML** 이 사실상 표준.

---

## 1. 흥미로운 발견 5가지

1. **Google 의 Design Doc 은 시퀀스를 *명시적으로 언급하지 않는다*** — Malte Ubl 원문은 *system-context-diagram* (정적) 만 권장. *"다이어그램이 핵심이 아니라 트레이드오프 글이 핵심"* 톤
2. **arc42 가 가장 정통적인 자리** — Tip 6-11: *"UML 시퀀스 다이어그램 + PlantUML 같은 텍스트 DSL"* 명시. 시나리오 선택 기준은 **아키텍처적 관련성** (모든 use case ❌, 대표적 소수 ✅)
3. **Stripe 는 PaymentIntent = 상태도, 결제 흐름 = 시퀀스도** 의 *두 다이어그램 결합* — 라이프사이클은 상태, 흐름은 시퀀스. **역할 분리 모범 사례**
4. **Mermaid 가 사실상 표준** — GitLab handbook = Mermaid + Hugo 로 빌드 (docs-as-code 살아있는 사례). GitHub/Notion 내장도 결정타
5. **Martin Fowler 의 "Sketch mode"** — *"완벽한 UML 보다 의도가 전달되는 스케치가 낫다"*. Activation bar 같은 디테일 생략 무방

---

## 2. 시퀀스 다이어그램이 *어디에* 들어가는가

| 위치 | 정통성 | 비고 |
|---|---|---|
| **arc42 § 6 Runtime View** | ★★★ 공식 자리 | Tip 6-11 — PlantUML 권장 |
| Google Design Doc § "The actual design" | ★★ 시퀀스 직접 언급 없음 | 정적 컨텍스트 우선 |
| Uber RFC § "Network interactions" | ★★ | 모바일/서비스 RFC 템플릿 |
| Stripe 공개 Docs | ★★ | PaymentIntent 등 |
| Sourcegraph RFC | ★ 자유 양식 | 다이어그램 가이드 없음 (공개 자료 부족) |

---

## 3. 도구 비교

| 도구 | 강점 | 약점 | 채택 |
|---|---|---|---|
| **Mermaid** | 빠른 작성, PR diff, GitHub/GitLab/Notion 내장 | UML 정통 표기 일부 누락 | **GitLab 공식**, GitHub, Notion |
| **PlantUML** | UML 2.5 가장 충실, autoactivation | Java 런타임, syntax verbose | **arc42 표준 권장**, Atlassian Macro |
| draw.io | GUI, 비개발자 가능 | text diff 불가, "doc rot" | Atlassian 기본 |
| Excalidraw | 손글씨 풍, 화이트보드 협업 | UML 표기 없음 | 모던 SaaS 비공식 광범위 |
| Lucidchart | SaaS, 권한·공유 | 유료, 종속성 | 엔터프라이즈 |
| Figma/FigJam | 디자인 통합 | 엔지니어링 표기 부족 | 디자인 친화 회사 |
| **Structurizr DSL** | 모델-as-code, C4 dynamic view | 학습 곡선 | C4 정통 |
| Kroki | 통합 API (다양한 표기 단일 게이트웨이) | 자체 표기 없음 | docs-as-code 백엔드 |

---

## 4. UML 시퀀스 표준 규칙 요약

### 참가자 vs 액터
- **액터** — 시스템 *외부* (사용자, 외부 결제사). 스틱맨 아이콘
- **참가자** — 시스템 *내부* (Service, Repository). 박스
- PlantUML 7종: `boundary` / `control` / `entity` / `database` / `collections` / `queue` 등 (정통 UML 스테레오타입)

### 호출 화살표

| 의미 | PlantUML | Mermaid | 시각 |
|---|---|---|---|
| 동기 호출 | `->` 또는 `->>` | `->>` | 실선 + 채운 화살촉 |
| 비동기 호출 | `->>` (얇음) | `-)` | 실선 + 빈 화살촉 |
| 반환 | `-->` | `-->>` | 점선 |
| 메시지 손실 | `->x` | `-x` | 끝에 X |

**반환 화살표는 *의미 있을 때만*** — Interledger 가이드 + 분산 시스템 모범 사례 합의

### 결합 프래그먼트 (Combined Fragments) — UML 2

| 키워드 | 의미 | 흔한 용도 |
|---|---|---|
| `alt` | 분기 (if/else) | 성공/실패 |
| `opt` | 단일 조건부 실행 | 캐시 미스 시만 DB |
| `loop` | 반복 | 페이지네이션 |
| `par` | 병렬 | 동시 fan-out |
| `critical` | 임계 영역 | 트랜잭션 |
| `break` | 예외/중단 | 에러로 탈출 |
| `ref` | 다른 시퀀스 참조 | 공통 부분 추출 |

### Lifeline / Activation bar
- **Lifeline** — 참가자 박스에서 아래로 내려오는 세로 점선. **시간 축** (위→아래)
- **Activation bar** — Lifeline 위 얇은 사각형. 해당 참가자가 CPU 잡은 구간. UML 2.5 ExecutionSpecification
- Fowler: *"Sketch mode 에서는 생략 무방"*

---

## 5. 회사·커뮤니티별 가이드

### Google (Malte Ubl — Industrial Empathy)
- 1차 출처: [industrialempathy.com — Design Docs at Google](https://www.industrialempathy.com/posts/design-docs-at-google/)
- 명시적으로 다루는 다이어그램은 **system-context-diagram 하나뿐** — 시퀀스 직접 언급 없음
- 도구 강제 X (Google Docs 충분)
- *"다이어그램이 핵심이 아니라 트레이드오프 글이 핵심"* 톤

### Stripe
- 공식 RFC 가이드 비공개. 공개 [Stripe Docs — PaymentIntents Lifecycle](https://docs.stripe.com/payments/paymentintents/lifecycle) 에서 패턴 추출
- **결제 흐름 = 시퀀스**, **PaymentIntent 라이프사이클 = 상태도** — 두 다이어그램의 역할 분리

### Atlassian / Confluence
- 마켓플레이스: PlantUML Macro, Sequence Diagrams Macro, ZenUML 다수
- **SVG 동적 렌더링 → PDF/Word export 불포함** 한계 명시
- Confluence 자체는 위지윅 draw.io 기본

### GitLab (Handbook-as-Code 모범)
- [Handbook — Mermaid 페이지](https://handbook.gitlab.com/handbook/tools-and-tips/mermaid/)
- GitLab Markdown(GLFM) 이 Mermaid 정식 렌더 — *handbook 자체가 GitLab + Mermaid + Hugo* 로 빌드
- autonumber 같은 기능 추가 이슈 공개 ([gitlab-org/gitlab#210240](https://gitlab.com/gitlab-org/gitlab/-/issues/210240))

### Sourcegraph
- [핸드북 RFC 페이지](https://github.com/sourcegraph/handbook/blob/main/content/company-info-and-process/communication/rfcs/index.md) — 자유 양식. **다이어그램 가이드 자체 없음** (공개 자료 부족)
- 상태 라벨 (WIP / Reviewing problem / Approved / Implemented) 풍부 — 시각화는 작성자 재량

### Martin Fowler — *UML Distilled*
- 세 모드: **UmlAsSketch / Blueprint / ProgrammingLanguage**. 대부분 팀은 **Sketch**
- *"Sketch 사용자는 UML 표준의 세부에 크게 신경 쓰지 않음. UML 2 의 복잡성 증가가 오히려 부담"*
- 결론: **완벽한 UML 보다 의도가 전달되는 스케치가 낫다**

### Simon Brown — C4 Model
- 핵심 4 (Context/Container/Component/Code) + supplementary 3 (System landscape / **Dynamic** / Deployment)
- **Dynamic diagram** 은 UML 시퀀스가 아니라 UML *Communication diagram* — 자유 배치 + 번호로 순서
- 두 스타일 허용: **협업 스타일(자유 배치 + 번호)** vs **시퀀스 스타일**
- *"흥미롭거나 반복적인 패턴, 복잡한 상호작용에 *절제 있게* 사용"* — 공식 가이드

### arc42
- **Section 6 Runtime View** 가 시퀀스의 공식 자리
- Tip 6-11: *"UML 시퀀스 다이어그램 + PlantUML 같은 텍스트 DSL"* 명시
- 시나리오 선택 단일 기준: **아키텍처적 관련성**

### Mermaid / PlantUML 공식
- **Mermaid**: 화살표 6종, autonumber, box 로 참가자 그룹화, 액터 외 boundary/control/entity/database/collections/queue
- **PlantUML**: 동일 + autoactivation + return + 색상 lifeline + 분할선 + newpage — UML 2.5 ExecutionSpecification 충실

---

## 6. 모범 사례 Top 10

| # | 규칙 | 출처 |
|---|---|---|
| 1 | **한 다이어그램 = 한 시나리오** (제목이 한 문장) | arc42 6장 |
| 2 | **참가자 5\~7 제한** (8개↑ 인지 한계 초과) | go-uml 모범 |
| 3 | 시간 위→아래, 좌→우는 인접 호출 | PlantUML 공식 |
| 4 | 첫 메시지 발신자 가장 왼쪽 | Interledger 가이드 |
| 5 | **트랜잭션 경계를 `critical` 또는 색상 박스** | 분산 시스템 모범 |
| 6 | **외부 시스템 vs 내부 시스템 시각 구분** | arc42, C4 |
| 7 | **에러 흐름은 별도 다이어그램 또는 `alt`** | arc42 |
| 8 | 동기/비동기 화살표 모양 정확 구분 | PlantUML/Mermaid 공식 |
| 9 | **반환 화살표는 *의미 있을 때만*** | Interledger, Fowler |
| 10 | **앞 *이유*, 뒤 *해석*** | arc42 6장 구조 |

---

## 7. 안티 패턴 Top 7

| # | 안티 패턴 | 왜 나쁜가 | 대안 |
|---|---|---|---|
| 1 | 20+ 참가자 | 인지 한계 초과 | 시나리오 쪼개기, `ref` 연결 |
| 2 | **완전 대칭 ping-pong** (Controller→Service→...→DB→...→Controller) | 정보량 0 | 진짜 분기·외부 호출·트랜잭션에 초점 |
| 3 | 도메인 로직을 화살표로 (self-call 5단으로 클래스 다이어그램 흉내) | 시퀀스는 객체 *간* 도구 | 클래스/상태 다이어그램, pseudocode |
| 4 | UML 표기 마구잡이 (점/실선 혼용) | 독자 멈춤 | 한 다이어그램 = 한 표기법 |
| 5 | **반환 화살표 강박** | 잡음, 화살표 2배 | 의미 있을 때만 |
| 6 | 에러를 본 다이어그램 alt 로 우김 | nesting 지옥 | 별도 다이어그램 또는 break |
| 7 | 텍스트 없이 그림만 | 6개월 뒤 의도 오독 | 앞뒤 한 단락 텍스트 |

---

## 8. 시퀀스 vs 다른 다이어그램 — 언제 시퀀스를 *쓰지 말 것*

| 상황 | 더 나은 다이어그램 |
|---|---|
| 시스템 박스 관계·의존성 | **C4 Container/Component** (시스템 디자인 면접 1순위) |
| 한 객체 생명주기 | **State diagram** (Stripe PaymentIntent 식) |
| 사람+시스템 비즈니스 프로세스 | **BPMN / Activity** |
| 알고리즘 결정 분기 | **Flowchart** |
| 병렬·비순차 협업 | **C4 Dynamic view (협업 스타일)** |
| privacy review (데이터 흐름) | **DFD** |

---

## 9. 시스템 디자인 면접에서의 활용

### 효과적인 질문 유형
- "사용자가 좋아요를 누르면?" — 한 시나리오의 흐름
- "결제 실패 시 환불은?" — 에러·보상 흐름
- "두 마이크로서비스 일관성?" — Saga, 2PC, outbox
- "캐시 무효화는 언제?" — 시간 순서

### 시퀀스가 *잘못된* 질문
- "Twitter 를 설계하세요" — 컴포넌트 먼저
- "100만 RPS?" — 용량 산정 + 컴포넌트

### 그리는 순서 (Terrastruct 10팁)
1. 요구사항·제약 한쪽에 적기
2. 코어 컴포넌트 중앙에 먼저 (depth-first)
3. 단순 도형만, 의미는 라벨로
4. 그리면서 말하기 (침묵 금물)
5. 새 요소 추가 시 기존 영향 즉시 확인
6. 모든 요소 라벨링
7. 수정 두려워하지 말기

### 면접관이 보는 것
- 기술 지식 ↔ 의사소통 균형
- 간결함·완성도·정리
- 자기 설계 약점을 명시할 자신감
- **트레이드오프 인식**

---

## 10. 우리 프로젝트 적용 진단

`docs/design/02-sequence-diagrams.md` 가 빅테크 모범을 얼마나 반영하는가:

| 빅테크 모범 사례 | 우리 문서 | 상태 |
|---|---|---|
| 한 다이어그램 = 한 시나리오 | 검색·예약·취소·상태 각각 별도 | ✅ |
| 참가자 5\~7 제한 | 모두 5\~6 | ✅ |
| 시간 위→아래, 첫 발신자 왼쪽 | User 가 가장 왼쪽 | ✅ |
| 트랜잭션 경계 시각 | `rect` + 🔒 | ✅ Activation bar 보다 명료 |
| **외부 시스템 구분** | § 7 컨벤션 + 미니 예시 | ✅ (2026-05-29 추가) |
| 에러 흐름 별도 | § X.4 예외 흐름 표 분리 | ✅ alt 우김 회피 |
| 동기/비동기 구분 | 모두 동기. 외부 호출 컨벤션에 `-)` 정의 | ✅ |
| **반환 화살표 의미 있을 때만** | 단순 통과 제거 (2026-05-29) | ✅ (개선) |
| 앞 이유 / 뒤 해석 | Skill 5️⃣ 6️⃣ + arc42 § 6 구조 | ✅ 정확히 일치 |
| Mermaid + docs-as-code | GitHub 렌더링 + git diff | ✅ |
| **상태 다이어그램과 짝 (Stripe 패턴)** | § 4 Reservation 상태 머신 (2026-05-29) | ✅ (추가) |
| 자기 호출 (도메인 메서드) | 🔵 Note 부착 (2026-05-29) | ✅ 안티패턴 #3 회피 |
| **arc42 "Runtime View" 명명** | 문서 제목 (2026-05-29) | ✅ 산업 표준 정렬 |

---

## 11. 면접·실무 적용

### 🟢 즉시 채택 가능
- Mermaid 를 README/Design Doc 에 code block 으로 시작 — 도입 비용 0
- 다이어그램 앞뒤 *이유 / 해석* 한 줄
- 한 다이어그램 = 한 시나리오 = 5\~7 참가자 자기 검열
- 동기/비동기 화살표 정확 구분 (`->>` vs `-)`)
- 해피 / 실패 다이어그램 분리
- **arc42 "Runtime View"** 섹션 신설

### 🟡 점진 도입
- PlantUML 로 격상 → 결합 프래그먼트 정확 사용
- C4 Container/Component + 시퀀스 *짝* 운영
- `ref` 로 큰 시퀀스 분해

### 🔴 신중 도입
- Structurizr DSL 로 모델-as-code 전환 — 학습 곡선 큼
- 자동 생성 (코드 → 시퀀스) 도구 — 잡음 많음
- UML 2.5 전체 표기법 강제 — 부담 vs 가치

---

## 12. 어디부터 읽을까 — 추천 동선 (4 주)

### Week 1 — 표기법 정통
- **Fowler — UML Distilled** 시퀀스 다이어그램 챕터
- **PlantUML 공식** 시퀀스 페이지 통독 + 예제 5개 직접 작성
- **Mermaid 공식** sequenceDiagram 페이지 통독 + GitHub README 1개 게시

### Week 2 — 아키텍처 문서의 자리
- **arc42 Section 1\~6 + Tip 6-11** 정독
- **C4 model** 4 핵심 + Dynamic view 공식 페이지
- 사이드 프로젝트 README 에 "Runtime View" 섹션 + Mermaid 시퀀스 1장

### Week 3 — 회사 사례 분석
- **Malte Ubl** "Design Docs at Google" 정독
- **Pragmatic Engineer** "RFCs and Design Docs at 90+ companies"
- **Stripe PaymentIntents Lifecycle** — *왜 시퀀스 아니고 상태도인지* 분석

### Week 4 — 실전·면접
- 본인 시스템 1개 → (a) C4 Container, (b) 핵심 시나리오 시퀀스, (c) 에러 시퀀스 3장 PlantUML 작성
- 면접 문제 3개 (URL shortener, chat, payment) 화이트보드 *15분 안에* 컴포넌트+시퀀스
- 안티 패턴 Top 7 을 자기 다이어그램에서 직접 찾기

---

## 13. 출처 모음

### 1차 출처 (공식 문서·핸드북)

- [Industrial Empathy — Design Docs at Google (Malte Ubl)](https://www.industrialempathy.com/posts/design-docs-at-google/)
- [Industrial Empathy — A design doc](https://www.industrialempathy.com/posts/design-doc-a-design-doc/)
- [arc42 § 6 Runtime View](https://docs.arc42.org/section-6/)
- [arc42 Tip 6-11](https://docs.arc42.org/tips/6-11/)
- [c4model.com — Dynamic diagram](https://c4model.com/diagrams/dynamic)
- [Structurizr — Dynamic view](https://docs.structurizr.com/ui/diagrams/dynamic-view)
- [PlantUML — Sequence Diagram](https://plantuml.com/sequence-diagram)
- [Mermaid — sequenceDiagram](https://mermaid.js.org/syntax/sequenceDiagram.html)
- [Mermaid — stateDiagram-v2](https://mermaid.js.org/syntax/stateDiagram.html)
- [GitLab Handbook — Mermaid](https://handbook.gitlab.com/handbook/tools-and-tips/mermaid/)
- [Sourcegraph Handbook — RFCs](https://github.com/sourcegraph/handbook/blob/main/content/company-info-and-process/communication/rfcs/index.md)
- [Stripe Docs — PaymentIntents Lifecycle](https://docs.stripe.com/payments/paymentintents/lifecycle)

### 2차 출처 (스타일 가이드·종합)

- [Interledger — Sequence Diagram Style Guide](https://interledger.net/content/sequence-diagrams/)
- [Pragmatic Engineer — RFCs and Design Docs at 90+ companies](https://blog.pragmaticengineer.com/rfcs-and-design-docs/)
- [Terrastruct — 10 tips for system design interview diagrams](https://terrastruct.com/blog/post/10-tips-for-using-diagrams-to-ace-the-system-design-interview/)
- [go-uml — Best practices for sequence diagrams (distributed systems API)](https://www.go-uml.com/best-practices-sequence-diagrams-distributed-systems-api/)

### 책

- **Martin Fowler** — *UML Distilled* (3rd ed.) — UML 모드 분류 + 시퀀스 챕터
- **Simon Brown** — *The C4 Model: Visualizing Software Architecture* (O'Reilly) + *Software Architecture for Developers*
- **Gregor Hohpe** — *Enterprise Integration Patterns* — 메시징 패턴 시각적 어휘

### 한국 자료
검색 시점 *방법론* 글 1차 출처 확인되지 않음. *사용 사례* 는 있으나 회사 차원 가이드는 비공개. **공개 자료 부족**.

---

## 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-05-29 | 첫 작성 | Round 2 시퀀스 다이어그램 빅테크 리서치. 출처 + 모범/안티 + 우리 진단 + 추천 동선 |
