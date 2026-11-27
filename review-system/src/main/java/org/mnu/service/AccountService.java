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

/**
 * @brief 회원가입 검증과 Spring Security의 계정 조회를 담당하는 서비스입니다.
 * @details 계정은 기본 USER 권한으로 저장하고 비밀번호 원문 대신 주입된 인코더의 해시를 저장합니다.
 * 중복 아이디 사전 검사와 DB 유일 키 충돌을 같은 입력 오류로 처리합니다.
 */
@Service
public class AccountService implements UserDetailsService {
    /**
     * @brief 회원 계정 조회와 저장을 수행하는 MyBatis Mapper입니다.
     */
    private final AccountMapper mapper;
    /**
     * @brief 가입 비밀번호를 해시로 변환하는 비밀번호 인코더입니다.
     */
    private final PasswordEncoder encoder;
    /**
     * @brief 계정 저장소와 비밀번호 인코더를 주입받습니다.
     * @param mapper 회원 계정 Mapper
     * @param encoder 비밀번호 해시 인코더
     */
    public AccountService(AccountMapper mapper, PasswordEncoder encoder) { this.mapper = mapper; this.encoder = encoder; }
    /**
     * @brief 아이디로 인증에 필요한 회원 식별자와 권한 정보를 조회합니다.
     * @param username 조회할 로그인 아이디
     * @return DB의 회원 식별자, 표시 이름, 비밀번호 해시와 권한을 가진 인증 객체
     * @throws UsernameNotFoundException 해당 아이디의 계정이 없는 경우
     */
    public UserDetails loadUserByUsername(String username) {
        Map<String,Object> row = mapper.byUsername(username);
        if (row == null) throw new UsernameNotFoundException("아이디 또는 비밀번호를 확인하세요.");
        return new AccountPrincipal(((Number)row.get("id")).longValue(), (String)row.get("username"), (String)row.get("passwordHash"), (String)row.get("name"), (String)row.get("role"));
    }
    /**
     * @brief 가입 입력을 검증하고 새 회원을 하나의 트랜잭션으로 저장합니다.
     * @details 아이디 형식, 이름과 메일 길이, 특수문자를 포함한 비밀번호의 최소 길이와
     * UTF-8 최대 72바이트를 검사합니다. DB Mapper는 신규 권한을 USER로 고정합니다.
     * @param form 아이디, 비밀번호, 이름과 메일을 담은 가입 입력
     * @return 생성된 회원 식별자
     * @throws InputError 입력 형식이 잘못되었거나 아이디가 중복되는 경우
     */
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
