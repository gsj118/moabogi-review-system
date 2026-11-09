# 기술 스택 및 적용 영역 (Usetech.md)

이 문서는 영화·드라마 감상 리뷰 시스템에서 사용하는 기술 스택과 각 기술의 적용 영역을 정리합니다.

## 1. 언어 및 컴파일

| 기술 | 버전 | 적용 영역 |
|------|------|---------|
| **Java** | 소스: 1.8, JDK: 15.0.2 | 백엔드 애플리케이션 코드 작성 및 컴파일 |
| **UTF-8** | 표준 | 소스 코드, JSP, HTML, CSS, HTTP 요청/응답 인코딩 |

**상세:**
- 소스/타겟 컴파일 설정은 Java 8로 유지 (하위호환성)
- JDK 15로 검증하여 최신 Java 버전과의 호환성 확인
- 모든 문자열 처리는 UTF-8로 통일 (한글 지원)
- 비밀번호 해시는 UTF-8 72바이트 한계 내에서 처리

## 2. 웹 프레임워크

| 기술 | 버전 | 적용 영역 |
|------|------|---------|
| **Spring Framework** | 5.3.39 (BOM) | 의존성 주입, 트랜잭션, 보안 |
| **Spring MVC** | 5.3.39 | 요청 라우팅, 컨트롤러, 뷰 렌더링 |
| **Spring Security** | 5.8.16 | 인증(로그인), 인가(권한), CSRF 보호, 세션 관리 |
| **Spring JDBC** | 5.3.39 | 데이터베이스 연결, 트랜잭션 관리 |

**적용 영역:**
- **컨트롤러**: `AuthController`, `WorkController`, `RecordController`, `StatsController` - HTTP 요청 처리 및 응답
- **서비스**: `AccountService`, `WorkService`, `RecordService`, `StatsService` - `@Transactional` 트랜잭션 관리
- **인증**: `SecurityConfig` - 로그인 폼, 권한 검사, CSRF 토큰, 세션 고정 공격 방지
- **세션**: 로그인 상태 유지, 로그아웃 시 세션 무효화

## 3. 데이터 접근 및 ORM

| 기술 | 버전 | 적용 영역 |
|------|------|---------|
| **MyBatis** | 3.5.19 | SQL 매핑, 쿼리 작성 |
| **MyBatis-Spring** | 2.1.2 | Spring 통합, 자동 트랜잭션 관리 |
| **MySQL Connector/J** | 8.4.0 | MySQL 데이터베이스 드라이버 |
| **HikariCP** | 4.0.3 (Java 8용) | 커넥션 풀 관리 |

**적용 영역:**
- **SQL 매핑**: `src/main/resources/mapper/` XML 파일에서 동적 SQL 쿼리 정의
  - `AccountMapper.xml` - 회원 조회, 등록, 비밀번호 검증
  - `WorkMapper.xml` - 작품 검색, 필터링, 장르 관리
  - `RecordMapper.xml` - 감상 기록 CRUD, 필터링, 정렬, 페이지네이션
  - `StatsMapper.xml` - 통계 집계, 기간별 분석
- **쿼리 특징**:
  - 복잡 필터링: WHERE 동적 조건 (제목, 유형, 장르, 날짜 범위)
  - 윈도우 함수: `ROW_NUMBER() OVER` 재감상 순번 계산
  - CTE: WITH 절을 사용한 감상 기록 순위 지정
  - JOIN: 작품, 장르, 사용자 상태 조인
  - 집계: GROUP_CONCAT, COUNT, MAX, AVG 등

## 4. 데이터베이스

| 기술 | 버전 | 적용 영역 |
|------|------|---------|
| **MySQL Community Server** | 8.4.11 LTS | 데이터 저장소 |
| **InnoDB** | 기본 엔진 | 트랜잭션, ACID 준수, 외래 키 제약 |
| **utf8mb4** | 문자셋 | 한글 및 이모지 지원 |

