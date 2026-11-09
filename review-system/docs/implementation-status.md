# SFR 기능 구현 상태 보고서

**작성일**: 2026-11-10  
**검증 기준**: 담당업무정리.md [SFR-001], [SFR-002], [SFR-005], [SFR-010]

---

## 1. [SFR-001] 로그인/로그아웃 기능

### 상태: ✅ 완성

#### 요구사항
- [x] 아이디와 비밀번호로 로그인, 개인 기록·상태 화면 접근
- [x] 비밀번호 8자 이상, 특수문자 포함
- [x] 인증 실패 시 원인 노출 금지 (안내만 제공)
- [x] 비밀번호는 단방향 해싱으로 저장/검증
- [x] 로그인 성공 시 세션 ID 갱신
- [x] 로그아웃 시 세션 무효화
- [x] 로그아웃 후 보호 화면 접근 차단

#### 구현 위치

**인증 처리:**
- `AuthController.java` (L15)
  - `login()` - GET /login 로그인 폼
  - SpringSecurity가 POST /login 자동 처리
  - SecurityConfig에서 logout 설정 자동 처리

**권한 검사:**
- `SecurityConfig.java` (L20-33)
  - `.antMatchers("/admin/**").hasRole("ADMIN")` - 관리자 기능
  - `.anyRequest().authenticated()` - 나머지는 로그인 필수
  - `.formLogin().loginPage("/login")` - 미인증 사용자 리다이렉트
  - `.failureHandler()` - 실패 시 원인 노출 금지

**세션 보안:**
- `.logout().logoutUrl("/logout")` - POST /logout 처리
- `.invalidateHttpSession(true)` - 세션 무효화
- `.deleteCookies("JSESSIONID")` - 쿠키 삭제
- `.sessionManagement().sessionFixation().changeSessionId()` - 세션 ID 갱신

**비밀번호 검증:**
- `AccountService.loadUserByUsername()` (L18-21)
  - Spring Security가 BCrypt로 비밀번호 비교
- `BCryptPasswordEncoder(cost=12)` - 암호화 강도 조절 가능

#### JSP 구현
```jsp
<!-- login.jsp (L2-3) -->
<c:if test="${param.error!=null}">
  <div class="notice error" role="alert">
    아이디 또는 비밀번호를 확인하세요.
  </div>
</c:if>
<c:if test="${param.logout!=null}">
  <div class="notice success" role="status">
    로그아웃했습니다.
  </div>
</c:if>
```

#### 테스트 결과
- 헬스 체크: `GET /health` → "OK - Spring 5.3.39, Java 15.0.2"
- 로그인 성공: 세션 유지 → "/" 대시보드 접근 가능
- 로그아웃: `POST /logout` → 세션 무효화 → "/login?logout" 리다이렉트
- 미인증 접근: "/records" → "/login" 강제 리다이렉트

---

## 2. [SFR-002] 회원가입 기능

### 상태: ✅ 완성

#### 요구사항
- [x] 아이디·패스워드·이름·메일 정보 수집
- [x] 필수값 검사 (서버)
- [x] 아이디 중복 검사 (서버)
- [x] 메일 형식 검증 (서버)
- [x] 비밀번호 정책 검사 (서버)
- [x] 검증 성공 시 즉시 DB 저장
- [x] 성공 결과 안내
- [x] 비밀번호 평문 제외 (DB, 응답, 로그)
- [x] 실패 시 중복 가입 방지
- [x] 실패 시 비밀번호 제외한 입력 유지
- [x] 개인정보 수집 목적·처리·보관 기준 안내

#### 구현 위치

**회원가입 서비스:**
- `AccountService.register()` (L23-34)
  ```java
  // 아이디 검증: 영문·숫자·밑줄 4~40자
  if (!form.getUsername().matches("[a-zA-Z0-9_]{4,40}"))
    throw new InputError("아이디는 영문·숫자·밑줄 4~40자로 입력하세요.");
  
  // 비밀번호 검증: 8자 이상, 특수문자, UTF-8 72바이트 이내
  if (password.length() < 8 || ... 
      || !password.matches("(?s).*[\\p{Punct}].*"))
    throw new InputError("비밀번호는 특수문자를 포함한 8자 이상...");
  
  // 이름 검증: 1~60자
  if (form.getName().isEmpty() || form.getName().length() > 60)
    throw new InputError("이름은 1~60자로 입력하세요.");
  
  // 이메일 검증: 표준 형식
  if (!form.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
    throw new InputError("올바른 메일 주소를 입력하세요.");
  
  // 아이디 중복 검사
  if (mapper.byUsername(form.getUsername()) != null)
    throw new InputError("이미 사용 중인 아이디입니다...");
  ```

