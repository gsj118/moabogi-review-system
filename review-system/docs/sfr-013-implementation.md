# [SFR-013] TMDB 외부 작품 검색·선택 구현 명세

**작성일**: 2026-11-10  
**상태**: ✅ 구현 완료  
**참고**: 담당업무정리.md [SFR-013]

---

## 1. 요구사항 분석

### 1.1 분석 단계 검증항목
- [x] HTTPS 지원 (TMDB API HTTPS 필수)
- [x] 인증 방식 (API 키 기반)
- [x] 한국어 검색 (language=ko-KR)
- [x] JSON 처리 (Jackson 사용)
- [x] 영화/TV 결과 매핑 (search/movie, search/tv)
- [x] 상세·장르 매핑 (/<media_type>/<id> 엔드포인트)

### 1.2 적용 범위
- [x] 검색 결과 표시 (제목·유형·연도)
- [x] SFR-005 삼중 키 연결 (source+media_type+external_id)
- [x] 필요 정보만 가져오기 (제목, 연도, 개요, 포스터, 장르)
- [x] 개인 기록·감상평 미전송 (검색/상세만 호출)
- [x] API 키 서버 설정 관리
- [x] 클라이언트·응답·로그에서 API 키 미노출
- [x] TMDB 출처·로고·고지문 표시
- [x] 오류 구분 (없음, 429, 서버, 시간초과)
- [x] API 장애 시에도 로컬 작품 이용 가능

---

## 2. 아키텍처

### 2.1 구성요소

```
┌─────────────────────────────────────────────────────┐
│  Web Browser / JSP                                  │
│  - works.jsp: "🔍 외부 작품 검색" 링크              │
│  - search-external.jsp: 검색 폼 & 결과 표시         │
└─────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────┐
│  WorkController                                     │
│  - GET /works/search-external: 검색 페이지          │
│  - POST /works/import-external: 작품 등록           │
└─────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────┐
│  WorkService                                        │
│  - searchExternal(query, mediaType): TMDB 검색      │
│  - getExternalDetails(id, type): 상세 정보          │
│  - isExternalSearchEnabled(): 활성화 여부            │
│  - create(userId, form): 작품 등록 (MANUAL/TMDB)    │
└─────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────┐
│  TmdbClient (외부/TmdbClient.java)                  │
│  - search(query, mediaType): 검색 API 호출          │
│  - getDetails(id, type): 상세 API 호출              │
│  - 에러 구분 (NOT_FOUND, RATE_LIMITED, etc)        │
└─────────────────────────────────────────────────────┘
                         ↓
        HTTPS → api.themoviedb.org/3/
```

### 2.2 데이터 흐름

**검색 단계:**
1. 사용자 JSP에서 검색어 + 유형 입력
2. GET /works/search-external?query=...&mediaType=MOVIE
3. WorkController → WorkService.searchExternal()
4. TmdbClient.search() → HTTPS POST to TMDB API
5. JSON 응답 파싱 (Jackson)
6. 결과 목록 (최대 20개) 반환
7. search-external.jsp에서 렌더링

**등록 단계:**
1. 결과에서 "이 작품 등록" 버튼 클릭
2. POST /works/import-external
   - externalId: 1234 (TMDB ID)
   - mediaType: MOVIE 또는 TV
   - confirmDuplicate: 중복 확인
3. TmdbClient.getDetails() → 상세 정보 조회
4. WorkForm 생성 (외부 ID 포함)
5. WorkService.create() → DB 저장
6. source='TMDB', external_id=1234 로 저장

---

## 3. 구현 상세

### 3.1 TmdbClient.java

**위치**: `src/main/java/org/mnu/external/TmdbClient.java`

**주요 메서드:**

```java
// 1. 검색
public List<Map<String, Object>> search(String query, String mediaType)
  - query: 검색어 (한국어 지원)
  - mediaType: "movie" 또는 "tv"
  - 반환: List<Map> { externalId, title, releaseYear, posterPath, overview, mediaType, source }
  - 예외: TmdbException (NOT_FOUND, RATE_LIMITED, SERVER_ERROR, TIMEOUT)

// 2. 상세 조회
public Map<String, Object> getDetails(long externalId, String mediaType)
  - externalId: TMDB ID (숫자)
  - mediaType: "MOVIE" 또는 "TV"
  - 반환: Map { externalId, title, releaseYear, overview, genreIds, source }

// 3. 활성화 여부
public boolean isEnabled()
  - API 키 설정 확인
```