**데이터베이스 구조:**
```sql
-- 회원 관리
app_user (id, username, password_hash, name, email, role, created_at)

-- 공용 작품 정보
work (id, title, media_type, release_year, synopsis, source, external_id, created_by)
genre (id, name)
work_genre (work_id, genre_id) -- 복합 PK

-- 개인 감상 기록
viewing_record (id, user_id, work_id, viewed_on, rating, review, version, created_at, updated_at)
user_work_state (user_id, work_id, status, updated_at) -- 복합 PK, 현재 시청 상태

-- 폼 제출 보호
record_submission (user_id, request_token, request_hash, record_id, created_at) -- 복합 PK
```

**인덱싱:**
- `(user_id, viewed_on, created_at, id)` - 감상 기록 목록 조회
- `(user_id, work_id, viewed_on, created_at, id)` - 작품별 전체 이력
- `(genre_id, work_id)` - 장르별 작품 역조회
- `(user_id, status)` - 상태별 작품 조회

## 5. 보안

| 기술 | 버전/설정 | 적용 영역 |
|------|---------|---------|
| **Spring Security** | 5.8.16 | 인증/인가, 세션 보안 |
| **BCrypt** | cost=12 | 비밀번호 해싱 |
| **CSRF Protection** | Spring 기본값 | POST/PUT/DELETE 요청 보호 |
| **Content Security Policy** | `default-src 'self'` | XSS 방지 |
| **HttpOnly Cookie** | JSESSIONID | JavaScript 접근 차단 |
| **Session Fixation** | `changeSessionId()` | 로그인 후 세션 ID 갱신 |

**적용 시나리오:**
- **로그인** [SFR-001]
  - 아이디/비밀번호 검증: `AccountService.loadUserByUsername()`
  - 비밀번호 비교: BCrypt 해시값 일치 여부 확인
  - 성공 시 세션 ID 갱신 및 권한 로드
  - 실패 시 원인 노출 금지 ("아이디 또는 비밀번호를 확인하세요")

- **회원가입** [SFR-002]
  - 비밀번호 정책: 8자 이상, 특수문자 포함, UTF-8 72바이트 이내
  - 아이디 중복 검사: `AccountService.register()` 시 DB 조회
  - 이메일 형식 검증: 정규식
  - 비밀번호 평문 제외: 요청/응답/로그에서 제거

- **기록 변경** [SFR-010]
  - 작성자 확인: `Current.id(auth)` 통해 principal 기반 소유권 검증
  - CSRF 토큰: 모든 폼에 포함
  - 요청 UUID: 중복 제출 방지 (record_submission 테이블)
  - 쿠키 설정: HttpOnly, SameSite=Strict

## 6. 뷰 및 프론트엔드

| 기술 | 버전 | 적용 영역 |
|------|------|---------|
| **JSP** | 2.3 | 서버 사이드 뷰 렌더링 |
| **JSTL** | 1.2 | 조건문, 반복, URL 인코딩 |
| **Spring Security Tag** | 5.8.16 | 권한 기반 콘텐츠 표시 |
| **CSS** | 커스텀 | 반응형 디자인, 모바일 지원 |
| **JavaScript** | 바닐라 JS | 폼 제출 진행, DOM 조작 |

**JSP 파일 구조:**
- `/views/login.jsp` - 로그인 폼 [SFR-001]
- `/views/signup.jsp` - 회원가입 폼, 개인정보 수집 공지 [SFR-002]
- `/views/works.jsp` - 작품 검색·필터 [SFR-005]
- `/views/work-form.jsp` - 작품 직접 등록 [SFR-005]
- `/views/records.jsp` - 감상 기록 목록, 필터·정렬·페이지 [SFR-010]
- `/views/common/filter.jspf` - 공유 필터 UI (제목, 유형, 장르, 날짜)
- `/views/common/pagination.jspf` - 15건 단위 페이지네이션

**CSS 특징:**
- 모바일 우선 (320px, 390px, 1440px 반응형)
- 긴 텍스트 자동 줄바꿈 (잘라내기 없음)
- 다크/라이트 테마 지원

**JavaScript:**
- `data-progress` 속성: 폼 제출 중복 클릭 방지
- `sessionStorage`: 작성 중 폼 임시 저장 (선택사항)
- 키보드 접근성: Tab, Enter 키 지원

