<%@ page pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageHandlesMessage" value="${true}"/>
<%@ include file="common/header.jspf" %>
<c:set var="searchTerm" value="${empty query ? param.query : query}"/>
<c:set var="typeInput" value="${empty mediaType ? param.mediaType : mediaType}"/>
<c:set var="searchType" value="${typeInput == 'TV' ? 'TV' : 'MOVIE'}"/>
<c:url var="localUrl" value="/works">
  <c:if test="${not empty searchTerm}"><c:param name="keyword" value="${searchTerm}"/></c:if>
  <c:if test="${typeInput == 'MOVIE' || typeInput == 'TV'}"><c:param name="mediaType" value="${searchType}"/></c:if>
</c:url>
<c:url var="manualUrl" value="/works/new"><c:param name="title" value="${searchTerm}"/></c:url>

<section class="panel pad form-card" aria-label="TMDB 작품 검색">
  <c:choose>
    <c:when test="${not externalSearchEnabled || errorType == 'NOT_CONFIGURED'}">
      <p class="notice">지금은 TMDB 검색을 이용할 수 없습니다. 등록된 작품을 검색하거나 직접 등록할 수 있습니다.</p>
    </c:when>
    <c:otherwise>
      <form class="filter-grid" method="get" action="<c:url value='/works/search-external'/>">
        <div class="field"><label for="external-query">작품 제목</label>
          <input type="search" id="external-query" name="query" maxlength="200" required value="<c:out value='${searchTerm}'/>">
        </div>
        <div class="field"><label for="external-type">작품 유형</label>
          <select id="external-type" name="mediaType">
            <option value="MOVIE" ${searchType=='MOVIE'?'selected':''}>영화</option>
            <option value="TV" ${searchType=='TV'?'selected':''}>드라마</option>
          </select>
        </div>
        <button class="button" type="submit">TMDB 검색</button>
      </form>
      <c:choose>
        <c:when test="${errorType == 'NOT_FOUND' || (empty errorType && empty message && not empty searchTerm && empty externalResults)}">
          <p role="status">검색 결과가 없습니다.</p>
        </c:when>
        <c:when test="${not empty errorType || not empty message}">
          <p role="alert"><c:choose>
            <c:when test="${errorType == 'RATE_LIMITED'}">검색 요청이 많습니다. 잠시 후 다시 시도해 주세요.</c:when>
            <c:when test="${errorType == 'TIMEOUT'}">TMDB 응답 시간이 초과됐습니다.</c:when>
            <c:when test="${errorType == 'SERVER_ERROR'}">TMDB 서버가 응답하지 않습니다.</c:when>
            <c:otherwise>TMDB 검색을 마치지 못했습니다.</c:otherwise>
          </c:choose></p>
        </c:when>
        <c:when test="${not empty externalResults}">
          <ul class="tmdb-results" aria-label="TMDB 검색 결과">
            <c:forEach items="${externalResults}" var="work">
              <li class="tmdb-result">
                <div class="tmdb-poster">
                  <span>포스터 없음</span>
                  <img hidden alt="<c:out value='${work.title}'/> 포스터" data-poster-path="<c:out value='${work.posterPath}'/>" referrerpolicy="no-referrer">
                </div>
                <div>
                  <h2><c:out value="${work.title}"/></h2>
                  <p><c:out value="${work.mediaType=='TV'?'드라마':'영화'}"/> · <c:out value="${work.releaseYear}" default="연도 미상"/></p>
                  <c:if test="${not empty work.overview}"><p><c:out value="${work.overview}"/></p></c:if>
                  <c:if test="${not empty work.externalId}">
                    <div class="form-actions">
                      <a class="link" href="https://www.themoviedb.org/${work.mediaType=='TV'?'tv':'movie'}/<c:out value='${work.externalId}'/>">TMDB 작품 정보</a>
                      <form method="post" action="<c:url value='/works/import-external'/>">
                        <%@ include file="common/csrf.jspf" %>
                        <input type="hidden" name="externalId" value="<c:out value='${work.externalId}'/>">
                        <input type="hidden" name="mediaType" value="<c:out value='${work.mediaType}'/>">
                        <input type="hidden" name="confirmDuplicate" value="false">
                        <button class="button small" type="submit">이 작품 등록</button>
                      </form>
                    </div>
                  </c:if>
                </div>
              </li>
            </c:forEach>
          </ul>
        </c:when>
      </c:choose>
    </c:otherwise>
  </c:choose>
  <nav class="form-actions" aria-label="다른 작품 찾기">
    <a class="button secondary" href="<c:out value='${localUrl}'/>">등록된 작품 검색</a>
    <a class="link" href="<c:out value='${manualUrl}'/>">작품 직접 등록</a>
  </nav>
</section>
<section class="panel pad form-card section-gap tmdb-credit" aria-labelledby="tmdb-credit-title">
  <h2 id="tmdb-credit-title">작품 정보 출처</h2>
  <a href="https://www.themoviedb.org/"><img src="<c:url value='/resources/images/tmdb-logo.svg'/>" width="76" alt="TMDB"></a>
  <p class="muted small-text" lang="en">This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.</p>
</section>
<%@ include file="common/footer.jspf" %>