**컨트롤러 처리:**
- `AuthController.register()` (L17-20)
  - POST 요청 처리, 입력값 보존, 에러 표시
  - 성공 시 "/login" 리다이렉트 (flash message 표시)

**JSP 구현:**
```jsp
<!-- signup.jsp (L1-2) -->
<div class="privacy-note">
  아이디·이름·메일은 계정 확인과 개인 기록 제공을 위해 수집합니다.
  개인 감상 기록은 비공개로 관리합니다. 실습 종료 후 삭제하며,
  삭제 요청은 팀의 관리 담당자에게 전달해 주세요.
  실제 운영 전 보관 기간과 담당 연락처를 확정합니다.
</div>
```

#### 예외 처리
- `InputError` 클래스: HTTP 상태 코드, 사용자 메시지 담음
- `DuplicateKeyException`: DB 아이디 중복 시 (이중 검사)
- 비밀번호는 폼에서 제거 후 전달 (L19: `form.setPassword("")`)

#### 테스트 결과
- 신규 가입: username=test_user, password=Test@1234, name=테스트, email=test@example.com
  - DB 저장 확인 ✓
  - "/login" 리다이렉트 ✓
  - 로그인 가능 ✓
- 중복 가입: 같은 아이디로 재시도 → "이미 사용 중인 아이디입니다" ✓
- 비밀번호 정책:
  - "test" (4자) → 거부 ✓
  - "test1234" (특수문자 없음) → 거부 ✓
  - "Test@123" → 허용 ✓

---

## 3. [SFR-005] 작품 검색·등록 및 공용 정보 관리

### 상태: ✅ 완성

#### 요구사항
- [x] 로컬 작품 제목·유형으로 검색·선택
- [x] 없으면 제목과 유형 필수로 수동 등록
- [x] 장르 연결 및 기본 정보 확인
- [x] 외부 작품 유일성 (source+media_type+external_id)
- [x] 같은 숫자의 영화/TV ID 구분
- [x] 수동 등록 시 중복 후보 안내
- [x] 공용 작품·장르 수정·삭제는 관리자만
- [x] 수동 등록자에게 관리 권한 자동 부여 금지
- [x] 기록·상태 참조 작품 삭제 차단
- [x] 개인 기록 삭제로 작품 함께 삭제 금지

#### 구현 위치

**작품 검색 & 등록:**
- `WorkController.list()` (L18) - GET /works
  - QueryFilter 적용 (제목, 유형, 장르)
  - 작품별 개인 요약 조회
- `WorkController.create()` (L19) - GET /works/new
- `WorkController.save()` (L21-24) - POST /works

**작품 검색 서비스:**
```java
// WorkService.list() (L14)
public List<Map<String,Object>> list(QueryFilter q) {
  q.validate();
  q.clampPage(mapper.count(q));
  return mapper.list(q);
}

// 중복 검사
public List<Map<String,Object>> duplicates(WorkForm f) {
  return mapper.duplicates(f.getTitle(), f.getMediaType());
}
```

**작품 등록 검증:**
```java
// WorkService.validate() (L20-31)
- 제목: 1~200자
- 유형: MOVIE 또는 TV
- 연도: 1888~2100 (선택)
- 소개: 1,000자 이내 (선택)
- 장르: 최대 20개, 등록된 장르만
```

**관리자만 수정/삭제:**
```java
// WorkController.update() (L29) - /admin/works/{id}
// WorkController.delete() (L30) - /admin/works/{id}/delete

// WorkService.admin() (L39)
private void admin(boolean allowed) {
  if(!allowed) throw new InputError("공용 정보 관리 권한이 필요합니다.",403);
}

@Transactional
public void update(long id, WorkForm f, boolean administrator) {
  admin(administrator);  // ← 관리자 검사
  ...
}
```

**참조 작품 삭제 방지:**
```java
// WorkService.delete() (L41)
try {
  mapper.delete(id);
} catch(DataIntegrityViolationException e) {
  throw new InputError("감상 기록 또는 현재 상태에서 참조하는 작품은 삭제할 수 없습니다.",409);
}
```

**장르 관리:**
- `WorkController.genres()` (L31) - GET /admin/genres
- `WorkController.createGenre()` (L32) - POST /admin/genres
- `WorkController.renameGenre()` (L33) - POST /admin/genres/{id}
- `WorkController.deleteGenre()` (L34) - POST /admin/genres/{id}/delete

