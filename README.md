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

## 포함 범위

애플리케이션 소스·시험 코드·DB 스키마·설정 예시·실행/검증 도구·설계/인계 문서,
팀 제안요청서·1차 회의록·일정표, 최신 피드백 반영 기록과 일부 검증 증거를 포함합니다.
실제 계정/DB 설정·DB 데이터·설치 도구·빌드 결과·캐시·중복 ZIP·제3자 원본 수집 자료는 제외합니다.
일부 과거 증거 상대 링크는 로컬 보관 자료를 가리켜 저장소에서는 열리지 않을 수 있습니다.
기존 2026-10-16 검증 결과를 보존하며 최초 업로드를 위해 애플리케이션 시험을 새로 실행하지 않았습니다.
