<%@ page pageEncoding="UTF-8" %>
<%@ include file="common/header.jspf" %>
<p class="page-description">한 작품을 여러 번 봤다면, 각 감상을 별개 기록으로 남겨요. 감상 순서와 이전 평점 비교는 필터·페이지와 무관한 나의 전체 이력 기준입니다.</p>
<c:if test="${filter.workId>0}">
  <c:url var="scopeClearUrl" value="/records"><c:param name="keyword" value="${filter.keyword}"/><c:param name="mediaType" value="${filter.mediaType}"/><c:param name="genreId" value="${filter.genreId}"/><c:param name="from" value="${filter.from}"/><c:param name="to" value="${filter.to}"/><c:param name="sort" value="${filter.sort}"/></c:url>
  <section class="notice" aria-label="작품 범위">
    <p><strong>현재 작품 범위:</strong> <c:choose><c:when test="${not empty records}"><c:out value="${records[0].title}"/></c:when><c:otherwise>선택한 작품</c:otherwise></c:choose>의 감상 기록만 조회하고 있습니다.</p>
    <p class="small-text">제목·유형·장르·기간 조건도 함께 적용됩니다. 작품 범위를 해제해도 다른 조회 조건은 유지됩니다.</p>
    <div class="form-actions"><a class="button secondary" href="<c:url value='/works/${filter.workId}'/>">선택 작품 보기</a><a class="button secondary" href="<c:out value='${scopeClearUrl}'/>">작품 범위 해제</a></div>
  </section>
</c:if>
<form class="panel pad filters" method="get" action="<c:url value='/records'/>">
  <%@ include file="common/filter.jspf" %>
</form>
<c:if test="${not queryError}"><section class="panel"><%@ include file="common/record-list.jspf" %></section><%@ include file="common/pagination.jspf" %></c:if>
<%@ include file="common/footer.jspf" %>