## 7. 입출력 및 검증

| 기술 | 적용 영역 |
|------|---------|
| **Spring Validation** | `@ModelAttribute`, `BindingResult` |
| **정규식** | 아이디, 이메일, 정렬 기준 검증 |
| **날짜 파싱** | `LocalDate.parse()` YYYY-MM-DD 형식 |
| **Decimal** | 평점 0.5~5.0 범위, 0.5 간격 검증 |

**검증 항목:**

- **회원가입** [SFR-002]
  ```
  아이디: [a-zA-Z0-9_]{4,40}
  비밀번호: 8자 이상, 특수문자 포함, UTF-8 72바이트 이내
  이름: 1~60자
  메일: 표준 이메일 형식
  ```

- **작품 등록** [SFR-005]
  ```
  제목: 1~200자 (필수)
  유형: MOVIE 또는 TV (필수)
  연도: 1888~2100 (선택)
  소개: 1,000자 이내 (선택)
  장르: 최대 20개 (선택)
  ```

- **감상 기록** [SFR-010]
  ```
  감상일: YYYY-MM-DD 형식, 오늘 이전 (필수)
  평점: NULL(미평점) 또는 0.5~5.0 범위, 0.5 간격 (선택)
  감상평: 4,000자 이내 (선택)
  ```

- **조회 필터** [SFR-010]
  ```
  제목: 200자 이내 (SQL LIKE 이스케이프)
  유형: MOVIE, TV 또는 빈 값
  장르: 등록된 장르 ID
  시작일~종료일: YYYY-MM-DD, 시작 ≤ 종료
  정렬: recent, oldest, rating
  페이지: 1 이상의 정수
  ```

## 8. 트랜잭션 및 동시성

| 기술 | 적용 영역 |
|------|---------|
| **@Transactional** | 서비스 메서드 ACID 보장 |
| **MySQL ACID** | 감상 기록 저장·수정·삭제 원자성 |
| **Version** | 감상 기록 낙관적 락 (수정 시 버전 확인) |
| **UUID + Hash** | 폼 재제출 중복 방지 |
| **INSERT IGNORE** | record_submission 동시 삽입 안전성 |

**트랜잭션 예시:**
```
COMPLETE 동작: 기록 INSERT → 상태 UPSERT → 제출 결과 저장
PAST 동작: 기록 INSERT → 제출 결과 저장 (상태 변경 없음)
수정: 버전 확인 후 UPDATE (버전 불일치 시 409 conflict)
삭제: 소유권 확인 후 DELETE
```

## 9. 빌드 및 배포

| 기술 | 버전 | 적용 영역 |
|------|------|---------|
| **Maven** | 3.9.9 | 의존성 관리, 프로젝트 빌드 |
| **Maven Compiler** | 3.11.0 | Java 컴파일 |
| **Maven War** | 3.4.0 | WAR 패키징 |
| **Maven Surefire** | 3.2.5 | 단위 테스트 실행 |
| **Maven Failsafe** | 3.2.5 | 통합 테스트 실행 |
| **Tomcat** | 9.0.123 | 애플리케이션 서버 |
| **Java Servlet** | 3.1 | 웹 컨테이너 표준 |

**빌드 명령:**
```bash
# 기본 빌드
mvn clean package

# 통합 테스트 포함
mvn verify

# WAR 생성 위치
review-system/target/ROOT.war
```

**배포:**
- Tomcat 9에 ROOT.war로 배포 (루트 경로 /)
- 외부 `application.properties` 설정으로 DB 연결
- `JAVA_HOME`, `CATALINA_HOME`, `CATALINA_BASE` 환경변수 지정

## 10. 로깅 및 모니터링

| 기술 | 버전 | 적용 영역 |
|------|------|---------|
| **SLF4J** | 1.7.36 | 로깅 파사드 |
| **Logback** | 1.2.13 | 로깅 구현 |

**로깅 설정:**
- 파일: `src/main/resources/logback.xml`
- 레벨: INFO (프로덕션), DEBUG (개발)
- 로그 제외: 비밀번호, 인증 토큰, 민감 정보

