# 실제 검증 결과

## 후속 시험 계획

현재 적용 범위는 원 요구사항 19개 중 기본 16개+TMDB 1개이며, 파일 업로드·게시판/댓글 2개는 유보다. [Redmine 시험계획](http://114.70.92.70:3000/projects/e-team-2026/wiki/시험계획_결과)의 38개 사례는 미실행이다. #90은 #89 T03-04의 증거 검산이며 별도 사례로 세지 않는다.

현재 분담·일정은 [인계 문서](team-handover.md)와 [최종 계획](개발_최종계획_20261104.md)을 따른다. UC 10개와 #196의 완료·100%는 명세 작성·등록 확인 범위다. 아래 날짜별 결과는 당시 수행 증거이며 후속 시험 결과로 합산하지 않는다.

## 최신 인계 기준선 검증 — 2026-10-16

실제 수행: Codex, 현재 PC의 로컬 작업본. **팀원 인계용 기술 기준선으로 사용 가능**으로 판정한다. 팀원 독립 검토·인수와 실기기 검증 완료를 뜻하지 않는다. 기존 v0.1.1/F01~F08 → Repov 사용 흐름 → 재사용 검토가 실제 코드에 반영된 상태에서 시작했으며, [인계](team-handover.md)·[공통 UI/입력 계약](design.md)에 이번 기준을 통합했다. 기존 Redmine ZIP은 갱신하지 않았다.

### 실제 문제 → 최소 수정

| 확인한 문제 / 사용자 영향 | 이번 변경 / 위험과 범위 |
| --- | --- |
| 복합 조회에서 작품 범위 외에는 전체 초기화 링크만 제공. 한 조건만 풀고 싶어도 다른 조건을 잃기 쉬움 | 공통 applied-filters.jspf로 적용 조건·개별 해제 제공. 기존 c:url/c:param으로 나머지 조건을 보존하고 page를 생략한다. SQL/조회 규칙 변경 없이 실제 URL과 결과를 검사 |
| /stats?workId의 작품 범위가 화면에 없고 조회 버튼 재제출 시 사라짐 | 공통 필터가 workId hidden을 담당하고 통계에도 해제 가능한 작품 ID 범위를 표시. 기존 records의 작품 제목/범위 해제 UI는 그대로 재사용 |
| persisted pageshow가 form의 모든 p[role=status]를 제거해 저장 영향 안내까지 사라짐 | 제출 진행 문구에 .submit-progress를 부여하고 그 요소만 정리. UUID·저장 로직은 변경하지 않음 |
| 공백 없는 최대 길이 회원명/제목으로 데스크톱 폭이 1440→2170px가 되고 회원명이 본문과 겹침 | main의 긴 텍스트 줄바꿈과 sidebar/account/work-detail의 최소 폭을 보완. 제목 200자·회원명 60자 계약을 줄이지 않음 |
| 자동 페이지 폭 검사를 통과해도 320px의 날짜 입력은 마지막 일이 잘림 | 캡처에서 발견. 380px 이하에서 필터 날짜를 한 줄씩 배치. 390px의 기존 두 열은 유지 |

첫 세 문제의 실제 수정 전 재현은 [before-issues.json](evidence/handover-20261016/before-issues.json), [복합 조건 전](evidence/handover-20261016/before-filters.png)이다. 긴 문자열과 날짜 잘림도 [회원명 전](evidence/handover-20261016/before-long-name-1440.png)·[날짜 전](evidence/handover-20261016/before-date-input-320.png)에 보존했다. ‘공통화’를 이유로 Controller/Service/Mapper를 리팩터링하지 않았다.

### 이번에 실제 실행한 시험

| 계층 | 이번 실행 결과 / 근거 |
| --- | --- |
| 빌드·단위·실제 MySQL | JDK15/source·target1.8의 clean package verify 성공. 단위2, MySQL 통합26, 실패0·오류0·skip0. [maven-verify.log](evidence/handover-20261016/maven-verify.log). 기존 서버 시험을 복제하지 않고 재실행 |
| 실제 Tomcat9/JSP/Chrome134 기본 회귀 | 기존 브라우저80개 통과. 회원가입/로그인/검색→작성/별개 재감상/미평점/수정/삭제/세 저장 동작/CSRF/타인·관리자404/409초안/통계 비율8개/페이지 경계 확인. 입력 오류 렌더237/253/203ms. [browser-flow.json](evidence/handover-20261016/browser-flow.json) |
| 기존 레이아웃 검사 | 기존27개 통과, JavaScript 오류0. [layout-final.json](evidence/handover-20261016/layout-final.json). 위80개 이후 최종 시각 보완 전 실행이며 마지막 CSS의 시험으로 합산하지 않음 |
| 최종 보완본 브라우저·반응형 | 추가213개 assertion 통과, JS 오류0. [handover-checks.json](evidence/handover-20261016/handover-checks.json). 조건별 단독 해제/나머지 보존/page1, 다음 페이지 조건 유지, 통계 작품 범위 보존/해제, 특수문자, 오류 조건 일부 해제 후 복구, 저장 성공/뒤로가기, 최대 길이 문자열·포커스·label·HTTP응답 검사 |
| 최종 WAR | 시각 보완 후 clean package -DskipTests 성공. 이 명령 자체는 시험 재실행이 아니다. [maven-final-package.log](evidence/handover-20261016/maven-final-package.log). 이후 최종213개 실제 JSP 검사 수행 |
| 최종 캡처 | 1440×1000 / 390×844 / 320×844. 로그인·가입을 포함한 16종 화면48개 캡처. 홈/작품 검색·상세/작성·수정/미평점·유효평점 상세/삭제 확인/복합 기록목록/통계/상태/빈 결과/400·404를 직접 읽음. 42개 인증 화면 폭·헤딩·회원명·폼 label, 320px 날짜 필드 폭 검사. 대표 [PC 목록](evidence/handover-20261016/final-records-1440.png)·[390px 목록](evidence/handover-20261016/final-records-390.png)·[320px 날짜](evidence/handover-20261016/final-error-320.png)·[320px 평점 상세](evidence/handover-20261016/final-rated-detail-320.png) |

단위/통합시험은 조회 SQL의 전체 이력 순번, NULL 건너뛰기, 최초 유효/동일 평점, 날짜 수정·백필·삭제 후 재계산, 기간 밖 첫 감상, 기간 내부 대표 평점, 최신 NULL, 복수 장르/n/분모0, 안정적인 동률·페이지, UUID·롤백·충돌·소유권·공용 작품 보존을 실제 MySQL에서 확인했다. JSP의 최종 문자열과 레이아웃은 별도 브라우저 근거다. 위 수치는 assertion 수이며 사용자 수나 독립 시나리오 수가 아니다.

실제 브라우저 Back의 정상 폼 복귀도 확인했지만 그 실행에서 persisted=true 복원은 관찰되지 않았다(`realBackPersistedEvents:[]`). 따라서 BFCache 자체를 검증했다고 쓰지 않는다. persisted=true 정리 분기는 실제 DOM에 PageTransitionEvent를 발생시켜 별도로 확인했다.

검수 중의 실패/보완도 보존했다. 첫 추가 검사에서는 로그아웃 후 탐색 대기가 빠져 ERR_ABORTED가 발생하여 검사 스크립트의 waitForURL을 보완했다([첫 실행](evidence/handover-20261016/checks-attempt1-navigation.json)). 그다음 긴 문자열 폭 실패를 수정했다([폭 실패](evidence/handover-20261016/checks-attempt2-long-name.json)). 이후187개 자동 검사 통과 뒤 직접 캡처에서 날짜 잘림을 발견했고([당시 결과](evidence/handover-20261016/checks-before-visual-date-fix.json)), 날짜 배치 수정 후 최종213개를 통과했다. 자동 폭 통과를 시각 검수 완료로 대체하지 않았다.

### 보존·근거 재사용·종료 상태

- 앱 변경은 records.jsp, common/filter.jspf, 새 common/applied-filters.jspf, app.css, app.js다. Java/Mapper XML/DB schema/pom/서버 설정은 변경하지 않았다. 새로운 런타임 의존성·쿼리·N+1 조회·JSP 통계 산식은 없다. [변경·보존 요약](evidence/handover-20261016/completion.json)과 source-change.diff를 따른다.
- 이전 Repov·REA evidence를 새로 만들거나 덮지 않았다. 보존 inspect_web_page/목록 원본2건, 번들 응답 요약, 공개 Playwright/로그인 CUA 관찰 JSON, 이전 전후 캡처, QA-R1~R7을 **설계/QA 기준으로 재사용**했다. 선택 맥락·날짜/평점의 근접 배치·선택 입력·다음 행동을 검수 질문에 적용했다. 새 외부 분석·REA/MCP/Ghidra/Java의 setup/install/update/uninstall은 하지 않았다.
- 8082/review_system_test만 변경했다. 임시 회원과 그 회원이 만든 작품/기록/상태/요청을 정리했다. 실제 앱 review_system의 회원2·작품4·기록8·상태4·장르8·작품장르5·요청2는 7개 테이블 모두 전후 행 수/해시가 같다. [전](evidence/handover-20261016/app-data-before.json)·[후](evidence/handover-20261016/app-data-after.json)·[fixture 정리](evidence/handover-20261016/cleanup.json).
- 시작 때 꺼져 있던 앱8080을 실행하거나 배포하지 않았다. 이번에 사용한 시험8082와 시작한 MySQL은 종료했다. 외부 Redmine 수정, Git push, 배포는 수행하지 않았다.

미검증/후속: 물리 스마트폰·스크린리더·다른 브라우저·팀원 독립 검산/인수·새 PC 전체 설치·HTTPS/외부 운영·대규모 동시 부하. Repov의 미관찰 저장/수정/삭제/권한/실제 개인 통계는 계속 미확인이다. 게시판·댓글·첨부·TMDB는 유보이며 임시 저장·소셜·수동 횟수·추천 알고리즘·새 프레임워크는 범위 밖이다. 더 이상의 기능 추가 없이 현재 로컬 기준선에서 인계한다.

---

## 최신 추가 확인 — 2026-10-16 조사 재사용 검토

제품 변경은 **records.jsp1개**의 작품 범위 안내/해제뿐이다. REA·Repov를 다시 조작하거나 분석하지 않았다. [재사용 판단](사용자_관점_종합분석.md)의 출처별 근거·후보표·QA/발표 사례를 기존 문서에 통합했다.

- 보존 REA 원본2건을 로컬에서 재검토했다. 스크립트49개의 본문 SHA-256은49/49 일치, 해당300ms 캡처는 attach_limited/policy_filtered·요청0건이다. API 동작은 별도 공개 Playwright/로그인 CUA 근거에만 연결한다. 원본이 없는 분석 응답 요약을 전체 Evidence로 승격하지 않았다.
- 수정 전 로컬 실제 화면에서 workId1이 숨은 입력으로 유지되고, 목록 제목/화면에 작품 범위 안내가 없음을 확인했다. [수정 전 상태](evidence/repov-20261016/reuse-review/scope-before.json), [전](evidence/repov-20261016/reuse-review/scope-before-desktop.png) / [후](evidence/repov-20261016/reuse-review/scope-after-desktop.png), [320px후](evidence/repov-20261016/reuse-review/scope-after-320.png).
- 이번 Maven package 성공, 단위2개 실패0·오류0·skip0. 실제Tomcat/JSP에서 **23개 브라우저 assertion** 통과: 범위·제목 표시, 폼 범위 유지, 해제 시6개 조건 유지, workId/page 제거, 키보드 링크 초점, 다른 작품 재발견,0건 안내, 특수문자 인코딩, 오류와0건 구분,390/320px 폭·링크 가시성, JS오류0. 검수 캡처를 직접 읽었다.
- SQL·Java·DB·빌드 설정은 이번에 변경하지 않았다. 26개 통합/80개 브라우저/27개 최종 레이아웃은 **직전 구현의 실행 결과를 재검토한 것**이며 이번 재실행으로 보고하지 않는다. 이번 수정이 조회 표현뿐이므로 전체 DB/CRUD 시험을 중복하지 않고 실제 JSP와 링크 매개변수 검증에 집중했다.
- 이전 Repov evidence 전체 해시를 보존하고, 이번 결과는 하위 reuse-review에 모았다. 앱 DB 행 해시의 전후 일치도 확인했다. 기존 F01–F08·확정 데이터 규칙·현재 실행 환경을 보존했다. 외부Redmine수정·push·배포는 하지 않았다.

[이번 검증 요약](evidence/repov-20261016/reuse-review/review-verification.json), [23개 검사](evidence/repov-20261016/reuse-review/scope-checks.json), [REA 보존 검토](evidence/repov-20261016/reuse-review/evidence-review.json), [이번 JSP차이](evidence/repov-20261016/reuse-review/scope-change.diff). 실제 스마트폰·스크린리더·다른브라우저·Repov 저장/수정/삭제/실제 권한/비어 있지 않은 개인 통계는 계속 미검증이다. 새 조사 없이 이 제한을 해소했다고 주장하지 않는다.

---

## 최신 로컬 검증 — 2026-10-16 Repov 관찰 기반 개선

실제 구현/시험: Codex. 팀원 독립 검토·승인·인수 완료를 뜻하지 않습니다. [관찰과 적용 연결](사용자_관점_종합분석.md), [설계 계약](design.md)의2026-10-16절을 연결합니다.

- JDK15.0.2 / source·target1.8 / Spring5.3.39 / 외부Tomcat9 / 실제MySQL8.4·MyBatis 유지. `pom.xml`, `db/schema.sql`, 통계SQL/Service, 인증설정은 변경하지 않았습니다. 도구용 Java21·Ghidra는 사용/변경하지 않았습니다.
- 최종 서비스 코드의 `clean package verify` 성공: 단위2개, 통합26개(기존23+신규3), 실패0·오류0·skip0. 통합45.08초, 전체54.794초. 이후 시각 검수에서 발견한 CSS2곳만 보완하고 Maven package(-DskipTests) 성공, 최종 CSS의 실제 브라우저 검수를 별도로 수행했습니다.
- 새3개 시험은 전체 이력의 이전 유효 평점, NULL건너뛰기/변화0/0.5실제평점, 회원·작품분리, 기간밖비교, 필터·정렬·페이지와 무관한 순번, 같은날 생성시각/ID동률, 과거추가/날짜수정/유효→NULL변경/삭제의 재계산을 확인합니다. 비교의 기준 평점0은 기존 검증에서 금지하며 NULL대신0을 넣지 않습니다.
- 실제 Tomcat9/JSP/Chrome134의 브라우저 assertion80개 통과. JSP8개 경로, 검색직접작성, 상태/이력요약, 서버날짜도우미, UTF-16글자수, COMPLETE/PAST저장효과, 미평점포함4건재감상/원본보존, 필터후에도4번째/+1.5점, actual409초안복구, F01비율8개, F02오류수정, F04페이지경계, F06안전한제목초안, 가입redirect/로그인/CSRF/회원·관리자간비공개접근, 수정/삭제/공용작품·상태보존을 확인했습니다. 체크 수는 사용자/시나리오 수가 아닙니다.
- 1440×1000,390×844,320×844에서 실제 렌더링을 확인했습니다. PC/모바일 주요 화면을 직접 읽었습니다. 최종CSS에 대한 별도27개 assertion 통과(페이지 JS오류0)는 layout-final.json입니다. 표 이외의 페이지 가로 넘침, 긴이름과로고, 모바일 제목폭을 실제 DOM치수와 캡처로 검수했습니다. 통계의 넓은 표는 기존 내부스크롤 안내를 유지합니다.
- 실제화면에서 최초‘이전보다+0.0점’을 발견해 Service의 BigDecimal.signum 기반 방향값을 명시했고, 통합26개와 브라우저80개를 재실행했습니다. 이후320px 캡처에서 긴 회원명 겹침·제목35%폭 잔존을 발견해 CSS를 수정했습니다. 첫 최종레이아웃 assertion은 `.brand`의 빈 공간까지 포함한 전체박스를 비교해 실패했고 실제 텍스트span을 기준으로 고쳐 재검수했습니다. 이 실패를 앱의 미해결 결함이나 성공으로 바꾸지 않았습니다.
- 시험은 review_system_test/8082. 전용 fixture와 임시QA계정만 사용했습니다. 재개 시 실제앱 review_system의 회원2·작품4·감상8·상태4·장르8·작품장르5·요청원장2의 행 해시를 전후 대조했습니다. 시험데이터 정리와 일치 결과는 증거JSON을 따릅니다. 중단 전과 재개 사이 사용자가 추가한 데이터를 과거 시연건수로 되돌리지 않았습니다.

근거: [Maven요약](evidence/repov-20261016/maven-results.json), [80개브라우저결과](evidence/repov-20261016/browser-flow.json), [최종레이아웃](evidence/repov-20261016/layout-final.json), [보존](evidence/repov-20261016/preservation.json), [정리](evidence/repov-20261016/cleanup.json). 주요사진은 [분석문서의전후링크](사용자_관점_종합분석.md)에 모았습니다.

미검증: Repov의 실제저장·수정·삭제·중복저장·현재상태·비어있지않은개인통계/필터정렬, 물리모바일·다른브라우저·스크린리더, 대규모동시부하·HTTPS/외부운영. 로그인후 검색/평점생략/작성폼·본횟수·날짜입력/0건프로필·평점화면만 추가확인했습니다. TMDB·게시판·댓글·첨부는 기존 유보상태입니다. 외부Redmine수정·push·배포는 하지 않았습니다. 앱8080은 최신WAR로 실행하고 시험8082는 정리 후 종료합니다.

---

**최신 v0.1.1 — 2026-10-14:** MySQL 통합시험 23개(기존18+신규5)·단위시험2개 통과, 실제 Tomcat/JSP/Chrome에서 F01 8개 비율 및 F02–F08 흐름·추가 보완 확인. 상세 기대값·실제값·증거·미실행 환경은 [이번 수정 결과](fix-review-20261014.md)에 있습니다. 아래는 2026-10-13 기존 시험의 역사적 결과입니다.

검증일: 2026-10-13, Asia/Seoul. Codex가 작성한 자동시험과 화면 점검 결과입니다. 팀원 교차 검토·사용자 만족도 평가·교수자 승인을 대신하지 않습니다.

**후속 사용자 관점 점검:** 신규 가상 사용자로 30개 시나리오를 탐색한 결과, 유효 평점 비율이 75%·66.7% 대신 100%, 17.6% 대신 0%로 표시되는 오류를 발견했습니다. 아래의 기존 자동시험 통과 결과는 이 표시 오류까지 검증한 결과가 아닙니다. 오류 복구·페이지 경계·모바일 안내 개선점과 화면 증거는 [사용자 관점 종합 분석](사용자_관점_종합분석.md)에 별도로 기록했습니다. 당시 앱 오류는 미수정이었습니다. 2026-10-14 수정·재시험은 위 최신 문서와 별도로 구분합니다.

## 환경

- Windows 11 Pro 64비트, Intel Core i5-1334U(10코어/12논리 프로세서)
- 실제 JDK·javac 15.0.2, Maven 3.9.9, Spring 모든 모듈 5.3.39
- MySQL Community 8.4.11, Connector/J 8.4.0, MyBatis 3.5.19 / MyBatis-Spring 2.1.2
- 외부 Tomcat 9.0.123, Chrome 134.0.6998.36, Asia/Seoul 브라우저 시각
- 데스크톱 1440×1000, 모바일 크기 390×844. 실제 스마트폰 기기는 사용하지 않았습니다.

## 빌드·IDE·DB

최종 Maven WAR 빌드 성공. Spring 모듈의 JAR 버전이 모두 5.3.39임을 확인했고, WAR 의존성의 기본 클래스는 모두 Java 8 class major version 52 이하였습니다. 앱 클래스도 major 52입니다. 컴파일 시 source/target 1.8을 적용했습니다. STS 워크스페이스 가져오기·의존성 갱신·빌드 후 오류 마커 0개입니다.

실제 MySQL 전용 시험 DB에서 **통합시험 18개, 실패 0, 오류 0, skip 0**을 확인했습니다. 실행 시간은 60.10초였습니다. 이후 발견한 JSP·초기 데이터 인코딩 문제는 화면 단계에서 수정하고 실제 브라우저 시험을 통과했습니다. MySQL 시험을 다른 DB의 결과로 대체하지 않았습니다.

검증 항목에는 아래 내용이 포함됩니다.

- 10월 기대값 4건·2작품·2재감상 작품·3추가 감상·1미평점과 드라마 4.25(n=2), SF 4.50(n=1)
- 기간 밖 첫 감상과 평점, 최신 NULL, 대표 평점 수정·삭제, 과거 감상 추가·날짜 변경 후 재계산
- 같은 작품·같은 날 별개 감상, 동시 동일 UUID 재전송, 삭제 후 토큰 재사용 차단
- 임시 DB CHECK로 실제 상태 저장을 실패시킨 복합 트랜잭션 롤백: 기록·상태·토큰 예약 모두 취소
- 다른 회원·관리자의 타인 개인 기록 접근 거부, CSRF·미로그인·공용 정보 관리 권한
- NULL/0.5/5.0 허용, 0/0.7/5.5/0.55 거부, 미래·잘못된 날짜 거부, 한글·이모지
- 참조 작품·장르 삭제 차단, 기록 삭제 후 작품 유지, 상태·이력 독립, 소유자·작품 고정, 오래된 수정 거부
- 복수 장르 중복 방지, 양끝 날짜 포함, 31개 동률 기록의 안정된 페이지 정렬, SQL 파라미터와 정렬 허용 목록
- 회원가입 검증·BCrypt 저장·USER 기본 권한, 외부 영화/TV ID 구분, 수동 중복 확인, 빈 통계의 0/NULL

증빙: [mysql-integration.json](evidence/mysql-integration.json).

## 실제 Tomcat·Chrome 사용 흐름

**35개 브라우저 확인 항목이 모두 통과**했습니다. HTTP 요청 시험과 실제 JSP 렌더링을 함께 사용했습니다.

회원가입 → 세션 ID 갱신 로그인 → 작품·복수 장르 등록 → 상태 지정 → 과거 감상 추가 → 같은 날 별개 완료 감상 → 상태만 완료 → 통계 → 수정·삭제 확인 → 로그아웃 흐름을 수행했습니다. 타인·관리자의 개인 기록 접근, 일반 등록자의 공용 수정권 거부, CSRF, 참조 작품 삭제 차단도 확인했습니다.

저장된 `<script>` 문자열과 회원 이름을 실제 JSP에서 안전하게 표시했고 실행 대화상자가 발생하지 않았습니다. HttpOnly·SameSite=Lax와 로컬 HTTP의 비Secure 쿠키를 확인했습니다. 390px 통계·작품 화면에 페이지 전체 가로 넘침이 없었습니다. 넓은 표는 내부 가로 스크롤로 제공됩니다.

데스크톱·모바일 스크린샷을 직접 확인했습니다. 텍스트·입력 항목·카드·통계의 주요 레이아웃을 점검했으며, 헤더·메뉴·입력 label·포커스 표시가 구현되어 있습니다. 실제 다른 브라우저와 물리 스마트폰·스크린 리더를 이용한 별도 시험은 미실행입니다.

증빙: [browser-flow.json](evidence/browser-flow.json), [데스크톱 대시보드](evidence/dashboard-desktop.png), [통계](evidence/stats-desktop.png), [모바일 통계](evidence/stats-mobile.png), [모바일 작품](evidence/works-mobile.png), [입력 오류](evidence/input-error-desktop.png).

## Tomcat 재기동과 지속 저장

재기동 전후의 실제 MySQL 감상 기록·현재 상태 행을 대조해 동일함을 확인했습니다. 재기동 후 재로그인과 10월 통계의 4.25·4.50 표시도 확인했습니다. 앱 서버(8080)와 MySQL(3306)은 실행 상태로 두었고, 성능 시험 전용 서버(8081)는 종료했습니다.

증빙: [persistence.json](evidence/persistence.json).

## 입력 오류 표시 시간 — PER-001

제출 버튼을 누르는 시점부터 오류 안내가 있는 페이지를 로드하고 두 번의 animation frame이 지난 시점까지 측정했습니다. 입력 검사 방식은 서버 제출 시 검사입니다. 브라우저의 페이지 이동·스크롤·로컬 자동화 호출 비용도 포함합니다. 비밀번호는 오류 후 공란이며 날짜·조회 기간 입력 유지도 확인했습니다.

| 입력 오류 | 실제 표시 시간(ms) |
| --- | ---: |
| 필수 아이디 | 297 |
| 비밀번호 정책 | 144 |
| 필수 이름 | 110 |
| 메일 형식 | 137 |
| 아이디 중복 | 225 |
| 필수 작품 제목 | 194 |
| 필수 감상일 | 179 |
| 평점 간격 위반(옵션 변조) | 170 |
| 역전된 조회 기간 | 194 |
| 미래 감상일 반복 1 / 2 / 3 | 222 / 221 / 242 |

측정한 12회 모두 3,000ms 이내였습니다. 서버 입력 경계는 추가로 MySQL 통합시험에서 검사했습니다. 위 값은 이 PC·브라우저·측정 조건의 결과이며 모든 가능한 오류·다른 환경의 성능 보장은 아닙니다.

증빙: [input-validation.json](evidence/input-validation.json), [browser-flow.json](evidence/browser-flow.json).

## 목록·통계 성능

별도 `review_system_perf` DB에 작품 **1,000개**, 감상 기록 **10,000건**, 회원 10명·회원별 최대 **1,000건**을 생성했습니다. 영화·드라마, 여러 장르, 재감상, NULL 평점, 10월 안팎 기록을 포함합니다. 앱 데이터는 초기화하지 않았습니다.

페이지 이동 시작부터 load 완료 후 두 animation frame까지 실제 브라우저 표시 시간을 측정했습니다. 최초는 로그인 후 해당 조건의 첫 페이지 요청이고, MySQL 전체 버퍼의 완전한 cold start를 의미하지 않습니다. 같은 조건의 반복 요청 3회를 구분했습니다.

조건: 제목 ‘성능’, 2026-10-01~2026-10-13. 화면 크기 1440×1000.

| 대상 | 최초(ms) | 반복 1 | 반복 2 | 반복 3 |
| --- | ---: | ---: | ---: | ---: |
| 개인 기록 목록 | 1,090 | 198 | 183 | 199 |
| 통계 화면 | 932 | 283 | 250 | 183 |

조회·통계 허용 기준은 **팀 미확정**입니다. 제안할 초기 목표는 최초 2초·반복 1초 이내이며 팀 검토 후 확정해야 합니다. 입력 오류의 3초 기준을 조회·통계 전체에 적용하여 합격으로 선언하지 않았습니다. 대규모 동시 부하·장시간 안정성 시험은 미실행입니다.

증빙: [performance.json](evidence/performance.json), [성능 통계 화면](evidence/performance-stats-desktop.png). 생성·측정 코드는 상위 폴더 `.setup/seed_data.py`, `.setup/performance_checks.cjs`입니다.

## 확인 중 수정한 문제

1. 인증 서비스의 인터페이스 프록시가 구체 클래스 주입과 충돌: 클래스 기반 트랜잭션 프록시로 수정, MySQL 18개 시험 통과.
2. 롤백 시험의 트리거 생성이 MySQL binlog 권한에 막힘: 서버 전체 권한을 늘리지 않고 시험 DB의 임시 CHECK를 사용해 실제 저장 실패와 롤백 검증.
3. JSP의 정적 한글 및 Windows 초기 장르 SQL 인코딩: 모든 JSP·조각의 UTF-8과 MySQL CLI utf8mb4를 명시. 생성한 장르 데이터만 복구한 뒤 브라우저 검증.
4. 일부 JSTL 조건의 표현식 표시 누락: 전체 JSP 조건식 수정 후 감상평·저장 방식·오류 표시·빈 상태·통계 경고의 실제 브라우저 검증.
5. 재기동 시험 중 같은 설정 파일의 재작성으로 Tomcat 자동 재배포 발생: 설정이 변경된 경우만 파일을 쓰도록 수정하고 적용·실행 순서를 분리.

## 미실행·후속

- 게시판·댓글·첨부: 조건부 범위 미확정, 미구현·미시험.
- TMDB: API 키 없음, 실제 호출·최신 약관·로고·저장·갱신 정책 적용 미검증.
- HTTPS 배포, 실제 Secure 쿠키·TLS JDBC 연결, 외부 호스팅: 미실행.
- 실제 팀원 교차 검토·사용자 평가·모의해킹·법적 준수 인증: 미실행.
- 개인정보 보관기간·삭제 담당 연락처, 조회·통계의 팀 승인 허용 기준: 미확정.

모든 자동 검증은 Codex 수행 기록이며 팀원이 직접 시험·검토했다는 기록은 작성하지 않았습니다.
