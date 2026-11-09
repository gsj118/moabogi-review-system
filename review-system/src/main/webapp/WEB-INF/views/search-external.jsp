<%@ page pageEncoding="UTF-8" %>
<%@ include file="common/header.jspf" %>
<section class="main-content">
  <c:if test="${not externalSearchEnabled}">
    <div class="notice error" role="alert">
      <strong>외부 작품 검색이 설정되지 않았습니다.</strong>
      <p>TMDB API 키가 필요합니다. 관리자에게 문의하세요.</p>
    </div>
    <p class="text-center"><a class="button secondary" href="<c:url value='/works'/>">로컬 작품 검색으로 돌아가기</a></p>
  </c:if>

  <c:if test="${externalSearchEnabled}">
    <form method="get" class="search-box">
      <div class="search-fields">
        <input type="search" name="query" value="<c:out value='${query}'/>" placeholder="제목 입력 (예: 아바타)" maxlength="200" autofocus>
        <select name="mediaType">
          <option value="MOVIE" <c:if test="${mediaType == 'MOVIE'}">selected</c:if>>영화</option>
          <option value="TV" <c:if test="${mediaType == 'TV'}">selected</c:if>>드라마</option>
        </select>
        <button type="submit" class="button">검색</button>
      </div>
    </form>

    <c:if test="${not empty query}">
      <c:choose>
        <c:when test="${not empty externalResults}">
          <div class="results-info">
            <p>${externalResults.size()} 개의 검색 결과</p>
            <p class="muted">
              <span class="tmdb-attribution">
                제공: <a href="https://www.themoviedb.org/" target="_blank" rel="noopener">The Movie Database (TMDB)</a>
                <img src="https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bbb5ce97b72511e2a4c8faced28f7f7afc2e748.svg" alt="TMDB Logo" style="height: 20px; margin-left: 8px; vertical-align: middle;">
              </span>
            </p>
          </div>
          <div class="external-results">
            <c:forEach items="${externalResults}" var="work">
              <div class="result-card panel">
                <div class="result-info">
                  <h3><c:out value="${work.title}"/></h3>
                  <p class="result-meta">
                    <span class="media-type">${work.mediaType == 'MOVIE' ? '영화' : '드라마'}</span>
                    <c:if test="${not empty work.releaseYear}">
                      <span class="release-year"><c:out value="${work.releaseYear}"/></span>
                    </c:if>
                  </p>
                  <c:if test="${not empty work.overview}">
                    <p class="synopsis"><c:out value="${fn:substring(work.overview, 0, 150)}"/>...</p>
                  </c:if>
                </div>
                <div class="result-actions">
                  <form action="<c:url value='/works/import-external'/>" method="post" style="display: inline;">
                    <%@ include file="common/csrf.jspf" %>
                    <input type="hidden" name="externalId" value="${work.externalId}">
                    <input type="hidden" name="mediaType" value="${work.mediaType}">
                    <input type="hidden" name="confirmDuplicate" value="false">
                    <button type="submit" class="button small">이 작품 등록</button>
                  </form>
                </div>
              </div>
            </c:forEach>
          </div>
          <div class="notice info">
            <p>등록하려는 작품이 없나요? <a href="<c:url value='/works/new'/>">직접 등록</a>하세요.</p>
          </div>
        </c:when>
        <c:when test="${errorType == 'NOT_FOUND'}">
          <div class="notice warning" role="alert">
            <strong>검색 결과가 없습니다.</strong>
            <p>다른 검색어를 시도하거나 <a href="<c:url value='/works/new'/>">직접 등록</a>하세요.</p>
          </div>
        </c:when>
        <c:when test="${errorType == 'RATE_LIMITED'}">
          <div class="notice error" role="alert">
            <strong>요청이 많습니다.</strong>
            <p>${message}</p>
            <p>잠시 후 다시 시도해주세요.</p>
          </div>
        </c:when>
        <c:when test="${errorType == 'TIMEOUT'}">
          <div class="notice error" role="alert">
            <strong>연결 시간 초과</strong>
            <p>${message}</p>
            <p><a href="<c:url value='/works/new'/>">로컬 작품 직접 등록</a>으로 작품을 추가할 수 있습니다.</p>
          </div>
        </c:when>
        <c:when test="${errorType == 'SERVER_ERROR'}">
          <div class="notice error" role="alert">
            <strong>TMDB 서버 오류</strong>
            <p>${message}</p>
            <p>잠시 후 다시 시도하거나 <a href="<c:url value='/works/new'/>">직접 등록</a>하세요.</p>
          </div>
        </c:when>
        <c:otherwise>
          <c:if test="${not empty message}">
            <div class="notice error" role="alert">
              <strong>검색 중 오류가 발생했습니다.</strong>
              <p><c:out value="${message}"/></p>
            </div>
          </c:if>
        </c:otherwise>
      </c:choose>
    </c:if>
  </c:if>
</section>

<style>
.search-box { margin-bottom: 2rem; }
.search-fields { display: flex; gap: 1rem; flex-wrap: wrap; }
.search-fields input { flex: 1; min-width: 200px; }
.search-fields select { padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; }
.search-fields button { flex-shrink: 0; }
.results-info { margin: 1rem 0; font-size: 0.9rem; color: #666; }
.tmdb-attribution { display: flex; align-items: center; }
.external-results { display: grid; gap: 1rem; margin: 1rem 0; }
.result-card { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
.result-info { flex: 1; }
.result-info h3 { margin: 0 0 0.5rem 0; font-size: 1.1rem; }
.result-meta { font-size: 0.85rem; color: #666; margin: 0.25rem 0; }
.media-type { display: inline-block; padding: 0.2rem 0.5rem; background: #f0f0f0; border-radius: 3px; margin-right: 0.5rem; }
.release-year { color: #888; }
.synopsis { font-size: 0.9rem; color: #666; margin: 0.5rem 0 0 0; line-height: 1.4; }
.result-actions { flex-shrink: 0; }
.result-actions form { display: inline; }
.text-center { text-align: center; padding: 1rem 0; }
</style>

<%@ include file="common/footer.jspf" %>
