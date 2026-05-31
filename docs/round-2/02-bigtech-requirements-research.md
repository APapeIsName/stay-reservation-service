# Round 2 — 빅테크의 요구사항 정리 관행 리서치

> **목적** — 빅테크 회사들이 요구사항을 어떻게 정리하는지, 개발자와 기획자가 특히 중요시 여기는 것을 학습.
> 결과는 `docs/design/01-requirements.md` 의 구조·라벨링·Acceptance Criteria 도입에 반영됨 (2026-05-28 갱신).
>
> **사용법** — 이 문서는 *원문 출처 모음 + 핵심 통찰 압축*. 실제 학습은 § 8 의 *어디부터 읽을까* 동선을 따라 외부 자료 보러 가는 게 좋음.

---

## 한 줄 요약

> 빅테크가 합의한 건 **템플릿이 아니라 한 가지 원칙** — *"왜·무엇·어떻게를 분리해 글로 쓰고, 대안·트레이드오프·모르는 것을 명시한 뒤, 비동기로 합의하고 결정을 기록한다."*
> Amazon 은 6-pager 로, Google 은 Design Doc + OKR 로, Stripe 는 RFC 로, Linear·토스는 *짧게* 로, GitLab 은 *공개* 로 풀어낸 차이뿐.

---

## 1. 회사별 핵심 패턴 + 1차 출처 URL

학습 가치 / 우리 프로젝트 영향 순.

