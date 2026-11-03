package org.mnu.service;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.mnu.domain.*;
import org.mnu.mapper.AccountMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements UserDetailsService {
    private final AccountMapper mapper;
    private final PasswordEncoder encoder;
    public AccountService(AccountMapper mapper, PasswordEncoder encoder) { this.mapper = mapper; this.encoder = encoder; }
    public UserDetails loadUserByUsername(String username) {
        Map<String,Object> row = mapper.byUsername(username);
        if (row == null) throw new UsernameNotFoundException("아이디 또는 비밀번호를 확인하세요.");
        return new AccountPrincipal(((Number)row.get("id")).longValue(), (String)row.get("username"), (String)row.get("passwordHash"), (String)row.get("name"), (String)row.get("role"));
    }
    @Transactional public long register(SignupForm form) {
        if (!form.getUsername().matches("[a-zA-Z0-9_]{4,40}")) throw new InputError("아이디는 영문·숫자·밑줄 4~40자로 입력하세요.");
        String password = form.getPassword();
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72 || !password.matches("(?s).*[\\p{Punct}].*")) throw new InputError("비밀번호는 특수문자를 포함한 8자 이상, UTF-8 72바이트 이내로 입력하세요.");
        if (form.getName().isEmpty() || form.getName().length() > 60) throw new InputError("이름은 1~60자로 입력하세요.");
        if (form.getEmail().length() > 254 || !form.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new InputError("올바른 메일 주소를 입력하세요.");
        if (mapper.byUsername(form.getUsername()) != null) throw new InputError("이미 사용 중인 아이디입니다. 다른 아이디를 입력하세요.");
        Map<String,Object> row = new HashMap<String,Object>();
        row.put("username", form.getUsername()); row.put("name", form.getName()); row.put("email", form.getEmail()); row.put("passwordHash", encoder.encode(password));
        try { mapper.insert(row); } catch (DuplicateKeyException e) { throw new InputError("이미 사용 중인 아이디입니다. 다른 아이디를 입력하세요."); }
        return ((Number)row.get("id")).longValue();
    }
}