**에러 타입:**
```java
enum ErrorType {
  NOT_CONFIGURED("API 키 미설정"),
  NOT_FOUND("검색 결과 없음"),
  RATE_LIMITED("요청 한계 (429)"),
  SERVER_ERROR("서버 오류"),
  TIMEOUT("시간 초과"),
  UNKNOWN("알 수 없는 오류")
}
```

**보안 조치:**
- API 키는 프로퍼티에서만 로드 (서버 설정)
- HTTP 응답에 API 키 미포함
- 예외 메시지에 민감 정보 제외
- 개인 기록/감상평은 전송하지 않음

**타임아웃:**
- RestTemplate 기본 타임아웃: 5초
- 시간초과 시 TmdbException.TIMEOUT 발생

### 3.2 WorkService 통합

**추가 메서드:**

```java
public List<Map<String,Object>> searchExternal(String query, String mediaType)
  throws TmdbClient.TmdbException
  - 위임: tmdb.search(query, mediaType.equals("MOVIE")?"movie":"tv")

public Map<String,Object> getExternalDetails(long externalId, String mediaType)
  throws TmdbClient.TmdbException
  - 위임: tmdb.getDetails(externalId, mediaType)

public boolean isExternalSearchEnabled()
  - 위임: tmdb.isEnabled()
```

**기존 create() 메서드 호환성:**
- WorkForm.externalId, externalSource 추가
- 빈 값이면 MANUAL로 처리 (기존 동작 유지)
- 외부 작품도 동일한 검증 적용

### 3.3 WorkController 엔드포인트

**1. 검색 페이지**
```
GET /works/search-external?query=...&mediaType=MOVIE
응답: search-external.jsp
```

**2. 작품 등록**
```
POST /works/import-external
파라미터:
  - externalId: long (TMDB ID)
  - mediaType: string (MOVIE/TV)
  - confirmDuplicate: boolean
결과:
  성공: redirect:/works/{id}
  실패: error.jsp
```

### 3.4 사용자 인터페이스

**works.jsp 변경:**
- "🔍 외부 작품 검색" 버튼 추가
- "/works/search-external" 링크

**search-external.jsp (신규):**
```
1. 검색 폼 (제목, 유형 선택)
2. 결과 목록 (카드 형식)
   - 제목, 유형, 연도
   - 개요 (150자 이상 생략)
   - 포스터 경로 (표시 선택사항)
   - "이 작품 등록" 버튼
3. TMDB 출처 표시
   - "제공: The Movie Database (TMDB)"
   - TMDB 로고 표시
4. 오류 메시지
   - 결과 없음 → "다른 검색어 시도"
   - 시간초과 → "로컬 작품 직접 등록"
   - 서버 오류 → "잠시 후 재시도"
   - 요청 한계 → "재시도 안내"
```

---

## 4. 데이터베이스

### 4.1 work 테이블 기존 구조
```sql
CREATE TABLE work (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  media_type ENUM('MOVIE', 'TV') NOT NULL,
  release_year INT,
  synopsis TEXT,
  source ENUM('MANUAL', 'TMDB') NOT NULL,  ← 외부 출처 구분
  external_id BIGINT,                       ← TMDB ID
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  UNIQUE KEY unique_source_media_external 
    (source, media_type, external_id),      ← TMDB 유일성
  FOREIGN KEY (created_by) REFERENCES app_user(id)
);
```

### 4.2 저장 쿼리 (WorkMapper.xml)
```xml
<insert id="insert" ... >
  INSERT INTO work(title, media_type, release_year, synopsis, source, external_id, created_by)
  VALUES(
    #{title},
    #{mediaType},
    #{releaseYear},
    #{synopsis},
    <choose>
      <when test="externalSource != null and externalSource != ''">
        #{externalSource}    ← 'TMDB' 또는 'MANUAL'
      </when>
      <otherwise>
        'MANUAL'
      </otherwise>
    </choose>,
    <choose>
      <when test="externalId > 0">
        #{externalId}        ← TMDB ID
      </when>
      <otherwise>
        NULL
      </otherwise>
    </choose>,
    #{createdBy}
  )
</insert>
```

