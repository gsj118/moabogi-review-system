package org.mnu.config;

import org.mnu.service.AccountService;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    private final AccountService accounts;
    public SecurityConfig(AccountService accounts) { this.accounts = accounts; }
    @Bean public static PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Override protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(accounts).passwordEncoder(passwordEncoder());
    }
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
        http.headers().contentSecurityPolicy("default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        // Spring Security's CSRF protection remains enabled, including login/logout.
    }
}
