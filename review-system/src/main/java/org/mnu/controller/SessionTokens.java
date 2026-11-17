package org.mnu.controller;
import java.util.*;
import javax.servlet.http.HttpSession;
import org.mnu.domain.InputError;
/**
 * @brief HTTP 세션에 최근 64개의 감상 작성 UUID를 보관하는 유틸리티입니다.
 */
public final class SessionTokens {
    /**
     * @brief 유틸리티 인스턴스 생성을 막습니다.
     */
    private SessionTokens(){}
    /**
     * @brief 세션의 작성 토큰 집합을 얻거나 빈 집합을 만들어 보관합니다.
     * @param session 작성 요청 토큰을 보관할 HTTP 세션
     * @return 세션에 보관된 삽입 순서 유지 토큰 집합
     */
    @SuppressWarnings("unchecked") private static LinkedHashSet<String> tokens(HttpSession session){LinkedHashSet<String> set=(LinkedHashSet<String>)session.getAttribute("recordTokens");if(set==null){set=new LinkedHashSet<String>();session.setAttribute("recordTokens",set);}return set;}
    /**
     * @brief 세션 잠금 안에서 새 UUID를 발급하고 64개를 넘으면 가장 오래된 토큰을 제거합니다.
     * @param session 발급 토큰을 보관할 HTTP 세션
     * @return 새 감상 작성 요청에 사용할 UUID 문자열
     */
    public static String issue(HttpSession session){synchronized(session){LinkedHashSet<String> set=tokens(session);String token=UUID.randomUUID().toString();set.add(token);while(set.size()>64)set.remove(set.iterator().next());return token;}}
    /**
     * @brief 제출 UUID가 현재 세션의 발급 목록에 있는지 확인하며 토큰은 소비하지 않습니다.
     * @param session 작성 요청을 발급한 HTTP 세션
     * @param token 제출된 감상 작성 UUID
     * @throws InputError 세션에 발급 기록이 없는 토큰인 경우
     */
    public static void check(HttpSession session,String token){synchronized(session){if(!tokens(session).contains(token))throw new InputError("작성 화면을 다시 열어 저장하세요.");}}
}
