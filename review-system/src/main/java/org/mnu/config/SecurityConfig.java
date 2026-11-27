package org.mnu.config;

import org.mnu.service.AccountService;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @brief URL 권한·로그인·로그아웃·세션·CSRF·CSP를 구성하는 Spring Security 설정입니다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    /**
     * @brief 저장된 회원 정보를 인증에 제공하는 서비스입니다.
     */
    private final AccountService accounts;
    /**
     * @brief 회원 인증 서비스를 주입받습니다.
     * @param accounts UserDetailsService를 구현한 회원 서비스
     */
    public SecurityConfig(AccountService accounts) { this.accounts = accounts; }
    /**
     * @brief 비밀번호 저장·인증에 사용할 BCrypt 인코더를 제공합니다.
     * @return 작업 강도 12의 BCryptPasswordEncoder
     */
    @Bean public static PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    /**
     * @brief 회원 조회 서비스와 BCrypt 비밀번호 비교를 인증 관리자에 연결합니다.
     * @param auth Spring Security 인증 관리자 구성기
     * @throws Exception 인증 공급자 구성에 실패한 경우
     */
    @Override protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(accounts).passwordEncoder(passwordEncoder());
    }
    /**
     * @brief 공개 URL·관리자 경로·인증 필수 범위와 세션 갱신·무효화 및 CSP를 설정합니다.
     * @param http 웹 보안 필터 체인 구성기
     * @throws Exception 웹 보안 설정에 실패한 경우
     */
    @Override protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
            .antMatchers("/resources/**", "/login", "/signup", "/health").permitAll()
            .antMatchers("/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
            .and().formLogin().loginPage("/login").loginProcessingUrl("/login")
            .defaultSuccessUrl("/", true).failureHandler((request,response,exception)->{
                String username=request.getParameter("username");
                request.getSession().setAttribute("loginUsername",username==null?"":username.substring(0,Math.min(40,username.length())));
                response.sendRedirect(request.getContextPath()+"/login?error");
            }).permitAll()
            .and().logout().logoutUrl("/logout").logoutSuccessUrl("/login?logout")
            .invalidateHttpSession(true).deleteCookies("JSESSIONID")
            .and().sessionManagement().sessionFixation().changeSessionId();
        http.headers().contentSecurityPolicy("default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data: https://image.tmdb.org; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        // Spring Security's CSRF protection remains enabled, including login/logout.
    }
}
