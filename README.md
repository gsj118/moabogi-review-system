# 모아보기 — 영화·드라마 감상 기록 및 개인 취향 분석

정보시스템구축및관리 2026학년도 2학기 팀 E 프로젝트입니다.
회원별 감상 기록·재감상 이력·현재 시청 상태·평점과 감상평·감상 활동 및 장르별 통계를 제공합니다.

## 시작하기

- [프로젝트 실행·DB 설정](review-system/README.md)
- [팀원 인계와 현재 업무 분담](review-system/docs/team-handover.md)
- [요구사항과 구현 연결](review-system/docs/requirements.md)
- [구조·데이터·저장/통계 규칙](review-system/docs/design.md)
- [기존 검증 결과와 미검증 항목](review-system/docs/verification.md)
- [Redmine 프로젝트](http://114.70.92.70:3000/projects/e-team-2026)
- [협업 Wiki](http://114.70.92.70:3000/projects/e-team-2026/wiki)

## 실행 환경

Spring Legacy MVC 5.3 / Java 8 문법·JDK 15 검증 / Maven / Tomcat 9 / MyBatis / MySQL 8.4 / JSP를 사용합니다.
애플리케이션 Maven 프로젝트는 `review-system/`에 있습니다.

새 PC에서는 프로젝트 실행 문서의 설치·환경 생성 순서를 먼저 따르세요.
Windows 실행 도구는 사용자별 `%USERPROFILE%\dev\review-system` 경로를 사용합니다.
설치된 도구와 외부 DB 설정이 준비되면 저장소 최상위에서 다음 순서로 실행합니다.

```bat
start-db.cmd
start-server.cmd
```

접속 주소: http://localhost:8080/
DB 비밀번호는 저장소 밖에 두고 `review-system/config/application.properties.example`을 참고하세요.
일반 Maven 빌드와 실제 MySQL 통합시험은 구분하며, 통합시험은 전용 `review_system_test` DB에서만 실행합니다.

## 팀 분담과 현재 기준

2026-11-03 기준 구서준은 평점·감상평·활동/장르 통계 및 공통 UI·PM,
고현진은 감상 기록·재감상 이력·현재 상태, 조우성은 회원·인증·작품·조회 및 교차검토를 담당합니다.
최신 담당·일감·일정은 Redmine과 인계 문서의 맨 위 절을 따릅니다.

UC 10개의 명세 작성·등록은 완료됐으며 내용 교차검토·기능 시험·최종 인수는 별도입니다.
첨부·게시판·외부 검색은 적용/유보 결정 대기입니다.
이 저장소의 최초 업로드는 코드·문서 공유이며 팀원 접근 확인이나 수업 제출 완료를 뜻하지 않습니다.
문서의 과거 날짜에 적힌 배포·Git push 미수행은 당시 이력입니다.

## Doxygen 클래스 설계 — 2026-11-17

PM `8fa730d`의 Java 25파일·26타입과 별도 QA_JO `dfc9a33`의 Java 26파일·29타입을 각각 문서화했습니다. 타입 수에는 인터페이스·중첩 클래스·enum을 포함합니다. QA_JO는 PM이나 main에 병합하지 않았습니다.

[설계 Wiki](http://114.70.92.70:3000/projects/e-team-2026/wiki/설계_문서)에 전체 관계도, 클래스 일감 #215~#243, 실제 Doxygen 캡처 64장과 문서·소스 ZIP을 모았습니다. [추적성표](http://114.70.92.70:3000/projects/e-team-2026/wiki/요구사항_추적성표)에는 클래스와 요구사항·유스케이스의 관련 일감 연결 71개를 반영했습니다. 클래스 일감의 완료 범위는 문서 작성·확인이며 기능 인수나 팀원 독립 검토와 구분합니다.

조우성의 QA_JO 업로드를 확인했습니다. 외부 검색·가져오기 코드는 QA 브랜치에 있으며 영화 상세 필드 선택, 외부 ID 저장, 장르 ID 매핑의 보완과 실제 연계 시험이 남아 있습니다. main은 PM만 사용하고 담당자는 자기 브랜치에서 작업하며, 사용자의 별도 지시 전에는 병합하지 않습니다.

Python 3.10 이상·Doxygen·Graphviz가 설치된 환경에서 저장소 최상위의 다음 명령으로 재현합니다. QA 문서 전용 주석 원본도 저장소에 포함했습니다. 원본 소스 준비와 검증만 하려면 `-PrepareOnly`를 사용하며 이 경우 Doxygen·Graphviz는 필요하지 않습니다. 새 PC의 준비 조건과 실행 예시는 [문서 생성 안내](review-system/docs/doxygen/README.md)를 참고하세요.

```powershell
.\generate-doxygen.ps1
```

생성 후 `output/doxygen-design/pm/html/index.html`·`qa/html/index.html`을 열면 됩니다. XML과 주석 패치도 같은 출력 폴더에 생성됩니다. 출력 폴더는 Git에 포함하지 않으며 게시본 ZIP은 위 설계 Wiki에서 내려받습니다. `source-validation.json`에서 51파일의 원본 실행 토큰 일치와 Git ref 유지를, `type-inventory.json`에서 PM 26·QA 29타입과 설명 누락 0을 확인했습니다. 문자열·문자 리터럴 내용은 그대로 비교합니다. 이번 문서화 작업에서는 실제 TMDB API 호출과 DB 통합시험을 실행하지 않았습니다.

## 포함 범위

애플리케이션 소스·시험 코드·DB 스키마·설정 예시·실행/검증 도구·설계/인계 문서,
팀 제안요청서·1차 회의록·일정표, 최신 피드백 반영 기록과 일부 검증 증거를 포함합니다.
실제 계정/DB 설정·DB 데이터·설치 도구·빌드 결과·캐시·중복 ZIP·제3자 원본 수집 자료는 제외합니다.
일부 과거 증거 상대 링크는 로컬 보관 자료를 가리켜 저장소에서는 열리지 않을 수 있습니다.
기존 2026-10-16 검증 결과를 보존하며 최초 업로드를 위해 애플리케이션 시험을 새로 실행하지 않았습니다.
