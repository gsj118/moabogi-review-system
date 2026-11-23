# [SFR-003] 파일 업로드 기능 구현 명세

**작성일**: 2026-11-23  
**상태**: ✅ 백엔드 구현 완료  
**참고**: 웹페이지 미작성, 나중에 JSP 연결 가능

---

## 1. 개요

파일 첨부 기능은 게시글에 파일을 업로드하고 관리하는 기능입니다. 현재 웹페이지는 미작성이지만, 백엔드 로직이 완성되어 있으므로 JSP 페이지를 작성하면 바로 적용 가능합니다.

---

## 2. 구현 현황

### 2.1 완료된 구성요소

| 항목 | 위치 | 상태 |
|------|------|------|
| 데이터베이스 | db/schema.sql | ✅ file_attachment 테이블 추가 |
| 도메인 | domain/FileAttachment.java | ✅ 모든 필드 |
| 매퍼 | mapper/FileMapper.java | ✅ CRUD 메서드 |
| SQL | mapper/FileMapper.xml | ✅ 쿼리 작성 |
| 서비스 | service/FileService.java | ✅ 검증·저장·다운로드·삭제 |
| 컨트롤러 | controller/FileController.java | ✅ REST API 엔드포인트 |
| 설정 | web.xml, servlet-context.xml | ✅ MultipartResolver 설정 |

### 2.2 파일 저장 구조

```
%USERPROFILE%\dev\review-system\uploads\
└── {postId}
    ├── 550e8400-e29b-41d4-a716-446655440001.pdf
    ├── 550e8400-e29b-41d4-a716-446655440002.docx
    └── ...
```

저장명은 UUID + 확장자로 자동 생성됨 (파일명 변조 방지)

---

## 3. 엔드포인트

### 3.1 파일 업로드

```
POST /files/upload
Content-Type: multipart/form-data

Parameters:
  - postId (long, 필수): 게시글 ID
  - file (MultipartFile, 필수): 파일 데이터

Success Response: 302 (Redirect to /posts/{postId})
Error Response: error.jsp with message
```

**검증 사항:**
- 파일 필수
- 파일 크기: 최대 10MB
- 첨부 수: 게시글당 최대 5개
- 확장자: pdf, doc, docx, xls, xlsx, ppt, pptx, jpg, jpeg, png, gif, zip, txt
- MIME 타입 검증 (실제 파일 형식 확인)

**처리:**
- 저장명 생성 (UUID)
- 파일시스템 저장
- DB 레코드 생성
- 성공 메시지 반환

### 3.2 파일 다운로드

```
GET /files/{id}/download

Success Response: 200 (Binary file data)
  - Content-Disposition: attachment
  - Content-Type: (파일의 MIME 타입)
Error Response: 404 (not found), 403 (forbidden)
```

**검증 사항:**
- 파일 존재 여부
- 접근 권한 (업로드한 사용자만)

### 3.3 파일 삭제

```
POST /files/{id}/delete

Success Response: 302 (Redirect to /posts/{postId})
Error Response: error.jsp or redirect with message
```

**검증 사항:**
- 파일 존재 여부
- 접근 권한 (업로드한 사용자만)
- DB 삭제
- 파일시스템 삭제

### 3.4 게시글의 첨부 목록

```
GET /files/post/{postId}

Success Response: 200 (JSON array)
[
  {
    "id": 1,
    "postId": 123,
    "uploadedBy": 456,
    "originalFilename": "report.pdf",
    "storedFilename": "550e8400-e29b-41d4-a716-446655440001.pdf",
    "fileSize": 2048000,
    "mimeType": "application/pdf",
    "createdAt": "2026-11-23T14:30:00"
  },
  ...
]
```

---

## 4. 데이터베이스

### 4.1 file_attachment 테이블