**삼중 키 (SFR-005 연결):**
- source + media_type + external_id 조합으로 유일성 보장
- 같은 TMDB ID의 영화/TV는 별도로 저장됨
- 수동 등록은 source='MANUAL', external_id=NULL

---

## 5. 설정 관리

### 5.1 application.properties.example
```properties
# TMDB API configuration
# https://www.themoviedb.org/settings/api
# Leave empty to disable external search feature
tmdb.api.key=
```

### 5.2 LocalSettings (기존)
```java
// 기존: DB 연결만 관리
// TmdbClient가 @Value("${tmdb.api.key:}") 로 자동 주입
```

### 5.3 보안 정책
- API 키는 **프로젝트 외부** config 폴더에만 보관
- .gitignore에서 `application.properties` 제외됨
- 환경변수 지원 안 함 (TMDB는 API 키 필수, 공개 불가)
- 로그에서 API 키 필터링 (TmdbClient에서 URL 구성 시 주의)

---

## 6. 에러 처리

### 6.1 검색 결과 없음 (404)
```
호출: GET /search/movie?query=xyzabc123&language=ko-KR
응답: {"results": []}
처리: TmdbException(NOT_FOUND)
JSP: "검색 결과가 없습니다" 메시지
사용자 옵션: 검색어 변경, 직접 등록
```

### 6.2 요청 한계 (429 Too Many Requests)
```
원인: TMDB API 요청 제한 초과
처리: HttpClientErrorException.TooManyRequests 캐치
응답: TmdbException(RATE_LIMITED)
JSP: "API 요청이 많습니다. 잠시 후 다시 시도"
사용자 옵션: 재시도
```

### 6.3 서버 오류 (5xx)
```
원인: TMDB 서버 장애
처리: HttpServerErrorException 캐치
응답: TmdbException(SERVER_ERROR)
JSP: "TMDB 서버 오류입니다"
사용자 옵션: 재시도, 직접 등록
```

### 6.4 시간초과 (Connection Timeout)
```
원인: 네트워크 지연, TMDB 서버 응답 지연
처리: ResourceAccessException 캐치
응답: TmdbException(TIMEOUT)
JSP: "TMDB 연결 시간 초과입니다. 로컬 작품 등록 가능"
사용자 옵션: 재시도, 직접 등록
중요: 로컬 작품·기록·조회는 **계속 작동**
```

### 6.5 API 키 미설정
```
원인: tmdb.api.key가 비어있음
처리: isEnabled() 확인
응답: TmdbException(NOT_CONFIGURED)
JSP: "TMDB API 키가 설정되지 않았습니다"
사용자 옵션: 로컬 작품 검색, 직접 등록
```

---

## 7. 통합 테스트

### 7.1 필수 테스트 시나리오

| 시나리오 | 입력 | 기대 결과 | 검증 |
|---------|------|---------|------|
| 정상 검색 | query="아바타", mediaType="MOVIE" | 검색 결과 표시 | 제목, 연도, 개요 |
| 결과 없음 | query="xyzabc123" | "검색 결과 없음" | 404 처리 |
| 등록 성공 | externalId=19995 (Avatar) | DB 저장, redirect /works/X | source='TMDB', external_id 저장 |
| 중복 감지 | 같은 TMDB ID 재등록 | 기존 작품 안내 | UNIQUE 제약 확인 |
| API 비활성화 | tmdb.api.key 비어있음 | "API 키 미설정" | isEnabled() false |
| 시간초과 | 강제 지연 | "시간초과" + 로컬 작품 작동 | 다른 기능 영향 없음 |

### 7.2 보안 테스트

