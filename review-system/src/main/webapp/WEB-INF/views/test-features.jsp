<%@ page pageEncoding="UTF-8" %>
<%@ include file="common/header.jspf" %>
<style>
.test-container { max-width: 1200px; margin: 0 auto; }
.test-section {
  margin: 2rem 0;
  padding: 1.5rem;
  background: var(--bg-secondary);
  border-radius: 8px;
  border-left: 4px solid var(--accent);
}
.test-section h2 { margin-top: 0; }
.test-section p { color: var(--text-muted); font-size: 0.9rem; }
.form-group { margin-bottom: 1rem; }
label { display: block; font-weight: 600; margin-bottom: 0.4rem; }
input[type="text"], input[type="number"], select, textarea {
  width: 100%;
  padding: 0.6rem;
  border: 1px solid var(--border);
  border-radius: 4px;
  font-size: 0.95rem;
  font-family: inherit;
}
textarea { resize: vertical; min-height: 80px; }
.form-inline { display: flex; gap: 1rem; }
.form-inline input, .form-inline select { flex: 1; }
button {
  padding: 0.6rem 1.2rem;
  background: var(--accent);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 600;
}
button:hover { opacity: 0.9; }
.result {
  margin-top: 1rem;
  padding: 1rem;
  background: var(--bg-tertiary);
  border-radius: 4px;
  border-left: 3px solid var(--success);
}
.result.error { border-left-color: var(--error); }
.result pre { margin: 0; overflow-x: auto; font-size: 0.85rem; }
.two-column { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.test-status {
  display: inline-block;
  padding: 0.3rem 0.8rem;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 600;
  margin-left: 0.5rem;
}
.status-ready { background: var(--success); color: white; }
.status-disabled { background: var(--border); color: var(--text-muted); }
@media (max-width: 768px) {
  .two-column { grid-template-columns: 1fr; }
  .form-inline { flex-direction: column; }
}
</style>

<div class="test-container">
  <div class="section-heading">
    <div>
      <span class="eyebrow">FEATURE TESTING</span>
      <h2>기능 테스트 페이지</h2>
    </div>
    <p>구현된 기능들을 테스트해볼 수 있는 임시 페이지입니다.</p>
  </div>

  <!-- SFR-013: TMDB 외부 작품 검색 -->
  <section class="test-section">
    <h2>🎬 SFR-013: 외부 작품 검색 (TMDB)
      <c:if test="${externalSearchEnabled}">
        <span class="test-status status-ready">사용 가능</span>
      </c:if>
      <c:if test="${!externalSearchEnabled}">
        <span class="test-status status-disabled">API 키 미설정</span>
      </c:if>
    </h2>
    <p>TMDB API를 통해 외부 작품 정보를 검색하고 가져올 수 있습니다.</p>

    <c:if test="${externalSearchEnabled}">
      <form method="get" action="">
        <div class="form-inline">
          <div class="form-group" style="flex: 2;">
            <label>검색어</label>
            <input type="text" id="tmdbQuery" placeholder="작품 제목 입력" value="${query}">
          </div>
          <div class="form-group">
            <label>유형</label>
            <select id="tmdbMediaType">
              <option value="MOVIE" ${mediaType eq 'MOVIE' ? 'selected' : ''}>영화</option>
              <option value="TV" ${mediaType eq 'TV' ? 'selected' : ''}>드라마</option>
            </select>
          </div>
          <div class="form-group" style="margin-top: 1.7rem;">
            <button type="button" onclick="searchTmdb()">검색</button>
          </div>
        </div>
      </form>

      <c:if test="${not empty externalResults}">
        <div class="result">
          <h4>${fn:length(externalResults)}개의 결과 (최대 20개)</h4>
          <table style="width: 100%; border-collapse: collapse; font-size: 0.9rem;">
            <thead>
              <tr style="border-bottom: 1px solid var(--border);">
                <th style="padding: 0.5rem; text-align: left;">제목</th>
                <th style="padding: 0.5rem; text-align: left;">연도</th>
                <th style="padding: 0.5rem; text-align: left;">출처</th>
                <th style="padding: 0.5rem; text-align: center;">작업</th>
              </tr>
            </thead>
            <tbody>
              <c:forEach var="work" items="${externalResults}">
                <tr style="border-bottom: 1px solid var(--border);">
                  <td style="padding: 0.5rem;"><c:out value="${work.title}"/></td>
                  <td style="padding: 0.5rem;"><c:out value="${work.releaseYear}"/></td>
                  <td style="padding: 0.5rem;"><c:out value="${work.externalSource}"/></td>
                  <td style="padding: 0.5rem; text-align: center;">
                    <button type="button" onclick="showDetails(${work.externalId}, '${work.mediaType}', true)" style="padding: 0.3rem 0.6rem; font-size: 0.8rem;">상세보기</button>
                  </td>
                </tr>
              </c:forEach>
            </tbody>
          </table>
        </div>
      </c:if>

      <c:if test="${not empty errorType}">
        <div class="result error">
          <h4>❌ 오류 발생</h4>
          <p><strong>오류 유형:</strong> <c:out value="${errorType}"/></p>
          <p><strong>메시지:</strong> <c:out value="${message}"/></p>
        </div>
      </c:if>
    </c:if>

    <c:if test="${!externalSearchEnabled}">
      <div class="result error">
        <p>⚠️ TMDB API 키가 설정되지 않았습니다. application.properties 또는 environment에서 <code>tmdb.api.key</code>를 설정해주세요.</p>
      </div>
    </c:if>
  </section>

  <!-- SFR-003: 파일 첨부 -->
  <section class="test-section">
    <h2>📎 SFR-003: 파일 첨부</h2>
    <p>게시글에 파일을 첨부하고 관리할 수 있습니다. (최대 10MB, 5개 파일)</p>

    <div class="two-column">
      <div>
        <h3>파일 업로드</h3>
        <form id="fileUploadForm" enctype="multipart/form-data">
          <div class="form-group">
            <label>게시글 ID</label>
            <input type="number" id="postId" placeholder="게시글 ID 입력" value="1">
          </div>
          <div class="form-group">
            <label>파일 선택</label>
            <input type="file" id="fileInput" multiple style="padding: 0.4rem;">
          </div>
          <button type="button" onclick="uploadFile()">업로드</button>
        </form>
        <div id="uploadResult"></div>
      </div>

      <div>
        <h3>게시글 파일 목록 조회</h3>
        <form>
          <div class="form-group">
            <label>게시글 ID</label>
            <input type="number" id="postIdForList" placeholder="게시글 ID 입력" value="1">
          </div>
          <button type="button" onclick="listFiles()">조회</button>
        </form>
        <div id="listResult"></div>
      </div>
    </div>
  </section>

  <!-- 작품 등록 -->
  <section class="test-section">
    <h2>➕ 작품 등록</h2>
    <p>새로운 작품을 직접 등록하거나 외부 작품을 임포트할 수 있습니다.</p>

    <form method="post" action="<c:url value='/works'/>">
      <%@ include file="common/csrf.jspf" %>
      <div class="two-column">
        <div>
          <div class="form-group">
            <label>제목 *</label>
            <input type="text" name="title" placeholder="작품 제목" required>
          </div>
          <div class="form-group">
            <label>유형 *</label>
            <select name="mediaType">
              <option value="MOVIE">영화</option>
              <option value="TV">드라마</option>
            </select>
          </div>
        </div>
        <div>
          <div class="form-group">
            <label>공개 연도</label>
            <input type="text" name="releaseYear" placeholder="2024">
          </div>
          <div class="form-group">
            <label>장르</label>
            <select name="genreIds" multiple style="height: 80px;">
              <c:forEach var="genre" items="${genres}">
                <option value="${genre.id}"><c:out value="${genre.name}"/></option>
              </c:forEach>
            </select>
            <small style="color: var(--text-muted); display: block; margin-top: 0.3rem;">Ctrl 또는 Cmd를 누르고 선택</small>
          </div>
        </div>
      </div>

      <div class="form-group">
        <label>소개</label>
        <textarea name="synopsis" placeholder="작품 소개 (최대 1000자)"></textarea>
      </div>

      <div class="form-group" style="display: flex; align-items: center; gap: 0.5rem;">
        <input type="checkbox" name="confirmDuplicate" id="confirmDup" style="width: auto;">
        <label for="confirmDup" style="margin: 0;">같은 제목·유형의 작품이 있어도 등록하기</label>
      </div>

      <button type="submit" style="width: auto;">작품 등록</button>
    </form>
  </section>

  <!-- 테스트 가이드 -->
  <section class="test-section" style="border-left-color: var(--info, #0066cc);">
    <h2>📝 테스트 가이드</h2>
    <div style="font-size: 0.95rem; line-height: 1.6;">
      <h3>1. TMDB 외부 작품 검색 (SFR-013)</h3>
      <ul>
        <li>✓ 검색어를 입력하고 "검색" 버튼 클릭</li>
        <li>✓ 최대 20개의 검색 결과 확인</li>
        <li>✓ 각 작품의 제목, 연도, 출처 정보 확인</li>
        <li>✓ "상세보기"로 작품 상세 정보 조회</li>
        <li>✓ 오류 처리 (API 키 미설정, 검색 결과 없음 등)</li>
      </ul>

      <h3>2. 파일 첨부 (SFR-003)</h3>
      <ul>
        <li>✓ 파일 선택 후 업로드</li>
        <li>✓ 파일 목록 조회</li>
        <li>✓ 파일 다운로드</li>
        <li>✓ 파일 삭제</li>
        <li>✓ 최대 파일 크기 및 개수 제한 확인</li>
        <li>✓ 허용된 파일 형식 확인 (pdf, doc, docx, xls, xlsx, ppt, pptx, jpg, jpeg, png, gif, zip, txt)</li>
      </ul>

      <h3>3. 작품 등록</h3>
      <ul>
        <li>✓ 직접 작품 정보 입력 후 등록</li>
        <li>✓ 중복 작품 확인 및 처리</li>
        <li>✓ 장르 선택</li>
        <li>✓ 외부 작품 임포트 (TMDB 검색 결과에서)</li>
      </ul>
    </div>
  </section>
</div>

<script>
function searchTmdb() {
  const query = document.getElementById('tmdbQuery').value;
  const mediaType = document.getElementById('tmdbMediaType').value;
  if (query.trim() === '') {
    alert('검색어를 입력해주세요.');
    return;
  }
  window.location.href = '<c:url value="/works/search-external"/>' + '?query=' + encodeURIComponent(query) + '&mediaType=' + mediaType;
}

function showDetails(externalId, mediaType, confirmImport) {
  if (confirmImport) {
    const result = confirm('이 작품을 등록하시겠습니까?');
    if (result) {
      const form = document.createElement('form');
      form.method = 'POST';
      form.action = '<c:url value="/works/import-external"/>';
      form.innerHTML = `
        <input type="hidden" name="externalId" value="${externalId}">
        <input type="hidden" name="mediaType" value="${mediaType}">
        <input type="hidden" name="confirmDuplicate" value="false">
      `;
      document.body.appendChild(form);
      form.submit();
    }
  }
}

function uploadFile() {
  const postId = document.getElementById('postId').value;
  const fileInput = document.getElementById('fileInput');

  if (!postId) {
    alert('게시글 ID를 입력해주세요.');
    return;
  }
  if (fileInput.files.length === 0) {
    alert('파일을 선택해주세요.');
    return;
  }

  const formData = new FormData();
  formData.append('postId', postId);
  formData.append('file', fileInput.files[0]);

  const resultDiv = document.getElementById('uploadResult');
  resultDiv.innerHTML = '<p>업로드 중...</p>';

  fetch('<c:url value="/files/upload"/>', {
    method: 'POST',
    body: formData
  })
  .then(response => {
    if (response.ok) {
      resultDiv.innerHTML = '<div class="result" style="border-left-color: var(--success);"><h4>✓ 파일 업로드 완료</h4><p>파일이 정상적으로 업로드되었습니다.</p></div>';
      fileInput.value = '';
      listFiles();
    } else {
      return response.text().then(text => {
        throw new Error(text || '업로드 실패');
      });
    }
  })
  .catch(error => {
    resultDiv.innerHTML = '<div class="result error"><h4>❌ 업로드 실패</h4><p>' + error.message + '</p></div>';
  });
}

function listFiles() {
  const postId = document.getElementById('postIdForList').value;

  if (!postId) {
    alert('게시글 ID를 입력해주세요.');
    return;
  }

  const resultDiv = document.getElementById('listResult');
  resultDiv.innerHTML = '<p>조회 중...</p>';

  fetch('<c:url value="/files/post/"/>' + postId)
    .then(response => response.json())
    .then(files => {
      if (files.length === 0) {
        resultDiv.innerHTML = '<div class="result"><p>등록된 파일이 없습니다.</p></div>';
      } else {
        let html = '<div class="result"><h4>' + files.length + '개 파일</h4>';
        html += '<table style="width: 100%; border-collapse: collapse; font-size: 0.9rem;">';
        html += '<thead><tr style="border-bottom: 1px solid var(--border);"><th style="padding: 0.5rem; text-align: left;">파일명</th><th style="padding: 0.5rem; text-align: left;">크기</th><th style="padding: 0.5rem; text-align: center;">작업</th></tr></thead>';
        html += '<tbody>';
        files.forEach(file => {
          const size = (file.fileSize / 1024).toFixed(1) + ' KB';
          html += '<tr style="border-bottom: 1px solid var(--border);">';
          html += '<td style="padding: 0.5rem;">' + file.originalFilename + '</td>';
          html += '<td style="padding: 0.5rem;">' + size + '</td>';
          html += '<td style="padding: 0.5rem; text-align: center;">';
          html += '<a href="<c:url value="/files/"/>' + file.id + '/download" style="margin-right: 0.5rem; color: var(--accent); text-decoration: none;">다운로드</a>';
          html += '</td></tr>';
        });
        html += '</tbody></table></div>';
        resultDiv.innerHTML = html;
      }
    })
    .catch(error => {
      resultDiv.innerHTML = '<div class="result error"><h4>❌ 오류 발생</h4><p>' + error.message + '</p></div>';
    });
}
</script>

<%@ include file="common/footer.jspf" %>