```sql
CREATE TABLE IF NOT EXISTS file_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  uploaded_by BIGINT NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  stored_filename VARCHAR(36) NOT NULL,
  file_size BIGINT NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY ix_attachment_post (post_id),
  KEY ix_attachment_user (uploaded_by),
  CONSTRAINT fk_attachment_user FOREIGN KEY (uploaded_by) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT ck_attachment_size CHECK (file_size > 0 AND file_size <= 10485760),
  CONSTRAINT ck_attachment_ext CHECK (stored_filename REGEXP '^[a-f0-9]{8}-...$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

**필드 설명:**
- id: 첨부 고유 ID (자동 증가)
- post_id: 게시글 ID (나중에 SFR-004 post 테이블과 FK 연결)
- uploaded_by: 업로드한 사용자 ID (app_user FK)
- original_filename: 사용자가 업로드한 원본 파일명
- stored_filename: 서버에 저장된 파일명 (UUID)
- file_size: 파일 크기 (바이트)
- mime_type: MIME 타입
- created_at: 업로드 시간

**인덱스:**
- ix_attachment_post: postId로 빠른 조회
- ix_attachment_user: uploadedBy로 사용자 파일 추적

**제약:**
- CHECK: 파일 크기 1~10MB
- CHECK: 저장명 UUID 형식 + 허용 확장자

---

## 5. 보안 조치

### 5.1 파일명 변조 방지
- UUID 기반 저장명 (original_filename과 분리)
- 경로 접근 차단 (realpath 검증)

### 5.2 악성 파일 거부
- 확장자 화이트리스트 검증
- MIME 타입 검증 (실제 파일 형식 확인)
- 파일 내용 불일치 거부

### 5.3 접근 권한
- 다운로드: 파일 업로드자만 가능
- 삭제: 파일 업로드자만 가능
- DB 연결 시 추가: 게시글 소유자도 삭제 가능

### 5.4 경로 보안
- 실행 경로 밖 저장 (%USERPROFILE%/dev/review-system/uploads/)
- 심볼릭 링크 접근 차단
- 절대 경로 사용

### 5.5 SQL Injection 방지
- MyBatis 파라미터 바인딩
- Prepared Statement 사용

---

## 6. 웹페이지 연결 가이드

### 6.1 JSP 페이지 작성 체크리스트

#### 파일 업로드 폼 (예: post-form.jsp)

```jsp
<c:if test="${editing}">
  <div class="attachments-section">
    <h3>첨부 파일</h3>
    
    <!-- 현재 첨부 목록 -->
    <div id="current-attachments">
      <c:forEach items="${attachments}" var="file">
        <div class="attachment-item">
          <span>${file.originalFilename}</span>
          <span class="file-size">${file.fileSize / 1024} KB</span>
          <form action="/files/${file.id}/delete" method="post" style="display:inline;">
            <%@ include file="common/csrf.jspf" %>
            <button type="submit" class="button small danger">삭제</button>
          </form>
        </div>
      </c:forEach>
    </div>
    
    <!-- 새 파일 업로드 -->
    <c:if test="${fn:length(attachments) < 5}">
      <form id="upload-form" action="/files/upload" method="post" enctype="multipart/form-data">
        <%@ include file="common/csrf.jspf" %>
        <input type="hidden" name="postId" value="${post.id}">
        <input type="file" name="file" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.jpg,.jpeg,.png,.gif,.zip,.txt">
        <button type="submit" class="button small">파일 추가 (최대 ${5 - fn:length(attachments)}개)</button>
      </form>
      <p class="note">최대 10MB, 한 번에 하나씩 업로드</p>
    </c:if>
  </div>
</c:if>
```

#### 파일 다운로드 링크 (예: post-detail.jsp)

```jsp
<c:forEach items="${attachments}" var="file">
  <div class="file-item">
    <a href="/files/${file.id}/download" class="file-link">
      📎 ${file.originalFilename}
    </a>
    <span class="file-size">${file.fileSize / 1024} KB</span>
  </div>
</c:forEach>
```

### 6.2 컨트롤러 수정 (PostController)

```java
@GetMapping("/posts/{id}")
public String detail(@PathVariable long id, Model model) {
  // 기존 코드
  model.addAttribute("post", postService.get(id));
  
  // 첨부 파일 추가
  model.addAttribute("attachments", files.listByPost(id));
  
  return "post-detail";
}