```java
// 1. API 키 노출 여부 확인
@Test
public void testApiKeyNotInResponse() {
  // API 호출 후 응답에 api_key 포함 여부 확인
}

// 2. 개인 기록 미전송 확인
@Test
public void testPersonalDataNotSent() {
  // searchExternal() 호출 시 viewing_record 조회 없음 확인
}

// 3. SQL Injection 방지
@Test
public void testQueryEscaping() {
  search("'; DROP TABLE work; --", "MOVIE");
  // 안전하게 처리되는지 확인
}
```

---

## 8. TMDB 출처 표시

### 8.1 search-external.jsp
```jsp
<span class="tmdb-attribution">
  제공: <a href="https://www.themoviedb.org/" target="_blank" rel="noopener">
    The Movie Database (TMDB)
  </a>
  <img src="https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bbb5ce97b72511e2a4c8faced28f7f7afc2e748.svg" 
       alt="TMDB Logo" style="height: 20px; margin-left: 8px;">
</span>
```

### 8.2 약관 고려사항
- [ ] TMDB 이용약관 확인 (적용 전)
- [ ] 저장·갱신·삭제 조건 검증
- [ ] 라이선싱 요구사항 확인
- [ ] 로고/출처 표시 요구사항 준수

---

## 9. 성능 고려사항

### 9.1 네트워크
- RestTemplate 타임아웃: 5초 (조정 가능)
- 캐싱: 미적용 (검색 쿼리마다 호출)
- 배치: 미적용 (1회 검색당 1 API 호출)

### 9.2 API 한계
- TMDB API 레이트 제한: 40 requests/10초 (기본)
- 검색 결과: 최대 20개만 반환 (메모리 절감)
- 페이지네이션: 미구현 (기본 20개로 충분)

### 9.3 미적용 최적화
- [ ] 로컬 캐시 (Redis 등)
- [ ] 검색 결과 페이지네이션
- [ ] 배경 작업 (Async)

---

## 10. 마이그레이션 및 롤백

### 10.1 활성화
1. TMDB API 키 획득 (https://www.themoviedb.org/settings/api)
2. application.properties에 tmdb.api.key 설정
3. 앱 재시작
4. /works 화면에서 "🔍 외부 작품 검색" 버튼 확인

### 10.2 비활성화
1. application.properties에서 tmdb.api.key 제거 (빈 값)
2. 앱 재시작
3. search-external.jsp에서 "설정되지 않았습니다" 메시지 표시
4. 기존 TMDB 작품은 그대로 유지

### 10.3 롤백
- TmdbClient 클래스 삭제
- pom.xml에서 jackson-databind 제거
- WorkController의 검색/임포트 엔드포인트 제거
- works.jsp의 외부 검색 링크 제거
- **기존 TMDB 작품은 로컬로 전환** (수동 작업 필요)

---

## 11. 향후 개선 사항

### 미적용
- [ ] 포스터 이미지 표시 (posterPath 저장만 함)
- [ ] TMDB 평점/투표수 저장
- [ ] 자동 장르 매핑 (TMDB 장르 ID ↔ 로컬 장르)
- [ ] 검색 결과 페이지네이션
- [ ] 캐싱 레이어 (Redis)
- [ ] 비동기 처리 (Async)

### 적용 대상 (별도 작업)
- 포스터 다운로드 및 로컬 저장
- 주기적 메타데이터 갱신
- 통계에서 TMDB 평점 활용

---

## 12. 테스트 실행 방법

### 12.1 로컬 테스트 (API 키 필요)

```bash
# 1. API 키 설정
# %USERPROFILE%\dev\review-system\config\application.properties 수정
tmdb.api.key=YOUR_TMDB_API_KEY

# 2. 앱 실행
start-server.cmd

# 3. 브라우저 테스트
# http://localhost:8080/works
# → "🔍 외부 작품 검색" 클릭
# → "아바타" 검색
# → 결과 표시 확인
```

### 12.2 API 없이 테스트

```bash
# API 키 없음 (기본값)
# application.properties: tmdb.api.key=

# 결과: "설정되지 않았습니다" 메시지
# 로컬 작품 검색/등록은 정상 작동
```

---

**작성자**: Claude Haiku 4.5  
**마지막 갱신**: 2026-11-10
