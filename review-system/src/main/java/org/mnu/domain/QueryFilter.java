package org.mnu.domain;

import java.time.LocalDate;

/**
 * @brief 작품·기록·통계의 공통 조회 조건과 페이지 계산 및 검증을 담당합니다.
 */
public class QueryFilter {
    private String keyword = "" /**< @brief 제목 검색어입니다. */,
        mediaType = "" /**< @brief 작품 유형이며 빈 문자열은 전체 유형입니다. */,
        from = "" /**< @brief 시작일 원문이며 빈 문자열은 시작일 제한 없음입니다. */,
        to = "" /**< @brief 종료일 원문이며 빈 문자열은 종료일 제한 없음입니다. */,
        sort = "recent" /**< @brief 허용 정렬은 recent, oldest, rating입니다. */;
    private long genreId /**< @brief 장르 범위이며 0은 장르 제한 없음입니다. */,
        workId /**< @brief 작품 범위이며 0은 작품 제한 없음입니다. */;
    /**
     * @brief 1부터 시작하는 현재 조회 페이지입니다.
     */
    private int page = 1;
    /**
     * @brief 검색어 길이·유형·정렬·식별자·기간 순서를 검증합니다.
     * @throws InputError 허용되지 않은 조건 또는 올바르지 않은 날짜·기간인 경우
     */
    public void validate() {
        if (keyword.length() > 200) throw new InputError("검색어는 200자 이내로 입력하세요.");
        if (!mediaType.isEmpty() && !mediaType.equals("MOVIE") && !mediaType.equals("TV")) throw new InputError("영화 또는 드라마를 선택하세요.");
        if (!sort.equals("recent") && !sort.equals("oldest") && !sort.equals("rating")) throw new InputError("허용된 정렬 기준을 선택하세요.");
        if (genreId < 0 || workId < 0) throw new InputError("조회 조건을 확인하세요.");
        if (getStart() != null && getEnd() != null && getStart().isAfter(getEnd())) throw new InputError("시작일은 종료일보다 늦을 수 없습니다.");
    }
    /**
     * @brief 선택 날짜 문자열을 LocalDate로 변환합니다.
     * @param value YYYY-MM-DD 날짜 또는 빈 값
     * @return 변환된 날짜이며 null 또는 빈 문자열이면 null
     * @throws InputError 날짜 파싱에 실패한 경우
     */
    private LocalDate date(String value) {
        if (value == null || value.isEmpty()) return null;
        try { return LocalDate.parse(value); } catch (RuntimeException e) { throw new InputError("날짜를 YYYY-MM-DD 형식으로 입력하세요."); }
    }
    /**
     * @brief 조회 시작일을 날짜로 반환합니다.
     * @return 시작일이며 빈 입력이면 null
     * @throws InputError 시작일 형식이 잘못된 경우
     */
    public LocalDate getStart() { return date(from); }
    /**
     * @brief 조회 종료일을 날짜로 반환합니다.
     * @return 종료일이며 빈 입력이면 null
     * @throws InputError 종료일 형식이 잘못된 경우
     */
    public LocalDate getEnd() { return date(to); }
    /**
     * @brief 현재 페이지의 SQL 조회 시작 위치를 long으로 계산합니다.
     * @return 0부터 시작하는 행 오프셋
     */
    public long getOffset() { return ((long)page - 1) * getSize(); }
    /**
     * @brief 총건수에 맞추어 현재 페이지를 1부터 마지막 유효 페이지 사이로 보정합니다.
     * @param total 같은 조건으로 조회한 전체 건수
     */
    public void clampPage(long total) { long last=total==0?1:1+(total-1)/getSize();page=(int)Math.max(1,Math.min(page,last)); }
    /**
     * @brief 페이지 문자열을 1부터 Integer.MAX_VALUE 사이로 정규화합니다.
     * @param value 요청으로 전달된 페이지 문자열
     * @return 유효한 페이지 정수이며 null·비숫자는 1
     */
    public static int parsePage(String value) {
        try{return new java.math.BigInteger(value.trim()).max(java.math.BigInteger.ONE).min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).intValue();}
        catch(RuntimeException e){return 1;}
    }
    /**
     * @brief 한 페이지의 고정 조회 건수를 반환합니다.
     * @return 페이지당 15건
     */
    public int getSize() { return 15; }
    /**
     * @brief LIKE 검색어의 =, %, _ 문자를 이스케이프합니다.
     * @return SQL Mapper의 ESCAPE 규칙에 맞춘 검색어
     */
    public String getTerm() { return keyword.replace("=", "==").replace("%", "=%").replace("_", "=_"); }
    /**
     * @brief 검색어 값을 반환합니다.
     * @return 검색어
     */
    public String getKeyword() { return keyword; }
    /**
     * @brief null을 빈 문자열로 바꾸고 검색어의 앞뒤 공백을 제거합니다.
     * @param v 검색어
     */
    public void setKeyword(String v) { keyword = v == null ? "" : v.trim(); }
    /**
     * @brief 작품 유형 조건 값을 반환합니다.
     * @return 작품 유형 조건
     */
    public String getMediaType() { return mediaType; }
    /**
     * @brief null을 빈 문자열로 바꾸고 작품 유형 조건을 보관합니다.
     * @param v 작품 유형 조건
     */
    public void setMediaType(String v) { mediaType = v == null ? "" : v; }
    /**
     * @brief 시작일 원문 값을 반환합니다.
     * @return 시작일 원문
     */
    public String getFrom() { return from; }
    /**
     * @brief null을 빈 문자열로 바꾸고 시작일 원문을 보관합니다.
     * @param v 시작일 원문
     */
    public void setFrom(String v) { from = v == null ? "" : v; }
    /**
     * @brief 종료일 원문 값을 반환합니다.
     * @return 종료일 원문
     */
    public String getTo() { return to; }
    /**
     * @brief null을 빈 문자열로 바꾸고 종료일 원문을 보관합니다.
     * @param v 종료일 원문
     */
    public void setTo(String v) { to = v == null ? "" : v; }
    /**
     * @brief 정렬 코드 값을 반환합니다.
     * @return 정렬 코드
     */
    public String getSort() { return sort; }
    /**
     * @brief null이면 recent로 바꾸며 허용 정렬 검사는 validate에서 수행합니다.
     * @param v 정렬 코드
     */
    public void setSort(String v) { sort = v == null ? "recent" : v; }
    /**
     * @brief 장르 식별자 조건 값을 반환합니다.
     * @return 장르 식별자 조건
     */
    public long getGenreId() { return genreId; }
    /**
     * @brief 장르 범위를 설정하며 음수 검사는 validate에서 수행합니다.
     * @param v 장르 식별자 조건
     */
    public void setGenreId(long v) { genreId = v; }
    /**
     * @brief 작품 식별자 조건 값을 반환합니다.
     * @return 작품 식별자 조건
     */
    public long getWorkId() { return workId; }
    /**
     * @brief 작품 범위를 설정하며 음수 검사는 validate에서 수행합니다.
     * @param v 작품 식별자 조건
     */
    public void setWorkId(long v) { workId = v; }
    /**
     * @brief 현재 페이지 값을 반환합니다.
     * @return 현재 페이지
     */
    public int getPage() { return page; }
    /**
     * @brief 현재 페이지를 최소 1로 설정합니다.
     * @param v 현재 페이지
     */
    public void setPage(int v) { page = Math.max(1,v); }
}
