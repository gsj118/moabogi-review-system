package org.mnu.controller;
import org.mnu.domain.AccountPrincipal;
import org.springframework.security.core.Authentication;
/**
 * @brief 인증 주체에서 회원 식별자와 관리자 권한을 읽는 유틸리티입니다.
 */
public final class Current {
    /**
     * @brief 유틸리티 인스턴스 생성을 막습니다.
     */
    private Current(){}
    /**
     * @brief 인증 principal의 회원 식별자를 반환합니다.
     * @param auth AccountPrincipal을 가진 인증 정보
     * @return 인증된 회원의 DB 식별자
     * @throws ClassCastException principal이 AccountPrincipal 타입이 아닌 경우
     */
    public static long id(Authentication auth){return ((AccountPrincipal)auth.getPrincipal()).getId();}
    /**
     * @brief 인증 정보에 ROLE_ADMIN 권한이 포함됐는지 확인합니다.
     * @param auth 로그인 회원의 인증 정보
     * @return 관리자 권한이 있으면 true
     */
    public static boolean admin(Authentication auth){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));}
}
