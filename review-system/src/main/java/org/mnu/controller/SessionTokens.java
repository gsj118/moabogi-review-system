package org.mnu.controller;
import java.util.*;
import javax.servlet.http.HttpSession;
import org.mnu.domain.InputError;
public final class SessionTokens {
    private SessionTokens(){}
    @SuppressWarnings("unchecked") private static LinkedHashSet<String> tokens(HttpSession session){LinkedHashSet<String> set=(LinkedHashSet<String>)session.getAttribute("recordTokens");if(set==null){set=new LinkedHashSet<String>();session.setAttribute("recordTokens",set);}return set;}
    public static String issue(HttpSession session){synchronized(session){LinkedHashSet<String> set=tokens(session);String token=UUID.randomUUID().toString();set.add(token);while(set.size()>64)set.remove(set.iterator().next());return token;}}
    public static void check(HttpSession session,String token){synchronized(session){if(!tokens(session).contains(token))throw new InputError("작성 화면을 다시 열어 저장하세요.");}}
}
