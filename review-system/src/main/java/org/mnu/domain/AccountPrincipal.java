package org.mnu.domain;

import java.util.Collections;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * @brief Spring Security 사용자 정보에 회원 식별자와 표시 이름을 더한 인증 주체입니다.
 */
public class AccountPrincipal extends User {
    /**
     * @brief 직렬화 호환성 식별자입니다.
     */
    private static final long serialVersionUID = 1L;
    /**
     * @brief 인증된 회원의 DB 식별자입니다.
     */
    private final long id;
    /**
     * @brief 화면에 표시하는 회원 이름입니다.
     */
    private final String displayName;
    /**
     * @brief 저장된 회원 정보와 ROLE_ 접두 권한으로 인증 주체를 구성합니다.
     * @param id 회원 식별자
     * @param username 로그인 아이디
     * @param hash 저장된 비밀번호 해시
     * @param name 표시 이름
     * @param role ROLE_ 접두어를 붙이기 전 권한 코드
     */
    public AccountPrincipal(long id, String username, String hash, String name, String role) {
        super(username, hash, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        this.id = id; this.displayName = name;
    }
    /**
     * @brief 인증된 회원의 식별자를 반환합니다.
     * @return 개인 기록 소유권 조회에 사용하는 회원 식별자
     */
    public long getId() { return id; }
    /**
     * @brief 화면에 표시할 회원 이름을 반환합니다.
     * @return 회원의 표시 이름
     */
    public String getDisplayName() { return displayName; }
}