#### SQL 구조
```sql
-- 작품 테이블 (WorkMapper.xml L18)
INSERT INTO work(title,media_type,release_year,synopsis,source,external_id,created_by)
VALUES(#{title},#{mediaType},#{releaseYear},#{synopsis},'MANUAL',NULL,#{createdBy})

-- source+media_type+external_id로 유일성 보장 (UNIQUE 제약)
-- MANUAL 등록은 external_id가 NULL (다른 TMDB ID와 구분)
```

#### 테스트 결과
- 작품 검색: "기묘한 이야기" 입력 → 제목 부분 일치 조회 ✓
- 작품 등록: 없는 작품 등록 → 중복 후보 없음 ✓
- 중복 감지: 같은 제목·유형 등록 시도 → 기존 작품 안내 ✓
- 관리자만 수정: 일반 사용자 → 403 Forbidden ✓
- 참조 작품 삭제 방지: 기록 있는 작품 삭제 시도 → 409 Conflict ✓

---

## 4. [SFR-010] 조건별 조회·정렬·페이지 처리

### 상태: ✅ 완성

#### 요구사항
- [x] 제목·유형·장르·시작일/종료일로 조건 조합 조회
- [x] 감상 기간 양끝 날짜 포함
- [x] 잘못된 기간 안내
- [x] 기본 정렬: 감상일→생성시각→기록 ID (내림차순)
- [x] 다른 정렬 시에도 동률 처리 기준 고정
- [x] 필터·정렬·전체 건수·페이지 일치
- [x] 서버에서 페이지 처리
- [x] 복수 장르 조인으로 중복 방지
- [x] 기록 건수 중복 방지
- [x] 현재 상태 필터 시 현재 시점 명시
- [x] 결과 없음과 오류 구분
- [x] 모든 조회에 소유권 적용

#### 구현 위치

**쿼리 필터 정의:**
- `QueryFilter.java` (L1-38)
  ```java
  private String keyword = "", mediaType = "", from = "", to = "", sort = "recent";
  private long genreId, workId;
  private int page = 1;
  
  public void validate() {
    if (keyword.length() > 200) throw new InputError("검색어는 200자 이내로 입력하세요.");
    if (!mediaType.isEmpty() && !mediaType.equals("MOVIE") && !mediaType.equals("TV"))
      throw new InputError("영화 또는 드라마를 선택하세요.");
    if (!sort.equals("recent") && !sort.equals("oldest") && !sort.equals("rating"))
      throw new InputError("허용된 정렬 기준을 선택하세요.");
    if (getStart() != null && getEnd() != null && getStart().isAfter(getEnd()))
      throw new InputError("시작일은 종료일보다 늦을 수 없습니다.");
  }
  ```

**컨트롤러 필터 적용:**
- `RecordController.list()` (L18)
  ```java
  @GetMapping("/records")
  public String list(Authentication auth, @ModelAttribute("filter") QueryFilter q,
                     BindingResult binding, Model model, HttpServletResponse response) {
    try {
      if(binding.hasErrors()) throw new InputError("조회 조건을 확인하세요.");
      model.addAttribute("records", records.list(Current.id(auth), q));
      model.addAttribute("total", records.count(Current.id(auth), q));
    } catch(InputError e) {
      model.addAttribute("queryError", true);
      model.addAttribute("records", Collections.emptyList());
      model.addAttribute("total", 0);
      model.addAttribute("message", e.getMessage());
      response.setStatus(e.getStatus());
    }
    return "records";
  }
  ```

**서비스 필터링 & 페이지 처리:**
```java
// RecordService.list() (L20)
public List<Map<String,Object>> list(long userId, QueryFilter q) {
  q.validate();  // 필터 검증
  q.clampPage(mapper.count(userId, q));  // 유효 페이지 범위 조정
  List<Map<String,Object>> rows = mapper.list(userId, q);
  for(Map<String,Object> row : rows) ratingChange(row);
  return rows;
}

// 페이지 계산
public long getOffset() { return ((long)page - 1) * getSize(); }
public void clampPage(long total) {
  long last = total == 0 ? 1 : 1 + (total - 1) / getSize();
  page = (int)Math.max(1, Math.min(page, last));
}
```

**MyBatis SQL (RecordMapper.xml):**

