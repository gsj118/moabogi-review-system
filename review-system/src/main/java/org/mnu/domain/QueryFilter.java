package org.mnu.domain;

import java.time.LocalDate;

public class QueryFilter {
    private String keyword = "", mediaType = "", from = "", to = "", sort = "recent";
    private long genreId, workId;
    private int page = 1;
    public void validate() {
        if (keyword.length() > 200) throw new InputError("검색어는 200자 이내로 입력하세요.");
        if (!mediaType.isEmpty() && !mediaType.equals("MOVIE") && !mediaType.equals("TV")) throw new InputError("영화 또는 드라마를 선택하세요.");
        if (!sort.equals("recent") && !sort.equals("oldest") && !sort.equals("rating")) throw new InputError("허용된 정렬 기준을 선택하세요.");
        if (genreId < 0 || workId < 0) throw new InputError("조회 조건을 확인하세요.");
        if (getStart() != null && getEnd() != null && getStart().isAfter(getEnd())) throw new InputError("시작일은 종료일보다 늦을 수 없습니다.");
    }
    private LocalDate date(String value) {
        if (value == null || value.isEmpty()) return null;
        try { return LocalDate.parse(value); } catch (RuntimeException e) { throw new InputError("날짜를 YYYY-MM-DD 형식으로 입력하세요."); }
    }
    public LocalDate getStart() { return date(from); }
    public LocalDate getEnd() { return date(to); }
    public long getOffset() { return ((long)page - 1) * getSize(); }
    public void clampPage(long total) { long last=total==0?1:1+(total-1)/getSize();page=(int)Math.max(1,Math.min(page,last)); }
    public static int parsePage(String value) {
        try{return new java.math.BigInteger(value.trim()).max(java.math.BigInteger.ONE).min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).intValue();}
        catch(RuntimeException e){return 1;}
    }
    public int getSize() { return 15; }
    public String getTerm() { return keyword.replace("=", "==").replace("%", "=%").replace("_", "=_"); }
    public String getKeyword() { return keyword; } public void setKeyword(String v) { keyword = v == null ? "" : v.trim(); }
    public String getMediaType() { return mediaType; } public void setMediaType(String v) { mediaType = v == null ? "" : v; }
    public String getFrom() { return from; } public void setFrom(String v) { from = v == null ? "" : v; }
    public String getTo() { return to; } public void setTo(String v) { to = v == null ? "" : v; }
    public String getSort() { return sort; } public void setSort(String v) { sort = v == null ? "recent" : v; }
    public long getGenreId() { return genreId; } public void setGenreId(long v) { genreId = v; }
    public long getWorkId() { return workId; } public void setWorkId(long v) { workId = v; }
    public int getPage() { return page; } public void setPage(int v) { page = Math.max(1,v); }
}
