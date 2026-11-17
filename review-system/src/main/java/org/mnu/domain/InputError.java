package org.mnu.domain;

/**
 * @brief 사용자 오류 안내와 HTTP 상태를 전달하는 실행 예외입니다.
 */
public class InputError extends RuntimeException {
    /**
     * @brief 직렬화 호환성 식별자입니다.
     */
    private static final long serialVersionUID = 1L;
    /**
     * @brief 오류 응답에 사용할 HTTP 상태 코드입니다.
     */
    private final int status;
    /**
     * @brief 기본 HTTP 400 상태로 사용자 입력 오류를 생성합니다.
     * @param message 화면에 표시할 오류 안내
     */
    public InputError(String message) { this(message, 400); }
    /**
     * @brief 지정한 HTTP 상태와 사용자 안내로 오류를 생성합니다.
     * @param message 화면에 표시할 오류 안내
     * @param status 응답 HTTP 상태 코드
     */
    public InputError(String message, int status) { super(message); this.status = status; }
    /**
     * @brief 오류 응답의 HTTP 상태를 반환합니다.
     * @return 생성 시 지정한 HTTP 상태 코드
     */
    public int getStatus() { return status; }
}
