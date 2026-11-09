package org.mnu.controller;
import org.mnu.domain.AccountPrincipal;
import org.springframework.security.core.Authentication;
public final class Current {
    private Current(){}
    public static long id(Authentication auth){return ((AccountPrincipal)auth.getPrincipal()).getId();}
    public static boolean admin(Authentication auth){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));}
}