@GetMapping("/posts/{id}/edit")
public String edit(@PathVariable long id, Model model) {
  // 기존 코드
  model.addAttribute("post", postService.get(id));
  
  // 첨부 파일 추가
  model.addAttribute("attachments", files.listByPost(id));
  
  return "post-form";
}
```

### 6.3 의존성 주입

```java
@Controller
public class PostController {
  private final PostService posts;
  private final FileService files;  // ← 추가
  
  public PostController(PostService posts, FileService files) {
    this.posts = posts;
    this.files = files;
  }
}
```

### 6.4 테스트 순서

1. 파일 업로드 (`POST /files/upload`)
   - 작은 파일부터 테스트
   - 각 허용 확장자 확인
   - 초과 크기 거부 확인
   - 초과 개수 거부 확인

2. 파일 목록 조회 (`GET /files/post/{postId}`)
   - 업로드한 파일 목록 표시 확인

3. 파일 다운로드 (`GET /files/{id}/download`)
   - 원본 파일명 확인
   - 파일 내용 일치 확인

4. 파일 삭제 (`POST /files/{id}/delete`)
   - DB 레코드 삭제 확인
   - 파일시스템 파일 삭제 확인

5. 접근 권한
   - 다른 사용자 파일 다운로드 거부 (403)
   - 다른 사용자 파일 삭제 거부 (403)

---

## 7. 현재 제한사항

| 항목 | 현황 | 향후 |
|------|------|------|
| 게시판 | ❌ SFR-004 미구현 | post 테이블 생성 시 FK 추가 |
| 웹페이지 | ❌ JSP 미작성 | 위 가이드 참고 작성 |
| 파일 미리보기 | ❌ 미구현 | 이미지만 썸네일 추가 가능 |
| 바이러스 검사 | ❌ 미구현 | 클라우드 API 연동 가능 |
| 파일 버전 관리 | ❌ 미구현 | 나중에 필요시 추가 |

---

## 8. 설정 경로

**application.properties.example**

```properties
# File upload configuration (optional)
# If empty, defaults to %USERPROFILE%\dev\review-system\uploads
file.upload.dir=
```

**사용 방법:**
1. `config/application.properties`에서 `file.upload.dir` 설정
2. 미설정 시 기본 경로: `%USERPROFILE%\dev\review-system\uploads`

---

## 9. 에러 메시지

| 상황 | 메시지 |
|------|--------|
| 파일 미선택 | "파일을 선택해주세요." |
| 파일 크기 초과 | "파일 크기는 10MB 이하여야 합니다." |
| 첨부 수 초과 | "게시글당 최대 5개까지 첨부할 수 있습니다." |
| 허용되지 않는 확장자 | "허용되지 않는 파일 형식입니다. (...)" |
| MIME 타입 불일치 | "파일 형식이 맞지 않습니다. 실제 파일을 업로드해주세요." |
| 파일 저장 실패 | "파일 저장 중 오류가 발생했습니다." |
| 파일을 찾을 수 없음 | "파일을 찾을 수 없습니다." (404) |
| 접근 권한 없음 | "이 파일에 접근할 수 없습니다." (403) |

---

## 10. 클래스 다이어그램

```
FileAttachment (도메인)
  - id, postId, uploadedBy
  - originalFilename, storedFilename
  - fileSize, mimeType, createdAt

FileService (서비스)
  - upload(postId, userId, file): FileAttachment
  - download(id, userId): byte[]
  - delete(id, userId): void
  - listByPost(postId): List<Map>
  - get(id): FileAttachment
  
  - [비공개] getExtension(filename): String
  - [비공개] mimeTypeMatches(actual, expected): boolean

FileMapper (매퍼)
  - insert(fa)
  - delete(id, userId): int
  - selectById(id): FileAttachment
  - countByPost(postId): int
  - selectByPost(postId): List<Map>

FileController (컨트롤러)
  - upload(auth, postId, file): String
  - download(auth, id): ResponseEntity
  - delete(auth, id): String
  - listByPost(postId): List<Map>
```

---

**검증자**: Claude Haiku 4.5  
**검증일**: 2026-11-23  
**상태**: ✅ 백엔드 완성, JSP 연결 대기
