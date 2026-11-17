package org.mnu.domain;
/**
 * @brief 회원가입 입력을 보관하며 기본 문자열 정규화를 수행하는 폼입니다.
 */
public class SignupForm {
    private String username = "" /**< @brief 가입 아이디이며 null은 빈 문자열로 바꾸고 앞뒤 공백을 제거합니다. */,
        password = "" /**< @brief 가입 시 원문 비밀번호이며 해싱은 서비스가 담당합니다. */,
        name = "" /**< @brief 화면에 표시할 회원 이름입니다. */,
        email = "" /**< @brief 회원가입 메일 주소입니다. */;
    /**
     * @brief 가입 아이디 값을 반환합니다.
     * @return 가입 아이디
     */
    public String getUsername() { return username; }
    /**
     * @brief null을 빈 문자열로 바꾸고 아이디의 앞뒤 공백을 제거합니다.
     * @param v 가입 아이디
     */
    public void setUsername(String v) { username = v == null ? "" : v.trim(); }
    /**
     * @brief 입력한 원문 비밀번호 값을 반환합니다.
     * @return 입력한 원문 비밀번호
     */
    public String getPassword() { return password; }
    /**
     * @brief null을 빈 문자열로 바꾸며 비밀번호의 공백은 보존합니다.
     * @param v 입력한 원문 비밀번호
     */
    public void setPassword(String v) { password = v == null ? "" : v; }
    /**
     * @brief 회원 이름 값을 반환합니다.
     * @return 회원 이름
     */
    public String getName() { return name; }
    /**
     * @brief null을 빈 문자열로 바꾸고 이름의 앞뒤 공백을 제거합니다.
     * @param v 회원 이름
     */
    public void setName(String v) { name = v == null ? "" : v.trim(); }
    /**
     * @brief 메일 주소 값을 반환합니다.
     * @return 메일 주소
     */
    public String getEmail() { return email; }
    /**
     * @brief null을 빈 문자열로 바꾸고 메일 주소의 앞뒤 공백을 제거합니다.
     * @param v 메일 주소
     */
    public void setEmail(String v) { email = v == null ? "" : v.trim(); }
}
