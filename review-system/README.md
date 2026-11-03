# 모아보기 — 영화·드라마 감상 기록 및 개인 취향 분석

**2026-10-16 인계 기준선:** 기존 F01~F08·Repov 개선을 유지하고 조회 조건 개별 해제, 통계 작품 범위 보존, 뒤로가기 안내 복원, 긴 문자열 줄바꿈과 320px 날짜 표시를 보완했습니다. 현재 로컬 코드의 인계·재현 절차는 [team-handover.md](docs/team-handover.md)의 맨 위 절, 실제 실행 결과는 [verification.md](docs/verification.md)의 맨 위 절이 최신입니다. 아래에 나오는 기존 Redmine ZIP에는 이번 변경이 반영되지 않았습니다. 현재 서버 실행 여부는 실행 시 확인하세요.

현재 로컬 작업본은 **v0.1.1에 2026-10-16 사용 흐름 개선을 반영한 상태**입니다. [Repov 관찰·적용과 화면 전후](docs/사용자_관점_종합분석.md), [최신 검증](docs/verification.md)을 참고하세요. F01–F08과 추가 보완을 반영했습니다. 팀원은 아래 실행 안내를 따라 시작한 뒤 [역할별 인계·검토 절차](docs/team-handover.md)를 읽으세요. [이번 수정·실제 시험 결과](docs/fix-review-20261014.md)와 [레드마인 보완 #33](http://114.70.92.70:3000/issues/33)을 연결합니다. 대표 담당 계획과 팀원의 실제 수행은 구분합니다.
직전 재검토에서는 기록 목록의 작품 범위 표시/해제를 적용하고, 기존 분석 문서에 증거 분류·후보 판단·발표4사례를 통합했습니다. 그 이후의 인계 보완은 위 최신 안내를 따릅니다.

팀 E의 2026-10-12 최종 제안요청서를 기준으로 기존 Spring Legacy MVC 프로젝트를 이어서 개발했습니다. 이번 요청에서 DB를 MySQL·MyBatis로 확정했습니다. 제출한 HWP와 회의록은 수정하지 않았습니다.

## 실행

현재 PC에서는 프로젝트 상위 자료 폴더에서 실행합니다.

1. `start-db.cmd`: 과제용 MySQL 시작. 이미 실행 중이면 중복 실행하지 않습니다. DB가 꺼진 상태의 첫 시작 오류를 보완했습니다.
2. `start-server.cmd`: 8080 사용 여부를 먼저 검사한 뒤 Maven 빌드·외부 Tomcat 9 실행을 수행합니다. DB 시작도 확인합니다.
3. 브라우저에서 **http://localhost:8080/**를 열고 회원가입·로그인합니다.
4. 종료할 때 `stop-server.cmd`, DB도 종료할 때 `stop-db.cmd`를 사용합니다.

실행할 때 앱 서버와 DB의 현재 상태를 확인합니다. 이미 8080 포트가 사용 중이면 새 서버를 중복 실행하지 마세요. STS로 실행하려면 먼저 명령어 서버를 종료하고 `start-sts.cmd` → 프로젝트 우클릭 → Run As → Run on Server를 선택합니다. STS의 서버는 Servers 창에서 종료합니다.

`build.cmd`는 `review-system/target/ROOT.war`를 생성합니다. `dev-shell.cmd`에서는 JDK 15와 Maven이 적용됩니다. 기존 PC의 다른 Java와 Eclipse는 유지했습니다.

시험 DB의 브라우저 재현·fixture 생성/정리는 [인계 문서](docs/team-handover.md)의 전용 8082 서버 절차를 따릅니다. MySQL 통합시험은 review_system_test만 초기화합니다. 실제 앱·시연 DB를 시험 설정으로 지정하지 마세요.

## 고정한 버전

| 구성 | 버전 |
| --- | --- |
| STS / 실제 JDK / Maven | 3.9.18 / OpenJDK 15.0.2 / 3.9.9 |
| Spring Framework 모든 모듈 | 5.3.39, BOM으로 일치 |
| 컴파일 source / target | 1.8 / 1.8, Java 8 문법·API 사용 |
| Tomcat / Servlet | 9.0.123 / javax.servlet 3.1 |
| MySQL Community Server | 8.4.11 LTS, InnoDB, utf8mb4 |
| MyBatis / MyBatis-Spring | 3.5.19 / 2.1.2 |
| Connector/J / HikariCP | 8.4.0 / 4.0.3 |
| Spring Security / BCrypt | 5.8.16 / cost 12 |
| JSP / JSTL / Logback | JSP 2.3 / 1.2 / 1.2.13 |
| DBeaver Community | 26.2.2, 자체 JRE 25.0.4.1 사용 |

MyBatis-Spring 2.1은 Spring 5·Java 8 이상 조합입니다. HikariCP는 Java 8용 4.0.3을 선택했습니다. Java 8에서 실행 가능한 라이브러리를 JDK 15로 검증했습니다. 근거 링크는 [설계 문서](docs/design.md)에 있습니다.

## DB 설정과 비밀값

코드는 `LocalSettings`에서 다음 순서로 실제 설정 파일을 읽습니다.

1. JVM 옵션 `-Dreview.config=절대경로`
2. 환경변수 `REVIEW_CONFIG=절대경로`
3. 기본 경로 `%USERPROFILE%\dev\review-system\config\application.properties`

`DB_URL`, `DB_USER`, `DB_PASSWORD` 환경변수가 있으면 해당 파일의 값을 덮어씁니다. `.env` 자동 로딩에는 의존하지 않습니다. `config/application.properties.example`은 비밀값 없는 예시입니다.

현재 PC에서는 임의의 강한 비밀번호를 생성하여 **프로젝트 밖** 사용자 전용 `config` 폴더에 보관했습니다. 앱은 `review_app` 계정으로 `review_system`의 SELECT·INSERT·UPDATE·DELETE 권한만 사용합니다. root는 초기 스키마와 관리 작업에만 사용합니다. 비밀번호를 코드·문서·명령줄 인수에 넣지 않습니다.

| 용도 | DB / 설정 파일 |
| --- | --- |
| 앱 | `review_system` / 외부 `application.properties` |
| 통합시험 | `review_system_test` / 외부 `test.properties` |
| 합성 성능시험 | `review_system_perf` / 외부 `performance.properties` |
| CLI 관리 | 외부 `mysql-admin.cnf` |
| CLI 앱 / 시험 | 외부 `mysql-app.cnf` / `mysql-test.cnf` |

DB 계정과 앱의 회원 계정은 서로 다릅니다. 앱의 일반 회원에게 관리 권한을 자동 부여하지 않습니다. 등록한 특정 계정을 관리자로 지정할 때만 상위 폴더의 PowerShell에서 아래 명령을 명시적으로 실행합니다.

```powershell
.\.setup\promote_admin.ps1 -Username 등록된아이디
```

해당 회원은 로그아웃 후 다시 로그인해야 권한이 갱신됩니다. 관리자도 다른 회원의 개인 기록은 볼 수 없습니다.

## DBeaver 연결

상위 폴더의 `start-dbeaver.cmd` → **Database → New Database Connection → MySQL**을 선택합니다.

| Main 탭 항목 | 값 |
| --- | --- |
| Server Host | localhost |
| Port | 3306 |
| Database | review_system |
| Username | review_app |
| Password | 외부 `application.properties`의 `db.password` 값 |

로컬 실습 연결에서 필요한 경우 Driver properties의 `allowPublicKeyRetrieval=true`, `sslMode=DISABLED`, `connectionTimeZone=Asia/Seoul`을 지정하고 Test Connection을 실행합니다. 최초 JDBC 드라이버 다운로드에는 인터넷이 필요합니다. DBeaver는 자체 JRE를 사용하므로 STS의 JDK 15를 변경하지 않습니다.

새 PC의 스키마 적용 순서: 전용 DB·계정 생성 → DBeaver에서 관리자 연결 선택 → `db/schema.sql`을 해당 DB에 실행 → 장르 초기 데이터 입력 → 앱 설정 → 실행. 앱 계정에는 스키마 변경 권한을 부여하지 않습니다. 스키마는 앱 시작 때 실행하지 않으며 기존 데이터를 초기화하지 않습니다.

## Windows 네이티브 재설치

상위 폴더의 `.setup` 스크립트는 Python 3 표준 라이브러리와 PowerShell을 사용합니다. Docker가 필요하지 않습니다.

```powershell
python .setup/install_tools.py
python .setup/configure_tools.py
python .setup/install_database_tools.py
python .setup/configure_database.py
python .setup/configure_runtime.py
.\build.cmd -B -ntp
.\.setup\configure_workspace.ps1
python .setup/configure_runtime.py
```

DB 설치 스크립트는 다른 DB나 Windows 서비스·데이터 폴더를 삭제하지 않습니다. 3306이 사용 중이면 기존 서비스의 설정을 확인하고 전용 DB와 계정을 별도로 만드세요. `mysqld`가 시작되지 않으면 Microsoft Visual C++ x64 런타임 유무와 외부 `mysql-logs/mysql.err`을 확인합니다. 새 PC에서 이미 초기화된 MySQL을 연결하려면 예시 설정을 수정하고 스키마를 수동 적용합니다.

MySQL은 `%USERPROFILE%\dev\review-system\mysql-data`를 쓰는 전용 네이티브 프로세스로 구성했습니다. Windows 서비스 등록이나 PC 부팅 시 자동 시작은 설정하지 않았습니다.

## 시연 순서

1. 가입·로그인 → 작품 찾기 → 없으면 직접 등록, 여러 장르 선택
2. 작품 상세에서 ‘보고 싶음’ 또는 ‘시청 중’ 지정
3. **과거 감상 추가**: 기록만 추가, 현재 상태 유지
4. **완료하고 기록하기**: 기록 추가와 현재 완료 상태를 한 트랜잭션으로 저장
5. **상태만 완료**: 날짜 있는 기록 없이 상태만 완료
6. 같은 작품을 별개 기록으로 재감상 → 조건별 조회·수정·삭제
7. 통계에서 기간 설정 → 건수·작품 수·재감상 구분 → 대표 평점의 원본 기록 이동

시연 데이터는 명시적 실행으로 추가했습니다. `demo_viewer` 로그인 정보는 외부 `config/demo-account.properties`에만 있습니다. 임의로 실제 사용자 데이터와 합쳐 초기화하지 않습니다.

```powershell
python .setup/seed_data.py demo
```

2026년 10월 조회 기대값: 기록 4건, 작품 2개, 재감상 작품 2개, 재감상 추가 3회, 미평점 1건. 장르별 대표 평점은 드라마 4.25(n=2), SF 4.50(n=1)입니다. 자세한 SQL은 `db/demo-data.sql`에 있습니다.

## 시험 재현

실제 MySQL 통합시험은 **시험 DB만** 사용합니다. 상위 폴더에서 다음을 실행합니다.

```powershell
.\build.cmd -B -ntp verify "-Dreview.config=$env:USERPROFILE\dev\review-system\config\test.properties"
```

일반 `build.cmd`는 통합시험을 수행하지 않습니다. 시험 설정이 없으면 통합시험은 skip 표시가 나오므로, 빌드 성공을 실제 DB 시험 성공으로 해석하지 마세요. 시험은 DB 이름이 `review_system_test`인지 확인한 뒤 그 DB의 합성 데이터만 초기화합니다.

브라우저 자동 시험은 `.setup/browser_checks.cjs`(새 실행 증거는 docs/evidence/browser-실행일에 저장, 미래 날짜는 서버 오늘의 다음 날, 실제 비율 문자열 검사 포함), 성능 데이터 생성은 `.setup/seed_data.py performance`, 성능 브라우저 측정은 `.setup/performance_checks.cjs`입니다. 브라우저 시험에는 Node·Playwright·Chrome이 추가로 필요하며 앱 실행 자체에는 필요하지 않습니다. 성능 데이터 생성은 `review_system_perf`만 초기화합니다. 성능 서버는 `.setup/server_control.py start --performance`로 8081/8006 포트에 실행합니다.

실제 수행 결과·미실행 항목: [검증 결과](docs/verification.md). 구조·ERD·URL·통계 계산 규칙: [설계](docs/design.md). 요구사항과 코드·시험 연결: [구현 상태](docs/requirements.md).

후속 신규 사용자 탐색 결과: [사용자 관점 종합 분석](docs/사용자_관점_종합분석.md). 30개 시나리오와 화면 증거, 개선 우선순위를 기록했습니다. 이는 2026-10-13 수정 전 관찰입니다. **비율 오류와 F02–F08은 v0.1.1에서 수정·검증했습니다.** 당시 분석은 그대로 보존하고 새 결과를 위 수정 문서에 연결합니다.

## 조건부 기능과 운영 전 결정

게시판·댓글·파일 첨부는 적용 범위 미확정으로 유보했습니다. TMDB는 API 키가 없어 호출·약관 적용 검증을 수행하지 않았으며 연동 성공으로 표시하지 않습니다. 인터넷·TMDB 키가 없어도 등록된 로컬 작품과 기록·조회·통계는 동작합니다.

이번 구현은 과제용 로컬 실행 범위입니다. 실제 HTTPS 배포와 Secure 쿠키·TLS DB 연결은 미실행입니다. 실제 운영 전 개인정보 보관기간·삭제 담당 연락처·백업 주기와 조회·통계 성능 허용 기준을 팀이 확정해야 합니다. 외부 배포·Git push·제출 문서 변경은 수행하지 않았습니다.