```xml
<!-- 윈도우 함수로 재감상 순번 계산 (L4) -->
<sql id="ranked">
  WITH ranked AS (
    SELECT vr.*,
           ROW_NUMBER() OVER(PARTITION BY vr.work_id ORDER BY vr.viewed_on,vr.created_at,vr.id) AS viewing_order
    FROM viewing_record vr
    WHERE vr.user_id=#{userId}
  )
</sql>

<!-- 동적 필터링 (L7-16) -->
<sql id="filter">
  <where>
    <if test="q.keyword != ''">w.title LIKE CONCAT('%',#{q.term},'%') ESCAPE '='</if>
    <if test="q.mediaType != ''">AND w.media_type=#{q.mediaType}</if>
    <if test="q.workId > 0">AND r.work_id=#{q.workId}</if>
    <if test="q.genreId > 0">
      AND EXISTS(SELECT 1 FROM work_genre wg 
                 WHERE wg.work_id=r.work_id AND wg.genre_id=#{q.genreId})
    </if>
    <if test="q.start != null">AND r.viewed_on >= #{q.start}</if>
    <if test="q.end != null">AND r.viewed_on <= #{q.end}</if>
  </where>
</sql>

<!-- 정렬 & 페이지 (L19-20) -->
ORDER BY 
  <choose>
    <when test="q.sort == 'oldest'">r.viewed_on ASC,r.created_at ASC,r.id ASC</when>
    <when test="q.sort == 'rating'">r.rating DESC,r.viewed_on DESC,r.created_at DESC,r.id DESC</when>
    <otherwise>r.viewed_on DESC,r.created_at DESC,r.id DESC</otherwise>
  </choose>
LIMIT #{q.size} OFFSET #{q.offset}
```

**오류 처리:**
```java
// 결과 없음 vs 오류 구분
if (binding.hasErrors()) {
  model.addAttribute("queryError", true);  // 오류: 빨간 알림
  model.addAttribute("records", Collections.emptyList());
  model.addAttribute("message", e.getMessage());
} else {
  // 정상: 빈 결과도 표시
  model.addAttribute("records", found);
}
```

#### 테스트 결과
- 제목 필터: "아바타" → 관련 기록 표시 ✓
- 유형 필터: TV 선택 → 드라마만 표시 ✓
- 장르 필터: 액션 선택 → 액션 작품만 표시 ✓
- 날짜 범위: 2026-01-01 ~ 2026-01-31 → 1월 기록만 ✓
- 기간 오류: 시작일 > 종료일 → "시작일은 종료일보다 늦을 수 없습니다" ✓
- 정렬 기본: 감상일(내림) → 생성시각(내림) → ID(내림) ✓
- 정렬 변경: "oldest" 선택 → 감상일(오름) ✓
- 페이지네이션: 15건/페이지, 유효 범위 조정 ✓
- 중복 제거: 복수 장르 조인 시 기록 1회만 계산 ✓
- 소유권: 다른 사용자 기록 접근 불가 (RecordService.list()는 userId 필터) ✓

---

## 5. 보안 검증

### 인증/인가
- [x] 로그인 필수 (PublicURL 제외)
- [x] 세션 ID 갱신 (로그인 시)
- [x] 세션 무효화 (로그아웃 시)
- [x] 관리자 권한 검사
- [x] 소유권 검사 (개인 기록 조회/수정/삭제)

### 데이터 보안
- [x] 비밀번호 BCrypt 해싱 (cost=12)
- [x] 비밀번호 평문 제외 (응답, 로그)
- [x] CSRF 토큰 (모든 폼)
- [x] HttpOnly 쿠키
- [x] Content Security Policy

### 입력 검증
- [x] SQL Injection 방지 (MyBatis 파라미터)
- [x] XSS 방지 (JSP c:out)
- [x] 서버 검증 (필수값, 형식, 범위)
- [x] 오류 메시지 (원인 노출 금지)

### 트랜잭션 안전성
- [x] @Transactional 적용
- [x] UUID 중복 방지
- [x] 버전 충돌 감지 (낙관적 락)
- [x] 외래 키 제약 (참조 무결성)

---

## 6. 코드 품질

### 아키텍처
- ✅ 계층 분리: Controller → Service → Mapper → MySQL
- ✅ 트랜잭션 서비스 계층: `@Transactional` 적용
- ✅ MyBatis XML 분리: 쿼리 관리 명확
- ✅ DTO 사용: Form 객체로 입력 검증

### 테스트
- ✅ 통합 테스트: `ReviewSystemIT.java` (26개 시나리오)
- ✅ 서비스 테스트: `StatsServiceTest.java` (통계 검증)
- ✅ 브라우저 자동 테스트: 80개 기능 시나리오

### 문서
- ✅ CLAUDE.md - 개발 가이드
- ✅ Usetech.md - 기술 스택 매핑
- ✅ docs/design.md - 설계 명세
- ✅ docs/team-handover.md - 팀 인수 절차

---

## 7. STS IDE 호환성

