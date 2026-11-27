package org.mnu.domain;

/**
 * @brief 감상 기록 입력과 재전송 및 낙관적 잠금 정보를 전달하는 폼입니다.
 */
public class RecordForm {
    private long workId; /**< @brief 감상 대상 공용 작품의 식별자입니다. */
    private int version; /**< @brief 수정 화면에서 읽은 버전으로 최신 기록과 충돌을 검사합니다. */
    private String viewedOn = "" /**< @brief 서비스에서 검증할 감상일 입력 문자열입니다. */,
        rating = "" /**< @brief 선택 평점 문자열이며 빈 문자열은 미평점을 뜻합니다. */,
        review = "" /**< @brief 회원이 작성한 감상평 원문입니다. */,
        action = "PAST" /**< @brief PAST는 기록만, COMPLETE는 기록과 현재 완료 상태를 함께 저장하는 요청입니다. */,
        token = "" /**< @brief 세션 검사와 DB 재전송 판정에 사용하는 신규 작성 요청 UUID입니다. */;
    /**
     * @brief 작품 식별자 값을 반환합니다.
     * @return 작품 식별자
     */
    public long getWorkId() { return workId; }
    /**
     * @brief 감상 대상 작품 식별자를 설정합니다.
     * @param v 작품 식별자
     */
    public void setWorkId(long v) { workId = v; }
    /**
     * @brief 화면에서 읽은 기록 버전 값을 반환합니다.
     * @return 화면에서 읽은 기록 버전
     */
    public int getVersion() { return version; }
    /**
     * @brief 낙관적 잠금 비교에 사용할 제출 버전을 설정합니다.
     * @param v 화면에서 읽은 기록 버전
     */
    public void setVersion(int v) { version = v; }
    /**
     * @brief 감상일 문자열 값을 반환합니다.
     * @return 감상일 문자열
     */
    public String getViewedOn() { return viewedOn; }
    /**
     * @brief null을 빈 문자열로 바꾸고 감상일 원문을 보관합니다.
     * @param v 감상일 문자열
     */
    public void setViewedOn(String v) { viewedOn = v == null ? "" : v; }
    /**
     * @brief 평점 입력 문자열 값을 반환합니다.
     * @return 평점 입력 문자열
     */
    public String getRating() { return rating; }
    /**
     * @brief null을 빈 문자열로 바꾸며 실제 평점 검증은 서비스에 맡깁니다.
     * @param v 평점 입력 문자열
     */
    public void setRating(String v) { rating = v == null ? "" : v; }
    /**
     * @brief 감상평 원문 값을 반환합니다.
     * @return 감상평 원문
     */
    public String getReview() { return review; }
    /**
     * @brief null을 빈 문자열로 바꾸고 감상평을 보관합니다.
     * @param v 감상평 원문
     */
    public void setReview(String v) { review = v == null ? "" : v; }
    /**
     * @brief 저장 동작 코드 값을 반환합니다.
     * @return 저장 동작 코드
     */
    public String getAction() { return action; }
    /**
     * @brief 저장 동작을 그대로 보관하며 허용값 검사는 서비스가 수행합니다.
     * @param v 저장 동작 코드
     */
    public void setAction(String v) { action = v; }
    /**
     * @brief 작성 요청 UUID 값을 반환합니다.
     * @return 작성 요청 UUID
     */
    public String getToken() { return token; }
    /**
     * @brief 작성 요청 토큰을 그대로 보관합니다.
     * @param v 작성 요청 UUID
     */
    public void setToken(String v) { token = v; }
}