| 회사 | 핵심 패턴 | 1차 출처 |
|---|---|---|
| **Amazon** | PPT 금지, **6-pager / PR-FAQ**, 회의 첫 25분 침묵 독서 | [theprfaq.com — Amazon writing culture](https://www.theprfaq.com/articles/amazon-writing-culture) · [workingbackwards.com PR-FAQ template](https://workingbackwards.com/resources/working-backwards-pr-faq/) · [Coda — Working Backwards (Colin Bryar)](https://coda.io/@colin-bryar/working-backwards-how-write-an-amazon-pr-faq) · [sixpagermemo.com](https://www.sixpagermemo.com/blog/amazon-six-pager-template) |
| **Google** | **Design Doc** 표준: Context / Goals / **Non-Goals** / Design / **Alternatives Considered** / Cross-Cutting / Open Questions | [industrialempathy.com — Design Docs at Google (Malte Ubl)](https://www.industrialempathy.com/posts/design-docs-at-google/) · [Google Eng Practices](https://google.github.io/eng-practices/) · [whatmatters.com — OKR meaning](https://www.whatmatters.com/faqs/okr-meaning-definition-example) |
| **Stripe** | **RFC 문화** — 기술뿐 아니라 *조직 개편* 도 RFC. API Review 가 *문화 의식* | [Slab blog — Stripe writing culture](https://slab.com/blog/stripe-writing-culture/) · [Increment — Planning with RFCs](https://increment.com/planning/planning-with-requests-for-comments/) · [Pragmatic Engineer — Stripe](https://newsletter.pragmaticengineer.com/p/stripe) |
| **Sourcegraph** | RFC 상태 라벨 정교: WIP / **Reviewing Problem** / **Reviewing Solution** / Approved / Implemented | [Sourcegraph handbook — RFCs (GitHub)](https://github.com/sourcegraph/handbook/blob/main/content/company-info-and-process/communication/rfcs/index.md) |
| **Atlassian** | PRD Blueprint: Objectives / **Assumptions** / Requirements / **Open Q&A (답변 일자 추적)** / **Out of Scope** | [Atlassian PRD template](https://www.atlassian.com/software/confluence/templates/product-requirements) · [Lenny's PRD on Confluence](https://www.atlassian.com/software/confluence/templates/lennys-product-requirements) |
| **GitLab** | **Handbook-first** + All-remote — PRD·연봉계산기·조직도까지 공개 git 저장소 | [GitLab Product handbook](https://handbook.gitlab.com/handbook/product/) · [All-Remote async](https://handbook.gitlab.com/handbook/company/culture/all-remote/asynchronous/) · [Product Development Flow](https://handbook.gitlab.com/handbook/product-development-flow/) |
| **Netflix** | "**Context, not Control**" — 메모·QBR 전사 공개. 정렬은 정보로 | [Netflix Culture Memo](https://jobs.netflix.com/culture) |
| **Airbnb** | PRD 안에 **사용자 인용 직접 삽입**. Design Ops 가 도구·컴포넌트 일관성 | [growthx.club — Airbnb spec template](https://growthx.club/learn/templates/airbnbs-product-spec-template) · [First Round Review — Airbnb Design](https://review.firstround.com/defining-product-design-a-dispatch-from-airbnbs-design-chief/) |
| **Spotify** | **DIBB** (Data → Insight → Belief → Bet) 2-page Bet 문서 + Squad/Bet 정렬 | [product-frameworks.com — DIBB](https://www.product-frameworks.com/DIBB.html) · [Crisp blog — Spotify Rhythm (Henrik Kniberg)](https://blog.crisp.se/2016/06/08/henrikkniberg/spotify-rhythm) |
| **Linear** | 1\~2p 짧은 spec, *"Write issues not user stories"* — 20년 전 가정 깨졌다 | [Linear Method](https://linear.app/method/introduction) · [Linear — Write issues not user stories](https://linear.app/method/write-issues-not-user-stories) · [Linear — how we run projects](https://linear.app/now/how-we-run-projects-at-linear) |
| **Basecamp** | **Shape Up** — Pitch + **Appetite (6주)** + Betting Table. Backlog 없음 | [Shape Up book (무료 공개)](https://basecamp.com/shapeup) · [Shape Up summary](https://www.sebastienphlix.com/book-summaries/singer-shape-up) |
| **Shopify** | 사내 GSD 5단계: **Proposal → Prototype → Build → Release → Results**. **OK1 / OK2** 2단계 사인오프 | [Lenny's Newsletter — How Shopify builds product](https://www.lennysnewsletter.com/p/how-shopify-builds-product) |
| **Figma** | **EngCrit** — FigJam, *"approval 아니라 feedback"*, 침묵 sticky 페이즈 | [Figma blog — How we run EngCrits](https://www.figma.com/blog/how-we-run-eng-crits-at-figma/) · [Figma engineering values](https://www.figma.com/blog/figmas-engineering-values/) |
| **Uber** | DUCK → RFC. 도메인별(Backend/Web/Mobile) 템플릿 + 승인자 필드 분리 | [Pragmatic Engineer — RFCs and Design Docs](https://newsletter.pragmaticengineer.com/p/rfcs-and-design-docs) |
| **Notion** | 자사 워크스페이스에 PRD/Tech Spec 템플릿 다수 공개 | [Notion templates — engineering & tech spec](https://www.notion.com/templates/category/engineering-tech-spec) · [Notion — how to write a tech spec](https://www.notion.com/use-case/project-management/how-to-write-a-tech-spec) |
| **Meta (Facebook)** | 엔지니어 주도, 빠른 합의 — 공식 PRD 템플릿 공개 거의 없음 (*공개 자료 부족*) | [Pragmatic Engineer — Facebook engineering](https://newsletter.pragmaticengineer.com/p/facebook) |
| **Microsoft** | RFC-like 프로세스 언급 있으나 공식 표준 공개 자료 부족. OSS(TypeScript, .NET) Proposal 프로세스가 외부 가시 | [Pragmatic Engineer — RFCs and Design Docs](https://blog.pragmaticengineer.com/rfcs-and-design-docs/) |
| **토스** | "**핵심만**" 짧은 기획서. 지표·경쟁사·파트너 의견까지 전사 공유 (Netflix Context not Control 의 한국형) | [토스피드 — PO 인터뷰](https://toss.im/tossfeed/article/toss-productowner-interview) · [SLASH 24](https://toss.im/slash-24) |

---

## 2. 개발자가 중요시 여기는 것 (Top 10)

| # | 항목 | 왜 중요 | 강조 회사 |
|---|---|---|---|
| 1 | **"왜(Why)" 명시** | 6개월 뒤 코드 수정자에게 필요한 건 "무엇"이 아니라 "왜" (Chesterton's Fence) | Google Context, Stripe Motivation, ADR Context |
| 2 | **Goals / Non-Goals 분리** | Non-goal 없으면 스코프 크리프 + "왜 이거 안 했냐" 끝없음 | Google ("ACID 안 함" 같은 *합리적 후보의 의도적 제외*) |
| 3 | **Alternatives Considered** | *왜 다른 걸 안 골랐나* 가 신뢰를 만든다 | Google Design Doc 핵심 섹션 |
| 4 | **Open Questions / Unsolved** | 모르는 것을 모른다고 쓸 수 있는 게 성숙도 | Stripe, Atlassian, Sourcegraph |
| 5 | **비기능 요구사항(NFR) 정량화** | "빠르게" ❌ → "P99 200ms" ✅ — 측정 가능해야 검증 가능 | Google Cross-Cutting Concerns |
| 6 | **의존성·전제 (Assumptions)** | 가정이 깨지면 전제가 깨진다. 명시 안 하면 안 보임 | Atlassian PRD Assumptions, Amazon PR-FAQ |
| 7 | **보안·프라이버시·관측성** | 늦게 추가하면 비싸다. Google 은 별도 privacy design doc 의무 | Google, Stripe API Review |
| 8 | **롤아웃·롤백 계획** | "어떻게 끄나" 없는 기능은 끌 수 없음 | Uber RFC, Stripe Definition of Done |
| 9 | **변경 이력 (Living Document)** | 결정의 진화 추적. 6개월 뒤 "왜 바꿨지" 답할 수 있음 | GitLab(git diff), ADR(superseded), Sourcegraph |
| 10 | **Reviewable Size (분량 제한)** | 50p 짜리는 안 읽힘. Amazon 6p / Linear 1\~2p 는 *읽힘을 강제* | Amazon, Linear, Basecamp |

---

## 3. 기획자/PM 이 중요시 여기는 것 (Top 10)

| # | 항목 | 왜 중요 | 강조 회사 |
|---|---|---|---|
| 1 | **비즈니스 목표·KPI 연결** | OKR 연결 없으면 우선순위 변동 시 1순위로 잘림 | Google OKR, Spotify Bet |
| 2 | **사용자 가치·페르소나·실제 인용** | 솔루션이 아니라 *사용자 통증* 을 출발점으로 만드는 forcing function | Airbnb (고객 인용 직접 삽입), Amazon PR |
| 3 | **문제 정의가 솔루션보다 먼저** | Lenny: *"문제 정의가 어떤 문제든 푸는 첫 번째이자 가장 중요한 단계"* | Lenny 1-pager, Sourcegraph (Reviewing Problem 분리) |
| 4 | **MVP 범위 / Out of Scope** | 안 할 것을 명시해야 할 것이 명확 | Atlassian, Basecamp "No-gos", Google Non-Goals |
| 5 | **Success Metrics** (Launch + Post-Launch) | "출시했다"는 성공이 아니다. T+30 / T+90 무엇이 변해야 성공인가 | GitLab MVC, Spotify Bet success metric |
| 6 | **이해관계자 합의 (Sign-off)** | 누가 결정했나가 명확해야 책임이 명확 | Shopify OK1/OK2, Sourcegraph Decider/Approvers |
| 7 | **사용자 관점 NFR** | 엔지니어의 "P99 200ms" 를 사용자 관점 ("탭하면 즉시 반응한다고 느껴야") 으로 번역 | 일반 PRD 관행 |
| 8 | **INVEST 원칙** | Bill Wake (2003) — Independent / Negotiable / Valuable / Estimable / Small / Testable | Scrum/SAFe 전통. **Linear 는 명시적 거부 (cargo cult)** |
| 9 | **Acceptance Criteria (Given/When/Then)** | BDD Gherkin — 모호하지 않은 완료 조건. 테스트로 직결 | BDD 전통, Atlassian Jira 통합 |
| 10 | **Open Questions 추적** | 답이 없는 질문도 *질문으로 존재*해야 의사결정이 추적됨 | Atlassian PRD "Open Questions & Answers (답변일자까지)", Stripe RFC "Unsolved questions" |

---

## 4. 산업 공통 패턴 (10 가지)

빅테크 공통으로 자리잡은 요소:

1. **"왜" 의 명시** — Goals 가 아니라 Motivation/Context 별도 섹션
2. **Non-Goals / Out of Scope**
3. **Alternatives Considered** — 단일 솔루션만 제시하면 신뢰 안 얻음
4. **Open Questions** — 모르는 것을 모른다고 쓸 자유
5. **Trade-offs** — 모든 결정은 *무엇을 잃었는지* 명시
6. **Success Metrics** — 정량적·검증가능
7. **Status / Lifecycle** — Draft → Review → Approved → Implemented → Superseded
8. **Single Source of Truth** — 결정은 한 군데, 링크로 연결
9. **Async + 침묵 시간** — Amazon 침묵 독서, Figma FigJam sticky 침묵 페이즈
10. **Public by Default** — Sourcegraph·GitLab은 외부에도, 다른 회사도 사내 전체에는

---

## 5. 회사별 독특한 결정 (왜 그렇게 갔나)

| 회사 | 결정 | 왜 |
|---|---|---|
| **Amazon** | PPT 금지 → 6-pager | Bezos: *"PPT 는 아이디어 얼버무릴 면죄부."* 내러티브가 *상대적 중요도와 연결* 을 강제 |
| **Amazon** | 회의 시작 침묵 독서 | 비동기 자료 준비를 *동기 회의 안* 에서 수행 — 모두가 같은 컨텍스트로 시작 |
| **Amazon** | 저자명 숨김 | "신원이 아닌 아이디어" 에 집중 |
| **Google** | "Mini design doc" (1\~3p) 인정 | 모든 결정에 20페이지 강요하면 문서 자체가 죽음. 규모에 맞춤 |
| **Stripe** | API Review 를 *문화 의식* 으로 격상 | API 가 회사의 *제품 자체* — 한번 잘못 내면 영구 기술부채 |
| **Netflix** | Context not Control | "하이 퍼포머만 모은다" 인재 전략의 *논리적 귀결* — 통제 대신 정보 |
| **Linear** | User Story 거부 | 20년 전 가정(고객이 못 말함, 패턴 없음) 깨졌다는 *역사적 reasoning* |
| **GitLab** | Handbook 이 공개 | 분산·비동기 + Open core 비즈니스 모델이 *문서 공개* 와 정합 |
| **Basecamp** | Backlog 없음 + Appetite 먼저 | 추정의 본질적 어려움을 우회 — "얼마면 할 만한가" 를 *먼저* 정함 |
| **Figma** | EngCrit 이 "approval 아님" | 디자인 크릿의 *원래 의도* (개선 제안) 를 엔지니어링에 이식 |
| **Spotify** | DIBB 의 "B-B" (Belief-Bet) 분리 | 가설(belief) 과 실험(bet) 을 분리해 *틀려도 학습* 하도록 |
| **Shopify** | OK1 / OK2 두 단계 | 디렉터 합의(OK1) 와 임원 합의(OK2) 분리 — *기술 사인오프* 와 *전략 사인오프* 분리 |

---

## 6. 우리 프로젝트 적용 진단

`docs/design/01-requirements.md` 가 빅테크 공통 패턴을 얼마나 반영하는가:

| 빅테크 패턴 | 우리 문서 | 상태 |
|---|---|---|
| "왜" 분리 | § 1 Round 2 범위 + § 6 정책 결정의 근거 (questions.md 링크) | ✅ |
| Goals / Non-Goals | § 1.1 포함 / § 1.2 제외·이월 | ✅ Google 식 모범 — Non-Goals 가 *합리적 후보의 의도적 제외* |
| Alternatives Considered | § 9 Risk 의 선택지 표 + questions.md 의 트레이드오프 | ✅ — questions.md 가 ADR 역할 |
| Open Questions | § 6 Q4 보류 + 재검토 트리거 | ✅ Sourcegraph 식 분리 (보류·검토 단계 명시) |
| Trade-offs | § 9 Risk + Q1\~Q3 의 *재검토 트리거* | ✅ |
| **Success Metrics** | § 8 학습 메트릭 (2026-05-28 추가) | ✅ 학습 프로젝트라 *학습 메트릭* 으로 번역 |
| **Acceptance Criteria (Given/When/Then)** | § 4.3 예약 시나리오 4건 (2026-05-28 추가) | ✅ BDD 직결 → Round 3 코드에서 그대로 테스트화 |
| **Status / Lifecycle 라벨** | 상단 라벨 (2026-05-28 추가) | ✅ |
| 변경 이력 | § 변경 이력 (날짜 + 사유) | ✅ |
| Reviewable Size | ~400줄 | ⚠️ Linear 기준엔 길지만 Google Design Doc 기준엔 적정 |
| NFR 정량화 | § 8 비기능 요구사항 (KST·KRW·STRICT·페이지) | ⚠️ 학습용이라 SLA/SLO 없음 — 면접 답변에선 *"추가 시 P99 / 가용성"* 언급 |
| Stakeholder Sign-off | 없음 | ➖ 혼자 학습이라 N/A |
| Public by Default | GitHub 공개 레포 | ✅ |

---

## 7. 면접·실무 적용 (lessons applicable)

### 🟢 즉시 채택 가능 (학습·실무 양쪽)

| 패턴 | 왜 |
|---|---|
| **Goals / Non-Goals 분리** | 어떤 문서·면접 답변에도 즉시 효과 |
| **Alternatives Considered** | 시스템 디자인 면접에서 *최강의 차별점* |
| **Open Questions** | "모릅니다" 를 *구조적으로* 말하는 방법 |
| **ADR (Michael Nygard)** | 1페이지·Markdown·git 친화. 우리 `questions.md` 가 이미 이 패턴 |
| **Given/When/Then AC** | 요구사항을 모호하지 않게 + 테스트로 직결 |
| **6-pager 의 침묵 독서 + 데이터 우선** | 작은 팀 회의에도 적용 |
| **Why-What-How 세 줄 요약 (Linear)** | 이슈·PR description·면접 자기소개 모두 적용 |

### 🟡 조건부 (조직·맥락에 따라)

| 패턴 | 조건 |
|---|---|
| **PR-FAQ Working Backwards** | B2C·신제품에 강함. B2B 내부 도구엔 과함 |
| **OKR** | 조직이 측정 문화에 준비됐을 때 (아니면 *숫자 위한 숫자*) |
| **Shape Up Appetite** | Backlog 관리를 포기할 수 있는 조직 |
| **All-public Handbook (GitLab)** | 비즈니스 모델 (open core 등) 과 정합할 때 |
| **GSD 5단계 + OK1/OK2** | 100명 넘어서면 가치 있음. 스타트업엔 과함 |

### 🔴 컨텍스트 의존 (그대로 가져오면 위험)

| 패턴 | 위험 |
|---|---|
| **Linear "no user stories"** | 고신뢰·고맥락 100명 조직에서나. 신입·외주 많으면 위험 |
| **Netflix "Context not Control"** | 인재 밀도 (keeper test) 와 함께 작동. 인재 정책 없이 도입 시 무책임 |
| **Amazon 6-pager 의 PPT 금지** | 다른 회사·고객 미팅엔 안 통함. 사내용 |
| **Figma EngCrit "approval 아님"** | 결정 권한이 명확한 다른 채널 있을 때만. 안 그러면 무한 토론 |

---

## 8. 어디부터 읽을까 — 추천 동선 (학습 우선순위)

### 첫 주 (개념 잡기)

1. **Industrial Empathy — Design Docs at Google** ([link](https://www.industrialempathy.com/posts/design-docs-at-google/))
   - 30분 읽기. 우리 `01-requirements.md` 의 모태
2. **Atlassian PRD Blueprint** ([link](https://www.atlassian.com/software/confluence/templates/product-requirements))
   - 실제 템플릿 그대로 한 번 채워보기
3. **Increment — Planning with RFCs (Stripe)** ([link](https://increment.com/planning/planning-with-requests-for-comments/))
   - RFC 작성 가이드. 우리 `questions.md` 의 관점 확장

### 둘째 주 (구조·문화 비교)

4. **Pragmatic Engineer — RFCs and Design Docs at companies** ([link](https://blog.pragmaticengineer.com/rfcs-and-design-docs/))
   - Google / Facebook / Amazon / Microsoft / Uber 비교. 회사별 차이의 *왜*
5. **theprfaq.com — Amazon writing culture** ([link](https://www.theprfaq.com/articles/amazon-writing-culture))
   - Bezos 메모 + 6-pager 의 역사
6. **Coda — Working Backwards (Colin Bryar)** ([link](https://coda.io/@colin-bryar/working-backwards-how-write-an-amazon-pr-faq))
   - Amazon 출신이 직접 쓴 PR-FAQ 가이드

### 셋째 주 (대안적 시각)

7. **Linear Method** ([link](https://linear.app/method/introduction))
   - 짧은 spec + "no user stories" — 반론 시각
8. **Basecamp — Shape Up (무료 책)** ([link](https://basecamp.com/shapeup))
   - Backlog 없는 모델. *Appetite* 개념의 충격
9. **Figma blog — How we run EngCrits** ([link](https://www.figma.com/blog/how-we-run-eng-crits-at-figma/))
   - 경량 동기 리뷰. *approval 아님* 의 의미

### 넷째 주 (한국·실제 사례)

10. **토스피드 PO 인터뷰** ([link](https://toss.im/tossfeed/article/toss-productowner-interview))
11. **SLASH 24** ([link](https://toss.im/slash-24))
12. **GitLab Product handbook 둘러보기** ([link](https://handbook.gitlab.com/handbook/product/))
    - 실제 *공개된* 문서 운영의 정수

### Public Design Doc / RFC 컬렉션 (실제 production 자료)

- **CockroachDB design.md** — [github.com/cockroachdb/cockroach](https://github.com/cockroachdb/cockroach/blob/master/docs/design.md)
- **Sourcegraph handbook RFCs** — [github.com/sourcegraph/handbook](https://github.com/sourcegraph/handbook/blob/main/content/company-info-and-process/communication/rfcs/index.md)
- **Rust RFCs** — [github.com/rust-lang/rfcs](https://github.com/rust-lang/rfcs)
- **Kubernetes Enhancement Proposals (KEP)** — [github.com/kubernetes/enhancements](https://github.com/kubernetes/enhancements)

---

## 9. 책 / 뉴스레터 / 컨퍼런스

### 책

| 책 | 핵심 |
|---|---|
| **Working Backwards** (Colin Bryar & Bill Carr) | Amazon 내부자 회고. PR-FAQ·메커니즘 디테일. *Amazon 패턴 학습의 정점* |
| **Measure What Matters** (John Doerr) | OKR 의 정전 (Andy Grove → Intel → Google) |
| **Inspired / Empowered** (Marty Cagan) | Product Discovery, Opportunity Assessment |
| **Shape Up** (Ryan Singer) | Basecamp, 무료 공개 |
| **User Stories Applied** (Mike Cohn) | INVEST 원칙 상세 |
| **Software Engineering at Google** | Documentation 챕터 공개 — [abseil.io](https://abseil.io/resources/swe-book/html/ch10.html) |
| **The Pragmatic Programmer** (25주년 판) | ADR/문서 관련 추가 챕터 |

### 블로그·뉴스레터

| 매체 | 가치 |
|---|---|
| **Lenny's Newsletter** | PRD·1-pager·product 작성 전반. PM 관점의 *글로벌 표준* |
| **The Pragmatic Engineer (Gergely Orosz)** | 회사별 RFC/Design Doc 시리즈 (Stripe, Figma, Meta, Uber) |
| **Industrial Empathy (Malte Ubl)** | Google Design Doc 글 원본 |
| **Increment Magazine** | Stripe 발행. RFC·planning 특집 |
| **First Round Review** | Airbnb·Stripe 등 deep-dive |
| **Commoncog** | Working Backwards 비판적 정리 |

### Talk / 컨퍼런스

- **Crisp talk by Henrik Kniberg** — Spotify Engineering Culture 영상 2편 (YouTube)
- **토스 SLASH** — [toss.im/slash-24](https://toss.im/slash-24), [toss.im/slash-23](https://toss.im/slash-23) (한국어, 무료)
- **Strange Loop / QCon** — Design Doc·RFC 관련 talk 검색

---

## 10. 면접 답변 템플릿 — "요구사항 정리할 때 뭘 중시하나요?"

> "빅테크 공통 원칙 5가지를 따릅니다.
>
> (1) **'왜' 와 '무엇' 을 분리** — 결정의 근거를 별도 누적해 6개월 뒤에도 추적 가능하게.
>
> (2) **Non-Goals 명시** — Google Design Doc 의 가장 중요한 섹션이라 봅니다. 안 할 것을 못 적으면 스코프 크리프가 시작됩니다.
>
> (3) **Alternatives Considered** — 단일 솔루션만 제시하면 신뢰가 안 생깁니다. 최소 2개 대안 + 트레이드오프.
>
> (4) **Open Questions** — 모르는 것을 모른다고 쓰는 게 성숙도이고, 미래 의사결정 추적의 출발점입니다.
>
> (5) **Living Document** — 결정의 진화를 git 변경 이력으로 남깁니다.
>
> 본 프로젝트에서도 `docs/design/01-requirements.md` 와 `docs/round-N/03-questions.md` 를 분리해 (1)\~(5) 를 적용했습니다."

---

## 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-05-28 | 첫 작성 | Round 2 빅테크 리서치 결과 정리. 출처 모음 + 추천 동선 + 우리 프로젝트 진단 |
