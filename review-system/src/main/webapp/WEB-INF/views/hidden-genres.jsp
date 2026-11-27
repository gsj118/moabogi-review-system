<%@ page pageEncoding="UTF-8" %>
<%@ include file="common/header.jspf" %>
<section class="panel pad form-card">
  <p>선택한 장르가 포함된 작품을 등록된 작품 목록과 검색 결과에서 숨깁니다. 내 계정에만 적용되며, 체크를 해제하고 저장하면 다시 볼 수 있어요.</p>
  <form method="post" action="<c:url value='/works/hidden-genres'/>" data-progress>
    <%@ include file="common/csrf.jspf" %>
    <fieldset class="field">
      <legend>숨길 장르</legend>
      <c:forEach items="${genres}" var="g">
        <label class="choice">
          <input type="checkbox" name="hide" value="${g.id}" ${g.hidden == 1 ? 'checked' : ''}>
          <span><c:out value="${g.name}"/></span>
        </label>
      </c:forEach>
    </fieldset>
    <div class="form-actions">
      <button class="button" type="submit">저장</button>
      <a class="button secondary" href="<c:url value='/works'/>">작품 목록으로 돌아가기</a>
    </div>
  </form>
</section>
<%@ include file="common/footer.jspf" %>