**헬스 체크:**
```
GET /health
응답: "OK - Spring 5.3.39, Java 15.0.2"
```

## 11. 테스트

| 기술 | 버전 | 적용 영역 |
|------|------|---------|
| **JUnit** | 4.13.2 | 단위 테스트 프레임워크 |
| **Spring Test** | 5.3.39 | Spring 컨텍스트 테스트 |
| **Spring Security Test** | 5.8.16 | 인증 테스트 |
| **MyBatis** | 3.5.19 | SQL 테스트 |

**테스트 위치:**
- `src/test/java/org/mnu/` - 통합 테스트
  - `ReviewSystemIT.java` - 26개 회원/기록/상태 시나리오
  - `StatsServiceTest.java` - 통계 집계 검증

**테스트 실행:**
```bash
# 단위 테스트 (DB 불필요)
mvn test

# 통합 테스트 (review_system_test 사용)
mvn verify -Dreview.config=<path>/test.properties
```

**브라우저 자동화 테스트:**
- Node.js + Playwright + Chrome
- `.setup/repov_browser_checks.cjs` - 80개 기능 시나리오
- `.setup/handover_checks.cjs` - 필터/페이지 보존 검증
- 결과: `review-system/docs/evidence/` 저장

## 12. 개발 환경

| 도구 | 버전 | 용도 |
|------|------|------|
| **Spring Tool Suite (STS)** | 3.9.18 | IDE, Tomcat 통합 실행 |
| **Eclipse** | 4.21 | IDE 기본 설정 |
| **DBeaver** | 26.2.2 | 데이터베이스 클라이언트 (선택) |
| **PowerShell** | 7+ | 자동화 스크립트 (Windows) |
| **Python** | 3+ | 설치/구성 스크립트 |

**STS 설정:**
- Workspace: `dev/review-system/sts-bundle/workspace`
- Tomcat: v9.0.123 로컬 서버
- JDK: 15.0.2
- JSP Encoding: UTF-8
- 프로젝트 Facet: Dynamic Web Module 3.1, Java 1.8

## 13. SFR 기능별 기술 매핑

### [SFR-001] 로그인/로그아웃

| 계층 | 기술 | 코드 |
|------|------|------|
| 컨트롤러 | Spring MVC | `AuthController.login()`, `logout()` (Security 자동) |
| 서비스 | Spring Security | `AccountService.loadUserByUsername()` |
| 보안 | BCrypt | `passwordEncoder().matches()` |
| 설정 | Spring Security Config | `SecurityConfig.configure(HttpSecurity)` |
| 뷰 | JSP | `login.jsp` |
| 데이터 | MySQL | `app_user.password_hash` |

### [SFR-002] 회원가입

| 계층 | 기술 | 코드 |
|------|------|------|
| 컨트롤러 | Spring MVC | `AuthController.register()` |
| 서비스 | Spring | `AccountService.register()` (@Transactional) |
| 검증 | 정규식 + 비즈니스로직 | 아이디, 비밀번호, 이메일 검증 |
| 보안 | BCrypt | 비밀번호 해싱 |
| 뷰 | JSP | `signup.jsp` + 개인정보 수집 공지 |
| 데이터 | MySQL | `app_user` 테이블 INSERT |
| 예외 | Spring | `InputError`, `DuplicateKeyException` 처리 |

### [SFR-005] 작품 검색·등록

| 계층 | 기술 | 코드 |
|------|------|------|
| 컨트롤러 | Spring MVC | `WorkController.list()`, `create()`, `detail()` |
| 서비스 | Spring + MyBatis | `WorkService` 필터/검증 |
| 필터 | QueryFilter | 제목(LIKE), 유형, 장르 |
| 관리 | Spring Security | 관리자만 수정/삭제 |
| 뷰 | JSP | `works.jsp`, `work-form.jsp`, `work-detail.jsp` |
| 데이터 | MySQL | `work`, `genre`, `work_genre` 테이블 |
| SQL | MyBatis XML | `WorkMapper.xml` 동적 쿼리 |

### [SFR-010] 조건별 조회·정렬·페이지

