# 팀 E — v0.1.1에서 이어서 작업하기

## 현재 Redmine 협업 기준 — 2026-11-03

후속 실적 표시 반영: UC10개는 **명세 작성·등록 범위로 완료·100%**, #196은 등록 확인 기록을 남겨 진행으로 표시했다. 원 요구19개·설계·보완·실제 시험·최종 인수의 상태/진척률은 유지했다. [완료 포함 간트](http://114.70.92.70:3000/projects/e-team-2026/issues/gantt?month=10&query_id=8&year=2026) · [근거와 변경 기록](../../output/feedback-review-20261103/진척_실적반영_20261103.md). 기능 전체·독립 인수·수업 제출 완료를 뜻하지 않는다.


현재 담당·일감은 [Redmine 협업 Wiki](http://114.70.92.70:3000/projects/e-team-2026/wiki)와 [피드백 반영 기록](../../output/feedback-review-20261103/반영완료_20261103.md)을 우선한다. 아래 날짜별 코드·시험·과거 담당 기록은 당시 이력이며 최신 분담으로 덮어쓰지 않는다.

- 구서준: 평점·감상평 #20, 통계 #23·#24·UC #79·#80, 설계 #193→보완 #194→시험 #195. 공통 UI·PM 병행.
- 고현진: 감상 기록 CRUD·재감상 전체 이력/순번·현재 상태, #82→#85→#89. #90은89 T03-04의 실행 증거 참조·검산이며 평가 시험은195에 있다.
- 조우성: 회원·인증·작품·조회 및 구서준 평가·통계의 독립 교차검토. 개인 의견노트는 확정 요구·완료 근거가 아니다.
- 제안서 요구19개 유지. 현재57개 일감은 기존 기능의 수행·검증·관리로 구분하며 추가 기능 개발을 뜻하지 않는다. 조건부 #15·#16·#25는 적용/유보 미결이다.
- 공식 분석 마감11/03의 등록 확인 #196과 내부 후속검토11/04–06 #68을 구분한다. 설계11/18·보완11/25·시험12/02·인수/문서12/04·예비12/07–09 #197은 내부 목표이고 공식 세부 마감은 공지 확인이 필요하다.
- 후속 시험은 독립 예정33사례와90 증거 검산이다. 이번에는 코드 수정·재시험·팀 인수·실제 제출 실적을 새로 만들지 않았다. 기존10/16 자동시험 증거와 담당자의 실제 수행·인수는 별도다.


## 현재 인계용 기준 프로토타입 — 2026-10-16

**현재 로컬 폴더**는 v0.1.1에 F01~F08, Repov 기반 사용 흐름, 작품 범위 표시, 이번 소규모 인계 보완을 합친 기준선이다. 아래의 기존 Redmine ZIP 안내는 배포 이력이다. 이번 변경은 그 ZIP이나 외부 Redmine에 반영하지 않았으므로 기존 ZIP을 현재 로컬 코드와 같다고 간주하지 않는다. 팀원 인수/승인 기록도 추가하지 않았다.

첫 시연은 로그인 → 작품 찾기(개인 상태/기록 수) → 작품 상세/기존 이력 → 완료하고 기록하기 또는 과거 감상 추가 → 저장 상세 → 작품 전체 이력 → 수정/삭제 → 통계의 대표 원본 순서다. ‘상태만 완료’도 별도로 실행해 기록 수가 증가하지 않는 것을 대조한다. 기본 신규 화면은 [설계의 공통 UI 표](design.md)를 그대로 따른다.

수정하면 안 되는 계약은 공용 작품/회원별 기록, 현재 상태/감상 이벤트 분리, 재감상의 새 INSERT, 선택 평점·감상평, NULL≠0, 전체 이력의 감상일/생성시각/ID 순서, 이전 유효 평점의 NULL 건너뛰기, 기간 내부 대표 평점·분모·복수 장르, UUID 재전송·version 충돌, principal 기반 소유권·개인 삭제 범위다. 구체 정의는 design.md의 기존 저장·통계·이력 절을 참조한다.

연결 위치: 회원/인증은 AuthController·AccountService, 공용 작품/장르는 WorkController·WorkService, 감상/상태는 RecordController·RecordService, 통계는 StatsController·StatsService다. Mapper는 회원 범위·전체 이력·집계를 지키고 JSP는 받은 값을 표시한다. 검색 카드의 개인 요약은 기존 일괄 조회를 재사용한다. 이번 변경은 서버 조회를 추가하지 않았다. 신규 QueryFilter 조건을 추가하면 validate·Mapper 바인딩·페이지·`applied-filters.jspf`의 표시/보존/해제 목록을 함께 검토한다.

### 변경 후 다시 실행할 검사

| 변경 영역 | 다시 확인할 기존 근거 |
| --- | --- |
| 저장/수정/삭제·소유권·동시성·NULL·통계 | 기존 MySQL `ReviewSystemIT` 26개와 `StatsServiceTest` 2개. QA-R2/R4/R5/R6의 명명된 사례를 재사용한다 |
| 검색/작성/기록/성공·오류·JSP 표현 | `.setup/repov_browser_checks.cjs` 기존 80개. QA-R1/R2/R5/R6와 F01~F08 연결 |
| 필터/정렬/페이지/통계 작품 범위·뒤로가기·긴 문자열 | `.setup/handover_checks.cjs`. 조건 일부만 해제, 나머지 조건 유지, page 초기화, 실제 DOM/입력/키보드/복원 분기를 확인한다 |
| 공통 CSS/JSP·모바일 | 기존 `.setup/repov_layout.cjs` 및 handover 검사의 1440/390/320 캡처. 자동 폭 통과와 별도로 날짜 전체 표시·긴 제목·회원명·버튼·평점 변화·오류를 직접 읽는다 |

현재 PC의 **새 증거 폴더**에 재현하는 순서(기존 evidence 경로 재사용 금지):

```powershell
.\start-db.cmd
$env:MOA_EVIDENCE_DIR=Join-Path (Get-Location) 'review-system\docs\evidence\handover-재시험일시'
# 상위 폴더에서 실행. 통합시험은 review_system_test를 초기화한다.
.\build.cmd -B -ntp verify "-Dreview.config=$env:USERPROFILE\dev\review-system\config\test.properties"
python .setup/repov_fixture.py snapshot-before
python .setup/repov_fixture.py seed
python .setup/server_control.py start --test
$env:NODE_PATH="$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules"
node .setup/repov_browser_checks.cjs
node .setup/repov_layout.cjs
node .setup/handover_checks.cjs
# 실패해도 전용 fixture 정리와 앱 DB 보존 확인은 수행한다.
python .setup/repov_fixture.py cleanup
python .setup/repov_fixture.py snapshot-after
python .setup/server_control.py stop --test
Remove-Item Env:MOA_EVIDENCE_DIR
```

검사 명령의 종료 코드를 확인하고 실패하면 다음 검사의 성공으로 덮지 않는다. `MOA_EVIDENCE_DIR`은 세 기존 검증 도구와 새 handover 검사에 동일하게 적용한다. 앱 실행 의존성이나 DB 설정이 아니다. 자격증명은 기존 프로젝트 밖 config에만 생성한다. 서버 재시작 전 이전 8082를 종료하며 앱 8080에 시험 WAR를 올리지 않는다. `.setup/handover_before.cjs`는 당시 문제 재현용으로 보존한 스크립트이며 재시험 필수 절차가 아니다.

기술적 로컬 인계 기준선으로 사용할 수 있다. 게시판·댓글·첨부·TMDB는 계속 유보이며 임시 저장·소셜·수동 횟수·SPA·추천 알고리즘을 추가하지 않았다. 실제 스마트폰/스크린리더/다른 브라우저, 팀원 독립 검산·인수, 새 PC 전체 설치, HTTPS/외부 운영은 미검증이다. 이번 작업은 로컬 시험만 수행했고 외부 Redmine 수정·Git push·배포는 하지 않았다. 최신 실제 실행 수치·실패 및 재검수는 [verification.md](verification.md)의 맨 위 절을 따른다.

작성일 2026-10-14, Asia/Seoul. 초기본 보완은 **구서준의 Codex 지원을 활용한 초기본 보완**이다. 제안서의 대표 담당 계획은 유지하며, 아래 팀원 작업은 앞으로 직접 수행하고 기록할 내용이다. 팀원의 개발·검토·시험·인수가 끝났다는 기록이 아니다.

## 먼저 실행하고 기준본 확인

1. 레드마인 파일 메뉴의 **v0.1.1 수정·검증본**을 새 폴더에 압축 해제한다. v0.1 첨부 #30은 수정 전 비교 자료이다.
2. 같은 PC라면 상위 폴더에서 `start-db.cmd` → `start-server.cmd` → http://localhost:8080/ 순서로 실행한다. 새 PC 설치는 README의 설치 순서를 따른다. `.setup` 폴더를 포함하여 풀어야 한다.
3. 설정은 JVM `review.config` → `REVIEW_CONFIG` → `%USERPROFILE%\dev\review-system\config\application.properties` 순서이다. DB 환경변수는 파일값보다 우선한다. 실제 비밀번호를 ZIP·레드마인·스크린샷에 붙이지 않는다.
4. 본인 개발용 DB와 외부 설정을 준비한다. `db/schema.sql`은 명시적으로 선택한 DB에만 적용한다. 기존 팀원 DB를 초기화하지 않는다. 장르는 개발용 DB에서 준비하고 일반 회원 가입과 관리 권한을 구분한다.
5. STS 3/Spring Legacy MVC 5.3.39/JDK 15/source·target 1.8/Tomcat 9/MySQL 8.4/MyBatis를 유지한다. STS와 명령어 서버는 같은 8080을 함께 사용하지 않는다.

Maven 버전은 1.0.0-SNAPSHOT이다. 배포 자료의 v0.1.1과 의미가 다르며 패키지 이름만으로 Maven 버전을 바꾸지 않는다. 새 PC에서 설치 전체를 다시 실행한 검증은 아직 없다.

## 역할별 첫 작업과 제출할 결과

| 대표 담당 | 담당 요구사항·일감 | 먼저 볼 코드 | 직접 남길 결과 |
| --- | --- | --- | --- |
| 조우성(QA) | SFR-001·002·005·010 / #13·14·17·22 | SecurityConfig, AuthController, AccountService, WorkController·Service·Mapper, QueryFilter, RecordMapper | 실행 환경, 회원/공용 작품/필터 입력·출력 계약 검토, 정상·오류·경계 시험, 발견 결함과 재시험 |
| 고현진(SE) | SFR-006·007·008·009·011·012 / #18–24 | RecordController·Service·Mapper, StatsController·Service·Mapper, RecordForm, DB schema | 세 저장 동작·롤백·소유권·동시 수정, 전체 이력 순번·기간 통계·대표 평점 독립 검산 |
| 구서준(PM) | INR-001·QUR-001 / #27·31 | JSP 공통 조각, record-form/stats/works/login, app.js·css, docs | 화면 이동·오류 복구·모바일 점검, 요구사항/설계/시험 연결 유지, 범위·일정 합의, 문서·시연 |
| 조우성(QA), 전원 교차 검토 | PER-001·TER-001·SER-001·SER-002 / #26·28·29·30 | ReviewSystemIT, StatsServiceTest, docs/evidence, SecurityConfig·LocalSettings | 독립 시험 결과, 허용 기준 합의, 보호 기준 검토, 적용된 기능의 인수 근거 |

코드에 손대기 전에 해당 일감의 정의·입출력·완료 기준과 `docs/design.md`를 읽는다. 단순 재작성에 그치지 않고 담당 영역의 계약을 검토하고, 오류가 있으면 직접 수정·시험하여 변경 파일과 근거를 남긴다. 서로 다른 파일을 맡더라도 공통 QueryFilter·JSP 조각·schema 변경은 관련 담당자와 먼저 조율한다.

## 이번 보완을 다시 확인하는 절차

상위 폴더 PowerShell에서 실행한다.

```powershell
.\start-db.cmd
.\build.cmd -B -ntp verify "-Dreview.config=$env:USERPROFILE\dev\review-system\config\test.properties"
python .setup/fix_fixture.py seed
python .setup/server_control.py start --test
```

통합시험은 **review_system_test의 합성 데이터를 삭제하고 다시 만든다**. 앱 DB와 혼용하지 않는다. 브라우저 시험 서버는 http://localhost:8082/ 이며 앱 8080과 분리한다. 합성 계정은 프로젝트 밖 `config/fix-browser-account.json`에 생성된다. `docs/evidence/fix-20261014/fixture.json`에 대상 ID와 기간·기대값을 기록한다. 같은 fixture가 남아 있으면 먼저 cleanup하며 seed를 중복 실행하지 않는다.

| 확인할 흐름 | 기대 결과 |
| --- | --- |
| F01, 2026-01-01부터 01-08까지 하루씩 조회 | — / 0% / 100% / 25% / 50% / 66.7% / 75% / 17.6% |
| F02, 시작일 > 종료일 또는 잘못된 날짜 | 제목·메뉴·조건 유지, 오류 영역 포커스, 결과 숨김, 조건 수정 후 정상 조회 |
| F03, 같은 기록을 두 창에서 편집 | 먼저 저장한 값 유지, 오래된 초안 보존, 최신 상세·편집 새 창 링크, 재저장 버튼 없음 |
| F04, 페이지 경계 작품을 복합 필터로 조회 | 15+1건, ID 중복·누락 없음, page=999는 2/2, 0·음수·비숫자는 1, 결과 감소 시 유효 페이지 |
| F05, 390×844와 1440px | 페이지 전체 넘침 없음, 필요한 표만 안내·내부 스크롤·키보드 초점, 첫 열에서 원본 이동 |
| F06, 한글·공백·따옴표·< > & 검색 | 직접 등록 제목에 안전한 수정 가능 초안, 자동 저장 없음, 기존 중복 확인 유지 |
| F07, 실제/존재하지 않는 아이디의 인증 실패 | 같은 일반 메시지, 아이디 유지, 비밀번호 공란, URL에 비밀번호 없음 |
| F08, 회원가입 성공 | /login?registered= 의도한 표시값만, 가입 성공 안내·공통 화면 정상 |

입력 날짜·조회 조건과 실제 결과 문자열을 적는다. 삭제 확인 후 취소와 실제 삭제를 다른 시험으로 작성한다. 현재 상태는 기간과 독립적이고 복수 장르 합계는 전체보다 클 수 있다. 전체 이력의 14번째 감상은 재감상 추가 13회이다.

시험 종료 후 본인이 만든 fixture만 정리한다.

```powershell
python .setup/server_control.py stop --test
python .setup/fix_fixture.py cleanup
```

실제 수행 증거는 `docs/fix-review-20261014.md`와 날짜별 evidence 폴더를 기준으로 읽는다. 기존 2026-10-13 시험·사용자 분석은 수정 전 역사적 기록으로 보존한다.

## 레드마인에 남길 양식과 완료 기준

해당 요구사항 일감의 댓글에 아래를 채운다. 새 결함은 기존 일감이 있는지 확인하고 연결한다.

```text
수행자 / 실제 수행일:
기준 ZIP·버전 / 실행 환경·시험 DB:
검토한 코드·계약 / 변경 파일과 이유:
시작 조건·입력 / 기대값 / 실제 화면·DB 값:
정상·오류·경계 시험과 증거 위치:
발견 결함 / 수정·재시험 / 미실행 항목:
교차 검토자와 실제 검토일(받기 전에는 요청 상태):
인수 가능한 범위 / 남은 조건 / 다음 담당:
일정 제안 또는 팀이 확정한 날짜와 합의 근거:
```

코드 보완 일감은 명시된 수정·검증 기준을 충족하면 종료할 수 있다. 요구사항 일감은 관련 담당·교차 검토와 인수 조건까지 확인한다. 검토를 요청했다는 이유만으로 완료하지 않는다. 백분율 산정 기준은 미합의이므로 단계·체크 항목으로 설명하며 임의의 80%·90%를 입력하지 않는다. 초기 등록 #12는 완료를 유지하고 후속 보완은 댓글과 별도 보완 일감으로 연결한다.

## 합의가 필요한 기준안 — 확정 전

| 항목 | 제안 | 결정 방법·남은 책임 |
| --- | --- | --- |
| SFR-003·004 첨부·게시판 | 기본 기능 인수 후 필요성·공개 범위·권한·파일 제약을 함께 판단하고, 추가하면 별도 설계·시험 확보 | 조우성·고현진 기술 검토 → 구서준 범위/일정 조정 → 팀 댓글 합의; 지금은 미구현 |
| SFR-013 TMDB | API 키·HTTPS·한국어 검색·이용 조건·출처 표시·429/장애 대안을 검증한 뒤 적용 판단 | 조우성 검증 계획, 전원 범위 결정; 키는 외부 설정, 실제 호출 미실행 |
| 조회·통계 성능 | 기존 합성 DB 1,000작품·10,000기록·회원별 최대1,000건, 1440px에서 최초2초·반복1초 목표 | 조우성 측정 방식과 동시 사용자 조건 제안 → 팀 허용 기준 확정; 입력 오류3초와 별개 |
| 과제 회원정보 | 시연은 가상 이름·example.invalid 메일 사용, 아이디·비밀번호·이름·메일 외 수집 없음; 원문 비밀번호 보관 없음 | 조우성 현재 필수 정보와 안내 검토, 팀 수집 목적·담당 확정; 법적 준수 인증 아님 |
| 보관·삭제·백업 | 과제 종료·성적 확인 후30일 내 시연/시험 DB와 백업 정리 제안; 개발 중 주1회와 큰 변경 전 외부 보호 폴더 백업 | 구서준 삭제 책임, 조우성 실제 정리·복원 절차 검토; 기한·연락 창구·접근 범위 팀 미확정 |
| 교차 검토·인수 | 담당자가 자체 시험 근거 제출 → 다른 팀원1명 이상 독립 검산 → 전원 전체 흐름 확인 | #28에 실제 검토일·결과 기록, 오류가 있으면 원 요구사항과 결함 연결 |

## 일정 검토안 — 확정 날짜 필드에 입력하지 않음

제안서의 10월 분석·설계, 11월 기능 시험, 11월 하순 전체 시험, 12월 제출 전 안정화 구간을 유지한다.

| 제안 구간 | 검토할 산출물 | 담당 계획 |
| --- | --- | --- |
| 10월14–16일 | 수정본 다운로드·본인 PC 실행, 담당 코드 계약·잔여 항목 검토, 등록 마감 확인 | 각 대표 담당, 구서준 취합 |
| 10월19–23일 | 담당별 독립 재현·교차 검토, 조건부 기능 및 운영/성능 기준 판단 | 조우성·고현진 검토, 전원 합의 |
| 10월26–30일 | 설계·입출력·DB 계약과 정확한 WBS 확정, 승인된 범위만 후속 구현 계획 | 전원, 구서준 일정 반영 |
| 11월1–2주 | 담당 기능 보완·자체 시험, 11월11일 일정 수립 안내에 맞춘 점검 | 각 기능 담당, QA 결과 관리 |
| 11월3–4주 | 교차 검토와 전체 연결 시험·성능 기준 대조, 주요 결함 재시험 | 전원, 조우성 시험 관리 |
| 12월 제출 전 | 최종 인수·문서·시연·종료 보고 | 전원, 구서준 통합 |

이는 검토안이다. 팀원이 착수하거나 완료했다고 날짜를 소급 입력하지 않는다. 일정 합의 댓글이 생긴 뒤 레드마인 시작·완료일 및 Gantt를 갱신한다. 이번 구서준 보완의 실제 착수·완료일은 2026-10-14로 별도 기록한다.

## 2026-10-16 사용 흐름 보완 인계

기존 F01–F08을 유지한 로컬 개선입니다. [관찰→문제→적용→파일](사용자_관점_종합분석.md), [전체이력 표시계약](design.md), [시험](verification.md)을 먼저 확인합니다. DB마이그레이션과 신규 앱의존성은 없습니다. 대표 담당 계획은 그대로입니다.

핵심 연결은 WorkController.myWorks/historySummary, RecordController.form/detail.historySummary → RecordService.workSummaries/list/get → RecordMapper.xml의 회원별 전체이력입니다. history-summary/rating-change/record-list는 공통조각입니다. 최초 유효 평점과 변화0을 구분하며 JSP에서 BigDecimal과 정수0을 비교하지 않습니다. stats의 대표평점은 선택기간 내부이므로 전체이력 요약과 섞지 않습니다. 새정렬/필터를 추가할 때도 원래 ROW_NUMBER와 비교의 범위를 유지해야 합니다.

현재PC의 재현 순서(별도시험DB만 사용):

```powershell
.\start-db.cmd
.\build.cmd -B -ntp verify "-Dreview.config=$env:USERPROFILE\dev\review-system\config\test.properties"
python .setup/repov_fixture.py snapshot-before
python .setup/repov_fixture.py seed
python .setup/server_control.py start --test
$env:NODE_PATH="$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules"
node .setup/repov_browser_checks.cjs
node .setup/repov_layout.cjs
python .setup/repov_fixture.py cleanup
python .setup/repov_fixture.py snapshot-after
python .setup/server_control.py stop --test
```

NODE_PATH는 현재PC에 이미 설치된 검증용 Playwright 위치입니다. 앱의 Node의존성을 추가한 것이 아닙니다. 실행할 때마다 새fixture를 만들고, 검증스크립트는그ID를읽습니다. 계정비밀번호는 프로젝트밖 config/repov-browser-account.json에만 임시생성하고 정리시삭제합니다. 원본20261014 fixture/evidence는 수정하지 않습니다. 같은일자 증거를 보존하려면 재실행전 repov-20261016폴더를 별도보관하세요.

팀원의 후속은 기존 담당대로 소유권/전체이력/통계범위 계약과 실기기·다른브라우저 화면을 교차검토하는 것입니다. 외부Redmine등록/배포/승인은 이번에 수행하지 않았습니다.

## 2026-10-16 재검토 결과 재사용

추가 기능 목록보다 [분석 문서의 재사용 검토](사용자_관점_종합분석.md)에 있는 **출처표·후보 판단·QA-R1~R7·발표4사례**를 먼저 사용한다. 기존 대표 담당과 팀원 인수 미완료 상태는 유지한다.

- 구서준(PM)은 현재 선택 작품/조회 범위, 선택 입력, 저장 영향, 다음 행동을 화면에서 읽을 수 있는지 본다. 기존 공통 조각을 재사용하고 UI 전용 별도 집계를 만들지 않는다. 발표는 외부 관찰→우리 문제→요구에 맞는 선택→실제 시험 순서의4사례 중에서 고른다.
- 조우성(QA)은 기능 개수보다 QA-R1~R7에서 변경에 해당하는 계약을 선택한다. 소유권/개인 삭제/CSRF·기간/페이지가 있는지 확인한다. 서버 수치 검증과 실제 JSP의 최종 문구를 구분하고, 자동 폭 검사 뒤 캡처도 직접 읽는다. 새 범위 표시는 `.setup/repov_scope_checks.cjs`로 재현한다.
- 고현진(SE)은 전체 이력의 정렬·이벤트/작품 단위·최신 유효 평점/기간 대표 평점·복수 장르/분모가 유지되는지 검산한다. 이미 시험한 경계를 같은 방식으로 중복 작성하지 말고 기존 ReviewSystemIT의 명명된 사례를 이어 쓴다.

원본 REA2건, 공개 Playwright, 로그인 CUA 관찰 요약, 원본 미보존 번들 요약을 한 종류의 증거로 발표하지 않는다. 다음 조사부터는 질문과 동작을 정하고 관찰을 먼저 시작한 뒤 재현하며, 실패를 포함해 Evidence/한계를 즉시 내보낸다. 저장 동작을 주장하려면 전후 값/URL/요청/저장 후 재조회까지 필요하다. 프런트 번들 문자열로 서버 DB/프레임워크를 단정하지 않는다.

새 화면의 최소 체크는 다음5개다:①선택 맥락/조회 범위 표시 ②필수·선택과 저장 영향 ③NULL/0·기록/작품·기간/전체 단위 ④오류·빈 상태의 다음 행동 ⑤회원별 서버 권한과 모바일/키보드 읽기 순서. 이 기준을 만족하면 Repov의 다른 기능을 추가할 필요는 없다.

이번 추가 재현(현재PC, 읽기 전용 시연 계정이 설정된 로컬8080):

```powershell
$env:NODE_PATH="$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules"
node .setup/repov_scope_checks.cjs
```

이 검사는 기록을 만들거나 수정/삭제하지 않고 로그인·조회·링크 이동만 수행한다. 현재 시연 자료의 작품1/여러 작품을 사용하므로 다른PC에서는 동일 자료 또는 별도 합성 자료를 준비하고, 실제 비밀번호는 문서/스크린샷에 넣지 않는다. 원래26개 통합/80개 브라우저/27개 레이아웃 결과는 이전 실행이며 이번 검사와 합산하지 않는다. 실기기·스크린리더 및 팀원 독립 검토는 여전히 후속이다.