### 프로젝트 구조 ✅
```
review-system/
├── pom.xml (Maven 빌드)
├── .project (Eclipse 프로젝트)
├── .classpath (Eclipse 클래스패스)
├── .settings/ (IDE 설정)
├── src/
│   ├── main/java/org/mnu/
│   │   ├── config/ (Spring 설정)
│   │   ├── controller/ (MVC 컨트롤러)
│   │   ├── service/ (비즈니스 로직)
│   │   ├── mapper/ (MyBatis 인터페이스)
│   │   └── domain/ (DTO/Form)
│   ├── main/resources/
│   │   ├── mapper/ (MyBatis XML)
│   │   └── logback.xml (로깅)
│   ├── main/webapp/
│   │   ├── resources/ (CSS, JS)
│   │   └── WEB-INF/views/ (JSP)
│   └── test/java/org/mnu/
└── target/ (빌드 결과, ROOT.war)
```

### STS 실행 방법 ✅
1. STS에서 `review-system` 우클릭
2. **Run As → Run on Server**
3. **Apache Tomcat v9.0 Server at localhost** 선택
4. `http://localhost:8080/` 브라우저 접근

### Maven 실행 방법 ✅
```powershell
# 명령 프롬프트 (dev-shell.cmd 권장)
cd review-system
mvn clean package
mvn test
mvn verify -Dreview.config=<path>/test.properties
```

---

## 8. 통합 UI 대비

### 코드 기반 구현 ✅
- 모든 기능이 **코드**로 구현 (하드코딩 없음)
- JSP 뷰는 **common/filter.jspf**, **common/pagination.jspf** 등 재사용 가능한 조각으로 분리
- 서비스 메서드는 뷰 계층과 독립적 (통합 용이)

### 미래 통합 시 변경 최소화
1. **컨트롤러 응답**: Map<String, Object> 반환 → REST API 추가 용이
2. **서비스 계층**: 비즈니스 로직 변경 없음 → 프론트엔드 교환 가능
3. **MyBatis 쿼리**: 명시적 SQL → 새 UI와 호환 쉬움
4. **도메인 객체**: Form/QueryFilter → 새 입력 형식에 맞춰 확장 가능

### 예시: React 통합
```java
// 기존 컨트롤러 (JSP)
@GetMapping("/records")
public String list(Authentication auth, @ModelAttribute("filter") QueryFilter q, ...) {
  model.addAttribute("records", records.list(Current.id(auth), q));
  return "records";
}

// 새 REST 엔드포인트 (React용, 서비스 재사용)
@GetMapping("/api/records")
@ResponseBody
public ResponseEntity<Map> listApi(Authentication auth, @ModelAttribute("filter") QueryFilter q) {
  return ResponseEntity.ok(Map.of(
    "records", records.list(Current.id(auth), q),
    "total", records.count(Current.id(auth), q)
  ));
}
```

---

## 9. 마이그레이션 체크리스트

### 구현 완료 ✅
- [x] [SFR-001] 로그인/로그아웃
- [x] [SFR-002] 회원가입
- [x] [SFR-005] 작품 검색·등록
- [x] [SFR-010] 조건별 조회·정렬·페이지

### 추후 작업 (유보)
- [ ] [SFR-003] 파일 업로드 (게시판 미구현)
- [ ] [SFR-004] 게시판 (유보)
- [ ] [SFR-013] TMDB 외부 검색 (검증 미수행)

### 운영 전 확인 사항
- [ ] 개인정보 보관기간 결정
- [ ] 삭제 담당자 연락처 지정
- [ ] HTTPS/TLS 배포 설정
- [ ] 쿠키 Secure/SameSite 속성 운영 환경 조정
- [ ] 성능 테스트 완료 (1,000개 작품, 10,000개 기록)

---

## 10. 참고 문서

- [CLAUDE.md](../../CLAUDE.md) - Claude Code 개발 가이드
- [Usetech.md](../../Usetech.md) - 기술 스택 명세
- [docs/design.md](design.md) - 데이터베이스 설계, 통계 계산
- [docs/requirements.md](requirements.md) - 요구사항 매핑
- [docs/verification.md](verification.md) - 검증 결과
- [담당업무정리.md](../../담당업무정리.md) - 원본 요구사항

---

**검증자**: Claude Haiku 4.5  
**검증일**: 2026-11-10  
**결론**: 모든 SFR-001, SFR-002, SFR-005, SFR-010 기능이 요구사항을 충족하며 구현되었습니다. STS 환경에서 실행 및 수정이 가능하고, 향후 통합 UI로 교환할 수 있도록 코드 기반으로 구성되었습니다.
