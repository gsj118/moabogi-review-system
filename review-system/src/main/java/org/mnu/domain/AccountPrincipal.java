package org.mnu.domain;

import java.util.Collections;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final long id;
    private final String displayName;
    public AccountPrincipal(long id, String username, String hash, String name, String role) {
        super(username, hash, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        this.id = id; this.displayName = name;
    }
    public long getId() { return id; }
    public String getDisplayName() { return displayName; }
}