| 계층 | 기술 | 코드 |
|------|------|------|
| 컨트롤러 | Spring MVC | `RecordController.list()` |
| 서비스 | Spring | `RecordService.list()`, 필터 검증 |
| 필터 | QueryFilter | 제목, 유형, 장르, 날짜 범위, 정렬, 페이지 |
| 정렬 | 비즈니스 로직 | 감상일→생성시각→ID (내림차순 기본) |
| 페이지 | Java Math | 15건/페이지, LIMIT/OFFSET |
| SQL | MyBatis + MySQL | `ROW_NUMBER()` 재감상 순번, DISTINCT 중복 제거 |
| 뷰 | JSP + JSTL | `records.jsp`, `common/filter.jspf`, `pagination.jspf` |
| 보안 | Spring Security | 사용자 ID 기반 소유권 필터 |

### [SFR-013] TMDB 외부 작품 검색·선택

| 계층 | 기술 | 코드 |
|------|------|------|
| 컨트롤러 | Spring MVC | `WorkController.searchExternal()`, `importExternal()` |
| 서비스 | Spring | `WorkService.searchExternal()`, `getExternalDetails()` |
| API 클라이언트 | Spring RestTemplate | `TmdbClient.search()`, `getDetails()` |
| JSON 파싱 | Jackson 2.15.3 | ObjectMapper로 응답 처리 |
| HTTPS | RestTemplate | HTTPS 자동 지원 |
| 에러 처리 | 커스텀 예외 | `TmdbException` (ErrorType 구분) |
| 뷰 | JSP | `search-external.jsp`, `works.jsp` 링크 |
| 데이터 | MySQL | `work` 테이블 (source, external_id) |
| 설정 | application.properties | `tmdb.api.key` (서버 관리) |

## 14. 성능 최적화

| 기술 | 적용 영역 |
|------|---------|
| **HikariCP** | 커넥션 풀 (기본 10개) |
| **인덱싱** | 조회 성능 (user_id + 정렬 조건) |
| **ROW_NUMBER()** | 윈도우 함수로 중복 제거 |
| **페이지네이션** | 15건 단위 지연 로딩 |
| **CTE** | 재감상 순번 계산 최적화 |
| **LEFT JOIN** | 상태 없는 작품도 조회 가능 |

**성능 기준:**
- 입력 검증: 3초 이내
- 조회 응답: 설계 단계 측정 기준 (1,000개 작품, 10,000개 기록 규모)

## 15. 외부 API 및 통합

| 기술 | 상태 | 적용 영역 |
|------|------|---------|
| **TMDB (The Movie Database)** | ✓ 구현 | [SFR-013] 외부 작품 검색·선택 |
| **Jackson (JSON)** | 2.15.3 | TMDB API JSON 파싱 |
| **로컬 작품 DB** | ✓ 구현 | [SFR-005] 공용 작품 관리 |

**TMDB 구현:**
- HTTPS 지원 (RestTemplate 사용)
- API 키 서버 설정 관리
- 검색 (search/movie, search/tv)
- 상세 조회 (/{media_type}/{id})
- 한국어 검색 (language=ko-KR)
- 에러 처리 (NOT_FOUND, RATE_LIMITED, TIMEOUT, SERVER_ERROR)
- API 장애 시에도 로컬 작품 이용 가능

**위치:**
- `TmdbClient.java` - API 클라이언트
- `search-external.jsp` - 검색 UI
- `WorkService.searchExternal()` - 검색 서비스

---

## 기술 선택 근거

1. **Spring Legacy MVC**: 수업 가이드라인, 전통적 엔터프라이즈 패턴
2. **MyBatis**: 명시적 SQL 제어, 복잡한 쿼리 최적화 필요
3. **MySQL 8.4 LTS**: 장기 지원, 안정성, 한글 지원
4. **BCrypt**: 비밀번호 해싱 표준, 비용 인자(12) 조절 가능
5. **JSP**: 서버 사이드 렌더링, SEO 이점
6. **Spring Security**: 표준 인증/인가, CSRF 보호 자동화

---

마지막 갱신: 2026-11-10
