package org.mnu.domain;
import java.util.ArrayList;
import java.util.List;
/**
 * @brief 공용 작품 작성·수정 입력과 수동 중복 확인 여부를 전달하는 폼입니다.
 */
public class WorkForm {
    private String title = "" /**< @brief 공용 작품의 제목입니다. */,
        mediaType = "MOVIE" /**< @brief 작품 유형 코드이며 기본값은 MOVIE입니다. */,
        synopsis = "" /**< @brief 선택 입력인 작품 소개입니다. */,
        releaseYear = "" /**< @brief 선택 입력인 개봉·방영 연도 문자열입니다. */;
    private List<Long> genreIds = new ArrayList<Long>(); /**< @brief 작품에 연결할 장르 식별자 목록입니다. */
    private boolean confirmDuplicate; /**< @brief 수동 중복 후보를 확인한 뒤 계속 등록할지 나타냅니다. */
    /**
     * @brief 작품 제목 값을 반환합니다.
     * @return 작품 제목
     */
    public String getTitle() { return title; }
    /**
     * @brief null을 빈 문자열로 바꾸고 제목의 앞뒤 공백을 제거합니다.
     * @param v 작품 제목
     */
    public void setTitle(String v) { title = v == null ? "" : v.trim(); }
    /**
     * @brief 작품 유형 코드 값을 반환합니다.
     * @return 작품 유형 코드
     */
    public String getMediaType() { return mediaType; }
    /**
     * @brief 작품 유형을 그대로 보관하며 허용값 검사는 서비스가 수행합니다.
     * @param v 작품 유형 코드
     */
    public void setMediaType(String v) { mediaType = v; }
    /**
     * @brief 작품 소개 값을 반환합니다.
     * @return 작품 소개
     */
    public String getSynopsis() { return synopsis; }
    /**
     * @brief null을 빈 문자열로 바꾸고 소개 원문을 보관합니다.
     * @param v 작품 소개
     */
    public void setSynopsis(String v) { synopsis = v == null ? "" : v; }
    /**
     * @brief 개봉·방영 연도 문자열 값을 반환합니다.
     * @return 개봉·방영 연도 문자열
     */
    public String getReleaseYear() { return releaseYear; }
    /**
     * @brief null을 빈 문자열로 바꾸고 연도 입력을 보관합니다.
     * @param v 개봉·방영 연도 문자열
     */
    public void setReleaseYear(String v) { releaseYear = v == null ? "" : v; }
    /**
     * @brief 연결 장르 식별자 목록 값을 반환합니다.
     * @return 연결 장르 식별자 목록
     */
    public List<Long> getGenreIds() { return genreIds; }
    /**
     * @brief null이면 빈 목록으로 바꾸고 그 밖에는 전달된 목록을 보관합니다.
     * @param v 연결 장르 식별자 목록
     */
    public void setGenreIds(List<Long> v) { genreIds = v == null ? new ArrayList<Long>() : v; }
    /**
     * @brief 수동 중복 후보 확인 여부 값을 반환합니다.
     * @return 수동 중복 후보 확인 여부
     */
    public boolean isConfirmDuplicate() { return confirmDuplicate; }
    /**
     * @brief 수동 중복 후보 확인 여부를 설정합니다.
     * @param v 수동 중복 후보 확인 여부
     */
    public void setConfirmDuplicate(boolean v) { confirmDuplicate = v; }
}
